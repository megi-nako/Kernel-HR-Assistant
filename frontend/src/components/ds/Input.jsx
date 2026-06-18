import React from 'react'

/**
 * Text input with optional label, leading/trailing slots, and states.
 */
export function Input({
  label, hint, error, leadingIcon, trailingSlot, size = 'md',
  id, style, containerStyle, disabled = false, ...rest
}) {
  const [focus, setFocus] = React.useState(false)
  const reactId = React.useId()
  const inputId = id || reactId
  const h = size === 'lg' ? 54 : size === 'sm' ? 38 : 46
  const invalid = Boolean(error)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, ...containerStyle }}>
      {label && (
        <label htmlFor={inputId} style={{
          fontFamily: 'var(--font-sans)', fontSize: 'var(--text-sm)',
          fontWeight: 'var(--weight-semibold)', color: 'var(--text-strong)',
        }}>{label}</label>
      )}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8, height: h, padding: '0 14px',
        background: disabled ? 'var(--surface-sunken)' : 'var(--surface-card)',
        border: `1px solid ${invalid ? 'var(--danger)' : focus ? 'var(--border-brand)' : 'var(--border-default)'}`,
        borderRadius: 'var(--radius-control)',
        boxShadow: focus ? (invalid ? 'var(--ring-danger)' : 'var(--ring-brand)') : 'var(--shadow-xs)',
        transition: 'var(--transition-control)',
      }}>
        {leadingIcon && <span style={{ display: 'flex', color: 'var(--text-subtle)' }}>{leadingIcon}</span>}
        <input
          id={inputId} disabled={disabled}
          onFocus={(e) => { setFocus(true); rest.onFocus?.(e) }}
          onBlur={(e) => { setFocus(false); rest.onBlur?.(e) }}
          style={{
            flex: 1, minWidth: 0, border: 'none', outline: 'none', background: 'transparent',
            fontFamily: 'var(--font-sans)', fontSize: 'var(--text-md)', color: 'var(--text-strong)',
            height: '100%', ...style,
          }}
          {...rest}
        />
        {trailingSlot}
      </div>
      {(hint || error) && (
        <span style={{
          fontSize: 'var(--text-xs)', fontWeight: 'var(--weight-medium)',
          color: invalid ? 'var(--danger)' : 'var(--text-muted)',
        }}>{error || hint}</span>
      )}
    </div>
  )
}
