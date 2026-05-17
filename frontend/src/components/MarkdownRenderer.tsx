import { memo } from 'react'
import ReactMarkdown from 'react-markdown'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'

interface Props {
  content: string
  className?: string
}

export default memo(function MarkdownRenderer({ content, className = '' }: Props) {
  return (
    <div className={`prose-content ${className}`}>
      <ReactMarkdown
        components={{
          code({ className: cls, children, ...props }) {
            const match = /language-(\w+)/.exec(cls || '')
            const codeStr = String(children).replace(/\n$/, '')
            if (match) {
              return (
                <div className="relative group my-2">
                  <div className="flex items-center justify-between px-4 py-1.5 bg-gray-800 dark:bg-gray-900 rounded-t-lg text-xs text-gray-400">
                    <span>{match[1]}</span>
                    <button
                      onClick={() => navigator.clipboard.writeText(codeStr)}
                      className="opacity-0 group-hover:opacity-100 hover:text-white transition-all"
                    >
                      复制
                    </button>
                  </div>
                  <SyntaxHighlighter
                    style={oneDark}
                    language={match[1]}
                    PreTag="div"
                    customStyle={{ margin: 0, borderTopLeftRadius: 0, borderTopRightRadius: 0 }}
                  >
                    {codeStr}
                  </SyntaxHighlighter>
                </div>
              )
            }
            return (
              <code className={cls} {...props}>
                {children}
              </code>
            )
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
})
