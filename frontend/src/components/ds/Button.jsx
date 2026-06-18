import React from 'react'

const SIZES = {
  sm: { height: 36, padding: '0 14px', fontSize: 'var(--text-sm)', gap: 6,  radius: 'var(--radius-sm)' },
  md: { height: 44, padding: '0 20px', fontSize: 'var(--text-md)', gap: 8,  radius: 'var(--radius-control)' },
  lg: { height: 54, padding: '0 28px', fontSize: 'var(--text-lg)', gap: 10, radius: 'var(--radius-lg)' },
}

const VARIANTS = {
  primary:   { background: 'var(--brand)',        color: 'var(--text-on-brand)', border: '1px solid transparent', boxShadow: 'var(--shadow-brand)' },
  secondary: { background: 'var(--surface-card)', color: 'var(--text-strong)',   border: '1px solid var(--border-default)', boxShadow: 'var(--shadow-xs)' },
  soft:      { background: 'var(--brand-soft)',   color: 'var(--brand)',          border: '1px solid transparent', boxShadow: 'none' },
  ghost:     { background: 'transparent',         color: 'var(--text-body)',      border: '1px solid transparent', boxShadow: 'none' },
  danger:    { background: 'var(--danger)',        color: 'var(--neutral-0)',      border: '1px solid transparent', boxShadow: 'none' },
}

const HOVER = {
  primary: 'var(--brand-hover)', secondary: 'var(--surface-sunken)', soft: 'var(--brand-soft-hover)',
  ghost: 'var(--surface-sunken)', danger: 'var(--red-500)',
}

export function Button({
  children, variant = 'primary', size = 'md', leftIcon, rightIcon,
  loading = false, disabled = false, fullWidth = false, type = 'button',
  onClick, style, ...rest
}) {
  const s = SIZES[size] || SIZES.md
  const v = VARIANTS[variant] || VARIANTS.primary
  const isDisabled = disabled || loading
  const [hover, setHover] = React.useState(false)

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={isDisabled}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        gap: s.gap, height: s.height, padding: s.padding,
        width: fullWidth ? '100%' : 'auto',
        fontFamily: 'var(--font-sans)', fontSize: s.fontSize,
        fontWeight: 'var(--weight-semibold)', lineHeight: 1,
        letterSpacing: 'var(--tracking-snug)', whiteSpace: 'nowrap',
        borderRadius: s.radius, cursor: isDisabled ? 'not-allowed' : 'pointer',
        transition: 'var(--transition-control)', userSelect: 'none',
        opacity: isDisabled ? 0.55 : 1,
        transform: hover && !isDisabled ? 'translateY(-1px)' : 'translateY(0)',
        ...v,
        background: hover && !isDisabled ? (HOVER[variant] || v.background) : v.background,
        ...style,
      }}
      {...rest}
    >
      {loading && <Spinner />}
      {!loading && leftIcon}
      {children && <span>{children}</span>}
      {!loading && rightIcon}
    </button>
  )
}

function Spinner() {
  return (
    <span style={{
      width: 16, height: 16, borderRadius: '50%',
      border: '2px solid currentColor', borderTopColor: 'transparent',
      display: 'inline-block', animation: 'hm-spin 0.7s linear infinite',
    }} />
  )
}
