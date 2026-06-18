import React from 'react'

const PADS = { sm: 'var(--space-4)', md: 'var(--space-6)', lg: 'var(--space-8)' }

/**
 * Surface container — soft-rounded, low shadow. The base panel of Harmony.
 */
export function Card({ children, padding = 'md', interactive = false, selected = false, style, onClick, ...rest }) {
  const [hover, setHover] = React.useState(false)
  return (
    <div
      onClick={onClick}
      onMouseEnter={() => interactive && setHover(true)}
      onMouseLeave={() => interactive && setHover(false)}
      style={{
        background: 'var(--surface-card)',
        border: `1px solid ${selected ? 'var(--border-brand)' : 'var(--border-subtle)'}`,
        borderRadius: 'var(--radius-card)',
        padding: PADS[padding] ?? padding,
        boxShadow: selected ? 'var(--ring-brand)' : hover ? 'var(--shadow-md)' : 'var(--shadow-sm)',
        cursor: interactive ? 'pointer' : 'default',
        transform: hover ? 'translateY(-2px)' : 'none',
        transition: 'var(--transition-control)',
        ...style,
      }}
      {...rest}
    >
      {children}
    </div>
  )
}
