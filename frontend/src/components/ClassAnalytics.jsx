import { Link } from 'react-router-dom'
import { BarChartBox, LineChartBox, PALETTE, RiskDonut } from './Charts'
import { Badge, Card, ProgressBar, Stat, Table } from './ui'
import { pct } from '../lib/format'

export function SupportList({ rows, linkBase }) {
  return (
    <Table rows={rows} empty="No students flagged right now." columns={[
      { key: 'fullName', label: 'Student', render: (r) => (linkBase ? <Link className="font-semibold hover:underline" to={`${linkBase}/${r.id}`}>{r.fullName}</Link> : <span className="font-semibold">{r.fullName}</span>) },
      { key: 'rollNumber', label: 'Roll' },
      { key: 'riskLevel', label: 'Risk', render: (r) => <Badge value={r.riskLevel} /> },
      { key: 'averagePercentage', label: 'Avg', render: (r) => pct(r.averagePercentage) },
      { key: 'indicators', label: 'Why', render: (r) => <span className="text-slateink">{(r.indicators || []).slice(0, 2).join('; ') || '—'}</span> },
    ]} />
  )
}

export default function ClassAnalytics({ data, linkBase }) {
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        <Stat label="Students" value={data.totalStudents} />
        <Stat label="Average performance" value={pct(data.averagePerformance)} />
        <Stat label="Need support" value={data.studentsNeedingSupport.length} tone={data.studentsNeedingSupport.length ? 'coral' : 'sage'} note="medium or high risk" />
      </div>
      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="Risk distribution"><RiskDonut data={data.riskDistribution} /></Card>
        <Card className="lg:col-span-2" title="Subject averages"><BarChartBox data={data.subjectPerformance} x="subject" domain={[0, 100]} bars={[{ key: 'average', name: 'Average %', color: PALETTE.ink }]} /></Card>
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Attendance by semester"><LineChartBox data={data.attendanceTrend} x="semester" lines={[{ key: 'average', name: 'Attendance %', color: PALETTE.sage }]} /></Card>
        <Card title="Most common skill gaps" subtitle="Students missing each skill for their goal">
          <BarChartBox layout="vertical" data={data.skillGapTrends} x="skill" bars={[{ key: 'count', name: 'Students', color: PALETTE.coral }]} />
        </Card>
      </div>
      <Card title="Student progress">
        <Table rows={data.studentProgress} columns={[
          { key: 'fullName', label: 'Student', render: (r) => (linkBase ? <Link className="font-semibold hover:underline" to={`${linkBase}/${r.id}`}>{r.fullName}</Link> : r.fullName) },
          { key: 'careerGoal', label: 'Goal' },
          { key: 'averagePercentage', label: 'Avg', render: (r) => pct(r.averagePercentage) },
          { key: 'riskLevel', label: 'Risk', render: (r) => <Badge value={r.riskLevel} /> },
          { key: 'progressPercent', label: 'Roadmap', className: 'w-48', render: (r) => <ProgressBar value={r.progressPercent} label=" " /> },
        ]} />
      </Card>
    </div>
  )
}
