import React, { useState, useEffect } from 'react'
import { Plus, Search, MessageCircle, LogOut } from 'lucide-react'
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
        padding: '9px 12px', border: 'none', borderRadius: 'var(--radius-control)', cursor: 'pointer',
        fontFamily: 'var(--font-sans)', fontSize: 13.5,
        fontWeight: active ? 600 : 500,
        color: active ? '#fff' : 'var(--text-on-dark-muted)',
        background: active ? 'rgba(214,36,154,.20)' : hover ? 'var(--surface-dark-hover)' : 'transparent',
        boxShadow: active ? 'inset 2px 0 0 var(--eng-400)' : 'none',
        transition: 'var(--transition-control)',
      }}
    >
      <span style={{ color: active ? 'var(--eng-300)' : 'var(--text-on-dark-subtle)', display: 'flex', flex: 'none' }}>
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
  const [hoverSearch, setHoverSearch] = useState(false)
  const [hoverOut, setHoverOut] = useState(false)

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
      background: 'var(--surface-dark)', borderRight: '1px solid var(--border-on-dark)',
      display: 'flex', flexDirection: 'column', padding: '18px 14px',
    }}>
      {/* Logo */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '2px 4px 18px' }}>
        <img src="/eng-full-light.svg" height="24" alt="eng" />
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
      <div
        onMouseEnter={() => setHoverSearch(true)}
        onMouseLeave={() => setHoverSearch(false)}
        style={{
          display: 'flex', alignItems: 'center', gap: 8, padding: '9px 12px', marginBottom: 16,
          border: `1px solid ${hoverSearch ? 'var(--border-on-dark-strong)' : 'var(--border-on-dark)'}`,
          borderRadius: 'var(--radius-control)', color: 'var(--text-on-dark-subtle)', cursor: 'text',
          transition: 'var(--transition-control)',
        }}
      >
        <Search size={16} />
        <span style={{ fontSize: 13.5 }}>Search chats</span>
      </div>

      {/* Chat history */}
      <div style={{ flex: 1, overflowY: 'auto', margin: '0 -4px', padding: '0 4px' }}>
        {Object.keys(groups).map((when) => (
          <div key={when} style={{ marginBottom: 14 }}>
            <div style={{
              fontSize: 11, fontWeight: 800, letterSpacing: '.05em',
              textTransform: 'uppercase', color: 'var(--text-on-dark-subtle)', padding: '0 12px 6px',
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
          fontSize: 11.5, color: 'var(--text-on-dark-muted)', borderTop: '1px solid var(--border-on-dark)',
          paddingTop: 12, marginBottom: 8,
        }}>
          <div style={{ fontWeight: 700, color: 'var(--text-on-dark-subtle)', marginBottom: 3 }}>Index status</div>
          <div>{status.docCount} documents indexed</div>
        </div>
      )}

      {/* User info */}
      <div style={{ borderTop: '1px solid var(--border-on-dark)', paddingTop: 14, marginTop: 4 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 8px', borderRadius: 'var(--radius-control)' }}>
          <Avatar name={displayName} size="md" status="online" />
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ fontSize: 13.5, fontWeight: 700, color: '#fff', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
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
            onMouseEnter={() => setHoverOut(true)}
            onMouseLeave={() => setHoverOut(false)}
            title="Sign out"
            style={{
              background: hoverOut ? 'var(--surface-dark-hover)' : 'none', border: 'none', cursor: 'pointer',
              color: hoverOut ? '#fff' : 'var(--text-on-dark-subtle)', display: 'flex', padding: 6,
              borderRadius: 'var(--radius-control)', transition: 'var(--transition-control)',
            }}
          >
            <LogOut size={17} />
          </button>
        </div>
      </div>
    </aside>
  )
}
