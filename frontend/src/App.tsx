import { useState, useEffect, useCallback } from 'react'
import { Toaster } from 'react-hot-toast'
import Sidebar from './components/Sidebar'
import ChatPage from './pages/ChatPage'
import KnowledgePage from './pages/KnowledgePage'
import ErrorBoundary from './components/ErrorBoundary'

export type Page = 'chat' | 'knowledge'

function getInitialDark(): boolean {
  try {
    const saved = localStorage.getItem('mingdao-theme')
    if (saved === 'dark' || saved === 'light') return saved === 'dark'
  } catch { /* ignore */ }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export default function App() {
  const [page, setPage] = useState<Page>('chat')
  const [dark, setDark] = useState(getInitialDark)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark)
    try { localStorage.setItem('mingdao-theme', dark ? 'dark' : 'light') } catch { /* ignore */ }
  }, [dark])

  const toggleDark = useCallback(() => setDark((d) => !d), [])

  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = (e: MediaQueryListEvent) => {
      try {
        if (!localStorage.getItem('mingdao-theme')) setDark(e.matches)
      } catch { /* ignore */ }
    }
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  return (
    <ErrorBoundary>
      <div className="h-screen flex bg-white dark:bg-gray-950 text-gray-900 dark:text-gray-100 overflow-hidden">
        <Sidebar page={page} onPageChange={setPage} dark={dark} onToggleDark={toggleDark} />
        <main className="flex-1 flex flex-col min-w-0">
          <ErrorBoundary>
            {page === 'chat' ? <ChatPage /> : <KnowledgePage />}
          </ErrorBoundary>
        </main>
      </div>
      <Toaster
        position="top-center"
        gutter={8}
        toastOptions={{
          duration: 3000,
          style: {
            background: dark ? '#1f2937' : '#fff',
            color: dark ? '#f3f4f6' : '#1f2937',
            border: '1px solid ' + (dark ? '#374151' : '#e5e7eb'),
            borderRadius: '0.75rem',
            fontSize: '0.875rem',
          },
        }}
      />
    </ErrorBoundary>
  )
}
