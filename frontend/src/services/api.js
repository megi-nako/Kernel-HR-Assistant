const BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'

async function req(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export const listUsers = () => req('GET', '/api/users')
export const login = (username) => req('POST', '/api/login', { username })
export const logout = () => req('POST', '/api/logout', {})
// office is NOT sent — backend reads it from the server session
export const chat = (question) => req('POST', '/api/chat', { question })
export const status = () => req('GET', '/api/status')

export default { listUsers, login, logout, chat, status }
