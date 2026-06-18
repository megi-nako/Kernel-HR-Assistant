import React, { useState, useEffect } from 'react'
import { Plus, Search, MessageCircle, Settings } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from './ds/Avatar'
import { Button } from './ds/Button'
import { useSession } from '../contexts/SessionContext'
import api from '../services'

function HistoryItem({ item, active, onSelect }) {
  const [hover, setHover] = useState(false)
  return (
    <button
      onClick={() => onSelect(item.id)}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left',
        padding: '9px 12px', border: 'none', borderRadius: 'var(--radius-sm)', cursor: 'pointer',
        fontFamily: 'var(--font-sans)', fontSize: 13.5,
        fontWeight: active ? 600 : 500,
        color: active ? 'var(--brand)' : 'var(--text-body)',
        background: active ? 'var(--brand-soft)' : hover ? 'var(--surface-sunken)' : 'transparent',
        transition: 'var(--transition-control)',
      }}
    >
      <span style={{ color: active ? 'var(--brand)' : 'var(--text-subtle)', display: 'flex', flex: 'none' }}>
        <MessageCircle size={16} />
      </span>
      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.title}</span>
    </button>
  )
}

export default function Sidebar({ history, activeId, onSelect, onNew }) {
  const { user, clearUser } = useSession()
  const navigate = useNavigate()
  const [status, setStatus] = useState(null)

  useEffect(() => {
    api.status().then(setStatus).catch(() => {})
  }, [])

  const groups = {}
  history.forEach((h) => {
    groups[h.when] = groups[h.when] || []
    groups[h.when].push(h)
  })

  const officeBadgeColor = user?.office === 'serbia' ? '#2980b9' : '#c0392b'
  const displayName = user?.displayName?.replace(/_/g, ' ') ?? ''

  return (
    <aside style={{
      width: 268, flex: 'none', height: '100%', boxSizing: 'border-box',
      background: 'var(--surface-card)', borderRight: '1px solid var(--border-subtle)',
      display: 'flex', flexDirection: 'column', padding: '18px 14px',
    }}>
      {/* Logo */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 4px 16px' }}>
        <img src="/logo-wordmark.svg" height="30" alt="Harmony" />
      </div>

      {/* New chat button */}
      <Button
        variant="primary"
        fullWidth
        leftIcon={<Plus size={18} />}
        onClick={onNew}
        style={{ marginBottom: 18 }}
      >
        New chat
      </Button>

      {/* Search (decorative) */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8, padding: '9px 12px', marginBottom: 16,
        border: '1px solid var(--border-default)', borderRadius: 'var(--radius-sm)',
        color: 'var(--text-subtle)', cursor: 'text',
      }}>
        <Search size={16} />
        <span style={{ fontSize: 13.5, color: 'var(--text-subtle)' }}>Search chats</span>
      </div>

      {/* Chat history */}
      <div style={{ flex: 1, overflowY: 'auto', margin: '0 -4px', padding: '0 4px' }}>
        {Object.keys(groups).map((when) => (
          <div key={when} style={{ marginBottom: 14 }}>
            <div style={{
              fontSize: 11, fontWeight: 800, letterSpacing: '.05em',
              textTransform: 'uppercase', color: 'var(--text-subtle)', padding: '0 12px 6px',
            }}>
              {when}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {groups[when].map((item) => (
                <HistoryItem key={item.id} item={item} active={item.id === activeId} onSelect={onSelect} />
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* Index status */}
      {status && (
        <div style={{
          fontSize: 11.5, color: 'var(--text-muted)', borderTop: '1px solid var(--border-subtle)',
          paddingTop: 12, marginBottom: 8,
        }}>
          <div style={{ fontWeight: 700, color: 'var(--text-subtle)', marginBottom: 3 }}>Index status</div>
          <div>{status.docCount} documents indexed</div>
        </div>
      )}

      {/* User info */}
      <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: 14, marginTop: 4 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 8px', borderRadius: 'var(--radius-sm)' }}>
          <Avatar name={displayName} size="md" status="online" />
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ fontSize: 13.5, fontWeight: 700, color: 'var(--text-strong)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {displayName}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 2 }}>
              <span style={{
                fontSize: 11, fontWeight: 700, letterSpacing: '.04em',
                textTransform: 'uppercase', padding: '1px 6px',
                borderRadius: 'var(--radius-pill)',
                background: officeBadgeColor, color: '#fff',
              }}>
                {user?.office}
              </span>
            </div>
          </div>
          <button
            onClick={async () => { await api.logout(); clearUser(); navigate('/login') }}
            title="Sign out"
            style={{
              background: 'none', border: 'none', cursor: 'pointer',
              color: 'var(--text-subtle)', display: 'flex', padding: 4, borderRadius: 'var(--radius-xs)',
            }}
          >
            <Settings size={17} />
          </button>
        </div>
      </div>
    </aside>
  )
}
