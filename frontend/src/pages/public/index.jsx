import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import GrowthLoopRing from '../../components/GrowthLoopRing'
import { Button, Field, Input } from '../../components/ui'
import { homeFor, useAuth } from '../../context/AuthContext'

const STEPS = [
  ['Profile', 'Set a career goal and list the skills you already have.'],
  ['Analyse', 'Risk, skill-gap and dependency-graph engines read your data.'],
  ['Roadmap', 'Missing skills are ordered so prerequisites come first.'],
  ['Mentor', 'Get matched with a teacher or peer who can help, with reasons.'],
  ['Reflect', 'Both sides rate the session; your plan regenerates.'],
]

export function Home() {
  return (
    <div className="mx-auto max-w-6xl px-5 sm:px-8">
      <section className="grid items-center gap-10 py-12 lg:grid-cols-[1.1fr_1fr] lg:py-20">
        <div>
          <h1 className="text-5xl font-extrabold leading-[1.02] text-ink sm:text-6xl">Every semester, a tighter loop between what you know and where you're going.</h1>
          <p className="mt-6 max-w-xl text-lg text-slateink">GrowthLoop reads marks, attendance and skills, spots who needs support early, and turns a career goal into an ordered roadmap with a mentor attached.</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/register"><Button size="lg" variant="accent">Create a student account</Button></Link>
            <Link to="/login"><Button size="lg" variant="outline">Log in</Button></Link>
          </div>
        </div>
        <div className="rounded-3xl bg-ink p-8 shadow-lift">
          <p className="mb-5 text-sm text-ink-100">Aarav, Java Backend Developer goal, week 6</p>
          <GrowthLoopRing roadmap={42} coverage={61} academics={74} />
        </div>
      </section>
      <section className="border-t border-chalk-300 py-14">
        <h2 className="text-3xl font-extrabold">How the loop works</h2>
        <ol className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-5">
          {STEPS.map(([t, d], i) => (
            <li key={t} className="relative pl-10 lg:pl-0 lg:pt-12">
              <span className="absolute left-0 top-0 grid h-8 w-8 place-items-center rounded-full bg-marigold font-display font-bold text-ink">{i + 1}</span>
              <p className="font-display text-lg font-bold">{t}</p>
              <p className="mt-1 text-sm text-slateink">{d}</p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}

export function About() {
  return (
    <div className="mx-auto max-w-3xl px-5 py-12 sm:px-8">
      <h1 className="text-4xl font-extrabold">About GrowthLoop</h1>
      <div className="mt-6 space-y-4 text-slateink">
        <p>GrowthLoop connects three independent engines to one recommendation engine. The <b className="text-ink">risk engine</b> (logistic regression) flags students who may need academic support. The <b className="text-ink">skill-gap engine</b> compares a student's skills with their career goal using TF-IDF similarity. The <b className="text-ink">dependency graph</b> (NetworkX) orders missing skills so prerequisites come first.</p>
        <p>Their outputs merge into a personal roadmap, resource suggestions and mentor or peer matches. After each mentoring session both sides leave feedback, which updates trust scores and regenerates recommendations. Model retraining is a separate, admin-triggered step.</p>
        <p className="rounded-xl border border-marigold-100 bg-marigold-50 p-4 text-sm">The risk model in this version is a prototype trained on synthetic data. Its scores are an early-support signal for teachers, not a grade or a judgement. Academic data is entered by admins today; university portal integration is planned.</p>
      </div>
    </div>
  )
}

function AuthShell({ title, children, foot }) {
  return (
    <div className="mx-auto grid max-w-md px-5 py-10">
      <h1 className="text-3xl font-extrabold">{title}</h1>
      <div className="mt-6 rounded-2xl border border-chalk-300 bg-white p-6">{children}</div>
      <p className="mt-4 text-center text-sm text-slateink">{foot}</p>
    </div>
  )
}

export function Login() {
  const { user, login } = useAuth()
  const nav = useNavigate()
  const loc = useLocation()
  const [f, setF] = useState({ email: '', password: '' })
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  if (user) return <Navigate to={homeFor(user.role)} replace />
  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setErr('')
    try {
      const u = await login(f.email.trim(), f.password)
      nav(loc.state?.from && loc.state.from.startsWith(`/${u.role.toLowerCase()}`) ? loc.state.from : homeFor(u.role), { replace: true })
    } catch (ex) { setErr(errorMessage(ex)) } finally { setBusy(false) }
  }
  const demo = (email, password) => setF({ email, password })
  return (
    <AuthShell title="Log in" foot={<>New here? <Link className="link" to="/register">Create a student account</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Email"><Input type="email" required autoComplete="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
        <Field label="Password"><Input type="password" required autoComplete="current-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} /></Field>
        {err && <p className="rounded-lg bg-coral-50 px-3 py-2 text-sm text-coral-700" role="alert">{err}</p>}
        <Button className="w-full" loading={busy} type="submit">Log in</Button>
      </form>
      <div className="mt-5 border-t border-chalk-200 pt-4 text-sm">
        <p className="text-slateink">Try a demo account:</p>
        <div className="mt-2 flex flex-wrap gap-2">
          <Button size="sm" variant="outline" onClick={() => demo('aarav.shah@growthloop.dev', 'Student@123')}>Student</Button>
          <Button size="sm" variant="outline" onClick={() => demo('meera.iyer@growthloop.dev', 'Teacher@123')}>Teacher</Button>
          <Button size="sm" variant="outline" onClick={() => demo('admin@growthloop.dev', 'Admin@123')}>Admin</Button>
        </div>
      </div>
    </AuthShell>
  )
}

export function Register() {
  const { user, register } = useAuth()
  const nav = useNavigate()
  const [f, setF] = useState({ fullName: '', email: '', password: '', confirm: '' })
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  if (user) return <Navigate to={homeFor(user.role)} replace />
  const submit = async (e) => {
    e.preventDefault()
    if (f.password.length < 8) return setErr('Password needs at least 8 characters.')
    if (f.password !== f.confirm) return setErr('Passwords do not match.')
    setBusy(true); setErr('')
    try {
      await register({ fullName: f.fullName.trim(), email: f.email.trim(), password: f.password })
      nav('/student/profile', { replace: true })
    } catch (ex) { setErr(errorMessage(ex)) } finally { setBusy(false) }
  }
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  return (
    <AuthShell title="Create a student account" foot={<>Already registered? <Link className="link" to="/login">Log in</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Full name"><Input required value={f.fullName} onChange={set('fullName')} /></Field>
        <Field label="Email"><Input type="email" required value={f.email} onChange={set('email')} /></Field>
        <Field label="Password" hint="At least 8 characters"><Input type="password" required value={f.password} onChange={set('password')} /></Field>
        <Field label="Confirm password"><Input type="password" required value={f.confirm} onChange={set('confirm')} /></Field>
        {err && <p className="rounded-lg bg-coral-50 px-3 py-2 text-sm text-coral-700" role="alert">{err}</p>}
        <Button className="w-full" loading={busy} type="submit">Create account</Button>
      </form>
      <p className="mt-4 text-xs text-slateink">Teacher and admin accounts are created by an administrator.</p>
    </AuthShell>
  )
}

export function NotFound() {
  return (
    <div className="mx-auto max-w-md px-5 py-20 text-center">
      <h1 className="text-4xl font-extrabold">Page not found</h1>
      <p className="mt-2 text-slateink">That link doesn't lead anywhere.</p>
      <Link to="/" className="link mt-6 inline-block">Go to the home page</Link>
    </div>
  )
}
