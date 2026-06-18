import React, { useState, useRef } from 'react'
import Sidebar from './Sidebar'
import ChatView from './ChatView'
import { useSession } from '../contexts/SessionContext'
import mockApi from '../services/mockApi'

const INITIAL_HISTORY = [
  { id: 'c1', title: 'Remaining vacation days',   when: 'Today' },
  { id: 'c2', title: 'Dental & vision coverage',  when: 'Today' },
  { id: 'c3', title: 'Submitting a travel expense', when: 'Yesterday' },
  { id: 'c4', title: 'Parental leave policy',     when: 'Yesterday' },
  { id: 'c5', title: 'Update home address',        when: 'Last week' },
  { id: 'c6', title: 'Work from home policy',      when: 'Last week' },
]

const now = () => new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })

export default function ChatLayout() {
  const { user } = useSession()
  const [messages, setMessages]   = useState([])
  const [typing, setTyping]       = useState(false)
  const [history]                 = useState(INITIAL_HISTORY)
  const [activeId, setActiveId]   = useState(null)
  const [title, setTitle]         = useState('New chat')
  const timer                     = useRef(null)

  const send = async (text) => {
    setMessages((m) => [...m, { role: 'user', text, time: now() }])
    if (!activeId) {
      setTitle(text.length > 38 ? text.slice(0, 38) + '…' : text)
    }
    setTyping(true)
    clearTimeout(timer.current)
    try {
      const res = await mockApi.chat(text)
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

  const newChat = () => {
    clearTimeout(timer.current)
    setTyping(false)
    setActiveId(null)
    setTitle('New chat')
    setMessages([])
  }

  const selectChat = (id) => {
    clearTimeout(timer.current)
    setTyping(false)
    const item = history.find((h) => h.id === id)
    if (!item) return
    setActiveId(id)
    setTitle(item.title)
    setMessages([
      { role: 'user', text: item.title + '?', time: '9:41 AM' },
      { role: 'ai',   text: `This is a historical conversation about "${item.title}". Ask me more to continue.`, time: '9:41 AM' },
    ])
  }

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <Sidebar
        history={history}
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
