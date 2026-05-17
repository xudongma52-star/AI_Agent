import { create } from 'zustand'
import type { Conversation, Message } from '../types'

interface ChatState {
  conversations: Conversation[]
  activeConversationId: string | null
  useRag: boolean
  useStream: boolean
  isStreaming: boolean
  sidebarOpen: boolean

  // Actions
  createConversation: () => string
  deleteConversation: (id: string) => void
  setActiveConversation: (id: string) => void
  addMessage: (conversationId: string, message: Message) => void
  appendToLastMessage: (conversationId: string, text: string) => void
  setUseRag: (v: boolean) => void
  setUseStream: (v: boolean) => void
  setIsStreaming: (v: boolean) => void
  setSidebarOpen: (v: boolean) => void
  updateConversationTitle: (id: string, title: string) => void
  setBackendChatId: (conversationId: string, backendChatId: string) => void
  clearConversation: (id: string) => void
  getActiveConversation: () => Conversation | undefined
}

function loadConversations(): Conversation[] {
  try {
    const raw = localStorage.getItem('mingdao-conversations')
    if (raw) return JSON.parse(raw)
  } catch { /* ignore */ }
  return []
}

function saveConversations(conversations: Conversation[]) {
  try {
    localStorage.setItem('mingdao-conversations', JSON.stringify(conversations))
  } catch { /* ignore */ }
}

let idCounter = Date.now()

function genId(): string {
  return (++idCounter).toString(36) + '-' + Math.random().toString(36).slice(2, 8)
}

export const useChatStore = create<ChatState>((set, get) => ({
  conversations: loadConversations(),
  activeConversationId: null,
  useRag: false,
  useStream: true,
  isStreaming: false,
  sidebarOpen: true,

  createConversation: () => {
    const id = genId()
    const conv: Conversation = {
      id,
      title: '新对话',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      messages: [],
    }
    set((s) => {
      const next = { ...s, conversations: [conv, ...s.conversations], activeConversationId: id }
      saveConversations(next.conversations)
      return next
    })
    return id
  },

  deleteConversation: (id) => {
    set((s) => {
      const filtered = s.conversations.filter((c) => c.id !== id)
      const next = {
        conversations: filtered,
        activeConversationId: s.activeConversationId === id
          ? (filtered[0]?.id ?? null)
          : s.activeConversationId,
      }
      saveConversations(next.conversations)
      return next
    })
  },

  setActiveConversation: (id) => {
    set({ activeConversationId: id })
  },

  addMessage: (conversationId, message) => {
    set((s) => {
      const next = {
        conversations: s.conversations.map((c) => {
          if (c.id !== conversationId) return c
          const isFirstUserMsg = c.messages.length === 0 && message.role === 'user'
          return {
            ...c,
            title: isFirstUserMsg
              ? (message.content.slice(0, 30) + (message.content.length > 30 ? '...' : ''))
              : c.title,
            messages: [...c.messages, message],
            updatedAt: Date.now(),
          }
        }),
      }
      saveConversations(next.conversations)
      return next
    })
  },

  appendToLastMessage: (conversationId, text) => {
    set((s) => {
      const next = {
        conversations: s.conversations.map((c) => {
          if (c.id !== conversationId) return c
          const msgs = [...c.messages]
          if (msgs.length > 0) {
            const last = { ...msgs[msgs.length - 1] }
            last.content += text
            msgs[msgs.length - 1] = last
          }
          return { ...c, messages: msgs, updatedAt: Date.now() }
        }),
      }
      saveConversations(next.conversations)
      return next
    })
  },

  setUseRag: (v) => set({ useRag: v }),
  setUseStream: (v) => set({ useStream: v }),
  setIsStreaming: (v) => set({ isStreaming: v }),
  setSidebarOpen: (v) => set({ sidebarOpen: v }),

  updateConversationTitle: (id, title) => {
    set((s) => {
      const next = {
        conversations: s.conversations.map((c) =>
          c.id === id ? { ...c, title, updatedAt: Date.now() } : c
        ),
      }
      saveConversations(next.conversations)
      return next
    })
  },

  setBackendChatId: (conversationId, backendChatId) => {
    set((s) => {
      const next = {
        conversations: s.conversations.map((c) =>
          c.id === conversationId ? { ...c, backendChatId, updatedAt: Date.now() } : c
        ),
      }
      saveConversations(next.conversations)
      return next
    })
  },

  clearConversation: (id) => {
    set((s) => {
      const next = {
        conversations: s.conversations.map((c) =>
          c.id === id ? { ...c, messages: [], title: '新对话', backendChatId: undefined, updatedAt: Date.now() } : c
        ),
      }
      saveConversations(next.conversations)
      return next
    })
  },

  getActiveConversation: () => {
    const s = get()
    return s.conversations.find((c) => c.id === s.activeConversationId)
  },
}))
