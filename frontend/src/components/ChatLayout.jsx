import React, { useState, useRef } from 'react'
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

export default function ChatLayout() {
  const { user } = useSession()
  const [messages, setMessages]         = useState([])
  const [typing, setTyping]             = useState(false)
  const [conversations, setConversations] = useState([])
  const [activeId, setActiveId]         = useState(null)
  const [title, setTitle]               = useState('New chat')
  const timer                           = useRef(null)
  // ref so newChat/selectChat can read the current messages without stale closure
  const messagesRef                     = useRef(messages)
  const titleRef                        = useRef(title)
  const activeIdRef                     = useRef(activeId)
  messagesRef.current  = messages
  titleRef.current     = title
  activeIdRef.current  = activeId

  const send = async (text) => {
    // Snapshot history BEFORE the optimistic user-message update so the new
    // question is not included in the history sent to the backend.
    const historySnapshot = buildHistory(messages)

    setMessages((m) => [...m, { role: 'user', text, time: now() }])
    if (!activeId) {
      setTitle(text.length > 38 ? text.slice(0, 38) + '…' : text)
    }
    setTyping(true)
    clearTimeout(timer.current)
    try {
      const res = await api.chat(text, historySnapshot)
      timer.current = setTimeout(() => {
        setTyping(false)
        setMessages((m) => [...m, {
          role: 'ai',
          text: res.text,
          time: now(),
          language: res.language,
          citations: res.citations,
          refused: res.refused,
          reason: res.reason,
        }])
      }, 500)
    } catch {
      setTyping(false)
      setMessages((m) => [...m, { role: 'ai', text: 'Error contacting the assistant. Please try again.', time: now() }])
    }
  }

  // Archive the current conversation (if non-empty) to the sidebar history list.
  const archiveCurrent = () => {
    const msgs = messagesRef.current
    if (msgs.length === 0) return
    const convTitle = titleRef.current === 'New chat'
      ? (msgs[0]?.text.slice(0, 40) + (msgs[0]?.text.length > 40 ? '…' : ''))
      : titleRef.current
    setConversations((prev) => [{
      id: activeIdRef.current ?? String(Date.now()),
      title: convTitle,
      when: 'Today',
      messages: msgs,
    }, ...prev.filter((c) => c.id !== activeIdRef.current)])
  }

  const newChat = () => {
    archiveCurrent()
    clearTimeout(timer.current)
    setTyping(false)
    setActiveId(null)
    setTitle('New chat')
    setMessages([])
  }

  const selectChat = (id) => {
    clearTimeout(timer.current)
    setTyping(false)
    // Archive the currently open (unsaved) conversation before switching
    if (activeIdRef.current === null) archiveCurrent()
    const conv = conversations.find((c) => c.id === id) ||
                 [{ id, title: 'New chat', when: 'Today', messages: [] }].find(() => true)
    if (!conv) return
    setActiveId(id)
    setTitle(conv.title)
    setMessages(conv.messages)
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
