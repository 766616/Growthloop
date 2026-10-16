import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Brain, Pencil, Plus, Trash2, Upload, Wand2 } from 'lucide-react'
import { errorMessage } from '../../api/client'
import { Admin, Catalog } from '../../api/endpoints'
import { BarChartBox, PALETTE } from '../../components/Charts'
import ClassAnalytics, { SupportList } from '../../components/ClassAnalytics'
import { useToast } from '../../components/Toast'
import { Badge, Button, Card, ConfirmDialog, ErrorState, Field, Input, Loading, Modal, PageHeader, Select, Stat, Table, Textarea } from '../../components/ui'
import { AcademicTables } from '../student'
import { date, dateTime, LEVELS, relative, sentence, time } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'

const levelOpts = LEVELS.map((l) => ({ value: l, label: sentence(l) }))
const csv = (s) => (s || '').split(',').map((x) => x.trim()).filter(Boolean)

/** Runs an action with toast + busy state. */
function useAction() {
  const toast = useToast()
  const [busy, setBusy] = useState(null)
  const run = async (key, fn, ok) => {
    setBusy(key)
    try { const r = await fn(); if (ok) toast.success(typeof ok === 'function' ? ok(r) : ok); return r ?? true } catch (e) { toast.error(errorMessage(e)); return false } finally { setBusy(null) }
  }
  return { busy, run }
}

// ================================================================ Dashboard
export function AdminDashboard() {
  const { data: d, loading, error, reload } = useAsync(() => Admin.dashboard(), [])
  const { busy, run } = useAction()
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  return (
    <div className="space-y-6">
      <PageHeader title="Platform overview" actions={<>
        <Button variant="outline" icon={Wand2} loading={busy === 'all'} onClick={async () => (await run('all', () => Admin.analyzeAll(), (r) => `Analysed ${r.analyzed} students${r.failed.length ? `, ${r.failed.length} failed` : ''}`)) && reload({ silent: true })}>Analyse all students</Button>
        <Button variant="outline" icon={Brain} loading={busy === 'retrain'} onClick={() => run('retrain', () => Admin.retrain(), 'Risk model retrained')}>Retrain risk model</Button>
      </>}>Everything below is computed from the live database.</PageHeader>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Students" value={d.totalStudents} /><Stat label="Teachers" value={d.totalTeachers} note={`${d.totalMentors} mentoring`} />
        <Stat label="Sessions" value={d.totalSessions} note={`${d.completedSessions} completed`} /><Stat label="Feedback entries" value={d.totalFeedback} />
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Students needing support"><SupportList rows={d.studentsNeedingSupport} /></Card>
        <Card title="Most requested session skills"><BarChartBox layout="vertical" data={d.mostRequestedSkills} x="skill" bars={[{ key: 'count', name: 'Requests', color: PALETTE.marigold }]} /></Card>
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Recent users" action={<Link className="link text-sm" to="/admin/users">Manage</Link>}>
          <Table rows={d.recentUsers} columns={[{ key: 'fullName', label: 'Name' }, { key: 'role', label: 'Role', render: (r) => <Badge tone="neutral">{sentence(r.role)}</Badge> }, { key: 'createdAt', label: 'Joined', render: (r) => relative(r.createdAt) }]} />
        </Card>
        <Card title="Recent sessions">
          <Table rows={d.recentSessions} columns={[{ key: 'topic', label: 'Topic' }, { key: 'r', label: 'Between', render: (r) => `${r.requester.fullName} & ${r.mentor.fullName}` }, { key: 'status', label: 'Status', render: (r) => <Badge value={r.status} /> }]} />
        </Card>
      </div>
    </div>
  )
}

export function AdminAnalytics() {
  const { data, loading, error, reload } = useAsync(() => Admin.dashboard(), [])
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  return (
    <div className="space-y-6">
      <PageHeader title="Analytics">Platform-wide academic, skill and mentoring trends.</PageHeader>
      <ClassAnalytics data={data} linkBase="/teacher/student" />
      <Card title="Sessions by status"><BarChartBox data={data.sessionsByStatus.map((s) => ({ ...s, status: sentence(s.status) }))} x="status" bars={[{ key: 'count', name: 'Sessions', color: PALETTE.ink }]} /></Card>
    </div>
  )
}

export function AdminFeedback() {
  const { data, loading, error, reload } = useAsync(() => Admin.feedback(), [])
  const [participantQuery, setParticipantQuery] = useState('')
  const [sessionQuery, setSessionQuery] = useState('')
  const [rating, setRating] = useState('')
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  const rows = (data || []).filter((item) => {
    const participants = `${item.from?.fullName || ''} ${item.to?.fullName || ''}`.toLowerCase()
    const session = `${item.session?.topic || item.sessionTopic || ''} ${item.session?.id || item.sessionId || ''}`.toLowerCase()
    return participants.includes(participantQuery.trim().toLowerCase())
      && session.includes(sessionQuery.trim().toLowerCase())
      && (!rating || String(item.rating) === rating)
  })
  return (
    <div>
      <PageHeader title="Platform feedback">All feedback submitted across completed mentoring sessions.</PageHeader>
      <Card>
        <div className="mb-4 grid gap-3 sm:grid-cols-3">
          <Field label="Search student or mentor"><Input value={participantQuery} onChange={(e) => setParticipantQuery(e.target.value)} placeholder="Name" /></Field>
          <Field label="Search session"><Input value={sessionQuery} onChange={(e) => setSessionQuery(e.target.value)} placeholder="Topic or session ID" /></Field>
          <Field label="Rating"><Select value={rating} onChange={(e) => setRating(e.target.value)} options={[
            { value: '', label: 'All ratings' }, ...[5, 4, 3, 2, 1].map((value) => ({ value, label: `${value} / 5` })),
          ]} /></Field>
        </div>
        <Table rows={rows} empty={(data || []).length ? 'No feedback matches these filters.' : 'No session feedback has been submitted yet.'} columns={[
          { key: 'sessionId', label: 'Session ID', render: (r) => r.session?.id ?? r.sessionId ?? '—' },
          { key: 'sessionTopic', label: 'Session', render: (r) => (
            <div><span className="font-semibold">{r.session?.topic || r.sessionTopic || 'Session'}</span>
              {(r.session?.date || r.sessionDate) && <p className="text-xs text-slateink">{date(r.session?.date || r.sessionDate)} {time(r.session?.time || r.sessionTime)}</p>}
              {(r.session?.status || r.sessionStatus) && <Badge value={r.session?.status || r.sessionStatus} />}
            </div>
          ) },
          { key: 'from', label: 'From', render: (r) => r.from?.fullName || '—' },
          { key: 'to', label: 'To', render: (r) => r.to?.fullName || '—' },
          { key: 'rating', label: 'Rating', render: (r) => `${r.rating ?? '—'}/5` },
          { key: 'helpfulness', label: 'Helpfulness', render: (r) => r.helpfulness == null ? '—' : `${r.helpfulness}/5` },
          { key: 'comment', label: 'Comment', render: (r) => <div className="min-w-40">
            <p>{r.comment || '—'}</p>
            {r.topicsCovered && <p className="mt-1 text-xs text-slateink"><b>Topics:</b> {r.topicsCovered}</p>}
            {r.progressUpdate && <p className="mt-1 text-xs text-slateink"><b>Progress:</b> {r.progressUpdate}</p>}
          </div> },
          { key: 'createdAt', label: 'Submitted', render: (r) => r.createdAt ? dateTime(r.createdAt) : '—' },
        ]} />
      </Card>
    </div>
  )
}

// ================================================================ Users
const blankUser = { fullName: '', email: '', password: '', role: 'STUDENT', rollNumber: '', branch: '', semester: '', careerGoalId: '', assignedTeacherId: '', department: '', designation: '', expertise: '', mentor: true }

export function AdminUsers() {
  const [role, setRole] = useState('')
  const users = useAsync(() => Admin.users(role || undefined), [role])
  const goals = useAsync(() => Catalog.goals(), [])
  const teachers = useAsync(() => Admin.teachers(), [])
  const { busy, run } = useAction()
  const [form, setForm] = useState(null)
  const [del, setDel] = useState(null)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })
  const save = async () => {
    const f = form
    const body = { ...f, semester: f.semester ? Number(f.semester) : null, careerGoalId: f.careerGoalId ? Number(f.careerGoalId) : null,
      assignedTeacherId: f.assignedTeacherId ? Number(f.assignedTeacherId) : null, expertise: csv(f.expertise) }
    if (await run('save', () => Admin.createUser(body), 'User created')) { setForm(null); users.reload({ silent: true }) }
  }
  return (
    <div>
      <PageHeader title="Users" actions={<Button icon={Plus} onClick={() => setForm(blankUser)}>Add user</Button>}>Create accounts, disable access or remove users without history.</PageHeader>
      <Card>
        <Select className="mb-4 max-w-xs" value={role} onChange={(e) => setRole(e.target.value)} placeholder="All roles" options={['STUDENT', 'TEACHER', 'ADMIN'].map((r) => ({ value: r, label: sentence(r) }))} />
        {users.loading ? <Loading /> : users.error ? <ErrorState error={users.error} onRetry={users.reload} /> : (
          <Table rows={users.data} columns={[
            { key: 'fullName', label: 'Name', render: (r) => <span className="font-semibold">{r.fullName}</span> }, { key: 'email', label: 'Email' },
            { key: 'role', label: 'Role', render: (r) => <Badge tone="neutral">{sentence(r.role)}</Badge> },
            { key: 'enabled', label: 'Access', render: (r) => <Badge tone={r.enabled ? 'sage' : 'coral'}>{r.enabled ? 'Active' : 'Disabled'}</Badge> },
            { key: 'createdAt', label: 'Created', render: (r) => date(r.createdAt) },
            { key: 'a', label: '', render: (r) => <div className="flex justify-end gap-1">
              <Button size="sm" variant="ghost" loading={busy === `t${r.id}`} onClick={async () => (await run(`t${r.id}`, () => Admin.updateUser(r.id, { enabled: !r.enabled }), r.enabled ? 'User disabled' : 'User enabled')) && users.reload({ silent: true })}>{r.enabled ? 'Disable' : 'Enable'}</Button>
              <Button size="sm" variant="ghost" onClick={() => setDel(r)} aria-label="Delete"><Trash2 className="h-4 w-4" /></Button></div> },
          ]} />
        )}
      </Card>
      <Modal open={!!form} onClose={() => setForm(null)} title="Add user" wide footer={<><Button variant="outline" onClick={() => setForm(null)}>Cancel</Button><Button loading={busy === 'save'} onClick={save}>Create user</Button></>}>
        {form && <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Full name"><Input value={form.fullName} onChange={set('fullName')} /></Field>
          <Field label="Email"><Input type="email" value={form.email} onChange={set('email')} /></Field>
          <Field label="Temporary password" hint="At least 8 characters"><Input value={form.password} onChange={set('password')} /></Field>
          <Field label="Role"><Select value={form.role} onChange={set('role')} options={['STUDENT', 'TEACHER', 'ADMIN'].map((r) => ({ value: r, label: sentence(r) }))} /></Field>
          {form.role === 'STUDENT' && <>
            <Field label="Roll number" hint="Leave empty to auto-generate"><Input value={form.rollNumber} onChange={set('rollNumber')} /></Field>
            <Field label="Branch"><Input value={form.branch} onChange={set('branch')} /></Field>
            <Field label="Semester"><Input type="number" min="1" max="12" value={form.semester} onChange={set('semester')} /></Field>
            <Field label="Career goal"><Select placeholder="Student chooses later" value={form.careerGoalId} onChange={set('careerGoalId')} options={(goals.data || []).map((g) => ({ value: g.id, label: g.name }))} /></Field>
            <Field label="Assigned teacher"><Select placeholder="None" value={form.assignedTeacherId} onChange={set('assignedTeacherId')} options={(teachers.data || []).map((t) => ({ value: t.id, label: t.fullName }))} /></Field>
          </>}
          {form.role === 'TEACHER' && <>
            <Field label="Department"><Input value={form.department} onChange={set('department')} /></Field>
            <Field label="Designation"><Input value={form.designation} onChange={set('designation')} /></Field>
            <Field label="Expertise" hint="Comma separated skill names"><Input value={form.expertise} onChange={set('expertise')} /></Field>
            <label className="flex items-center gap-2 pt-6 text-sm"><input type="checkbox" checked={form.mentor} onChange={set('mentor')} /> Accepts mentoring requests</label>
          </>}
        </div>}
      </Modal>
      <ConfirmDialog open={!!del} title="Delete user?" danger confirmLabel="Delete" loading={busy === 'del'}
        message={`${del?.fullName} will be removed. Users with sessions or feedback can only be disabled.`}
        onClose={() => setDel(null)} onConfirm={async () => { if (await run('del', () => Admin.deleteUser(del.id), 'User deleted')) { setDel(null); users.reload({ silent: true }) } }} />
    </div>
  )
}

// ================================================================ Students / Teachers
export function AdminStudents() {
  const students = useAsync(() => Admin.students(), [])
  const teachers = useAsync(() => Admin.teachers(), [])
  const { run } = useAction()
  if (students.loading) return <Loading />
  if (students.error) return <ErrorState error={students.error} onRetry={students.reload} />
  return (
    <div>
      <PageHeader title="Students">Assign a teacher so they appear on that teacher's dashboard.</PageHeader>
      <Card>
        <Table rows={students.data} columns={[
          { key: 'fullName', label: 'Student', render: (r) => <Link className="font-semibold hover:underline" to={`/teacher/student/${r.id}`}>{r.fullName}</Link> },
          { key: 'rollNumber', label: 'Roll' }, { key: 'branch', label: 'Branch' }, { key: 'semester', label: 'Sem' },
          { key: 'careerGoal', label: 'Goal', render: (r) => r.careerGoal?.name || '—' },
          { key: 'assignedTeacher', label: 'Teacher', render: (r) => (
            <Select className="min-w-[170px]" placeholder="Unassigned" value={r.assignedTeacher?.id || ''} options={(teachers.data || []).map((t) => ({ value: t.id, label: t.fullName }))}
              onChange={async (e) => (await run('a', () => Admin.assignTeacher(r.id, e.target.value ? Number(e.target.value) : null), 'Teacher assignment saved')) && students.reload({ silent: true })} />
          ) },
        ]} />
      </Card>
    </div>
  )
}

export function AdminTeachers() {
  const { data, loading, error, reload } = useAsync(() => Admin.teachers(), [])
  const { run } = useAction()
  if (loading) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  return (
    <div>
      <PageHeader title="Teachers">Trust scores come from session feedback and influence mentor matching.</PageHeader>
      <Card>
        <Table rows={data} columns={[
          { key: 'fullName', label: 'Teacher', render: (r) => <span className="font-semibold">{r.fullName}</span> }, { key: 'department', label: 'Department' },
          { key: 'expertise', label: 'Expertise', render: (r) => r.expertise.slice(0, 4).join(', ') },
          { key: 'trustScore', label: 'Trust', render: (r) => (r.trustScore == null ? '—' : `${Math.round(r.trustScore * 100)}%`) },
          { key: 'mentor', label: 'Mentoring', render: (r) => <Button size="sm" variant={r.mentor ? 'outline' : 'ghost'}
            onClick={async () => (await run('m', () => Admin.updateUser(r.userId, { mentor: !r.mentor }), 'Saved')) && reload({ silent: true })}>{r.mentor ? 'On' : 'Off'}</Button> },
        ]} />
      </Card>
    </div>
  )
}

// ================================================================ Academics (manual + CSV)
export function AdminAcademics() {
  const students = useAsync(() => Admin.students(), [])
  const [sid, setSid] = useState('')
  const rec = useAsync(() => (sid ? Admin.academics(sid) : Promise.resolve(null)), [sid])
  const { busy, run } = useAction()
  const blank = { subject: '', marks: '', maxMarks: 100, semester: '', attendancePercent: '', assignmentTitle: '', assignmentStatus: '', assignmentScore: '' }
  const [f, setF] = useState(blank)
  const [file, setFile] = useState(null)
  const [report, setReport] = useState(null)
  useEffect(() => { if (!sid && students.data?.length) setSid(String(students.data[0].id)) }, [students.data, sid])
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  const n = (v) => (v === '' ? null : Number(v))
  const add = async (e) => {
    e.preventDefault()
    const body = { studentId: Number(sid), subject: f.subject, marks: n(f.marks), maxMarks: n(f.maxMarks), semester: n(f.semester), attendancePercent: n(f.attendancePercent),
      assignmentTitle: f.assignmentTitle || null, assignmentStatus: f.assignmentStatus || null, assignmentScore: n(f.assignmentScore), assignmentMaxScore: f.assignmentScore ? 100 : null }
    if (await run('add', () => Admin.addAcademic(body), 'Academic record saved')) { setF(blank); rec.reload({ silent: true }) }
  }
  const upload = async () => {
    const r = await run('csv', () => Admin.importCsv(file), (x) => `Imported ${x.successCount} of ${x.totalRows} rows`)
    if (r) { setReport(r); rec.reload({ silent: true }) }
  }
  return (
    <div className="space-y-6">
      <PageHeader title="Academic records">Enter marks one by one or import a CSV. Portal integration comes later.</PageHeader>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Add a record">
          <form onSubmit={add} className="grid gap-3 sm:grid-cols-2">
            <Field className="sm:col-span-2" label="Student"><Select value={sid} onChange={(e) => setSid(e.target.value)} options={(students.data || []).map((s) => ({ value: s.id, label: `${s.fullName} (${s.rollNumber})` }))} /></Field>
            <Field label="Subject"><Input required value={f.subject} onChange={set('subject')} /></Field>
            <Field label="Semester"><Input type="number" min="1" max="12" value={f.semester} onChange={set('semester')} /></Field>
            <Field label="Marks"><Input required type="number" min="0" step="0.5" value={f.marks} onChange={set('marks')} /></Field>
            <Field label="Max marks"><Input required type="number" min="1" value={f.maxMarks} onChange={set('maxMarks')} /></Field>
            <Field label="Attendance %"><Input type="number" min="0" max="100" value={f.attendancePercent} onChange={set('attendancePercent')} /></Field>
            <Field label="Assignment title"><Input value={f.assignmentTitle} onChange={set('assignmentTitle')} /></Field>
            <Field label="Assignment status"><Select placeholder="—" value={f.assignmentStatus} onChange={set('assignmentStatus')} options={['SUBMITTED', 'PENDING', 'LATE', 'MISSING'].map((s) => ({ value: s, label: sentence(s) }))} /></Field>
            <Field label="Assignment score (of 100)"><Input type="number" min="0" max="100" value={f.assignmentScore} onChange={set('assignmentScore')} /></Field>
            <Button className="sm:col-span-2" type="submit" loading={busy === 'add'} disabled={!sid}>Save record</Button>
          </form>
        </Card>
        <Card title="Import CSV" subtitle="Columns: student_id, subject, marks, max_marks, attendance, assignment_score (optional: semester, academic_year, assignment_status). student_id is the roll number.">
          <div className="flex flex-col gap-3 sm:flex-row">
            <input type="file" accept=".csv" onChange={(e) => { setFile(e.target.files[0]); setReport(null) }} className="field file:mr-3 file:rounded-md file:border-0 file:bg-ink-50 file:px-3 file:py-1 file:text-sm file:font-semibold" />
            <Button icon={Upload} loading={busy === 'csv'} disabled={!file} onClick={upload}>Import</Button>
          </div>
          {report && <div className="mt-4">
            <div className="flex gap-3 text-sm"><Badge tone="sage">{report.successCount} imported</Badge>{report.failedCount > 0 && <Badge tone="coral">{report.failedCount} failed</Badge>}</div>
            {report.failed.length > 0 && <ul className="mt-3 max-h-64 space-y-2 overflow-y-auto text-sm">
              {report.failed.map((r) => <li key={r.row} className="rounded-lg bg-coral-50 px-3 py-2"><b>Row {r.row}</b> {r.studentId && `(${r.studentId})`}: {r.errors.join('; ')}</li>)}
            </ul>}
          </div>}
        </Card>
      </div>
      {rec.loading ? <Loading /> : rec.error ? <ErrorState error={rec.error} /> : rec.data &&
        <AcademicTables data={rec.data} onDelete={async (r) => (await run('d', () => Admin.deleteAcademic(r.id), 'Record deleted')) && rec.reload({ silent: true })} />}
    </div>
  )
}

// ================================================================ Generic catalog CRUD
function CrudPage({ title, intro, load, columns, empty, toBody, fromRow, save, remove, renderForm, label }) {
  const list = useAsync(load, [])
  const { busy, run } = useAction()
  const [form, setForm] = useState(null)
  const [del, setDel] = useState(null)
  const submit = async () => {
    if (await run('save', () => save(form.id, toBody(form)), `${label} saved`)) { setForm(null); list.reload({ silent: true }) }
  }
  return (
    <div>
      <PageHeader title={title} actions={<Button icon={Plus} onClick={() => setForm(empty)}>Add {label.toLowerCase()}</Button>}>{intro}</PageHeader>
      <Card>
        {list.loading ? <Loading /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : (
          <Table rows={list.data} columns={[...columns, { key: 'act', label: '', render: (r) => <div className="flex justify-end gap-1">
            <Button size="sm" variant="ghost" onClick={() => setForm(fromRow(r))} aria-label="Edit"><Pencil className="h-4 w-4" /></Button>
            <Button size="sm" variant="ghost" onClick={() => setDel(r)} aria-label="Delete"><Trash2 className="h-4 w-4" /></Button></div> }]} />
        )}
      </Card>
      <Modal open={!!form} wide onClose={() => setForm(null)} title={form?.id ? `Edit ${label.toLowerCase()}` : `Add ${label.toLowerCase()}`}
        footer={<><Button variant="outline" onClick={() => setForm(null)}>Cancel</Button><Button loading={busy === 'save'} onClick={submit}>Save {label.toLowerCase()}</Button></>}>
        {form && renderForm(form, setForm)}
      </Modal>
      <ConfirmDialog open={!!del} danger title={`Delete ${label.toLowerCase()}?`} confirmLabel="Delete" loading={busy === 'del'} message={`"${del?.name || del?.title}" will be removed.`}
        onClose={() => setDel(null)} onConfirm={async () => { if (await run('del', () => remove(del.id), `${label} deleted`)) { setDel(null); list.reload({ silent: true }) } }} />
    </div>
  )
}

export function AdminSkills() {
  const all = useAsync(() => Catalog.skills(), [])
  return (
    <CrudPage title="Skills" label="Skill" intro="Prerequisites build the dependency graph. Cycles are rejected."
      load={() => Catalog.skills()}
      empty={{ name: '', category: '', description: '', difficulty: 'BEGINNER', estimatedHours: '', practiceTask: '', prerequisiteIds: [] }}
      fromRow={(r) => ({ ...r, estimatedHours: r.estimatedHours ?? '' })}
      toBody={(f) => ({ name: f.name, category: f.category, description: f.description, difficulty: f.difficulty || null, estimatedHours: f.estimatedHours === '' ? null : Number(f.estimatedHours), practiceTask: f.practiceTask, prerequisiteIds: f.prerequisiteIds })}
      save={(id, b) => (id ? Admin.updateSkill(id, b) : Admin.createSkill(b))} remove={Admin.deleteSkill}
      columns={[{ key: 'name', label: 'Skill', render: (r) => <span className="font-semibold">{r.name}</span> }, { key: 'category', label: 'Category' },
        { key: 'difficulty', label: 'Level', render: (r) => <Badge value={r.difficulty} /> }, { key: 'prerequisites', label: 'Prerequisites', render: (r) => r.prerequisites.join(', ') || '—' }]}
      renderForm={(f, setF) => (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Name"><Input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
          <Field label="Category"><Input value={f.category || ''} onChange={(e) => setF({ ...f, category: e.target.value })} /></Field>
          <Field label="Difficulty"><Select value={f.difficulty || ''} onChange={(e) => setF({ ...f, difficulty: e.target.value })} options={levelOpts} /></Field>
          <Field label="Estimated hours"><Input type="number" min="0" value={f.estimatedHours} onChange={(e) => setF({ ...f, estimatedHours: e.target.value })} /></Field>
          <Field className="sm:col-span-2" label="Description"><Textarea value={f.description || ''} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
          <Field className="sm:col-span-2" label="Practice task"><Input value={f.practiceTask || ''} onChange={(e) => setF({ ...f, practiceTask: e.target.value })} /></Field>
          <Field className="sm:col-span-2" label="Prerequisites">
            <div className="flex max-h-40 flex-wrap gap-2 overflow-y-auto rounded-lg border border-chalk-300 p-2">
              {(all.data || []).filter((s) => s.id !== f.id).map((s) => {
                const on = f.prerequisiteIds.includes(s.id)
                return <button type="button" key={s.id} onClick={() => setF({ ...f, prerequisiteIds: on ? f.prerequisiteIds.filter((x) => x !== s.id) : [...f.prerequisiteIds, s.id] })}
                  className={`rounded-full px-3 py-1 text-xs font-semibold ${on ? 'bg-ink text-white' : 'bg-chalk text-ink-700'}`}>{s.name}</button>
              })}
            </div>
          </Field>
        </div>
      )} />
  )
}

export function AdminCareerGoals() {
  const skills = useAsync(() => Catalog.skills(), [])
  return (
    <CrudPage title="Career goals" label="Career goal" intro="Each goal lists the skills it needs, how important each is and the level required."
      load={() => Catalog.goals()} empty={{ name: '', description: '', skills: [] }}
      fromRow={(r) => ({ ...r, skills: r.skills.map((s) => ({ skillId: s.skillId, importance: s.importance, requiredLevel: s.requiredLevel })) })}
      toBody={(f) => ({ name: f.name, description: f.description, skills: f.skills.map((s) => ({ ...s, skillId: Number(s.skillId), importance: Number(s.importance) })) })}
      save={(id, b) => (id ? Admin.updateGoal(id, b) : Admin.createGoal(b))} remove={Admin.deleteGoal}
      columns={[{ key: 'name', label: 'Goal', render: (r) => <span className="font-semibold">{r.name}</span> }, { key: 'skills', label: 'Skills', render: (r) => r.skills.length }, { key: 'description', label: 'Description', render: (r) => <span className="text-slateink">{r.description}</span> }]}
      renderForm={(f, setF) => {
        const upd = (i, k, v) => setF({ ...f, skills: f.skills.map((s, j) => (j === i ? { ...s, [k]: v } : s)) })
        return (
          <div className="space-y-4">
            <Field label="Name"><Input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
            <Field label="Description"><Textarea value={f.description || ''} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
            <div>
              <p className="label">Required skills</p>
              <div className="space-y-2">
                {f.skills.map((s, i) => (
                  <div key={i} className="grid grid-cols-[1fr_90px_140px_auto] gap-2">
                    <Select value={s.skillId} onChange={(e) => upd(i, 'skillId', e.target.value)} options={(skills.data || []).map((x) => ({ value: x.id, label: x.name }))} />
                    <Select value={s.importance} onChange={(e) => upd(i, 'importance', e.target.value)} options={[1, 2, 3, 4, 5].map((n) => ({ value: n, label: `Imp. ${n}` }))} />
                    <Select value={s.requiredLevel} onChange={(e) => upd(i, 'requiredLevel', e.target.value)} options={levelOpts} />
                    <Button variant="ghost" onClick={() => setF({ ...f, skills: f.skills.filter((_, j) => j !== i) })} aria-label="Remove"><Trash2 className="h-4 w-4" /></Button>
                  </div>
                ))}
              </div>
              <Button className="mt-2" size="sm" variant="outline" icon={Plus} disabled={!skills.data?.length}
                onClick={() => setF({ ...f, skills: [...f.skills, { skillId: skills.data[0].id, importance: 3, requiredLevel: 'INTERMEDIATE' }] })}>Add skill</Button>
            </div>
          </div>
        )
      }} />
  )
}

export function AdminResources() {
  const skills = useAsync(() => Catalog.skills(), [])
  const types = ['VIDEO', 'ARTICLE', 'DOCUMENTATION', 'COURSE', 'PRACTICE', 'PROJECT']
  return (
    <CrudPage title="Learning resources" label="Resource" intro="Resources are attached to roadmap steps for the skill they teach."
      load={() => Catalog.resources()} empty={{ title: '', description: '', skillId: '', type: 'DOCUMENTATION', url: '', difficulty: 'BEGINNER', estimatedMinutes: '' }}
      fromRow={(r) => ({ ...r, skillId: r.skill?.id || '', estimatedMinutes: r.estimatedMinutes ?? '' })}
      toBody={(f) => ({ title: f.title, description: f.description, skillId: f.skillId ? Number(f.skillId) : null, type: f.type, url: f.url, difficulty: f.difficulty || null, estimatedMinutes: f.estimatedMinutes === '' ? null : Number(f.estimatedMinutes) })}
      save={(id, b) => (id ? Admin.updateResource(id, b) : Admin.createResource(b))} remove={Admin.deleteResource}
      columns={[{ key: 'title', label: 'Title', render: (r) => (r.url ? <a className="link" href={r.url} target="_blank" rel="noreferrer">{r.title}</a> : r.title) },
        { key: 'skill', label: 'Skill', render: (r) => r.skill?.name || '—' }, { key: 'type', label: 'Type', render: (r) => sentence(r.type) }, { key: 'difficulty', label: 'Level', render: (r) => <Badge value={r.difficulty} /> }]}
      renderForm={(f, setF) => (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field className="sm:col-span-2" label="Title"><Input value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} /></Field>
          <Field className="sm:col-span-2" label="URL"><Input value={f.url || ''} onChange={(e) => setF({ ...f, url: e.target.value })} placeholder="https://" /></Field>
          <Field label="Skill"><Select placeholder="None" value={f.skillId} onChange={(e) => setF({ ...f, skillId: e.target.value })} options={(skills.data || []).map((s) => ({ value: s.id, label: s.name }))} /></Field>
          <Field label="Type"><Select value={f.type} onChange={(e) => setF({ ...f, type: e.target.value })} options={types.map((t) => ({ value: t, label: sentence(t) }))} /></Field>
          <Field label="Level"><Select value={f.difficulty || ''} onChange={(e) => setF({ ...f, difficulty: e.target.value })} options={levelOpts} /></Field>
          <Field label="Minutes"><Input type="number" min="0" value={f.estimatedMinutes} onChange={(e) => setF({ ...f, estimatedMinutes: e.target.value })} /></Field>
          <Field className="sm:col-span-2" label="Description"><Textarea value={f.description || ''} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
        </div>
      )} />
  )
}
