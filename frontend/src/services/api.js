// Default to relative paths so the Vite dev proxy (vite.config.js → :8080) handles
// requests with no CORS friction. Override with VITE_API_BASE for a non-proxied build.
const BASE = import.meta.env.VITE_API_BASE ?? ''

async function req(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`)
    err.status = res.status
    throw err
  }
  return res.json()
}

export const listUsers = () => req('GET', '/api/users')
export const login = (username) => req('POST', '/api/login', { username })
export const logout = () => req('POST', '/api/logout', {})
// office is NOT sent — backend reads it from the server session.
// history is [{role:'user'|'assistant', content:'...'}] — complete pairs only, no refused turns.
export const chat = (question, history = []) => req('POST', '/api/chat', { question, history })
export const status = () => req('GET', '/api/status')

export default { listUsers, login, logout, chat, status }
