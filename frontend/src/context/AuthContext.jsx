import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { setUnauthorizedHandler, storage } from '../api/client'
import { Auth } from '../api/endpoints'

const AuthCtx = createContext(null)

export const homeFor = (role) => ({ ADMIN: '/admin/dashboard', TEACHER: '/teacher/dashboard', STUDENT: '/student/dashboard' }[role] || '/')

export function AuthProvider({ children }) {
  const [user, setUser] = useState(storage.user)
  const [ready, setReady] = useState(!storage.token)

  const logout = useCallback(() => {
    storage.clear()
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => logout())
    if (!storage.token) return
    Auth.me()
      .then((u) => { setUser(u); storage.save(storage.token, u) })
      .catch(() => logout())
      .finally(() => setReady(true))
  }, [logout])

  const accept = (data) => {
    storage.save(data.token, data.user)
    setUser(data.user)
    return data.user
  }
  const login = async (email, password) => accept(await Auth.login(email, password))
  const register = async (body) => accept(await Auth.register(body))

  const value = useMemo(() => ({ user, ready, login, register, logout }), [user, ready, logout])
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}

export const useAuth = () => useContext(AuthCtx)
