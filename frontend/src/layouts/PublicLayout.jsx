import { Link, NavLink, Outlet } from 'react-router-dom'
import Logo from '../components/Logo'
import { homeFor, useAuth } from '../context/AuthContext'

export default function PublicLayout() {
  const { user } = useAuth()
  return (
    <div className="flex min-h-screen flex-col">
      <header className="mx-auto flex w-full max-w-6xl items-center justify-between px-5 py-5 sm:px-8">
        <Link to="/"><Logo /></Link>
        <nav className="flex items-center gap-1 text-sm font-medium sm:gap-3">
          <NavLink to="/about" className="rounded-lg px-3 py-2 text-ink-700 hover:bg-white">About</NavLink>
          {user ? (
            <Link to={homeFor(user.role)} className="rounded-lg bg-ink px-4 py-2 font-semibold text-white hover:bg-ink-700">Open dashboard</Link>
          ) : (
            <>
              <NavLink to="/login" className="rounded-lg px-3 py-2 text-ink-700 hover:bg-white">Log in</NavLink>
              <Link to="/register" className="rounded-lg bg-ink px-4 py-2 font-semibold text-white hover:bg-ink-700">Create account</Link>
            </>
          )}
        </nav>
      </header>
      <main className="flex-1"><Outlet /></main>
      <footer className="border-t border-chalk-300">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-5 py-6 text-sm text-slateink sm:flex-row sm:justify-between sm:px-8">
          <p>GrowthLoop — a student growth and mentoring platform.</p>
          <p>Demo data is fictional. Risk scores are an early-support signal, not a judgement.</p>
        </div>
      </footer>
    </div>
  )
}
