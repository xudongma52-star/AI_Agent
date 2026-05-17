import { useMemo } from 'react'
import type { Page } from '../App'
import { useChatStore } from '../store/chatStore'
import {
  MessageSquare, Plus, Trash2, BookOpen, Moon, Sun,
  PanelLeftClose, Database, ToggleLeft, ToggleRight,
} from 'lucide-react'

interface Props {
  page: Page
  onPageChange: (p: Page) => void
  dark: boolean
  onToggleDark: () => void
}

export default function Sidebar({ page, onPageChange, dark, onToggleDark }: Props) {
  const {
    conversations, activeConversationId, sidebarOpen,
    createConversation, deleteConversation, setActiveConversation,
    useRag, setUseRag, useStream, setUseStream,
    setSidebarOpen,
  } = useChatStore()

  const sorted = useMemo(
    () => [...conversations].sort((a, b) => b.updatedAt - a.updatedAt),
    [conversations],
  )

  if (!sidebarOpen) {
    return null
  }

  return (
    <>
      {/* 移动端遮罩 */}
      <div
        className="fixed inset-0 z-20 bg-black/40 lg:hidden"
        onClick={() => setSidebarOpen(false)}
      />

      <aside className="fixed lg:static inset-y-0 left-0 z-30 w-72 bg-gray-50 dark:bg-gray-900 border-r border-gray-200 dark:border-gray-800 flex flex-col shrink-0">
        {/* 顶部品牌区 */}
        <div className="h-14 flex items-center justify-between px-4 border-b border-gray-200 dark:border-gray-800 shrink-0">
          <h1 className="text-lg font-bold tracking-wide">
            <span className="text-amber-500">明</span>道
          </h1>
          <button
            onClick={() => setSidebarOpen(false)}
            className="p-1.5 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-800 transition-colors"
            title="收起侧栏"
          >
            <PanelLeftClose className="w-4 h-4" />
          </button>
        </div>

        {/* 导航标签 */}
        <nav className="flex mx-3 mt-3 gap-1 p-0.5 bg-gray-200/60 dark:bg-gray-800/60 rounded-lg shrink-0">
          {([
            ['chat', '对话', MessageSquare],
            ['knowledge', '知识库', Database],
          ] as const).map(([key, label, Icon]) => (
            <button
              key={key}
              onClick={() => onPageChange(key)}
              className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 rounded-md text-sm font-medium transition-all ${
                page === key
                  ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                  : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {label}
            </button>
          ))}
        </nav>

        {/* 对话列表区域（仅对话页显示） */}
        {page === 'chat' && (
          <>
            <div className="flex items-center justify-between px-4 mt-4 mb-1 shrink-0">
              <span className="text-xs font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wider">
                对话列表
              </span>
              <button
                onClick={createConversation}
                className="p-1 rounded-md hover:bg-gray-200 dark:hover:bg-gray-800 transition-colors text-gray-500 hover:text-amber-500"
                title="新建对话"
              >
                <Plus className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-3 pb-2">
              {sorted.length === 0 ? (
                <p className="text-center text-xs text-gray-400 dark:text-gray-500 mt-8 px-2">
                  暂无对话，点击 + 开始新的对话
                </p>
              ) : (
                <div className="space-y-0.5">
                  {sorted.map((conv) => {
                    const isActive = conv.id === activeConversationId
                    return (
                      <div
                        key={conv.id}
                        onClick={() => {
                          setActiveConversation(conv.id)
                        }}
                        className={`group flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer transition-all text-sm ${
                          isActive
                            ? 'bg-amber-50 dark:bg-amber-900/20 text-amber-800 dark:text-amber-200 border border-amber-200/60 dark:border-amber-800/30'
                            : 'hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300 border border-transparent'
                        }`}
                      >
                        <MessageSquare className={`w-3.5 h-3.5 shrink-0 ${isActive ? 'text-amber-500' : 'text-gray-400'}`} />
                        <span className="flex-1 truncate">{conv.title}</span>
                        <button
                          onClick={(e) => {
                            e.stopPropagation()
                            if (confirm('确定删除该对话？')) deleteConversation(conv.id)
                          }}
                          className="p-0.5 rounded opacity-0 group-hover:opacity-100 hover:bg-red-100 dark:hover:bg-red-900/30 text-gray-400 hover:text-red-500 transition-all"
                          title="删除对话"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </>
        )}

        {/* 底部设置区 */}
        <div className="border-t border-gray-200 dark:border-gray-800 px-4 py-3 space-y-3 shrink-0">
          {/* RAG 开关 */}
          <label className="flex items-center justify-between cursor-pointer">
            <span className="text-sm text-gray-600 dark:text-gray-400 flex items-center gap-1.5">
              <BookOpen className="w-3.5 h-3.5" />
              知识库增强
            </span>
            <button
              role="switch"
              aria-checked={useRag}
              onClick={() => setUseRag(!useRag)}
              className={`transition-colors ${useRag ? 'text-amber-500' : 'text-gray-400'}`}
            >
              {useRag ? <ToggleRight className="w-8 h-5" /> : <ToggleLeft className="w-8 h-5" />}
            </button>
          </label>

          {/* 流式开关 */}
          <label className="flex items-center justify-between cursor-pointer">
            <span className="text-sm text-gray-600 dark:text-gray-400">流式响应</span>
            <button
              role="switch"
              aria-checked={useStream}
              onClick={() => setUseStream(!useStream)}
              className={`transition-colors ${useStream ? 'text-amber-500' : 'text-gray-400'}`}
            >
              {useStream ? <ToggleRight className="w-8 h-5" /> : <ToggleLeft className="w-8 h-5" />}
            </button>
          </label>

          {/* 暗色模式 */}
          <label className="flex items-center justify-between cursor-pointer">
            <span className="text-sm text-gray-600 dark:text-gray-400 flex items-center gap-1.5">
              {dark ? <Moon className="w-3.5 h-3.5" /> : <Sun className="w-3.5 h-3.5" />}
              暗色模式
            </span>
            <button
              role="switch"
              aria-checked={dark}
              onClick={onToggleDark}
              className={`transition-colors ${dark ? 'text-amber-500' : 'text-gray-400'}`}
            >
              {dark ? <ToggleRight className="w-8 h-5" /> : <ToggleLeft className="w-8 h-5" />}
            </button>
          </label>
        </div>
      </aside>
    </>
  )
}
