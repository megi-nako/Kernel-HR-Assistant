import React from 'react'

export function TypingIndicator({ style }) {
  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 8,
      padding: '14px 16px', background: 'var(--bubble-ai-bg)',
      border: '1px solid var(--bubble-ai-border)',
      borderRadius: 'var(--radius-bubble)',
      borderBottomLeftRadius: 'var(--radius-xs)',
      boxShadow: 'var(--shadow-sm)', ...style,
    }}>
      <span style={{ display: 'inline-flex', gap: 4 }}>
        {[0, 1, 2].map((i) => (
          <span key={i} style={{
            width: 7, height: 7, borderRadius: '50%', background: 'var(--brand)',
            animation: `hm-typing 1.2s ${i * 0.18}s infinite var(--ease-standard)`,
          }} />
        ))}
      </span>
    </div>
  )
}
