import {
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, LineChart, Line,
  PieChart, Pie, Cell, RadarChart, PolarGrid, PolarAngleAxis, Radar, Legend,
} from 'recharts'

export const PALETTE = { ink: '#1B2A4A', marigold: '#F2A93B', sage: '#5E8C6A', coral: '#E0664F', muted: '#8A97B0', grid: '#E4E8E1' }
const RISK_COLOR = { LOW: PALETTE.sage, MEDIUM: PALETTE.marigold, HIGH: PALETTE.coral, UNKNOWN: PALETTE.muted }

const tooltip = { contentStyle: { borderRadius: 10, border: '1px solid #D2D8CE', fontSize: 13 }, cursor: { fill: 'rgba(27,42,74,0.05)' } }
const axis = { tick: { fontSize: 12, fill: '#4A5568' }, axisLine: false, tickLine: false }

function Empty({ height }) {
  return <div style={{ height }} className="grid place-items-center text-sm text-slateink">No data yet</div>
}

export function BarChartBox({ data, x, bars, height = 260, domain, layout = 'horizontal' }) {
  if (!data?.length) return <Empty height={height} />
  const vertical = layout === 'vertical'
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout={layout} margin={{ top: 8, right: 12, left: vertical ? 8 : -12, bottom: 4 }}>
        <CartesianGrid stroke={PALETTE.grid} vertical={vertical} horizontal={!vertical} />
        {vertical ? (
          <>
            <XAxis type="number" domain={domain} {...axis} />
            <YAxis type="category" dataKey={x} width={130} {...axis} />
          </>
        ) : (
          <>
            <XAxis dataKey={x} {...axis} interval={0} angle={data.length > 5 ? -20 : 0} textAnchor={data.length > 5 ? 'end' : 'middle'} height={data.length > 5 ? 56 : 30} />
            <YAxis domain={domain} {...axis} />
          </>
        )}
        <Tooltip {...tooltip} />
        {bars.length > 1 && <Legend wrapperStyle={{ fontSize: 12 }} />}
        {bars.map((b) => <Bar key={b.key} dataKey={b.key} name={b.name} fill={b.color || PALETTE.ink} radius={vertical ? [0, 6, 6, 0] : [6, 6, 0, 0]} maxBarSize={36} />)}
      </BarChart>
    </ResponsiveContainer>
  )
}

export function LineChartBox({ data, x, lines, height = 240, domain = [0, 100] }) {
  if (!data?.length) return <Empty height={height} />
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 8, right: 16, left: -12, bottom: 4 }}>
        <CartesianGrid stroke={PALETTE.grid} vertical={false} />
        <XAxis dataKey={x} {...axis} />
        <YAxis domain={domain} {...axis} />
        <Tooltip {...tooltip} />
        {lines.map((l) => <Line key={l.key} type="monotone" dataKey={l.key} name={l.name} stroke={l.color || PALETTE.ink} strokeWidth={2.5} dot={{ r: 4, fill: l.color || PALETTE.ink }} />)}
      </LineChart>
    </ResponsiveContainer>
  )
}

export function RiskDonut({ data, height = 220 }) {
  const rows = (data || []).filter((d) => d.count > 0)
  if (!rows.length) return <Empty height={height} />
  return (
    <ResponsiveContainer width="100%" height={height}>
      <PieChart>
        <Pie data={rows} dataKey="count" nameKey="level" innerRadius="58%" outerRadius="85%" paddingAngle={2} stroke="none">
          {rows.map((r) => <Cell key={r.level} fill={RISK_COLOR[r.level] || PALETTE.muted} />)}
        </Pie>
        <Tooltip {...tooltip} />
        <Legend wrapperStyle={{ fontSize: 12 }} formatter={(v) => `${v.charAt(0)}${v.slice(1).toLowerCase()}`} />
      </PieChart>
    </ResponsiveContainer>
  )
}

export function SkillRadar({ data, height = 280 }) {
  if (!data?.length) return <Empty height={height} />
  return (
    <ResponsiveContainer width="100%" height={height}>
      <RadarChart data={data} outerRadius="72%">
        <PolarGrid stroke={PALETTE.grid} />
        <PolarAngleAxis dataKey="skill" tick={{ fontSize: 11, fill: '#4A5568' }} />
        <Radar name="Required" dataKey="required" stroke={PALETTE.muted} fill={PALETTE.muted} fillOpacity={0.15} />
        <Radar name="You" dataKey="current" stroke={PALETTE.marigold} fill={PALETTE.marigold} fillOpacity={0.45} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Tooltip {...tooltip} />
      </RadarChart>
    </ResponsiveContainer>
  )
}
