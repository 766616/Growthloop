import { useCallback, useEffect, useRef, useState } from 'react'
import { Bell, LogOut, Menu } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Notifications } from '../api/endpoints'
import { useAuth } from '../context/AuthContext'
import { relative, sentence } from '../lib/format'
import { Avatar } from './ui'

export default function Navbar({ onMenu }) {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  const [open, setOpen] = useState(false)
  const [data, setData] = useState({ items: [], unread: 0 })
  const [loadError, setLoadError] = useState(false)
  const box = useRef(null)
  const requestId = useRef(0)

  const load = useCallback(() => {
    const current = ++requestId.current
    return Notifications.list()
      .then((next) => {
        if (current === requestId.current && next) {
          setData(next)
          setLoadError(false)
        }
      })
      .catch(() => {
        if (current === requestId.current) setLoadError(true)
      })
  }, [user?.id])

  useEffect(() => {
    requestId.current += 1
    setData({ items: [], unread: 0 })
    setLoadError(false)
    load()
    const refreshWhenVisible = () => { if (!document.hidden) load() }
    const refreshWhenOnline = () => load()
    const t = setInterval(refreshWhenVisible, 60000)
    document.addEventListener('visibilitychange', refreshWhenVisible)
    window.addEventListener('online', refreshWhenOnline)
    return () => {
      requestId.current += 1
      clearInterval(t)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
      window.removeEventListener('online', refreshWhenOnline)
    }
  }, [load])
  useEffect(() => {
    const close = (e) => box.current && !box.current.contains(e.target) && setOpen(false)
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const readAll = async () => { await Notifications.readAll().catch(() => {}); load() }
  const readOne = async (n) => { if (!n.read) { await Notifications.read(n.id).catch(() => {}); load() } }

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-chalk-300 bg-chalk/90 px-4 backdrop-blur sm:px-8">
      <button className="rounded-lg p-2 text-ink hover:bg-white lg:hidden" onClick={onMenu} aria-label="Open menu"><Menu className="h-5 w-5" /></button>
      <div className="flex-1" />
      <div className="relative" ref={box}>
        <button onClick={() => { setOpen((o) => !o); if (!open) load() }} className="relative rounded-lg p-2 text-ink hover:bg-white" aria-label="Notifications">
          <Bell className="h-5 w-5" />
          {data.unread > 0 && <span className="absolute right-1 top-1 grid h-4 min-w-4 place-items-center rounded-full bg-coral px-1 text-xs font-bold text-white">{data.unread}</span>}
        </button>
        {open && (
          <div className="absolute right-0 mt-2 w-[min(92vw,360px)] overflow-hidden rounded-xl border border-chalk-300 bg-white shadow-lift">
            <div className="flex items-center justify-between border-b border-chalk-200 px-4 py-3">
              <p className="font-display font-bold">Notifications</p>
              {data.unread > 0 && <button className="text-xs font-semibold text-ink-500 hover:text-ink" onClick={readAll}>Mark all read</button>}
            </div>
            <ul className="max-h-96 overflow-y-auto">
              {loadError && <li className="flex items-center justify-between gap-3 border-b border-coral-100 bg-coral-50 px-4 py-3 text-sm text-coral-700" role="alert">
                <span>Notifications could not be loaded.</span>
                <button className="font-semibold underline" onClick={load}>Retry</button>
              </li>}
              {data.items.length === 0 && <li className="px-4 py-8 text-center text-sm text-slateink">You're all caught up.</li>}
              {data.items.map((n) => (
                <li key={n.id} onClick={() => readOne(n)} className={`cursor-pointer border-b border-chalk-200 px-4 py-3 last:border-0 hover:bg-chalk ${n.read ? '' : 'bg-marigold-50/60'}`}>
                  <p className="text-sm font-semibold text-ink">{n.title}</p>
                  <p className="text-sm text-slateink">{n.message}</p>
                  <p className="mt-1 text-xs text-ink-300">{relative(n.createdAt)}</p>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
      <div className="flex items-center gap-3 border-l border-chalk-300 pl-3">
        <Avatar name={user?.fullName} />
        <div className="hidden leading-tight sm:block">
          <p className="text-sm font-semibold text-ink">{user?.fullName}</p>
          <p className="text-xs text-slateink">{sentence(user?.role)}</p>
        </div>
        <button onClick={() => { logout(); nav('/login') }} className="rounded-lg p-2 text-slateink hover:bg-white hover:text-ink" aria-label="Log out" title="Log out">
          <LogOut className="h-5 w-5" />
        </button>
      </div>
    </header>
  )
}
