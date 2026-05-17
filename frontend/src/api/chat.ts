import type { ChatRequest, ChatResponse, RagQueryRequest, RagQueryResponse } from '../types'

const API_BASE = '/api'

/** 防御性清理：移除可能从底层 SSE 层泄露的嵌套协议前缀 */
function stripSSEArtifacts(text: string): string {
  return text
    .replace(/^data:\s*/gm, '')
    .replace(/^event:\s*\w+\s*$/gm, '')
    .replace(/^id:\s*\S+\s*$/gm, '')
    .trim()
}

async function request<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`请求失败 (${res.status}): ${text || res.statusText}`)
  }
  return res.json()
}

/** 开始新对话 */
export function startNewChat(req: ChatRequest): Promise<ChatResponse> {
  return request<ChatResponse>(`${API_BASE}/chat/start`, req)
}

/** 继续已有对话 */
export function continueChat(req: ChatRequest): Promise<ChatResponse> {
  return request<ChatResponse>(`${API_BASE}/chat/continue`, req)
}

/**
 * 流式对话：返回 AbortController 用于取消。
 *
 * 后端 SSE 协议（注意第一个 chatId 事件以 \n 结尾，后续数据以 \n\n 结尾）：
 *   event:chatId\ndata:<id>\n
 *   data:<chunk>\n\n
 *   data:<chunk>\n\n
 *
 * 采用逐行解析（空行 = 事件边界），兼容所有换行格式。
 */
export function streamChat(
  req: ChatRequest,
  onChunk: (text: string) => void,
  onDone: (chatId: string) => void,
  onError: (err: Error) => void,
): AbortController {
  const endpoint = req.chatId
    ? `${API_BASE}/chat/continue/stream`
    : `${API_BASE}/chat/start/stream`

  const controller = new AbortController()

  fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: req.message, chatId: req.chatId, useRag: req.useRag }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        const text = await response.text().catch(() => '')
        throw new Error(`流式请求失败 (${response.status}): ${text}`)
      }

      const reader = response.body?.getReader()
      if (!reader) throw new Error('浏览器不支持流式读取')

      const decoder = new TextDecoder()
      let buffer = ''
      let chatId = req.chatId || ''
      let eventType = ''
      let data = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // 逐行解析：空行 = 事件边界
        const lines = buffer.split('\n')
        // 最后一行可能不完整，保留到下次
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line === '') {
            // 空行 → 分发当前事件
            if (data) {
              if (eventType === 'chatId') {
                chatId = stripSSEArtifacts(data)
              } else if (eventType === 'error') {
                throw new Error(stripSSEArtifacts(data))
              } else {
                onChunk(stripSSEArtifacts(data))
              }
            }
            eventType = ''
            data = ''
          } else if (line.startsWith('event:')) {
            eventType = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            // 支持多行 data（虽然当前后端只发单行）
            data = data ? data + '\n' + line.slice(5) : line.slice(5)
          }
        }
      }

      // 处理流结束后 buffer 中可能残留的事件（以 \n 结尾、无后续空行）
      if (buffer) {
        const remLines = buffer.split('\n')
        for (const line of remLines) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            data = data ? data + '\n' + line.slice(5) : line.slice(5)
          }
        }
        if (data) {
          if (eventType === 'chatId') {
            chatId = stripSSEArtifacts(data)
          } else if (eventType === 'error') {
            throw new Error(stripSSEArtifacts(data))
          } else {
            onChunk(stripSSEArtifacts(data))
          }
        }
      }

      onDone(chatId)
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err instanceof Error ? err : new Error(String(err)))
      }
    })

  return controller
}

/** RAG 问答 */
export function ragQuery(req: RagQueryRequest): Promise<RagQueryResponse> {
  return request<RagQueryResponse>(`${API_BASE}/rag/question`, req)
}

/** 重建知识库 */
export function rebuildKnowledgeBase(): Promise<{ message: string }> {
  return request<{ message: string }>(`${API_BASE}/rag/rebuild`, {})
}

/** Chat 模块重建知识库 */
export function rebuildChatKnowledgeBase(): Promise<{ message: string }> {
  return request<{ message: string }>(`${API_BASE}/chat/rebuild`, {})
}
