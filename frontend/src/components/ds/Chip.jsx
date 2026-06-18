import React from 'react'

/**
 * Chip — selectable pill. Used for reply suggestions, filters, topics.
 */
export function Chip({ children, leadingIcon, selected = false, onClick, as = 'button', style, ...rest }) {
  const [hover, setHover] = React.useState(false)
  const clickable = Boolean(onClick) || as === 'button'
  const Tag = as
  return (
    <Tag
      type={as === 'button' ? 'button' : undefined}
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 7,
        padding: '8px 14px', borderRadius: 'var(--radius-pill)',
        fontFamily: 'var(--font-sans)', fontSize: 'var(--text-sm)',
        fontWeight: 'var(--weight-medium)', lineHeight: 1, whiteSpace: 'nowrap',
        cursor: clickable ? 'pointer' : 'default', transition: 'var(--transition-control)',
        color: selected ? 'var(--brand)' : 'var(--text-body)',
        background: selected ? 'var(--brand-soft)' : hover && clickable ? 'var(--surface-sunken)' : 'var(--surface-card)',
        border: `1px solid ${selected ? 'var(--border-brand)' : 'var(--border-default)'}`,
        ...style,
      }}
      {...rest}
    >
      {leadingIcon && <span style={{ display: 'flex', color: selected ? 'var(--brand)' : 'var(--text-subtle)' }}>{leadingIcon}</span>}
      {children}
    </Tag>
  )
}
