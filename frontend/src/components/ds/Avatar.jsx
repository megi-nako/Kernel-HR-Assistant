import React from 'react'

const SIZES = { xs: 24, sm: 32, md: 40, lg: 48, xl: 64 }

function initialsOf(name = '') {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return '?'
  return (parts[0][0] + (parts[1]?.[0] || '')).toUpperCase()
}

export function Avatar({ src, name, size = 'md', variant = 'user', status, style }) {
  const dim = SIZES[size] || SIZES.md
  const fontSize = dim <= 24 ? 10 : dim <= 32 ? 12 : dim <= 40 ? 14 : dim <= 48 ? 16 : 22
  const isAI = variant === 'ai'

  return (
    <span style={{ position: 'relative', display: 'inline-flex', flex: 'none' }}>
      <span style={{
        width: dim, height: dim, borderRadius: '50%', overflow: 'hidden',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: 'var(--font-sans)', fontSize, fontWeight: 'var(--weight-bold)',
        color: isAI ? 'var(--neutral-0)' : 'var(--brand)',
        background: isAI ? 'var(--brand-gradient)' : 'var(--brand-soft)',
        border: isAI ? 'none' : '1px solid var(--eng-100)',
        ...style,
      }}>
        {src
          ? <img src={src} alt={name || ''} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          : isAI
            ? <img src="/eng-icon-light.svg" alt="" style={{ width: dim * 0.56, height: dim * 0.56, objectFit: 'contain' }} />
            : initialsOf(name)}
      </span>
      {status && (
        <span style={{
          position: 'absolute', right: 0, bottom: 0,
          width: dim * 0.28, height: dim * 0.28, minWidth: 8, minHeight: 8,
          borderRadius: '50%', border: '2px solid var(--surface-card)',
          background: status === 'online' ? 'var(--success)' : status === 'busy' ? 'var(--danger)' : 'var(--neutral-400)',
        }} />
      )}
    </span>
  )
}
