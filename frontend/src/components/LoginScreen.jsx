import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Clock, ShieldCheck, Lock } from 'lucide-react'
import { useSession } from '../contexts/SessionContext'
import api from '../services'

function Feature({ icon: Icon, title, desc }) {
  return (
    <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
      <div style={{
        width: 40, height: 40, flex: 'none', borderRadius: 'var(--radius-md)',
        background: 'rgba(255,255,255,.16)', display: 'flex',
        alignItems: 'center', justifyContent: 'center', color: '#fff',
      }}>
        <Icon size={20} />
      </div>
      <div>
        <div style={{ color: '#fff', fontWeight: 700, fontSize: 15 }}>{title}</div>
        <div style={{ color: 'rgba(255,255,255,.72)', fontSize: 13.5, lineHeight: 1.5 }}>{desc}</div>
      </div>
    </div>
  )
}

export default function LoginScreen() {
  const navigate = useNavigate()
  const { setUser } = useSession()
  const [users, setUsers] = useState([])
  const [selectedUser, setSelectedUser] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api.listUsers().then(setUsers).catch(() => setError('Could not load profiles.'))
  }, [])

  const signIn = async () => {
    if (!selectedUser) return
    setLoading(true)
    setError('')
    try {
      const user = await api.login(selectedUser)
      setUser(user)
      navigate('/chat')
    } catch (e) {
      setError('Sign-in failed. Please try again.')
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--surface-card)' }}>
      {/* Brand panel */}
      <div style={{
        flex: '1 1 50%', position: 'relative', overflow: 'hidden',
        background: 'linear-gradient(150deg,#4F46E5 0%,#6366F1 55%,#818CF8 100%)',
        padding: '56px 56px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
      }}>
        <div style={{
          position: 'absolute', width: 420, height: 420, borderRadius: '50%',
          background: 'rgba(255,255,255,.10)', top: -120, right: -120, pointerEvents: 'none',
        }} />
        <div style={{
          position: 'absolute', width: 260, height: 260, borderRadius: '50%',
          background: 'rgba(255,255,255,.08)', bottom: -80, left: -60, pointerEvents: 'none',
        }} />

        <img src="/logo-wordmark-inverse.svg" height="40" alt="Harmony" style={{ position: 'relative' }} />

        <div style={{ position: 'relative', maxWidth: 380 }}>
          <h1 style={{
            color: '#fff', fontSize: 38, lineHeight: 1.12, fontWeight: 800,
            letterSpacing: '-.02em', margin: '0 0 14px',
          }}>
            HR answers, the moment you need them.
          </h1>
          <p style={{ color: 'rgba(255,255,255,.78)', fontSize: 16, lineHeight: 1.6, margin: '0 0 36px' }}>
            Kernel HR is your always-on HR assistant — time off, benefits, payroll and policies, in plain language.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <Feature icon={Clock} title="Instant, accurate answers" desc="Pulled straight from your company handbook." />
            <Feature icon={ShieldCheck} title="Private & secure" desc="Office-isolated — Serbia and Albania data never mix." />
          </div>
        </div>

        <div style={{ position: 'relative', color: 'rgba(255,255,255,.6)', fontSize: 12.5 }}>
          © 2026 Kernel HR · Engineering Albania & Engineering Serbia
        </div>
      </div>

      {/* Sign-in panel */}
      <div style={{ flex: '1 1 50%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 32 }}>
        <div style={{ width: '100%', maxWidth: 380 }}>
          <img src="/logo-mark.svg" width="56" height="56" alt="Kernel HR" style={{ marginBottom: 24 }} />

          <h2 style={{
            fontSize: 28, fontWeight: 800, letterSpacing: '-.02em',
            color: 'var(--text-strong)', margin: '0 0 8px',
          }}>Welcome back</h2>
          <p style={{ fontSize: 15, color: 'var(--text-muted)', lineHeight: 1.55, margin: '0 0 32px' }}>
            Select your profile to start chatting with the Kernel HR Assistant.
          </p>

          {/* Profile selector */}
          <div style={{ position: 'relative', marginBottom: 12 }}>
            <select
              value={selectedUser}
              onChange={(e) => setSelectedUser(e.target.value)}
              disabled={loading}
              style={{
                width: '100%', height: 54, padding: '0 40px 0 16px',
                fontFamily: 'var(--font-sans)', fontSize: 15,
                color: selectedUser ? 'var(--text-strong)' : 'var(--text-muted)',
                background: 'var(--surface-card)',
                border: '1px solid var(--border-default)',
                borderRadius: 'var(--radius-control)',
                boxShadow: 'var(--shadow-sm)',
                cursor: loading ? 'wait' : 'pointer',
                outline: 'none',
                appearance: 'none',
                transition: 'var(--transition-control)',
              }}
              onFocus={(e) => { e.target.style.borderColor = 'var(--border-brand)'; e.target.style.boxShadow = 'var(--ring-brand)' }}
              onBlur={(e)  => { e.target.style.borderColor = 'var(--border-default)'; e.target.style.boxShadow = 'var(--shadow-sm)' }}
            >
              <option value="">Select your profile…</option>
              {users.map((u) => (
                <option key={u} value={u}>{u}</option>
              ))}
            </select>
            {/* Chevron */}
            <svg style={{ position: 'absolute', right: 14, top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none', color: 'var(--text-muted)' }}
              width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M6 9l6 6 6-6" />
            </svg>
          </div>

          <button
            onClick={signIn}
            disabled={!selectedUser || loading}
            style={{
              width: '100%', height: 54, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12,
              background: selectedUser && !loading ? 'var(--brand)' : 'var(--surface-card)',
              color: selectedUser && !loading ? 'var(--text-on-brand)' : 'var(--text-muted)',
              border: selectedUser && !loading ? '1px solid transparent' : '1px solid var(--border-default)',
              borderRadius: 'var(--radius-control)',
              fontFamily: 'var(--font-sans)', fontSize: 16, fontWeight: 600,
              cursor: (!selectedUser || loading) ? 'not-allowed' : 'pointer',
              boxShadow: selectedUser && !loading ? 'var(--shadow-brand)' : 'var(--shadow-sm)',
              transition: 'var(--transition-control)',
              opacity: !selectedUser ? 0.6 : 1,
            }}
          >
            {loading
              ? (
                <>
                  <span style={{
                    width: 18, height: 18, borderRadius: '50%',
                    border: '2px solid currentColor', borderTopColor: 'transparent',
                    display: 'inline-block', animation: 'hm-login-spin .7s linear infinite',
                  }} />
                  Signing in…
                </>
              )
              : 'Sign In'}
          </button>

          {error && (
            <p style={{ marginTop: 12, fontSize: 13, color: 'var(--danger)', textAlign: 'center' }}>{error}</p>
          )}

          <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '28px 0' }}>
            <div style={{ flex: 1, height: 1, background: 'var(--border-subtle)' }} />
            <span style={{ fontSize: 12, color: 'var(--text-subtle)', fontWeight: 600 }}>SECURE ACCESS</span>
            <div style={{ flex: 1, height: 1, background: 'var(--border-subtle)' }} />
          </div>

          <div style={{ display: 'flex', gap: 9, alignItems: 'flex-start', color: 'var(--text-muted)', fontSize: 12.5, lineHeight: 1.5 }}>
            <span style={{ color: 'var(--success)', display: 'flex', marginTop: 1 }}>
              <Lock size={15} />
            </span>
            <span>
              The assistant only accesses HR documents scoped to your office. Your conversations stay within your organization.
            </span>
          </div>

          <p style={{ marginTop: 36, fontSize: 12.5, color: 'var(--text-subtle)', textAlign: 'center' }}>
            Demo mode — profile picker replaces Microsoft SSO.
          </p>
        </div>
      </div>
    </div>
  )
}
