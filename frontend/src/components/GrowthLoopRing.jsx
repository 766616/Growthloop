/**
 * The signature visual: three concentric arcs that close into a loop as the student grows.
 * Outer = roadmap completion, middle = skill coverage for the goal, inner = academic average.
 */
const RINGS = [
  { key: 'roadmap', r: 84, color: '#F2A93B', label: 'Roadmap done' },
  { key: 'coverage', r: 64, color: '#5E8C6A', label: 'Goal skills covered' },
  { key: 'academics', r: 44, color: '#8FB3E8', label: 'Academic average' },
]

export default function GrowthLoopRing({ roadmap = 0, coverage = 0, academics = 0, size = 220, center }) {
  const values = { roadmap, coverage, academics }
  return (
    <div className="flex flex-col items-center gap-5 sm:flex-row sm:items-center">
      <svg width={size} height={size} viewBox="0 0 200 200" role="img"
        aria-label={`Roadmap ${Math.round(roadmap)}%, skills ${Math.round(coverage)}%, academics ${Math.round(academics)}%`}>
        {RINGS.map((ring) => {
          const len = 2 * Math.PI * ring.r
          const v = Math.max(0, Math.min(100, Number(values[ring.key]) || 0))
          return (
            <g key={ring.key} transform="rotate(-90 100 100)">
              <circle cx="100" cy="100" r={ring.r} fill="none" stroke="rgba(255,255,255,0.12)" strokeWidth="13" />
              <circle className="loop-arc" cx="100" cy="100" r={ring.r} fill="none" stroke={ring.color} strokeWidth="13"
                strokeLinecap="round" strokeDasharray={len} strokeDashoffset={len * (1 - v / 100)} style={{ '--len': len }} />
            </g>
          )
        })}
        <text x="100" y="96" textAnchor="middle" className="font-display" fontSize="30" fontWeight="800" fill="#fff">{center ?? `${Math.round(roadmap)}%`}</text>
        <text x="100" y="116" textAnchor="middle" fontSize="11" fill="#D9DFEA">of your loop</text>
      </svg>
      <ul className="space-y-3">
        {RINGS.map((ring) => (
          <li key={ring.key} className="flex items-center gap-3">
            <span className="h-3 w-3 rounded-full" style={{ background: ring.color }} />
            <span className="text-sm text-ink-100">{ring.label}</span>
            <span className="ml-auto pl-4 font-display text-lg font-bold text-white">{Math.round(Number(values[ring.key]) || 0)}%</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
