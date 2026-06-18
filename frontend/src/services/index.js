// Unified API facade — the rest of the app imports from here, never from
// `api` or `mockApi` directly.
//
// Selection is controlled by VITE_USE_MOCK:
//   'true'           → always use the in-memory mock (no backend needed)
//   'false'          → always use the real backend (errors surface as-is)
//   unset / 'auto'   → use the real backend, but transparently fall back to the
//                      mock if the backend is unreachable (connection refused /
//                      network error). Auth/HTTP errors are NOT swallowed.
import * as real from './api'
import * as mock from './mockApi'

const mode = String(import.meta.env.VITE_USE_MOCK ?? 'auto').toLowerCase()

let useMock = mode === 'true'
const allowFallback = mode !== 'false' && mode !== 'true'

// "Backend is down" surfaces in two ways depending on setup:
//   • No proxy (absolute VITE_API_BASE): fetch throws a TypeError ("Failed to fetch").
//   • Through the Vite dev proxy: the proxy answers 502/503/504 (see vite.config.js).
// Either case triggers the mock fallback. A genuine backend 500 (e.g. agent error)
// is NOT treated as down, so real failures still surface.
const isBackendDown = (e) =>
  e instanceof TypeError ||
  (e?.status >= 502 && e?.status <= 504) ||
  /failed to fetch|networkerror|load failed/i.test(e?.message ?? '')

async function call(name, args) {
  if (useMock) return mock[name](...args)
  try {
    return await real[name](...args)
  } catch (e) {
    if (allowFallback && isBackendDown(e)) {
      if (!useMock) {
        useMock = true
        console.warn(`[api] backend unreachable — switching to mock data for this session.`)
      }
      return mock[name](...args)
    }
    throw e
  }
}

export const listUsers = (...a) => call('listUsers', a)
export const login     = (...a) => call('login', a)
export const logout    = (...a) => call('logout', a)
export const chat      = (...a) => call('chat', a)
export const status    = (...a) => call('status', a)

export const isUsingMock = () => useMock

export default { listUsers, login, logout, chat, status, isUsingMock }
