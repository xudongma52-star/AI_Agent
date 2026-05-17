import { useRef, useCallback, useState } from 'react'
import { streamChat } from '../api/chat'

export function useSSE() {
  const abortRef = useRef<AbortController | null>(null)
  const [isStreaming, setIsStreaming] = useState(false)

  const startStream = useCallback(
    (
      message: string,
      chatId: string | undefined,
      useRag: boolean,
      onChunk: (text: string) => void,
      onDone: (chatId: string) => void,
      onError: (err: Error) => void,
    ) => {
      abortRef.current?.abort()
      setIsStreaming(true)
      abortRef.current = streamChat(
        { message, chatId, useRag },
        onChunk,
        (finalChatId) => {
          abortRef.current = null
          setIsStreaming(false)
          onDone(finalChatId)
        },
        (err) => {
          abortRef.current = null
          setIsStreaming(false)
          onError(err)
        },
      )
    },
    [],
  )

  const stopStream = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setIsStreaming(false)
  }, [])

  return { startStream, stopStream, isStreaming }
}
