import { useState } from 'react'
import { CalendarClock, Star } from 'lucide-react'
import { errorMessage } from '../api/client'
import { Feedback, Sessions } from '../api/endpoints'
import { date, time, LEVELS, sentence } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { Badge, Button, Card, EmptyState, ErrorState, Field, Input, Loading, Modal, PageHeader, Select, Textarea } from './ui'
import { useToast } from './Toast'

function Stars({ value, onChange, label }) {
  return (
    <div role="radiogroup" aria-label={label} className="flex gap-1">
      {[1, 2, 3, 4, 5].map((n) => (
        <button type="button" key={n} onClick={() => onChange(n)} aria-label={`${n} of 5`}
          className={`rounded p-0.5 ${n <= value ? 'text-marigold' : 'text-chalk-300'}`}>
          <Star className="h-7 w-7" fill="currentColor" />
        </button>
      ))}
    </div>
  )
}

export function FeedbackModal({ session, onClose, onDone }) {
  const toast = useToast()
  const learnerView = session?.myRole === 'LEARNER'
  const [f, setF] = useState({ rating: 5, helpfulness: 5, comment: '', topicsCovered: session?.topic || '', progressUpdate: '', level: '' })
  const [busy, setBusy] = useState(false)
  if (!session) return null
  const submit = async () => {
    setBusy(true)
    try {
      const body = { sessionId: session.id, rating: f.rating, helpfulness: f.helpfulness, comment: f.comment, topicsCovered: f.topicsCovered, progressUpdate: f.progressUpdate }
      if (f.level && session.skill) body.skillProgress = { skillId: session.skill.id, proficiency: f.level }
      const res = await Feedback.submit(body)
      toast.success(res?.recommendationsRegenerated ? 'Feedback saved — recommendations updated' : `Feedback saved${res?.note ? `. ${res.note}` : ''}`)
      onDone?.()
      onClose()
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title={`Feedback: ${session.topic}`}
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button><Button loading={busy} onClick={submit}>Submit feedback</Button></>}>
      <div className="space-y-4">
        <Field label={learnerView ? `How was ${session.mentor?.fullName}?` : `How engaged was ${session.requester?.fullName}?`}><Stars label="Rating" value={f.rating} onChange={(v) => setF({ ...f, rating: v })} /></Field>
        <Field label={learnerView ? 'How helpful was the session?' : 'How well prepared were they?'}><Stars label="Helpfulness" value={f.helpfulness} onChange={(v) => setF({ ...f, helpfulness: v })} /></Field>
        <Field label="Topics covered"><Input value={f.topicsCovered} onChange={(e) => setF({ ...f, topicsCovered: e.target.value })} /></Field>
        <Field label="Comment"><Textarea value={f.comment} onChange={(e) => setF({ ...f, comment: e.target.value })} /></Field>
        <Field label="Progress update" hint="What can the learner do now that they couldn't before?"><Textarea value={f.progressUpdate} onChange={(e) => setF({ ...f, progressUpdate: e.target.value })} /></Field>
        {session.skill && (
          <Field label={`Learner's level in ${session.skill.name} after this session (optional)`}>
            <Select placeholder="No change" value={f.level} onChange={(e) => setF({ ...f, level: e.target.value })} options={LEVELS.map((l) => ({ value: l, label: sentence(l) }))} />
          </Field>
        )}
      </div>
    </Modal>
  )
}

export default function SessionsView({ title = 'Sessions', intro }) {
  const toast = useToast()
  const { data, loading, error, reload } = useAsync(() => Sessions.mine(), [])
  const [fb, setFb] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [tab, setTab] = useState('active')

  const act = async (s, status) => {
    setBusyId(`${s.id}-${status}`)
    try {
      await Sessions.setStatus(s.id, { status })
      toast.success(`Session ${sentence(status).toLowerCase()}`)
      reload({ silent: true })
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusyId(null) }
  }

  const list = (data || []).filter((s) => (tab === 'active' ? ['REQUESTED', 'ACCEPTED', 'SCHEDULED'].includes(s.status) : !['REQUESTED', 'ACCEPTED', 'SCHEDULED'].includes(s.status)))
  return (
    <div>
      <PageHeader title={title}>{intro}</PageHeader>
      <div className="mb-4 flex gap-2">
        {[['active', 'Active'], ['past', 'Past']].map(([k, l]) => (
          <button key={k} onClick={() => setTab(k)} className={`rounded-full px-4 py-1.5 text-sm font-semibold ${tab === k ? 'bg-ink text-white' : 'bg-white text-ink-700 ring-1 ring-chalk-300'}`}>{l}</button>
        ))}
      </div>
      {loading ? <Loading /> : error ? <ErrorState error={error} onRetry={reload} /> : !list.length ? (
        <EmptyState icon={CalendarClock} title={tab === 'active' ? 'No active sessions' : 'No past sessions'}>Session requests and their outcomes show up here.</EmptyState>
      ) : (
        <div className="space-y-3">
          {list.map((s) => {
            const mentor = s.myRole === 'MENTOR'
            const other = mentor ? s.requester : s.mentor
            const b = (st) => busyId === `${s.id}-${st}`
            return (
              <Card key={s.id} bodyClass="p-5">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-display text-lg font-bold">{s.topic}</p>
                      <Badge value={s.status} />
                      {s.skill && <Badge tone="neutral">{s.skill.name}</Badge>}
                    </div>
                    <p className="mt-1 text-sm text-slateink">{mentor ? 'Learner' : 'Mentor'}: <b className="text-ink">{other?.fullName}</b> · {s.date ? date(s.date) : 'date to be set'} {time(s.time)}</p>
                    {s.notes && <p className="mt-1 text-sm text-slateink">{s.notes}</p>}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {mentor && s.status === 'REQUESTED' && <>
                      <Button size="sm" loading={b('ACCEPTED')} onClick={() => act(s, 'ACCEPTED')}>Accept</Button>
                      <Button size="sm" variant="outline" loading={b('REJECTED')} onClick={() => act(s, 'REJECTED')}>Decline</Button>
                    </>}
                    {['ACCEPTED', 'SCHEDULED'].includes(s.status) && <Button size="sm" variant="accent" loading={b('COMPLETED')} onClick={() => act(s, 'COMPLETED')}>Mark completed</Button>}
                    {['REQUESTED', 'ACCEPTED', 'SCHEDULED'].includes(s.status) && !(mentor && s.status === 'REQUESTED') &&
                      <Button size="sm" variant="ghost" loading={b('CANCELLED')} onClick={() => act(s, 'CANCELLED')}>Cancel</Button>}
                    {s.status === 'COMPLETED' && (s.feedbackGiven ? <Badge tone="sage">Feedback given</Badge> : <Button size="sm" onClick={() => setFb(s)}>Leave feedback</Button>)}
                  </div>
                </div>
              </Card>
            )
          })}
        </div>
      )}
      {fb && <FeedbackModal session={fb} onClose={() => setFb(null)} onDone={() => reload({ silent: true })} />}
    </div>
  )
}
