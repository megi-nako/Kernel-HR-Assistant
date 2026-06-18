import React from 'react'

/**
 * On/off switch. Calm slide, brand-filled when on.
 */
export function Switch({ checked, defaultChecked = false, onChange, disabled = false, size = 'md', label, id, style }) {
  const isControlled = checked !== undefined
  const [internal, setInternal] = React.useState(defaultChecked)
  const on = isControlled ? checked : internal
  const reactId = React.useId()
  const switchId = id || reactId

  const W = size === 'sm' ? 36 : 46
  const H = size === 'sm' ? 20 : 26
  const knob = H - 6

  const toggle = () => {
    if (disabled) return
    if (!isControlled) setInternal(!on)
    onChange?.(!on)
  }

  const control = (
    <button
      type="button" role="switch" aria-checked={on} id={switchId} disabled={disabled} onClick={toggle}
      style={{
        position: 'relative', width: W, height: H, flex: 'none', padding: 0, border: 'none',
        borderRadius: 'var(--radius-full)', cursor: disabled ? 'not-allowed' : 'pointer',
        background: on ? 'var(--brand)' : 'var(--neutral-300)', opacity: disabled ? 0.5 : 1,
        transition: 'background-color var(--duration-normal) var(--ease-standard)', ...style,
      }}
    >
      <span style={{
        position: 'absolute', top: 3, left: on ? W - knob - 3 : 3, width: knob, height: knob,
        borderRadius: '50%', background: 'var(--neutral-0)', boxShadow: 'var(--shadow-sm)',
        transition: 'left var(--duration-normal) var(--ease-out)',
      }} />
    </button>
  )

  if (!label) return control
  return (
    <label htmlFor={switchId} style={{ display: 'inline-flex', alignItems: 'center', gap: 10, cursor: disabled ? 'not-allowed' : 'pointer' }}>
      {control}
      <span style={{ fontFamily: 'var(--font-sans)', fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)', color: 'var(--text-body)' }}>{label}</span>
    </label>
  )
}
