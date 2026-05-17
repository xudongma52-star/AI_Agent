import { useState, useRef, useCallback, type KeyboardEvent } from 'react'
import toast from 'react-hot-toast'
import { ragQuery, rebuildKnowledgeBase } from '../api/chat'
import MarkdownRenderer from '../components/MarkdownRenderer'
import type { RagSource } from '../types'
import {
  Search, Loader2, Database, BookOpen, RefreshCw,
  ChevronDown, ChevronUp, ExternalLink, PanelLeft,
} from 'lucide-react'
import { useChatStore } from '../store/chatStore'

interface QueryResult {
  answer: string
  sources: RagSource[]
}

export default function KnowledgePage() {
  const sidebarOpen = useChatStore((s) => s.sidebarOpen)
  const setSidebarOpen = useChatStore((s) => s.setSidebarOpen)
  const [question, setQuestion] = useState('')
  const [book, setBook] = useState('')
  const [loading, setLoading] = useState(false)
  const [rebuilding, setRebuilding] = useState(false)
  const [result, setResult] = useState<QueryResult | null>(null)
  const [showSources, setShowSources] = useState(true)
  const inputRef = useRef<HTMLInputElement>(null)

  const handleSearch = useCallback(async () => {
    const q = question.trim()
    if (!q || loading) return

    setLoading(true)
    setResult(null)
    try {
      const res = await ragQuery({ question: q, book: book.trim() || undefined })
      setResult({ answer: res.answer, sources: res.sources })
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '未知错误'
      toast.error(`检索失败: ${msg}`)
    } finally {
      setLoading(false)
    }
  }, [question, book, loading])

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') handleSearch()
  }

  const handleRebuild = useCallback(async () => {
    setRebuilding(true)
    try {
      await rebuildKnowledgeBase()
      toast.success('知识库重建完成')
    } catch (err: unknown) {
      toast.error(`重建失败: ${err instanceof Error ? err.message : '未知错误'}`)
    } finally {
      setRebuilding(false)
    }
  }, [])

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Header */}
      <div className="h-14 flex items-center justify-between px-4 border-b border-gray-200 dark:border-gray-800 shrink-0 bg-white/80 dark:bg-gray-950/80 backdrop-blur-sm">
        <div className="flex items-center gap-2">
          {!sidebarOpen && (
            <button
              onClick={() => setSidebarOpen(true)}
              className="p-1.5 -ml-1 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
              title="展开侧栏"
            >
              <PanelLeft className="w-4 h-4" />
            </button>
          )}
          <h2 className="text-sm font-semibold flex items-center gap-2">
            <Database className="w-4 h-4 text-amber-500" />
            知识库检索
          </h2>
        </div>
        <button
          onClick={handleRebuild}
          disabled={rebuilding}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-500 hover:text-amber-500 transition-colors disabled:opacity-50"
          title="重建知识库"
        >
          {rebuilding ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
          ) : (
            <RefreshCw className="w-3.5 h-3.5" />
          )}
          重建索引
        </button>
      </div>

      {/* 内容区 */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-3xl mx-auto px-4 py-6">
          {/* 搜索栏 */}
          <div className="space-y-2 mb-6">
            <div className="flex gap-2">
              <input
                ref={inputRef}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="输入问题检索经典文献…"
                className="flex-1 rounded-xl border border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 px-4 py-2.5 text-sm placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-amber-500/40 focus:border-amber-400 transition-colors"
                autoFocus
              />
              <button
                onClick={handleSearch}
                disabled={loading || !question.trim()}
                className="px-5 py-2.5 rounded-xl bg-amber-500 hover:bg-amber-600 disabled:bg-gray-300 dark:disabled:bg-gray-700 text-white font-medium text-sm transition-colors disabled:cursor-not-allowed flex items-center gap-1.5 shrink-0"
              >
                {loading ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Search className="w-4 h-4" />
                )}
                检索
              </button>
            </div>

            {/* 可选：书名过滤 */}
            <div className="flex items-center gap-2">
              <BookOpen className="w-3.5 h-3.5 text-gray-400 shrink-0" />
              <input
                value={book}
                onChange={(e) => setBook(e.target.value)}
                placeholder="按书名过滤（可选，如：传习录）"
                className="flex-1 text-xs rounded-lg border border-gray-200 dark:border-gray-700 bg-transparent px-3 py-1.5 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:border-amber-400 transition-colors"
              />
            </div>
          </div>

          {/* 加载态 */}
          {loading && (
            <div className="flex flex-col items-center justify-center py-16 animate-fade-in-up">
              <Loader2 className="w-8 h-8 text-amber-500 animate-spin-slow mb-4" />
              <p className="text-sm text-gray-500 dark:text-gray-400">正在检索经典文献…</p>
            </div>
          )}

          {/* 空态（未检索） */}
          {!loading && !result && (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <div className="w-16 h-16 rounded-2xl bg-amber-100 dark:bg-amber-900/20 flex items-center justify-center mb-4">
                <Database className="w-8 h-8 text-amber-500" />
              </div>
              <h3 className="text-base font-semibold mb-2">检索经典文献</h3>
              <p className="text-sm text-gray-500 dark:text-gray-400 max-w-sm">
                基于《传习录》等经典文本，使用 AI 语义检索找到最相关的原文段落并生成回答
              </p>
            </div>
          )}

          {/* 结果 */}
          {!loading && result && (
            <div className="animate-fade-in-up space-y-4">
              {/* 回答 */}
              <div className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl p-5">
                <h3 className="text-xs font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wider mb-3">
                  回答
                </h3>
                <MarkdownRenderer content={result.answer} />
              </div>

              {/* 引用源 */}
              {result.sources.length > 0 && (
                <div className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl p-5">
                  <button
                    onClick={() => setShowSources(!showSources)}
                    className="flex items-center gap-2 text-xs font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wider mb-3 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    引用来源 ({result.sources.length})
                    {showSources ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                  </button>
                  {showSources && (
                    <div className="space-y-2">
                      {result.sources.map((src, i) => (
                        <div
                          key={i}
                          className="bg-amber-50/60 dark:bg-amber-900/10 border border-amber-100 dark:border-amber-800/30 rounded-xl p-4"
                        >
                          <div className="flex items-center gap-2 mb-2">
                            <span className="text-xs font-semibold text-amber-700 dark:text-amber-300 bg-amber-100 dark:bg-amber-900/30 px-2 py-0.5 rounded-full">
                              {src.reference || src.book}
                            </span>
                            {src.similarity != null && (
                              <span className="text-[10px] text-gray-400 dark:text-gray-500">
                                相关度 {(src.similarity * 100).toFixed(0)}%
                              </span>
                            )}
                            {src.chapter && (
                              <span className="text-[10px] text-gray-400 dark:text-gray-500">
                                {src.chapter}{src.section ? ` · ${src.section}` : ''}
                              </span>
                            )}
                          </div>
                          <p className="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">
                            {src.content}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
