import React from 'react'

export function ChatBubble({ role = 'ai', children, time, name, status, style }) {
  const isUser = role === 'user'
  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      alignItems: isUser ? 'flex-end' : 'flex-start',
      maxWidth: '82%', alignSelf: isUser ? 'flex-end' : 'flex-start', ...style,
    }}>
      {name && (
        <span style={{
          fontSize: 'var(--text-xs)', fontWeight: 'var(--weight-semibold)',
          color: 'var(--text-muted)',
          margin: isUser ? '0 4px 5px 0' : '0 0 5px 4px',
        }}>{name}</span>
      )}
      <div style={{
        padding: '12px 16px',
        background: isUser ? 'var(--bubble-user-bg)' : 'var(--bubble-ai-bg)',
        color: isUser ? 'var(--bubble-user-text)' : 'var(--bubble-ai-text)',
        border: isUser ? '1px solid transparent' : '1px solid var(--bubble-ai-border)',
        borderRadius: 'var(--radius-bubble)',
        borderBottomRightRadius: isUser ? 'var(--radius-xs)' : 'var(--radius-bubble)',
        borderBottomLeftRadius:  isUser ? 'var(--radius-bubble)' : 'var(--radius-xs)',
        boxShadow: isUser ? 'none' : 'var(--shadow-sm)',
        fontFamily: 'var(--font-sans)', fontSize: 'var(--text-md)',
        lineHeight: 'var(--leading-relaxed)', wordBreak: 'break-word',
      }}>
        {children}
      </div>
      {(time || status) && (
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: 5,
          fontSize: 'var(--text-2xs)', color: 'var(--text-subtle)',
          margin: isUser ? '5px 4px 0 0' : '5px 0 0 4px',
        }}>
          {time}{status === 'sent' && isUser && ' · Sent'}
        </span>
      )}
    </div>
  )
}
