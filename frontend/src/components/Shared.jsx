import { useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { errorMessage, isAiDown } from '../api/client'
import { AI } from '../api/endpoints'
import { pct, relative } from '../lib/format'
import { Badge, Button, Card } from './ui'
import { useToast } from './Toast'

/** Runs the full AI pipeline (risk + gap + graph + matching -> roadmap & recommendations). */
export function RegenerateButton({ studentId, onDone, stale, label = 'Refresh recommendations', size = 'md' }) {
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const run = async () => {
    setBusy(true)
    try {
      await AI.regenerate(studentId)
      toast.success('Analysis updated — roadmap and recommendations regenerated')
      onDone?.()
    } catch (e) {
      toast.error(isAiDown(e) ? 'AI service is offline. Start it and try again.' : errorMessage(e))
    } finally { setBusy(false) }
  }
  return <Button size={size} variant={stale ? 'accent' : 'outline'} icon={RefreshCw} loading={busy} onClick={run}>{label}</Button>
}

export function StaleNote({ stale, lastAnalyzedAt }) {
  if (!stale && lastAnalyzedAt) return <p className="text-xs text-slateink">Last analysed {relative(lastAnalyzedAt)}</p>
  return <p className="text-xs font-medium text-marigold-700">{lastAnalyzedAt ? 'Your data changed since the last analysis.' : 'No analysis yet.'}</p>
}

export function RiskCard({ risk }) {
  if (!risk) return <Card title="Academic risk"><p className="text-sm text-slateink">Not analysed yet.</p></Card>
  return (
    <Card title="Academic risk" action={<Badge value={risk.riskLevel} />}>
      <p className="font-display text-4xl font-extrabold">{risk.probability == null ? '—' : pct(risk.probability * 100)}</p>
      <p className="text-xs text-slateink">estimated probability of needing academic support</p>
      {risk.indicators?.length > 0 && (
        <ul className="mt-4 space-y-1 text-sm">
          {risk.indicators.slice(0, 4).map((i) => <li key={i} className="flex gap-2"><span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-coral" />{i}</li>)}
        </ul>
      )}
      {risk.suggestedActions?.length > 0 && (
        <div className="mt-4 rounded-xl bg-chalk p-3 text-sm">
          <p className="font-semibold">Suggested next steps</p>
          <ul className="mt-1 list-disc pl-5 text-slateink">{risk.suggestedActions.map((a) => <li key={a}>{a}</li>)}</ul>
        </div>
      )}
      <p className="mt-3 text-xs text-ink-300">Prototype model ({risk.modelVersion}) trained on synthetic data — a support signal, not a judgement.</p>
    </Card>
  )
}

export function AiHint({ error }) {
  if (!isAiDown(error)) return null
  return <p className="rounded-xl bg-marigold-50 px-4 py-3 text-sm text-marigold-700">AI service offline — showing your last saved analysis.</p>
}
