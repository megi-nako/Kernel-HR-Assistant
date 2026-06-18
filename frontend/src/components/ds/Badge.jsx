import React from 'react'

const TONES = {
  neutral: { bg: 'var(--neutral-100)', fg: 'var(--neutral-600)', dot: 'var(--neutral-400)' },
  brand:   { bg: 'var(--brand-soft)',  fg: 'var(--brand)',       dot: 'var(--brand)' },
  success: { bg: 'var(--success-soft)',fg: 'var(--success)',     dot: 'var(--success)' },
  warning: { bg: 'var(--warning-soft)',fg: 'var(--warning)',     dot: 'var(--warning)' },
  danger:  { bg: 'var(--danger-soft)', fg: 'var(--danger)',      dot: 'var(--danger)' },
  info:    { bg: 'var(--info-soft)',   fg: 'var(--info)',        dot: 'var(--info)' },
}

export function Badge({ children, tone = 'neutral', size = 'md', dot = false, style }) {
  const t = TONES[tone] || TONES.neutral
  const pad = size === 'sm' ? '2px 8px' : '4px 10px'
  const fs  = size === 'sm' ? 'var(--text-2xs)' : 'var(--text-xs)'
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 6, padding: pad,
      background: t.bg, color: t.fg, borderRadius: 'var(--radius-pill)',
      fontFamily: 'var(--font-sans)', fontSize: fs, fontWeight: 'var(--weight-semibold)',
      lineHeight: 1.2, letterSpacing: 'var(--tracking-snug)', whiteSpace: 'nowrap', ...style,
    }}>
      {dot && <span style={{ width: 6, height: 6, borderRadius: '50%', background: t.dot, flex: 'none' }} />}
      {children}
    </span>
  )
}
