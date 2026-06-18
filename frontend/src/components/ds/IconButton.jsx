import React from 'react'

const SIZES = { sm: 32, md: 40, lg: 48 }

const VARIANTS = {
  solid:   { background: 'var(--brand)',        color: 'var(--text-on-brand)', border: '1px solid transparent' },
  soft:    { background: 'var(--brand-soft)',   color: 'var(--brand)',          border: '1px solid transparent' },
  ghost:   { background: 'transparent',         color: 'var(--text-muted)',     border: '1px solid transparent' },
  outline: { background: 'var(--surface-card)', color: 'var(--text-body)',      border: '1px solid var(--border-default)' },
}

const HOVER = {
  solid: 'var(--brand-hover)', soft: 'var(--brand-soft-hover)',
  ghost: 'var(--surface-sunken)', outline: 'var(--surface-sunken)',
}

export function IconButton({ children, variant = 'ghost', size = 'md', label, disabled = false, round = false, onClick, style, ...rest }) {
  const dim = SIZES[size] || SIZES.md
  const v = VARIANTS[variant] || VARIANTS.ghost
  const [hover, setHover] = React.useState(false)

  return (
    <button
      type="button" aria-label={label} title={label}
      onClick={onClick} disabled={disabled}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        width: dim, height: dim, flex: 'none',
        borderRadius: round ? 'var(--radius-full)' : 'var(--radius-sm)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        transition: 'var(--transition-control)',
        ...v,
        background: hover && !disabled ? HOVER[variant] : v.background,
        ...style,
      }}
      {...rest}
    >
      {children}
    </button>
  )
}
