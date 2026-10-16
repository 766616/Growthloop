import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard, UserRound, GraduationCap, Sparkles, Target, Map, Lightbulb, Users, CalendarClock, MessageSquareHeart,
  BarChart3, Shapes, Flag, BookOpen, UsersRound, School, MessageSquareText,
} from 'lucide-react'
import Logo from './Logo'

export const NAV = {
  STUDENT: [
    { to: '/student/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/student/profile', label: 'Profile & goal', icon: UserRound },
    { to: '/student/academics', label: 'Academics', icon: GraduationCap },
    { to: '/student/skills', label: 'My skills', icon: Sparkles },
    { to: '/student/skill-gap', label: 'Skill gap', icon: Target },
    { to: '/student/roadmap', label: 'Roadmap', icon: Map },
    { to: '/student/recommendations', label: 'Recommendations', icon: Lightbulb },
    { to: '/student/mentors', label: 'Mentors & peers', icon: Users },
    { to: '/student/sessions', label: 'Sessions', icon: CalendarClock },
    { to: '/student/feedback', label: 'Feedback', icon: MessageSquareHeart },
  ],
  TEACHER: [
    { to: '/teacher/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/teacher/students', label: 'My students', icon: UsersRound },
    { to: '/teacher/analytics', label: 'Class analytics', icon: BarChart3 },
    { to: '/teacher/sessions', label: 'Mentoring sessions', icon: CalendarClock },
  ],
  ADMIN: [
    { to: '/admin/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/admin/users', label: 'Users', icon: UsersRound },
    { to: '/admin/feedback', label: 'Feedback', icon: MessageSquareText },
    { to: '/admin/students', label: 'Students', icon: GraduationCap },
    { to: '/admin/teachers', label: 'Teachers', icon: School },
    { to: '/admin/academics', label: 'Academic records', icon: BookOpen },
    { to: '/admin/skills', label: 'Skills', icon: Shapes },
    { to: '/admin/career-goals', label: 'Career goals', icon: Flag },
    { to: '/admin/resources', label: 'Resources', icon: Lightbulb },
    { to: '/admin/analytics', label: 'Analytics', icon: BarChart3 },
  ],
}

export default function Sidebar({ role, open, onClose }) {
  const items = NAV[role] || []
  return (
    <>
      {open && <div className="fixed inset-0 z-30 bg-ink-900/40 lg:hidden" onClick={onClose} />}
      <aside className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col bg-ink transition-transform lg:translate-x-0 ${open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="px-6 pb-6 pt-6"><Logo light /></div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto px-3 pb-6">
          {items.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} onClick={onClose}
              className={({ isActive }) => `group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${isActive ? 'bg-white/10 text-white' : 'text-ink-100 hover:bg-white/5 hover:text-white'}`}>
              {({ isActive }) => (
                <>
                  <span className={`h-5 w-1 rounded-full ${isActive ? 'bg-marigold' : 'bg-transparent'}`} />
                  <Icon className="h-5 w-5" />
                  {label}
                </>
              )}
            </NavLink>
          ))}
        </nav>
        <p className="px-6 pb-5 text-xs text-ink-300">Learn, practise, get mentored, reflect — then loop again.</p>
      </aside>
    </>
  )
}
