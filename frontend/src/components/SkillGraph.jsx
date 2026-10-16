import { useMemo, useState } from 'react'

/**
 * Layered drawing of the skill dependency graph (prerequisite -> skill).
 * `status` maps skill id -> 'have' | 'partial' | 'missing' | 'goal' for colouring.
 */
const FILL = { have: '#5E8C6A', partial: '#F2A93B', missing: '#E0664F', goal: '#1B2A4A', other: '#FFFFFF' }

export default function SkillGraph({ nodes = [], edges = [], status = {}, focusIds }) {
  const [hover, setHover] = useState(null)
  const layout = useMemo(() => {
    const ids = new Set(focusIds && focusIds.length ? focusIds : nodes.map((n) => n.id))
    // include prerequisites of focused nodes so the chain is visible
    if (focusIds && focusIds.length) {
      let grew = true
      while (grew) {
        grew = false
        edges.forEach((e) => { if (ids.has(e.to) && !ids.has(e.from)) { ids.add(e.from); grew = true } })
      }
    }
    const shown = nodes.filter((n) => ids.has(n.id))
    const es = edges.filter((e) => ids.has(e.from) && ids.has(e.to))
    const parents = {}
    es.forEach((e) => { (parents[e.to] ||= []).push(e.from) })
    const depth = {}
    const d = (id, seen = new Set()) => {
      if (depth[id] !== undefined) return depth[id]
      if (seen.has(id)) return 0
      seen.add(id)
      const p = parents[id] || []
      depth[id] = p.length ? 1 + Math.max(...p.map((x) => d(x, seen))) : 0
      return depth[id]
    }
    shown.forEach((n) => d(n.id))
    const cols = {}
    shown.forEach((n) => { (cols[depth[n.id]] ||= []).push(n) })
    const colW = 190, rowH = 46, pad = 16
    const maxRows = Math.max(1, ...Object.values(cols).map((c) => c.length))
    const pos = {}
    Object.entries(cols).forEach(([c, list]) => {
      list.sort((a, b) => a.name.localeCompare(b.name))
      const offset = ((maxRows - list.length) * rowH) / 2
      list.forEach((n, i) => { pos[n.id] = { x: pad + Number(c) * colW, y: pad + offset + i * rowH } })
    })
    const width = pad * 2 + (Object.keys(cols).length - 1) * colW + 160
    const height = pad * 2 + maxRows * rowH
    return { shown, es, pos, width, height }
  }, [nodes, edges, focusIds])

  if (!layout.shown.length) return <p className="py-8 text-center text-sm text-slateink">No skills to show yet.</p>
  const related = (id) => hover && (id === hover || layout.es.some((e) => (e.from === hover && e.to === id) || (e.to === hover && e.from === id)))

  return (
    <div>
      <div className="overflow-x-auto rounded-xl border border-chalk-300 bg-chalk">
        <svg width={layout.width} height={layout.height} role="img" aria-label="Skill dependency graph">
          <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M0 0L10 5L0 10z" fill="#8A97B0" />
            </marker>
          </defs>
          {layout.es.map((e) => {
            const a = layout.pos[e.from], b = layout.pos[e.to]
            const x1 = a.x + 160, y1 = a.y + 15, x2 = b.x - 2, y2 = b.y + 15
            const lit = hover && (e.from === hover || e.to === hover)
            return <path key={`${e.from}-${e.to}`} d={`M${x1},${y1} C${x1 + 20},${y1} ${x2 - 20},${y2} ${x2},${y2}`}
              fill="none" stroke={lit ? '#1B2A4A' : '#B7C0CF'} strokeWidth={lit ? 2 : 1.2} markerEnd="url(#arrow)" opacity={hover && !lit ? 0.35 : 1} />
          })}
          {layout.shown.map((n) => {
            const p = layout.pos[n.id]
            const st = status[n.id] || 'other'
            const dark = st === 'goal' || st === 'have' || st === 'missing'
            return (
              <g key={n.id} transform={`translate(${p.x},${p.y})`} onMouseEnter={() => setHover(n.id)} onMouseLeave={() => setHover(null)}
                opacity={hover && !related(n.id) ? 0.4 : 1} style={{ cursor: 'default' }}>
                <rect width="160" height="30" rx="15" fill={FILL[st]} stroke={st === 'other' ? '#B7C0CF' : 'none'} />
                <text x="80" y="19.5" textAnchor="middle" fontSize="12" fontWeight="600" fill={dark ? '#fff' : '#1B2A4A'}>
                  {n.name.length > 22 ? `${n.name.slice(0, 21)}…` : n.name}
                </text>
              </g>
            )
          })}
        </svg>
      </div>
      <div className="mt-3 flex flex-wrap gap-4 text-xs text-slateink">
        {[['have', 'You have it'], ['partial', 'Partly there'], ['missing', 'Missing for your goal'], ['other', 'Not required']].map(([k, l]) => (
          <span key={k} className="flex items-center gap-1.5">
            <span className="h-3 w-5 rounded-full border border-chalk-300" style={{ background: FILL[k] }} />{l}
          </span>
        ))}
        <span>Arrows point from a prerequisite to the skill it unlocks.</span>
      </div>
    </div>
  )
}
