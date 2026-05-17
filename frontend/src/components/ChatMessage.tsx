import { useState, memo, useMemo } from 'react'
import type { Message } from '../types'
import MarkdownRenderer from './MarkdownRenderer'
import { User, Bot, ChevronDown, ChevronUp, BookOpen, FileDown, Loader2 } from 'lucide-react'

interface Props {
  message: Message
  isStreaming?: boolean
}

interface PdfDownload {
  uuid: string
  filename: string
}

const PDF_MARKER_RE = /\{\{PDF_DOWNLOAD:([^:]+):([^}]+)\}\}/g
const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi

function parsePdfDownloads(content: string): { cleaned: string; downloads: PdfDownload[] } {
  const downloads: PdfDownload[] = []
  let cleaned = content.replace(PDF_MARKER_RE, (_m, uuid, filename) => {
    downloads.push({ uuid, filename })
    return ''
  })
  // 先提取 PDF 标记，再清理残留的孤儿 UUID（不在标记内的）
  cleaned = cleaned.replace(UUID_RE, '')
  return { cleaned: cleaned.replace(/\n{3,}/g, '\n\n').trim(), downloads }
}

export default memo(function ChatMessage({ message, isStreaming }: Props) {
  const [showSources, setShowSources] = useState(false)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const isUser = message.role === 'user'
  const hasSources = message.sources && message.sources.length > 0

  const { cleaned: displayContent, downloads } = useMemo(
    () => (isUser ? { cleaned: message.content, downloads: [] } : parsePdfDownloads(message.content)),
    [message.content, isUser],
  )

  const isEmpty = !isUser && !displayContent && downloads.length === 0

  const handleDownload = async (pdf: PdfDownload) => {
    setDownloadingId(pdf.uuid)
    try {
      const res = await fetch(`/api/chat/pdf/download/${pdf.uuid}`)
      if (!res.ok) throw new Error('下载失败')
      const blob = await res.blob()

      // 优先使用 File System Access API，让用户选择保存路径
      if ('showSaveFilePicker' in window) {
        const ext = pdf.filename.endsWith('.pdf') ? '.pdf' : ''
        const handle = await window.showSaveFilePicker({
          suggestedName: pdf.filename,
          types: [
            {
              description: 'PDF 文档',
              accept: { 'application/pdf': [ext] },
            },
          ],
        })
        const writable = await handle.createWritable()
        await writable.write(blob)
        await writable.close()
      } else {
        // 降级：使用传统 <a> 下载
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = pdf.filename
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
      }
    } catch (err) {
      // 用户取消保存 或 其他错误，降级为直接打开链接
      if ((err as DOMException)?.name !== 'AbortError') {
        window.open(`/api/chat/pdf/download/${pdf.uuid}`, '_blank')
      }
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <div
      className={`flex gap-3 animate-fade-in-up ${
        isUser ? 'flex-row-reverse' : ''
      }`}
    >
      {/* 头像 */}
      <div
        className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 text-sm ${
          isUser
            ? 'bg-amber-100 dark:bg-amber-900/30 text-amber-600'
            : 'bg-gradient-to-br from-amber-100 to-amber-200 dark:from-amber-900/30 dark:to-amber-800/20 text-amber-700 dark:text-amber-400'
        }`}
      >
        {isUser ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
      </div>

      {/* 消息体 */}
      <div className={`flex-1 min-w-0 ${isUser ? 'flex flex-col items-end' : ''}`}>
        {isEmpty ? (
          <div className="msg-assistant rounded-2xl rounded-tl-md px-4 py-3 inline-flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-bounce" style={{ animationDelay: '0ms' }} />
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-bounce" style={{ animationDelay: '150ms' }} />
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-bounce" style={{ animationDelay: '300ms' }} />
          </div>
        ) : (
          <>
            {displayContent && (
              <div
                className={`inline-block max-w-[85%] rounded-2xl px-5 py-3 text-sm leading-relaxed break-words transition-shadow duration-300 ${
                  isUser
                    ? 'msg-user rounded-tr-md'
                    : `msg-assistant rounded-tl-md ${isStreaming ? 'streaming-active shadow-lg shadow-amber-500/5' : ''}`
                }`}
              >
                {isUser ? (
                  <p className="whitespace-pre-wrap">{displayContent}</p>
                ) : (
                  <MarkdownRenderer content={displayContent} />
                )}
              </div>
            )}

            {/* PDF 下载按钮 */}
            {downloads.length > 0 && (
              <div className="mt-2 space-y-2">
                {downloads.map((pdf) => (
                  <button
                    key={pdf.uuid}
                    onClick={() => handleDownload(pdf)}
                    disabled={downloadingId === pdf.uuid}
                    className="flex items-center gap-2.5 px-4 py-2.5 rounded-xl bg-gradient-to-r from-amber-50 to-orange-50 dark:from-amber-900/20 dark:to-orange-900/20 border border-amber-200 dark:border-amber-800/40 hover:border-amber-400 dark:hover:border-amber-600 transition-all text-sm group animate-fade-in-up"
                  >
                    <div className="w-8 h-8 rounded-lg bg-amber-100 dark:bg-amber-900/40 flex items-center justify-center shrink-0">
                      {downloadingId === pdf.uuid ? (
                        <Loader2 className="w-4 h-4 text-amber-500 animate-spin" />
                      ) : (
                        <FileDown className="w-4 h-4 text-amber-600 dark:text-amber-400" />
                      )}
                    </div>
                    <div className="text-left min-w-0">
                      <p className="font-medium text-gray-800 dark:text-gray-200 truncate">
                        {pdf.filename}
                      </p>
                      <p className="text-xs text-gray-500 dark:text-gray-400">
                        {downloadingId === pdf.uuid ? '下载中…' : '点击下载 PDF'}
                      </p>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </>
        )}

        {/* RAG 引用源 */}
        {!isUser && hasSources && (
          <div className="mt-2">
            <button
              onClick={() => setShowSources(!showSources)}
              className="inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400 hover:text-amber-700 dark:hover:text-amber-300 transition-colors"
            >
              <BookOpen className="w-3 h-3" />
              引用 ({message.sources!.length})
              {showSources ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
            </button>
            {showSources && (
              <div className="mt-2 space-y-1.5 animate-fade-in-up">
                {message.sources!.map((src, i) => (
                  <div
                    key={i}
                    className="bg-amber-50/80 dark:bg-amber-900/10 border border-amber-100/80 dark:border-amber-800/30 rounded-xl px-4 py-2.5 text-xs"
                  >
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-medium text-amber-700 dark:text-amber-300">
                        {src.reference || src.book}
                      </span>
                      {src.similarity != null && (
                        <span className="text-gray-400 dark:text-gray-500">
                          相关度 {(src.similarity * 100).toFixed(0)}%
                        </span>
                      )}
                    </div>
                    <p className="text-gray-600 dark:text-gray-400 leading-relaxed line-clamp-3">
                      {src.content}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* 时间戳 */}
        {!isEmpty && (
          <p className={`text-[10px] text-gray-400 dark:text-gray-600 mt-1 ${isUser ? 'text-right' : ''}`}>
            {new Date(message.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}
          </p>
        )}
      </div>
    </div>
  )
},
(prev, next) =>
  prev.message.id === next.message.id &&
  prev.message.content === next.message.content &&
  prev.isStreaming === next.isStreaming &&
  prev.message.sources === next.message.sources,
)
