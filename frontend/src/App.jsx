import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { SessionProvider, useSession } from './contexts/SessionContext'
import LoginScreen from './components/LoginScreen'
import ChatLayout from './components/ChatLayout'

function RequireAuth({ children }) {
  const { user } = useSession()
  return user ? children : <Navigate to="/login" replace />
}

function RootRedirect() {
  const { user } = useSession()
  return <Navigate to={user ? '/chat' : '/login'} replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <SessionProvider>
        <Routes>
          <Route path="/login" element={<LoginScreen />} />
          <Route path="/chat" element={<RequireAuth><ChatLayout /></RequireAuth>} />
          <Route path="*" element={<RootRedirect />} />
        </Routes>
      </SessionProvider>
    </BrowserRouter>
  )
}
