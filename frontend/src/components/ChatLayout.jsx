import React, { useState, useRef, useEffect } from 'react'
import Sidebar from './Sidebar'
import ChatView from './ChatView'
import { useSession } from '../contexts/SessionContext'
import api from '../services'

const now = () => new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })

// Convert the messages array to the [{role, content}] format the backend expects.
// Only includes complete user→assistant pairs where the AI response was not refused/errored.
// This guarantees the Anthropic API receives strictly alternating messages ending with assistant.
function buildHistory(msgs) {
  const result = []
  for (let i = 0; i < msgs.length; i++) {
    const m = msgs[i]
    const next = msgs[i + 1]
    if (
      m.role === 'user' &&
      next &&
      next.role === 'ai' &&
      !next.refused &&
      next.text &&
      !next.text.startsWith('Error')
    ) {
      result.push({ role: 'user', content: m.text })
      result.push({ role: 'assistant', content: next.text })
      i++ // skip the AI turn we just consumed
    }
    // unpaired user turn or refused/error AI turn — skip both
  }
  // Cap at 20 entries (10 turns) to stay within token limits
  return result.slice(-20)
}

// Bucket an ISO timestamp into a sidebar group label.
function whenLabel(iso) {
  if (!iso) return 'Today'
  const d = new Date(iso)
  const today = new Date()
  if (d.toDateString() === today.toDateString()) return 'Today'
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday'
  return 'Earlier'
}

export default function ChatLayout() {
  const { user } = useSession()
  const [messages, setMessages]           = useState([])
  const [typing, setTyping]               = useState(false)
  const [conversations, setConversations] = useState([])
  const [activeId, setActiveId]           = useState(null)
  const [title, setTitle]                 = useState('New chat')
  const timer                             = useRef(null)
  // refs so async callbacks read current values without stale closures
  const messagesRef                       = useRef(messages)
  const titleRef                          = useRef(title)
  const activeIdRef                        = useRef(activeId)
  messagesRef.current  = messages
  titleRef.current     = title
  activeIdRef.current  = activeId

  // Load the user's persisted conversations on mount (survives refresh / re-login).
  useEffect(() => {
    api.listConversations()
      .then((list) => setConversations(
        (list ?? []).map((c) => ({
          id: c.id,
          title: c.title,
          when: whenLabel(c.updatedAt),
          updatedAt: c.updatedAt,
        }))
      ))
      .catch(() => {})
  }, [])

  // Persist a conversation to the server and float it to the top of the sidebar.
  const persist = (id, convTitle, msgs) => {
    setConversations((prev) => {
      const others = prev.filter((c) => c.id !== id)
      return [{ id, title: convTitle, when: 'Today', updatedAt: new Date().toISOString(), messages: msgs }, ...others]
    })
    api.saveConversation({ id, title: convTitle, messages: msgs }).catch(() => {})
  }

  const send = async (text) => {
    // Snapshot history BEFORE the optimistic user-message update so the new
    // question is not included in the history sent to the backend.
    const baseMsgs = messagesRef.current
    const historySnapshot = buildHistory(baseMsgs)

    // Give the conversation a stable id + title on its first message.
    let convId = activeIdRef.current
    let convTitle = titleRef.current
    if (!convId) {
      convId = String(Date.now())
      convTitle = text.length > 38 ? text.slice(0, 38) + '…' : text
      setActiveId(convId)
      setTitle(convTitle)
    }

    const withUser = [...baseMsgs, { role: 'user', text, time: now() }]
    setMessages(withUser)
    setTyping(true)
    clearTimeout(timer.current)
    try {
      const res = await api.chat(text, historySnapshot)
      timer.current = setTimeout(() => {
        setTyping(false)
        const finalMsgs = [...withUser, {
          role: 'ai',
          text: res.text,
          time: now(),
          language: res.language,
          citations: res.citations,
          refused: res.refused,
          reason: res.reason,
        }]
        setMessages(finalMsgs)
        persist(convId, convTitle, finalMsgs)
      }, 500)
    } catch {
      setTyping(false)
      const finalMsgs = [...withUser, { role: 'ai', text: 'Error contacting the assistant. Please try again.', time: now() }]
      setMessages(finalMsgs)
      persist(convId, convTitle, finalMsgs)
    }
  }

  const newChat = () => {
    clearTimeout(timer.current)
    setTyping(false)
    setActiveId(null)
    setTitle('New chat')
    setMessages([])
  }

  const selectChat = async (id) => {
    clearTimeout(timer.current)
    setTyping(false)
    const conv = conversations.find((c) => c.id === id)
    setActiveId(id)
    setTitle(conv?.title ?? 'New chat')
    // Messages already cached locally (a conversation we created/opened this session)
    if (conv?.messages) {
      setMessages(conv.messages)
      return
    }
    // Otherwise fetch the full message list from the server (loaded lazily).
    try {
      const full = await api.getConversation(id)
      const msgs = full?.messages ?? []
      setMessages(msgs)
      if (full?.title) setTitle(full.title)
      setConversations((prev) => prev.map((c) => (c.id === id ? { ...c, messages: msgs } : c)))
    } catch {
      setMessages([])
    }
  }

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <Sidebar
        history={conversations.map((c) => ({ id: c.id, title: c.title, when: c.when }))}
        activeId={activeId}
        onSelect={selectChat}
        onNew={newChat}
      />
      <ChatView
        title={title}
        messages={messages}
        typing={typing}
        user={user}
        onSend={send}
      />
    </div>
  )
}
