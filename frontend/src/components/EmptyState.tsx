import { MessageSquare, Sparkles } from 'lucide-react'

interface Props {
  onQuickSend: (text: string) => void
}

const SUGGESTIONS = [
  '如何才能做到知行合一？',
  '道家"无为"的真正含义是什么？',
  '心即理和格物致知的关系？',
  '如何面对人生的困境与迷茫？',
  '阳明心学对现代生活的启示？',
]

export default function EmptyState({ onQuickSend }: Props) {
  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 animate-fade-in-up">
      <div className="w-20 h-20 rounded-2xl bg-amber-100 dark:bg-amber-900/20 flex items-center justify-center mb-6">
        <Sparkles className="w-10 h-10 text-amber-500" />
      </div>
      <h2 className="text-xl font-bold mb-2 text-gray-900 dark:text-gray-100">
        与明道对话
      </h2>
      <p className="text-sm text-gray-500 dark:text-gray-400 mb-8 text-center max-w-sm">
        融合阳明心学与老子道家思想，为你解忧答疑，启迪智慧
      </p>

      <div className="grid gap-2 w-full max-w-md">
        {SUGGESTIONS.map((text) => (
          <button
            key={text}
            onClick={() => onQuickSend(text)}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 hover:border-amber-300 dark:hover:border-amber-700 hover:bg-amber-50/50 dark:hover:bg-amber-900/10 text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 transition-all text-left"
          >
            <MessageSquare className="w-3.5 h-3.5 shrink-0 text-amber-400" />
            {text}
          </button>
        ))}
      </div>
    </div>
  )
}
