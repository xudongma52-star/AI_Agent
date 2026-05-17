import { useEffect, useRef, useCallback, useState } from 'react'
import toast from 'react-hot-toast'
import { useChatStore } from '../store/chatStore'
import { useSSE } from '../hooks/useSSE'
import { startNewChat, continueChat, rebuildChatKnowledgeBase } from '../api/chat'
import ChatMessage from '../components/ChatMessage'
import ChatInput from '../components/ChatInput'
import EmptyState from '../components/EmptyState'
import { Trash2, RefreshCw, Loader2, ChevronDown, PanelLeft } from 'lucide-react'
import type { Message } from '../types'

function genMsgId(): string {
  return Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8)
}

/** 清理从 SSE 层或模型输出中泄露的协议前缀和 UUID */
function cleanChunk(raw: string): string {
  return raw
    .replace(/^data:\s*/gm, '')
    .replace(/^event:\s*\w+\s*/gm, '')
    .replace(/^id:\s*\S+\s*/gm, '')
}

export default function ChatPage() {
  const store = useChatStore()
  const { startStream, stopStream, isStreaming } = useSSE()
  const bottomRef = useRef<HTMLDivElement>(null)
  const [rebuilding, setRebuilding] = useState(false)
  const sidebarOpen = useChatStore((s) => s.sidebarOpen)
  const setSidebarOpen = useChatStore((s) => s.setSidebarOpen)

  // 流式节流：以 50ms 为间隔批量更新 store，避免每 token 触发一次 re-render
  const chunkBufRef = useRef('')
  const flushTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const streamCidRef = useRef<string>('')

  const flushChunks = useCallback(() => {
    if (flushTimerRef.current) {
      clearTimeout(flushTimerRef.current)
      flushTimerRef.current = null
    }
    if (chunkBufRef.current && streamCidRef.current) {
      store.appendToLastMessage(streamCidRef.current, chunkBufRef.current)
      chunkBufRef.current = ''
    }
  }, [store])

  // 组件卸载时清理
  useEffect(() => () => {
    if (flushTimerRef.current) clearTimeout(flushTimerRef.current)
  }, [])

  const activeConv = store.getActiveConversation()
  const messages = activeConv?.messages ?? []

  // 自动滚底
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // ── 停止生成 ──
  const handleStop = useCallback(() => {
    flushChunks()
    stopStream()
    store.setIsStreaming(false)
  }, [flushChunks, stopStream, store])

  // ── 发送消息 ──
  const handleSend = useCallback(
    async (text: string) => {
      let convId = store.activeConversationId
      if (!convId) {
        convId = store.createConversation()
      }

      const userMsg: Message = {
        id: genMsgId(),
        role: 'user',
        content: text,
        timestamp: Date.now(),
      }
      store.addMessage(convId, userMsg)

      const assistantMsg: Message = {
        id: genMsgId(),
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
      }
      store.addMessage(convId, assistantMsg)

      const conv = store.getActiveConversation()
      const backendId = conv?.backendChatId
      const needsNewChat = !backendId

      // 记录当前流式会话 ID
      streamCidRef.current = convId
      chunkBufRef.current = ''

      if (store.useStream) {
        store.setIsStreaming(true)
        startStream(
          text,
          needsNewChat ? undefined : backendId,
          store.useRag,
          // onChunk — 节流批量更新
          (chunk) => {
            const cleaned = cleanChunk(chunk)
            if (!cleaned) return
            chunkBufRef.current += cleaned
            if (!flushTimerRef.current) {
              flushTimerRef.current = setTimeout(flushChunks, 50)
            }
          },
          // onDone
          (finalChatId) => {
            flushChunks()
            store.setIsStreaming(false)
            if (finalChatId && finalChatId !== backendId) {
              store.setBackendChatId(convId, finalChatId)
            }
          },
          // onError
          (err) => {
            flushChunks()
            store.setIsStreaming(false)
            store.appendToLastMessage(convId, `\n\n> 请求失败: ${err.message}`)
            toast.error(`对话失败: ${err.message}`)
          },
        )
      } else {
        store.setIsStreaming(true)
        try {
          const api = needsNewChat ? startNewChat : continueChat
          const res = await api({
            message: text,
            chatId: needsNewChat ? undefined : backendId,
            useRag: store.useRag,
          })
          // 同步模式也做一次清洗
          store.appendToLastMessage(convId, cleanChunk(res.message))
          if (res.chatId && res.chatId !== backendId) {
            store.setBackendChatId(convId, res.chatId)
          }
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : '未知错误'
          store.appendToLastMessage(convId, `\n\n> 请求失败: ${msg}`)
          toast.error(`对话失败: ${msg}`)
        } finally {
          store.setIsStreaming(false)
        }
      }
    },
    [store, startStream, flushChunks],
  )

  const handleClear = useCallback(() => {
    if (!store.activeConversationId) return
    store.clearConversation(store.activeConversationId)
    toast.success('对话已清空')
  }, [store])

  const handleRebuild = useCallback(async () => {
    setRebuilding(true)
    try {
      await rebuildChatKnowledgeBase()
      toast.success('知识库重建完成')
    } catch (err: unknown) {
      toast.error(`重建失败: ${err instanceof Error ? err.message : '未知错误'}`)
    } finally {
      setRebuilding(false)
    }
  }, [])

  if (!activeConv) {
    return (
      <div className="flex-1 flex flex-col">
        <ChatHeader title="明道对话" sidebarOpen={sidebarOpen} onOpenSidebar={() => setSidebarOpen(true)} onClear={undefined} onRebuild={handleRebuild} rebuilding={rebuilding} />
        <EmptyState onQuickSend={handleSend} />
      </div>
    )
  }

  return (
    <div className="flex-1 flex flex-col min-h-0">
      <ChatHeader
        title={activeConv.title}
        sidebarOpen={sidebarOpen}
        onOpenSidebar={() => setSidebarOpen(true)}
        createdAt={activeConv.createdAt}
        convId={activeConv.backendChatId || activeConv.id}
        messageCount={activeConv.messages.length}
        onClear={handleClear}
        onRebuild={handleRebuild}
        rebuilding={rebuilding}
      />

      {/* 消息列表 */}
      <div className="flex-1 overflow-y-auto px-4 py-4 scroll-smooth">
        <div className="max-w-3xl mx-auto space-y-5">
          {messages.length === 0 && (
            <p className="text-center text-sm text-gray-400 dark:text-gray-500 mt-12">
              开始一段新的对话吧
            </p>
          )}
          {messages.map((msg, i) => (
            <ChatMessage
              key={msg.id}
              message={msg}
              isStreaming={isStreaming && i === messages.length - 1 && msg.role === 'assistant'}
            />
          ))}
          <div ref={bottomRef} />
        </div>
      </div>

      <ChatInput onSend={handleSend} onStop={handleStop} isStreaming={isStreaming} />
    </div>
  )
}

// ── 页头 ──

function ChatHeader({
  title, sidebarOpen, onOpenSidebar, createdAt, convId, messageCount, onClear, onRebuild, rebuilding,
}: {
  title: string
  sidebarOpen: boolean
  onOpenSidebar: () => void
  createdAt?: number
  convId?: string
  messageCount?: number
  onClear: (() => void) | undefined
  onRebuild: () => void
  rebuilding: boolean
}) {
  const [showMeta, setShowMeta] = useState(false)

  return (
    <>
      <div className="h-14 flex items-center justify-between px-5 border-b border-gray-100 dark:border-gray-800 shrink-0 bg-white/90 dark:bg-gray-950/90 backdrop-blur-sm">
        <div className="flex items-center gap-2 min-w-0">
          {!sidebarOpen && (
            <button
              onClick={onOpenSidebar}
              className="shrink-0 p-1.5 -ml-1 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
              title="展开侧栏"
            >
              <PanelLeft className="w-4 h-4" />
            </button>
          )}
          <h2 className="text-sm font-semibold truncate text-gray-700 dark:text-gray-300">
            {title}
          </h2>
          {createdAt && (
            <button
              onClick={() => setShowMeta(!showMeta)}
              className={`shrink-0 p-1 rounded-md text-xs transition-all ${
                showMeta
                  ? 'bg-amber-100 dark:bg-amber-900/30 text-amber-600'
                  : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800'
              }`}
              title="会话详情"
            >
              <ChevronDown className={`w-3.5 h-3.5 transition-transform duration-200 ${showMeta ? 'rotate-180' : ''}`} />
            </button>
          )}
        </div>
        <div className="flex items-center gap-0.5">
          {onClear && (
            <button
              onClick={onClear}
              className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-400 hover:text-red-500 transition-colors"
              title="清空对话"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
          <button
            onClick={onRebuild}
            disabled={rebuilding}
            className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-400 hover:text-amber-500 transition-colors disabled:opacity-50"
            title="重建知识库"
          >
            {rebuilding ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {/* 折叠的会话元信息 */}
      {showMeta && createdAt && (
        <div className="px-5 py-2.5 border-b border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-900/50 animate-fade-in">
          <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
            <span>
              创建时间：{new Date(createdAt).toLocaleString('zh-CN')}
            </span>
            {convId && (
              <span className="font-mono text-[11px] select-all" title={convId}>
                ID：{convId.length > 20 ? convId.slice(0, 10) + '…' + convId.slice(-8) : convId}
              </span>
            )}
            {messageCount != null && (
              <span>消息数：{messageCount}</span>
            )}
          </div>
        </div>
      )}
    </>
  )
}
