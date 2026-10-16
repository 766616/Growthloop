import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { BookOpen, CalendarPlus, ExternalLink, Lightbulb, Map as MapIcon, Plus, Sparkles, Target, Trash2, Users } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { Catalog, Feedback as FeedbackApi, Recs, Roadmap as RoadmapApi, Sessions, Student } from '../../api/endpoints'
import { BarChartBox, LineChartBox, PALETTE, SkillRadar } from '../../components/Charts'
import GrowthLoopRing from '../../components/GrowthLoopRing'
import SessionsView from '../../components/SessionsView'
import { RegenerateButton, RiskCard, StaleNote } from '../../components/Shared'
import SkillGraph from '../../components/SkillGraph'
import { useToast } from '../../components/Toast'
import { Avatar, Badge, Button, Card, EmptyState, ErrorState, Field, Input, Loading, Modal, PageHeader, ProgressBar, Select, Stat, Table, Textarea } from '../../components/ui'
import { date, dateTime, LEVELS, pct, sentence, time } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'

const LEVEL_NUM = { BEGINNER: 1, INTERMEDIATE: 2, ADVANCED: 3 }

// ================================================================ Dashboard
export function StudentDashboard() {
  const { data: d, loading, error, reload } = useAsync(() => Student.dashboard(), [])
  if (loading) return <Loading label="Loading your dashboard" />
  if (error) return <ErrorState error={error} onRetry={reload} />
  const first = d.student.fullName.split(' ')[0]
  if (!d.profileComplete) {
    return (
      <div>
        <PageHeader title={`Welcome, ${first}`}>Two quick steps and GrowthLoop can build your roadmap.</PageHeader>
        <EmptyState icon={Target} title="Pick a career goal and add your skills" action={<Link to="/student/profile"><Button variant="accent">Set up my profile</Button></Link>}>
          The skill-gap engine compares what you know with what your goal needs.
        </EmptyState>
      </div>
    )
  }
  const sum = d.academicSummary || {}
  return (
    <div className="space-y-6">
      <PageHeader title={`Hi ${first}`} actions={<RegenerateButton stale={d.stale} onDone={reload} />}>
        Working towards <b className="text-ink">{d.careerGoal?.name}</b>. <StaleNote stale={d.stale} lastAnalyzedAt={d.lastAnalyzedAt} />
      </PageHeader>

      <div className="grid gap-6 lg:grid-cols-[1.25fr_1fr]">
        <Card tone="ink" title="Your growth loop" subtitle="Three signals that close the loop as you progress">
          <GrowthLoopRing roadmap={d.roadmap?.stats?.percent || 0} coverage={d.skillGap?.coveragePercent || 0} academics={sum.averagePercentage || 0} />
        </Card>
        <Card tone="tint" title="Do this next">
          {d.nextItem ? (
            <div>
              <p className="font-display text-2xl font-extrabold">{d.nextItem.skill?.name}</p>
              <p className="mt-1 text-sm text-slateink">{d.nextItem.reason}</p>
              {d.nextItem.practiceTask && <p className="mt-3 rounded-xl bg-white p-3 text-sm"><b>Practice:</b> {d.nextItem.practiceTask}</p>}
              <ul className="mt-3 space-y-1 text-sm">
                {d.nextResources.slice(0, 3).map((r) => <li key={r.id}><a className="link" href={r.url} target="_blank" rel="noreferrer">{r.title}</a> <span className="text-slateink">({sentence(r.type)})</span></li>)}
              </ul>
              <Link to="/student/roadmap" className="link mt-4 inline-block text-sm">Open roadmap</Link>
            </div>
          ) : <p className="text-sm text-slateink">{d.nextAction || 'Refresh recommendations to generate your roadmap.'}</p>}
        </Card>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Academic average" value={pct(sum.averagePercentage)} note={`${sum.totalSubjects || 0} subjects`} />
        <Stat label="Attendance" value={pct(sum.averageAttendance)} tone={sum.averageAttendance < 75 ? 'coral' : undefined} note="75% is the minimum" />
        <Stat label="Pending assignments" value={sum.pendingAssignments ?? 0} tone={sum.pendingAssignments ? 'marigold' : 'sage'} />
        <Stat label="Goal skills covered" value={pct(d.skillGap?.coveragePercent)} note={d.careerGoal?.name} />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2" title="Latest semester by subject" subtitle="Marks vs attendance (%)">
          <BarChartBox data={d.subjectPerformance} x="subject" domain={[0, 100]}
            bars={[{ key: 'percentage', name: 'Marks', color: PALETTE.ink }, { key: 'attendance', name: 'Attendance', color: PALETTE.marigold }]} />
        </Card>
        <RiskCard risk={d.risk} />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="Suggested mentors & peers" action={<Link className="link text-sm" to="/student/mentors">See all</Link>}>
          {d.matches.length ? d.matches.map((m) => (
            <div key={m.id} className="flex items-center gap-3 border-b border-chalk-200 py-2.5 last:border-0">
              <Avatar name={m.user.fullName} />
              <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{m.user.fullName}</p><p className="truncate text-xs text-slateink">{m.canHelpWith?.slice(0, 2).join(', ')}</p></div>
              <Badge value={m.type} />
            </div>
          )) : <p className="text-sm text-slateink">No matches yet.</p>}
        </Card>
        <Card title="Upcoming sessions" action={<Link className="link text-sm" to="/student/sessions">Manage</Link>}>
          {d.upcomingSessions.length ? d.upcomingSessions.map((s) => (
            <div key={s.id} className="border-b border-chalk-200 py-2.5 last:border-0">
              <div className="flex items-center justify-between gap-2"><p className="text-sm font-semibold">{s.topic}</p><Badge value={s.status} /></div>
              <p className="text-xs text-slateink">with {s.mentor.fullName} on {date(s.date)} {time(s.time)}</p>
            </div>
          )) : <p className="text-sm text-slateink">Nothing booked. Request a session from a mentor.</p>}
        </Card>
        <Card title="Top recommendations" action={<Link className="link text-sm" to="/student/recommendations">All</Link>}>
          {(d.recommendations || []).slice(0, 4).map((r) => (
            <div key={r.id} className="border-b border-chalk-200 py-2.5 last:border-0">
              <div className="flex items-center gap-2"><Badge tone="neutral">{sentence(r.type)}</Badge><p className="text-sm font-semibold">{r.title}</p></div>
            </div>
          ))}
          {!d.recommendations?.length && <p className="text-sm text-slateink">Run an analysis to get recommendations.</p>}
        </Card>
      </div>
    </div>
  )
}

// ================================================================ Profile
export function StudentProfile() {
  const toast = useToast()
  const prof = useAsync(() => Student.profile(), [])
  const goals = useAsync(() => Catalog.goals(), [])
  const [f, setF] = useState(null)
  const [busy, setBusy] = useState(false)
  const dirty = useRef(false)
  useEffect(() => {
    const p = prof.data
    if (p && (!dirty.current || !f)) setF({ fullName: p.fullName || '', phone: p.phone || '', education: p.education || '', branch: p.branch || '', semester: p.semester || '',
      careerGoalId: p.careerGoal?.id || '', interests: (p.interests || []).join(', '), availability: (p.availability || []).join(', '),
      learningPreferences: p.learningPreferences || '', bio: p.bio || '' })
  }, [prof.data])
  if (prof.loading || !f) return prof.error ? <ErrorState error={prof.error} onRetry={prof.reload} /> : <Loading />
  const set = (k) => (e) => { dirty.current = true; setF((current) => ({ ...current, [k]: e.target.value })) }
  const list = (s) => s.split(',').map((x) => x.trim()).filter(Boolean)
  const save = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      await Student.update({ ...f, semester: f.semester ? Number(f.semester) : null, careerGoalId: f.careerGoalId ? Number(f.careerGoalId) : null,
        interests: list(f.interests), availability: list(f.availability) })
      toast.success('Profile saved')
      dirty.current = false
      prof.reload({ silent: true })
    } catch (ex) { toast.error(errorMessage(ex)) } finally { setBusy(false) }
  }
  const goal = goals.data?.find((g) => String(g.id) === String(f.careerGoalId))
  return (
    <div>
      <PageHeader title="Profile & career goal">Roll number {prof.data.rollNumber}{prof.data.assignedTeacher && <> · Teacher: {prof.data.assignedTeacher.fullName}</>}</PageHeader>
      <form onSubmit={save} className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <Card title="About you">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Full name"><Input value={f.fullName} onChange={set('fullName')} required /></Field>
            <Field label="Phone"><Input value={f.phone} onChange={set('phone')} /></Field>
            <Field label="Education"><Input value={f.education} onChange={set('education')} placeholder="B.E. Computer Engineering" /></Field>
            <Field label="Branch"><Input value={f.branch} onChange={set('branch')} /></Field>
            <Field label="Semester"><Input type="number" min="1" max="12" value={f.semester} onChange={set('semester')} /></Field>
            <Field label="Availability" hint="Comma separated, e.g. Mon evening, Sat morning"><Input value={f.availability} onChange={set('availability')} /></Field>
            <Field className="sm:col-span-2" label="Interests" hint="Comma separated"><Input value={f.interests} onChange={set('interests')} /></Field>
            <Field className="sm:col-span-2" label="How you like to learn"><Input value={f.learningPreferences} onChange={set('learningPreferences')} placeholder="Videos, hands-on projects…" /></Field>
            <Field className="sm:col-span-2" label="Bio"><Textarea value={f.bio} onChange={set('bio')} /></Field>
          </div>
        </Card>
        <div className="space-y-6">
          <Card title="Career goal">
            <Select placeholder="Choose a goal" value={f.careerGoalId} onChange={set('careerGoalId')} options={(goals.data || []).map((g) => ({ value: g.id, label: g.name }))} />
            {goal && (
              <div className="mt-4">
                <p className="text-sm text-slateink">{goal.description}</p>
                <div className="mt-3 flex flex-wrap gap-1.5">{goal.skills.map((s) => <Badge key={s.skillId} tone={s.importance >= 4 ? 'ink' : 'neutral'}>{s.name}</Badge>)}</div>
              </div>
            )}
          </Card>
          <Button type="submit" className="w-full" loading={busy}>Save profile</Button>
          <Link to="/student/skills" className="link block text-center text-sm">Next: add your current skills</Link>
        </div>
      </form>
    </div>
  )
}

// ================================================================ Academics
export function StudentAcademics() {
  const { data, loading, error, reload } = useAsync(() => Student.academics(), [])
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  const s = data.summary
  return (
    <div className="space-y-6">
      <PageHeader title="Academics">Marks, attendance and assignments entered by your institute.</PageHeader>
      {!s.hasData ? <EmptyState icon={BookOpen} title="No academic records yet">Your admin adds marks manually or by CSV import.</EmptyState> : <>
        <div className="grid gap-4 sm:grid-cols-3">
          <Stat label="Average marks" value={pct(s.averagePercentage)} />
          <Stat label="Average attendance" value={pct(s.averageAttendance)} tone={s.averageAttendance < 75 ? 'coral' : 'sage'} />
          <Stat label="Pending / missing assignments" value={s.pendingAssignments} tone={s.pendingAssignments ? 'marigold' : 'sage'} />
        </div>
        <Card title="Semester trend"><LineChartBox data={s.semesterTrend} x="semester" lines={[{ key: 'average', name: 'Average %', color: PALETTE.marigold }]} /></Card>
        <AcademicTables data={data} />
      </>}
    </div>
  )
}

export function AcademicTables({ data, onDelete }) {
  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <Card title="Marks">
        <Table rows={data.records} columns={[
          { key: 'subject', label: 'Subject' }, { key: 'semester', label: 'Sem' },
          { key: 'marks', label: 'Marks', render: (r) => `${r.marks}/${r.maxMarks}` },
          { key: 'percentage', label: '%', render: (r) => <span className={r.percentage < 40 ? 'font-semibold text-coral-700' : ''}>{pct(r.percentage, 1)}</span> },
          ...(onDelete ? [{ key: 'x', label: '', render: (r) => <button className="text-slateink hover:text-coral" onClick={() => onDelete(r)} aria-label="Delete"><Trash2 className="h-4 w-4" /></button> }] : []),
        ]} />
      </Card>
      <div className="space-y-6">
        <Card title="Attendance">
          <Table rows={data.attendance} columns={[{ key: 'subject', label: 'Subject' }, { key: 'semester', label: 'Sem' },
            { key: 'percentage', label: 'Attendance', render: (r) => <ProgressBar value={r.percentage} tone={r.percentage < 75 ? 'coral' : 'sage'} label=" " /> }]} />
        </Card>
        <Card title="Assignments">
          <Table rows={data.assignments} columns={[{ key: 'title', label: 'Assignment' }, { key: 'status', label: 'Status', render: (r) => <Badge value={r.status} /> },
            { key: 'score', label: 'Score', render: (r) => (r.score == null ? '—' : `${r.score}/${r.maxScore}`) }]} />
        </Card>
      </div>
    </div>
  )
}

// ================================================================ Skills
export function StudentSkills() {
  const toast = useToast()
  const catalog = useAsync(() => Catalog.skills(), [])
  const mine = useAsync(() => Student.skills(), [])
  const [rows, setRows] = useState(null)
  const [pick, setPick] = useState({ skillId: '', proficiency: 'BEGINNER' })
  const [busy, setBusy] = useState(false)
  const dirty = useRef(false)
  const retry = () => Promise.all([catalog.reload(), mine.reload()])
  useEffect(() => {
    if (mine.data && !dirty.current) setRows(mine.data.map((s) => ({ skillId: s.skillId, name: s.name, category: s.category, proficiency: s.proficiency })))
  }, [mine.data])
  if (catalog.loading || mine.loading || !rows) return catalog.error || mine.error
    ? <ErrorState error={catalog.error || mine.error} onRetry={retry} />
    : <Loading />
  const available = (catalog.data || []).filter((s) => !rows.some((r) => r.skillId === s.id))
  const add = () => {
    const s = catalog.data.find((x) => String(x.id) === String(pick.skillId))
    if (!s) return
    dirty.current = true
    setRows([...rows, { skillId: s.id, name: s.name, category: s.category, proficiency: pick.proficiency }])
    setPick({ skillId: '', proficiency: 'BEGINNER' })
  }
  const save = async () => {
    setBusy(true)
    try {
      await Student.saveSkills(rows.map(({ skillId, proficiency }) => ({ skillId, proficiency })))
      toast.success('Skills saved. Refresh recommendations to update your roadmap.')
      dirty.current = false
      mine.reload({ silent: true })
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  return (
    <div className="space-y-6">
      <PageHeader title="My skills" actions={<Button loading={busy} onClick={save}>Save skills</Button>}>Be honest about levels — the roadmap is only as good as this list.</PageHeader>
      <Card title="Add a skill">
        <div className="flex flex-col gap-3 sm:flex-row">
          <Select className="sm:flex-1" placeholder="Choose a skill" value={pick.skillId} onChange={(e) => setPick({ ...pick, skillId: e.target.value })}
            options={available.map((s) => ({ value: s.id, label: `${s.name} (${s.category})` }))} />
          <Select className="sm:w-48" value={pick.proficiency} onChange={(e) => setPick({ ...pick, proficiency: e.target.value })} options={LEVELS.map((l) => ({ value: l, label: sentence(l) }))} />
          <Button icon={Plus} variant="accent" onClick={add} disabled={!pick.skillId}>Add</Button>
        </div>
      </Card>
      {!rows.length ? <EmptyState icon={Sparkles} title="No skills yet">Add the skills you already have, even at beginner level.</EmptyState> : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {rows.map((r) => (
            <div key={r.skillId} className="rounded-2xl border border-chalk-300 bg-white p-4">
              <div className="flex items-start justify-between"><div><p className="font-display font-bold">{r.name}</p><p className="text-xs text-slateink">{r.category}</p></div>
                <button onClick={() => { dirty.current = true; setRows(rows.filter((x) => x.skillId !== r.skillId)) }} className="text-slateink hover:text-coral" aria-label={`Remove ${r.name}`}><Trash2 className="h-4 w-4" /></button></div>
              <div className="mt-3 flex gap-1">
                {LEVELS.map((l) => (
                  <button key={l} onClick={() => { dirty.current = true; setRows(rows.map((x) => (x.skillId === r.skillId ? { ...x, proficiency: l } : x))) }}
                    className={`flex-1 rounded-lg py-1.5 text-xs font-semibold ${LEVEL_NUM[l] <= LEVEL_NUM[r.proficiency] ? 'bg-sage text-white' : 'bg-chalk text-slateink'}`}>{sentence(l)}</button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ================================================================ Skill gap
export function StudentSkillGap() {
  const analysis = useAsync(() => Student.analysis(), [])
  const graph = useAsync(() => Catalog.graph(), [])
  const profile = useAsync(() => Student.profile(), [])
  const [full, setFull] = useState(false)
  const gap = analysis.data?.skillGap
  const existing = Array.isArray(gap?.existing) ? gap.existing : []
  const partial = Array.isArray(gap?.partial) ? gap.partial : []
  const missing = Array.isArray(gap?.missing) ? gap.missing : []
  const retry = () => Promise.all([analysis.reload(), graph.reload(), profile.reload()])
  const status = useMemo(() => {
    const m = {}
    if (!gap) return m
    existing.forEach((g) => { if (g?.skill_id != null) m[g.skill_id] = 'have' })
    partial.forEach((g) => { if (g?.skill_id != null) m[g.skill_id] = 'partial' })
    missing.forEach((g) => { if (g?.skill_id != null) m[g.skill_id] = 'missing' })
    return m
  }, [existing, gap, missing, partial])
  if (analysis.loading || graph.loading || profile.loading) return <Loading />
  const loadError = analysis.error || profile.error || graph.error
  if (loadError) return <ErrorState error={loadError} onRetry={retry} />
  const goalSkills = profile.data?.careerGoal?.skills || []
  const current = Object.fromEntries((profile.data?.skills || []).map((s) => [s.skillId, LEVEL_NUM[s.proficiency]]))
  const radar = goalSkills.slice(0, 8).map((s) => ({ skill: s.name, required: LEVEL_NUM[s.requiredLevel], current: current[s.skillId] || 0 }))
  const ranked = [...partial, ...missing].sort((a, b) => (b.priority_score || 0) - (a.priority_score || 0))
  return (
    <div className="space-y-6">
      <PageHeader title="Skill gap" actions={<RegenerateButton stale={analysis.data?.stale} onDone={analysis.reload} label="Re-analyse" />}>
        Compared against <b className="text-ink">{profile.data?.careerGoal?.name || 'no goal yet'}</b>.
      </PageHeader>
      {!gap ? <EmptyState icon={Target} title="No skill-gap analysis yet">Set a goal and skills, then run the analysis.</EmptyState> : <>
        <div className="grid gap-6 lg:grid-cols-[1fr_1.2fr]">
          <Card title="Coverage" subtitle={`${existing.length} have · ${partial.length} partial · ${missing.length} missing`}>
            <p className="font-display text-5xl font-extrabold">{pct(gap.coveragePercent)}</p>
            <ProgressBar className="mt-3" value={gap.coveragePercent} tone="marigold" />
            {gap.recommendedNext?.length > 0 && <p className="mt-4 text-sm">Ready to start now: <b>{gap.recommendedNext.join(', ')}</b></p>}
          </Card>
          <Card title="You vs the goal" subtitle="1 = beginner, 3 = advanced"><SkillRadar data={radar} /></Card>
        </div>
        <Card title="Gaps ranked by priority">
          <Table rows={ranked} rowKey={(r) => r.skill} columns={[
            { key: 'skill', label: 'Skill', render: (r) => <span className="font-semibold">{r.skill}</span> },
            { key: 'gap_type', label: 'Gap', render: (r) => <Badge value={r.gap_type} /> },
            { key: 'priority', label: 'Priority', render: (r) => <Badge value={r.priority} /> },
            { key: 'required_level', label: 'Needed', render: (r) => sentence(r.required_level) },
            { key: 'ready', label: 'Can start?', render: (r) => (r.ready ? <span className="text-sage-700">Yes</span> : <span className="text-slateink">After {r.missing_prerequisites?.join(', ')}</span>) },
            { key: 'reason', label: 'Why', render: (r) => <span className="text-slateink">{r.reason || 'Required for your goal'}</span> },
          ]} />
        </Card>
        <Card title="Skill dependency graph" action={<Button size="sm" variant="ghost" onClick={() => setFull(!full)}>{full ? 'Show goal skills only' : 'Show every skill'}</Button>}>
          <SkillGraph nodes={Array.isArray(graph.data?.nodes) ? graph.data.nodes : []}
            edges={Array.isArray(graph.data?.edges) ? graph.data.edges : []} status={status}
            focusIds={full ? null : goalSkills.map((s) => s.skillId)} />
        </Card>
      </>}
    </div>
  )
}

// ================================================================ Roadmap
export function StudentRoadmap() {
  const toast = useToast()
  const { data, loading, error, reload, setData } = useAsync(() => RoadmapApi.mine(), [])
  const [busy, setBusy] = useState(null)
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  const setStatus = async (item, status) => {
    setBusy(item.id)
    try {
      setData(await RoadmapApi.setStatus(item.id, status))
      toast.success(status === 'COMPLETED' ? `${item.skill.name} completed and added to your skills` : 'Roadmap updated')
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(null) }
  }
  if (!data) return (
    <div><PageHeader title="Learning roadmap" />
      <EmptyState icon={MapIcon} title="No roadmap yet" action={<RegenerateButton stale label="Generate my roadmap" onDone={reload} />}>Your roadmap orders missing skills so prerequisites come first.</EmptyState></div>
  )
  const st = data.stats
  return (
    <div className="space-y-6">
      <PageHeader title="Learning roadmap" actions={<RegenerateButton onDone={reload} />}>
        Version {data.version} for {data.careerGoal?.name}. {data.nextAction}
      </PageHeader>
      <div className="grid gap-4 sm:grid-cols-4">
        <Stat label="Completed" value={`${st.completed}/${st.total}`} tone="sage" /><Stat label="In progress" value={st.inProgress} tone="marigold" />
        <Stat label="Progress" value={pct(st.percent)} /><Stat label="Hours remaining" value={st.hoursRemaining} note="estimated" />
      </div>
      <ol className="relative space-y-4 border-l-2 border-chalk-300 pl-6">
        {data.items.map((it) => (
          <li key={it.id} className="relative">
            <span className={`absolute -left-[35px] top-5 grid h-6 w-6 place-items-center rounded-full text-xs font-bold ${it.status === 'COMPLETED' ? 'bg-sage text-white' : it.status === 'IN_PROGRESS' ? 'bg-marigold text-ink' : 'bg-white text-ink ring-2 ring-chalk-300'}`}>{it.order}</span>
            <Card bodyClass="p-5">
              <div className="flex flex-col gap-3 md:flex-row md:justify-between">
                <div className="md:max-w-xl">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-display text-lg font-bold">{it.skill.name}</p><Badge value={it.status} /><Badge value={it.priority} />
                    {!it.readyNow && it.status !== 'COMPLETED' && <Badge tone="neutral">Needs {it.prerequisites.join(', ')}</Badge>}
                  </div>
                  <p className="mt-1 text-sm text-slateink">{it.reason}</p>
                  {it.practiceTask && <p className="mt-2 text-sm"><b>Practice:</b> {it.practiceTask}</p>}
                  {it.resources?.length > 0 && <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm">
                    {it.resources.map((r) => <a key={r.id} href={r.url} target="_blank" rel="noreferrer" className="link inline-flex items-center gap-1">{r.title}<ExternalLink className="h-3 w-3" /></a>)}
                  </div>}
                  <p className="mt-2 text-xs text-slateink">About {it.estimatedHours ?? '?'} hours{it.completedAt && ` · completed ${date(it.completedAt)}`}</p>
                </div>
                <div className="flex shrink-0 gap-2 md:flex-col">
                  {it.status === 'NOT_STARTED' && <Button size="sm" variant="accent" loading={busy === it.id} onClick={() => setStatus(it, 'IN_PROGRESS')}>Start</Button>}
                  {it.status === 'IN_PROGRESS' && <Button size="sm" loading={busy === it.id} onClick={() => setStatus(it, 'COMPLETED')}>Mark completed</Button>}
                  {it.status !== 'NOT_STARTED' && <Button size="sm" variant="ghost" disabled={busy === it.id} onClick={() => setStatus(it, 'NOT_STARTED')}>Reset</Button>}
                </div>
              </div>
            </Card>
          </li>
        ))}
      </ol>
    </div>
  )
}

// ================================================================ Recommendations
export function StudentRecommendations() {
  const { data, loading, error, reload } = useAsync(() => Recs.mine(), [])
  const [filter, setFilter] = useState('ALL')
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  const recs = data.recommendations || []
  const types = ['ALL', ...new Set(recs.map((r) => r.type))]
  const shown = recs.filter((r) => filter === 'ALL' || r.type === filter)
  return (
    <div className="space-y-6">
      <PageHeader title="Recommendations" actions={<RegenerateButton stale={data.stale} onDone={reload} />}>Built from your risk profile, skill gaps, the dependency graph, matches and session feedback.</PageHeader>
      <div className="flex flex-wrap gap-2">
        {types.map((t) => <button key={t} onClick={() => setFilter(t)} className={`rounded-full px-3.5 py-1.5 text-sm font-semibold ${filter === t ? 'bg-ink text-white' : 'bg-white text-ink-700 ring-1 ring-chalk-300'}`}>{t === 'ALL' ? 'All' : sentence(t)}</button>)}
      </div>
      {!shown.length ? <EmptyState icon={Lightbulb} title="No recommendations yet">Refresh to generate them from your latest data.</EmptyState> : (
        <div className="grid gap-4 md:grid-cols-2">
          {shown.map((r) => (
            <Card key={r.id} bodyClass="p-5">
              <div className="flex items-center gap-2"><Badge tone="ink">{sentence(r.type)}</Badge><span className="text-xs text-slateink">Priority {r.priority}</span></div>
              <p className="mt-2 font-display text-lg font-bold">{r.title}</p>
              <p className="mt-1 text-sm text-slateink">{r.description}</p>
              {r.payload?.url && <a className="link mt-2 inline-flex items-center gap-1 text-sm" href={r.payload.url} target="_blank" rel="noreferrer">Open resource<ExternalLink className="h-3 w-3" /></a>}
              {(r.type === 'MENTOR' || r.type === 'PEER') && <Link className="link mt-2 inline-block text-sm" to="/student/mentors">Request a session</Link>}
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}

// ================================================================ Mentors
export function StudentMentors() {
  const toast = useToast()
  const matches = useAsync(() => Recs.matches(), [])
  const mentors = useAsync(() => Catalog.mentors(), [])
  const skills = useAsync(() => Catalog.skills(), [])
  const [req, setReq] = useState(null)
  const [busy, setBusy] = useState(false)
  const open = (userId, name, skillName) => {
    const sk = skills.data?.find((s) => s.name === skillName)
    setReq({ mentorUserId: userId, name, topic: skillName ? `Help with ${skillName}` : '', skillId: sk?.id || '', date: '', time: '', notes: '' })
  }
  const send = async () => {
    setBusy(true)
    try {
      await Sessions.create({ mentorUserId: req.mentorUserId, topic: req.topic, skillId: req.skillId ? Number(req.skillId) : null,
        date: req.date || null, time: req.time || null, notes: req.notes })
      toast.success(`Request sent to ${req.name}`)
      setReq(null)
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  if (matches.loading || mentors.loading) return <Loading />
  return (
    <div className="space-y-6">
      <PageHeader title="Mentors & peers" actions={<RegenerateButton label="Re-match" onDone={() => matches.reload()} />}>Matches are ranked by who covers your gaps, shared goals, overlapping availability and trust from past feedback.</PageHeader>
      {matches.error && <ErrorState error={matches.error} />}
      {!matches.data?.length ? <EmptyState icon={Users} title="No matches yet">Run an analysis to get matched.</EmptyState> : (
        <div className="grid gap-4 md:grid-cols-2">
          {matches.data.map((m) => (
            <Card key={m.id} bodyClass="p-5">
              <div className="flex items-center gap-3">
                <Avatar name={m.user.fullName} className="h-11 w-11" />
                <div className="flex-1"><p className="font-display text-lg font-bold">{m.user.fullName}</p><Badge value={m.type} /></div>
                <div className="text-right"><p className="font-display text-2xl font-extrabold">{Math.round(m.score * 100)}</p><p className="text-xs text-slateink">match score</p></div>
              </div>
              {m.canHelpWith?.length > 0 && <div className="mt-3 flex flex-wrap gap-1.5">{m.canHelpWith.map((s) => <Badge key={s} tone="sage">{s}</Badge>)}</div>}
              <ul className="mt-3 list-disc space-y-0.5 pl-5 text-sm text-slateink">{m.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
              <Button className="mt-4" size="sm" icon={CalendarPlus} onClick={() => open(m.user.id, m.user.fullName, m.canHelpWith?.[0])}>Request session</Button>
            </Card>
          ))}
        </div>
      )}
      <Card title="All mentors">
        <Table rows={mentors.data || []} rowKey={(r) => r.userId} columns={[
          { key: 'fullName', label: 'Mentor', render: (r) => <span className="font-semibold">{r.fullName}</span> },
          { key: 'department', label: 'Department' },
          { key: 'expertise', label: 'Expertise', render: (r) => r.expertise.slice(0, 4).join(', ') },
          { key: 'averageRating', label: 'Rating', render: (r) => (r.averageRating ? `${r.averageRating}/5 (${r.reviews})` : 'New') },
          { key: 'a', label: '', render: (r) => <Button size="sm" variant="outline" onClick={() => open(r.userId, r.fullName)}>Request</Button> },
        ]} />
      </Card>
      <Modal open={!!req} onClose={() => setReq(null)} title={`Request a session with ${req?.name}`}
        footer={<><Button variant="outline" onClick={() => setReq(null)}>Cancel</Button><Button loading={busy} disabled={!req?.topic} onClick={send}>Send request</Button></>}>
        {req && <div className="space-y-4">
          <Field label="Topic"><Input value={req.topic} onChange={(e) => setReq({ ...req, topic: e.target.value })} required /></Field>
          <Field label="Skill (optional)"><Select placeholder="None" value={req.skillId} onChange={(e) => setReq({ ...req, skillId: e.target.value })} options={(skills.data || []).map((s) => ({ value: s.id, label: s.name }))} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Preferred date"><Input type="date" min={new Date().toISOString().slice(0, 10)} value={req.date} onChange={(e) => setReq({ ...req, date: e.target.value })} /></Field>
            <Field label="Time"><Input type="time" value={req.time} onChange={(e) => setReq({ ...req, time: e.target.value })} /></Field>
          </div>
          <Field label="Notes for the mentor"><Textarea value={req.notes} onChange={(e) => setReq({ ...req, notes: e.target.value })} /></Field>
        </div>}
      </Modal>
    </div>
  )
}

export const StudentSessions = () => <SessionsView title="Sessions" intro="Track requests, mark sessions completed and leave feedback." />

// ================================================================ Feedback
export function FeedbackPage() {
  const { data, loading, error, reload } = useAsync(() => FeedbackApi.mine(), [])
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  const Item = ({ f, received }) => (
    <div className="border-b border-chalk-200 py-3 last:border-0">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold">{received ? `From ${f.from.fullName}` : `To ${f.to.fullName}`} on "{f.session?.topic || f.sessionTopic}"</p>
        <span className="font-display font-bold text-marigold-700">{'★'.repeat(f.rating)}<span className="text-chalk-300">{'★'.repeat(5 - f.rating)}</span></span>
      </div>
      {(f.session?.date || f.sessionDate || f.session?.status || f.sessionStatus) && (
        <p className="mt-1 text-xs text-slateink">
          Session: {date(f.session?.date || f.sessionDate)} {time(f.session?.time || f.sessionTime)}
          {(f.session?.status || f.sessionStatus) && ` · ${sentence(f.session?.status || f.sessionStatus)}`}
        </p>
      )}
      {f.comment && <p className="mt-1 text-sm text-slateink">{f.comment}</p>}
      {f.progressUpdate && <p className="mt-1 text-sm"><b>Progress:</b> {f.progressUpdate}</p>}
      <p className="mt-1 text-xs text-ink-300">{dateTime(f.createdAt)}</p>
    </div>
  )
  return (
    <div className="space-y-6">
      <PageHeader title="Feedback">{data.averageRatingReceived ? `Average rating you've received: ${data.averageRatingReceived}/5` : 'Feedback appears here after completed sessions.'}</PageHeader>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Received">{data.received.length ? data.received.map((f) => <Item key={f.id} f={f} received />) : <p className="text-sm text-slateink">Nothing yet.</p>}</Card>
        <Card title="Given">{data.given.length ? data.given.map((f) => <Item key={f.id} f={f} />) : <p className="text-sm text-slateink">Nothing yet. Complete a session to leave feedback.</p>}</Card>
      </div>
    </div>
  )
}
