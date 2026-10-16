import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Search } from 'lucide-react'
import { Teacher } from '../../api/endpoints'
import ClassAnalytics, { SupportList } from '../../components/ClassAnalytics'
import SessionsView from '../../components/SessionsView'
import { RegenerateButton, RiskCard } from '../../components/Shared'
import { AcademicTables } from '../student'
import { Badge, Card, ErrorState, Input, Loading, PageHeader, ProgressBar, Stat, Table } from '../../components/ui'
import { date, pct, time } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../context/AuthContext'

export function TeacherDashboard() {
  const { user } = useAuth()
  const { data: d, loading, error, reload } = useAsync(() => Teacher.dashboard(), [])
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  return (
    <div className="space-y-6">
      <PageHeader title={`Good to see you, ${user.fullName.split(' ')[0]}`}>Students who need attention come first.</PageHeader>
      <div className="grid gap-4 sm:grid-cols-4">
        <Stat label="Assigned students" value={d.totalStudents} />
        <Stat label="Average performance" value={pct(d.averagePerformance)} />
        <Stat label="Pending requests" value={d.pendingRequests.length} tone={d.pendingRequests.length ? 'marigold' : undefined} />
        <Stat label="Your mentor rating" value={d.myAverageRating ? `${d.myAverageRating}/5` : '—'} />
      </div>
      <Card title="Students needing support" action={<Link to="/teacher/students" className="link text-sm">All students</Link>}>
        <SupportList rows={d.studentsNeedingSupport} linkBase="/teacher/student" />
      </Card>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Session requests" action={<Link to="/teacher/sessions" className="link text-sm">Respond</Link>}>
          {d.pendingRequests.length ? d.pendingRequests.map((s) => (
            <div key={s.id} className="border-b border-chalk-200 py-2.5 last:border-0"><p className="text-sm font-semibold">{s.topic}</p><p className="text-xs text-slateink">{s.requester.fullName}, {s.date ? date(s.date) : 'no date'} {time(s.time)}</p></div>
          )) : <p className="text-sm text-slateink">No pending requests.</p>}
        </Card>
        <Card title="Recent feedback about you">
          {d.recentFeedback.length ? d.recentFeedback.map((f) => (
            <div key={f.id} className="border-b border-chalk-200 py-2.5 last:border-0"><p className="text-sm"><b>{f.rating}/5</b> from {f.from.fullName}</p>{f.comment && <p className="text-sm text-slateink">{f.comment}</p>}</div>
          )) : <p className="text-sm text-slateink">No feedback yet.</p>}
        </Card>
      </div>
    </div>
  )
}

export function TeacherStudents() {
  const { data, loading, error, reload } = useAsync(() => Teacher.students(), [])
  const [q, setQ] = useState('')
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  const rows = data.filter((r) => `${r.fullName} ${r.rollNumber} ${r.careerGoal}`.toLowerCase().includes(q.toLowerCase()))
  return (
    <div>
      <PageHeader title="My students">Assigned to you, or linked through a mentoring session.</PageHeader>
      <Card>
        <div className="relative mb-4 max-w-sm"><Search className="absolute left-3 top-2.5 h-4 w-4 text-slateink" /><Input className="pl-9" placeholder="Search name, roll or goal" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <Table rows={rows} columns={[
          { key: 'fullName', label: 'Student', render: (r) => <Link className="font-semibold hover:underline" to={`/teacher/student/${r.id}`}>{r.fullName}</Link> },
          { key: 'rollNumber', label: 'Roll' }, { key: 'careerGoal', label: 'Goal' },
          { key: 'averagePercentage', label: 'Avg', render: (r) => pct(r.averagePercentage) },
          { key: 'riskLevel', label: 'Risk', render: (r) => <Badge value={r.riskLevel} /> },
          { key: 'progressPercent', label: 'Roadmap', className: 'w-44', render: (r) => <ProgressBar value={r.progressPercent} label=" " /> },
        ]} />
      </Card>
    </div>
  )
}

export function TeacherStudentDetail() {
  const { id } = useParams()
  const { data, loading, error, reload } = useAsync(() => Teacher.student(id), [id])
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  const p = data.profile, a = data.analysis
  return (
    <div className="space-y-6">
      <PageHeader title={p.fullName} actions={<RegenerateButton studentId={p.id} stale={a.stale} onDone={reload} label="Run analysis" />}>
        {p.rollNumber}, {p.branch || 'branch not set'}, semester {p.semester ?? '—'}. Goal: <b className="text-ink">{p.careerGoal?.name || 'not set'}</b>
      </PageHeader>
      <div className="grid gap-6 lg:grid-cols-3">
        <RiskCard risk={a.risk} />
        <Card title="Skill coverage">
          <p className="font-display text-4xl font-extrabold">{pct(a.skillGap?.coveragePercent)}</p>
          <ProgressBar className="mt-2" value={a.skillGap?.coveragePercent} tone="marigold" />
          <p className="mt-3 text-sm text-slateink">Missing: {a.skillGap?.missing?.map((m) => m.skill).join(', ') || '—'}</p>
          <div className="mt-3 flex flex-wrap gap-1.5">{p.skills.map((s) => <Badge key={s.skillId} value={s.proficiency}>{s.name}</Badge>)}</div>
        </Card>
        <Card title="Roadmap">
          {a.roadmap ? <>
            <ProgressBar value={a.roadmap.stats.percent} label={`${a.roadmap.stats.completed} of ${a.roadmap.stats.total} done`} />
            <ul className="mt-3 space-y-1 text-sm">{a.roadmap.items.slice(0, 6).map((i) => <li key={i.id} className="flex justify-between gap-2"><span>{i.order}. {i.skill.name}</span><Badge value={i.status} /></li>)}</ul>
          </> : <p className="text-sm text-slateink">No roadmap yet.</p>}
        </Card>
      </div>
      <AcademicTables data={data.academics} />
      <Card title="Sessions">
        <Table rows={data.sessions} columns={[{ key: 'topic', label: 'Topic' }, { key: 'mentor', label: 'Mentor', render: (r) => r.mentor.fullName },
          { key: 'date', label: 'Date', render: (r) => date(r.date) }, { key: 'status', label: 'Status', render: (r) => <Badge value={r.status} /> }]} />
      </Card>
    </div>
  )
}

export function TeacherAnalytics() {
  const { data, loading, error, reload } = useAsync(() => Teacher.analytics(), [])
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  return <div><PageHeader title="Class analytics">Across the students you're responsible for.</PageHeader><ClassAnalytics data={data} linkBase="/teacher/student" /></div>
}

export const TeacherSessions = () => <SessionsView title="Mentoring sessions" intro="Accept requests, mark sessions done and rate the learner's progress." />
