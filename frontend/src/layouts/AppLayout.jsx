import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Navbar from '../components/Navbar'
import Sidebar from '../components/Sidebar'
import { useAuth } from '../context/AuthContext'

export default function AppLayout() {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  return (
    <div className="min-h-screen">
      <Sidebar role={user?.role} open={open} onClose={() => setOpen(false)} />
      <div className="lg:pl-64">
        <Navbar onMenu={() => setOpen(true)} />
        <main className="mx-auto max-w-screen-2xl px-4 py-8 sm:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
