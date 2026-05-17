export interface ChatRequest {
  message: string
  chatId?: string
  useRag?: boolean
}

export interface ChatResponse {
  chatId: string
  message: string
  newConversation: boolean
}

export interface Conversation {
  id: string
  title: string
  createdAt: number
  updatedAt: number
  messages: Message[]
  /** 后端返回的 chatId，用于继续对话时传递给后端 */
  backendChatId?: string
}

export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  /** RAG 引用源（仅 assistant 消息可能有） */
  sources?: RagSource[]
}

export interface RagSource {
  content: string
  book: string
  chapter: string
  section: string
  reference: string
  similarity: number | null
}

export interface RagQueryRequest {
  question: string
  book?: string
}

export interface RagQueryResponse {
  answer: string
  sources: RagSource[]
}

export interface RagSearchResult {
  content: string
  book: string
  chapter: string
  section: string
  reference: string
  similarity: number | null
}
