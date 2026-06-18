import React, { useRef, useEffect, useState } from 'react'
import { Paperclip, Mic, ArrowUp, Info, Palmtree, HeartPulse, Banknote, FileText, Baby, GraduationCap, ExternalLink } from 'lucide-react'
import { Avatar } from './ds/Avatar'
import { Badge } from './ds/Badge'
import { IconButton } from './ds/IconButton'
import { ChatBubble } from './ds/ChatBubble'
import { TypingIndicator } from './ds/TypingIndicator'

const SUGGESTIONS = [
  { icon: Palmtree,       text: 'How many vacation days do I have?' },
  { icon: HeartPulse,     text: "What's covered by my health plan?" },
  { icon: Banknote,       text: 'When is the next payday?' },
  { icon: FileText,       text: 'How do I submit an expense?' },
  { icon: Baby,           text: 'What is the parental leave policy?' },
  { icon: GraduationCap,  text: "What's the learning budget?" },
]

function Citations({ citations }) {
  const [open, setOpen] = useState(false)
  if (!citations?.length) return null

  const isSlide = (name) => name.toLowerCase().endsWith('.pptx') || name.toLowerCase().endsWith('.ppt')

  return (
    <div style={{ marginTop: 8, fontSize: 13 }}>
      <button
        onClick={() => setOpen((o) => !o)}
        style={{
          background: 'none', border: 'none', cursor: 'pointer', padding: 0,
          display: 'flex', alignItems: 'center', gap: 6,
          color: 'var(--text-muted)', fontFamily: 'var(--font-sans)',
          fontSize: 12.5, fontWeight: 600,
        }}
      >
        <span style={{
          width: 18, height: 18, borderRadius: '50%', background: 'var(--brand-soft)',
          color: 'var(--brand)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 10, fontWeight: 800,
        }}>{citations.length}</span>
        Sources {open ? '▲' : '▼'}
      </button>
      {open && (
        <ul style={{ margin: '8px 0 0', padding: '0 0 0 4px', listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 6 }}>
          {citations.map((c, i) => (
            <li key={i} style={{
              display: 'flex', alignItems: 'flex-start', gap: 8,
              padding: '8px 12px', background: 'var(--surface-sunken)',
              borderRadius: 'var(--radius-sm)', fontSize: 12.5,
            }}>
              <FileText size={14} style={{ color: 'var(--text-subtle)', flex: 'none', marginTop: 1 }} />
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 600, color: 'var(--text-body)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {c.sourceName}
                </div>
                <div style={{ color: 'var(--text-muted)', marginTop: 2 }}>
                  {c.page != null && <span>{isSlide(c.sourceName) ? 'Slide' : 'Page'} {c.page}</span>}
                  {c.page != null && c.lastModified && <span> · </span>}
                  {c.lastModified && <span>{c.lastModified}</span>}
                </div>
              </div>
              {c.url && (
                <a href={c.url} target="_blank" rel="noopener noreferrer" style={{ flex: 'none', color: 'var(--brand)' }}>
                  <ExternalLink size={13} />
                </a>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function Welcome({ user, onSuggestion }) {
  const first = (user?.displayName ?? 'there').replace(/_/g, ' ').split(' ')[0]
  return (
    <div style={{
      flex: 1, display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center',
      padding: '24px 24px 8px', textAlign: 'center',
    }}>
      <Avatar variant="ai" size="xl" style={{ marginBottom: 20, boxShadow: 'var(--shadow-brand)' }} />
      <h1 style={{ fontSize: 30, fontWeight: 800, letterSpacing: '-.02em', color: 'var(--text-strong)', margin: '0 0 8px' }}>
        Hi {first}, I'm Kernel 👋
      </h1>
      <p style={{ fontSize: 16, color: 'var(--text-muted)', lineHeight: 1.55, margin: '0 0 30px', maxWidth: 460 }}>
        Your HR questions, answered instantly from official company documents. Ask me anything, or pick a topic to get started.
      </p>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, width: '100%', maxWidth: 560 }}>
        {SUGGESTIONS.map((s, i) => {
          const Icon = s.icon
          return (
            <SuggestionCard key={i} icon={<Icon size={18} />} text={s.text} onClick={() => onSuggestion(s.text)} />
          )
        })}
      </div>
    </div>
  )
}

function SuggestionCard({ icon, text, onClick }) {
  const [hover, setHover] = useState(false)
  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex', alignItems: 'center', gap: 12, padding: '14px 16px', textAlign: 'left',
        background: 'var(--surface-card)', border: `1px solid ${hover ? 'var(--border-brand)' : 'var(--border-default)'}`,
        borderRadius: 'var(--radius-md)', cursor: 'pointer',
        boxShadow: hover ? 'var(--shadow-md)' : 'var(--shadow-xs)',
        fontFamily: 'var(--font-sans)', fontSize: 14, fontWeight: 500, color: 'var(--text-body)',
        transform: hover ? 'translateY(-2px)' : 'none',
        transition: 'var(--transition-control)',
      }}
    >
      <span style={{
        width: 34, height: 34, flex: 'none', borderRadius: 'var(--radius-sm)',
        background: 'var(--brand-soft)', color: 'var(--brand)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        {icon}
      </span>
      {text}
    </button>
  )
}

function Composer({ onSend, disabled }) {
  const [value, setValue] = useState('')
  const ref = useRef(null)
  const canSend = value.trim().length > 0 && !disabled

  const grow = (el) => {
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 160) + 'px'
  }

  const send = () => {
    if (!canSend) return
    onSend(value.trim())
    setValue('')
    if (ref.current) ref.current.style.height = 'auto'
  }

  const onKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() }
  }

  return (
    <div style={{ padding: '12px 24px 18px' }}>
      <div style={{ maxWidth: 'var(--width-chat)', margin: '0 auto' }}>
        <div style={{
          display: 'flex', alignItems: 'flex-end', gap: 8, padding: '8px 8px 8px 10px',
          background: 'var(--surface-card)', border: '1px solid var(--border-default)',
          borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-md)',
        }}>
          <IconButton label="Attach file" variant="ghost">
            <Paperclip size={18} />
          </IconButton>
          <textarea
            ref={ref}
            rows={1}
            value={value}
            onChange={(e) => { setValue(e.target.value); grow(e.target) }}
            onKeyDown={onKey}
            disabled={disabled}
            placeholder="Ask about HR policies, leave, benefits, payroll…"
            style={{
              flex: 1, border: 'none', outline: 'none', resize: 'none',
              background: 'transparent', fontFamily: 'var(--font-sans)',
              fontSize: 15, lineHeight: 1.5, color: 'var(--text-strong)',
              padding: '9px 4px', maxHeight: 160,
            }}
          />
          <IconButton label="Voice input" variant="ghost">
            <Mic size={18} />
          </IconButton>
          <IconButton
            label="Send"
            variant="solid"
            onClick={send}
            style={{ opacity: canSend ? 1 : 0.45, pointerEvents: canSend ? 'auto' : 'none' }}
          >
            <ArrowUp size={18} />
          </IconButton>
        </div>
        <p style={{ textAlign: 'center', fontSize: 11.5, color: 'var(--text-subtle)', margin: '10px 0 0' }}>
          Kernel HR can make mistakes. Verify important details with People Ops.
        </p>
      </div>
    </div>
  )
}

export default function ChatView({ title, messages, typing, user, onSend }) {
  const scrollRef = useRef(null)

  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages.length, typing])

  const empty = messages.length === 0

  return (
    <main style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, background: 'var(--surface-page)' }}>
      {/* Header */}
      <header style={{
        display: 'flex', alignItems: 'center', gap: 12, padding: '14px 24px',
        borderBottom: '1px solid var(--border-subtle)',
        background: 'rgba(255,255,255,.8)', backdropFilter: 'var(--blur-sm)',
      }}>
        <Avatar variant="ai" size="sm" />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-strong)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {empty ? 'Kernel HR Assistant' : title}
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--success)' }} />
            Online · HR Assistant
          </div>
        </div>
        <Badge tone="brand">Beta</Badge>
        <IconButton label="Conversation info" variant="ghost">
          <Info size={18} />
        </IconButton>
      </header>

      {/* Messages area */}
      <div ref={scrollRef} style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
        {empty ? (
          <Welcome user={user} onSuggestion={onSend} />
        ) : (
          <div style={{
            maxWidth: 'var(--width-chat)', width: '100%', margin: '0 auto',
            padding: '26px 24px 8px', display: 'flex', flexDirection: 'column', gap: 16,
          }}>
            {messages.map((m, i) =>
              m.role === 'user' ? (
                <ChatBubble key={i} role="user" time={m.time} status="sent">
                  {m.text}
                </ChatBubble>
              ) : (
                <div key={i} style={{ display: 'flex', gap: 10, alignItems: 'flex-end' }}>
                  <Avatar variant="ai" size="sm" />
                  <div style={{ display: 'flex', flexDirection: 'column', maxWidth: '82%' }}>
                    {m.language && (
                      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-subtle)', marginBottom: 4, marginLeft: 4 }}>
                        {m.language.toUpperCase()}
                      </span>
                    )}
                    <ChatBubble role="ai" name="Kernel HR" time={m.time}>
                      {m.refused
                        ? <em style={{ color: 'var(--danger)' }}>{m.reason || m.text}</em>
                        : m.text}
                    </ChatBubble>
                    {!m.refused && <Citations citations={m.citations} />}
                  </div>
                </div>
              )
            )}
            {typing && (
              <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end' }}>
                <Avatar variant="ai" size="sm" />
                <TypingIndicator />
              </div>
            )}
            <div style={{ height: 8 }} />
          </div>
        )}
      </div>

      <Composer onSend={onSend} disabled={typing} />
    </main>
  )
}
