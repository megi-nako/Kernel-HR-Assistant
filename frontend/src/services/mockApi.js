const OFFICE_MAP = {
  urim_albania: 'albania',
  agathi_albania: 'albania',
  srb_user: 'serbia',
}

const delay = (ms) => new Promise((r) => setTimeout(r, ms))

let loggedInUser = null

export const listUsers = async () => {
  await delay(100)
  return ['urim_albania', 'agathi_albania', 'srb_user']
}

export const login = async (username) => {
  await delay(300)
  const office = OFFICE_MAP[username] ?? 'albania'
  loggedInUser = { upn: `${username}@eng.it`, displayName: username, office }
  return loggedInUser
}

export const logout = async () => {
  await delay(100)
  loggedInUser = null
}

export const chat = async (question) => {
  await delay(800)
  const office = loggedInUser?.office ?? 'unknown'
  return {
    text: `[MOCK] This is a canned answer for the ${office} office. Your question was: "${question}". In production, Claude Opus 4.8 will answer from the ${office} HR documents.`,
    language: 'en',
    citations: [
      { sourceName: 'Office Attendance Rules.pdf', lastModified: '2026-02-11', url: null, page: 3 },
      { sourceName: 'Internal Policies ESL.pdf', lastModified: '2025-12-01', url: null, page: 7 },
    ],
    refused: false,
    reason: null,
  }
}

export const status = async () => {
  await delay(200)
  const office = loggedInUser?.office ?? 'unknown'
  return { office, docCount: 24, lastIndexed: '2026-06-18T10:00:00Z' }
}

export default { listUsers, login, logout, chat, status }
