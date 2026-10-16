import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { homeFor, useAuth } from '../context/AuthContext'
import { Loading } from './ui'

export default function ProtectedRoute({ roles }) {
  const { user, ready } = useAuth()
  const loc = useLocation()
  if (!ready) return <Loading label="Checking your session" className="min-h-screen" />
  if (!user) return <Navigate to="/login" replace state={{ from: loc.pathname }} />
  if (roles && !roles.includes(user.role)) return <Navigate to={homeFor(user.role)} replace />
  return <Outlet />
}
