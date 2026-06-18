import React, { createContext, useContext, useState } from 'react'

const Ctx = createContext(null)

export function SessionProvider({ children }) {
  const [user, setUser] = useState(null)

  const clearUser = () => setUser(null)

  return (
    <Ctx.Provider value={{ user, setUser, clearUser }}>
      {children}
    </Ctx.Provider>
  )
}

export const useSession = () => useContext(Ctx)
