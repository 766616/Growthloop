export const pct = (v, digits = 0) => (v === null || v === undefined || Number.isNaN(Number(v)) ? '—' : `${Number(v).toFixed(digits)}%`)

export const num = (v, digits = 1) => (v === null || v === undefined ? '—' : Number(v).toFixed(digits))

export function date(v, opts = { day: 'numeric', month: 'short', year: 'numeric' }) {
  if (!v) return '—'
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleDateString('en-IN', opts)
}

export function dateTime(v) {
  if (!v) return '—'
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}

export const time = (t) => (t ? String(t).slice(0, 5) : '')

export function relative(v) {
  if (!v) return ''
  const diff = (Date.now() - new Date(v).getTime()) / 1000
  if (diff < 60) return 'just now'
  if (diff < 3600) return `${Math.floor(diff / 60)} min ago`
  if (diff < 86400) return `${Math.floor(diff / 3600)} h ago`
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} d ago`
  return date(v)
}

export const titleCase = (s) => (s ? String(s).toLowerCase().replace(/_/g, ' ').replace(/(^|\s)\S/g, (c) => c.toUpperCase()) : '')
export const sentence = (s) => (s ? String(s).toLowerCase().replace(/_/g, ' ').replace(/^\S/, (c) => c.toUpperCase()) : '')

export const initials = (name = '') => name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('')

/** Tone used by Badge for any status/level string coming from the API. */
export function toneOf(value) {
  switch (value) {
    case 'HIGH': case 'MISSING': case 'REJECTED': case 'CANCELLED': case 'LATE':
      return 'coral'
    case 'MEDIUM': case 'PARTIAL': case 'REQUESTED': case 'IN_PROGRESS': case 'PENDING':
      return 'marigold'
    case 'LOW': case 'COMPLETED': case 'SUBMITTED': case 'ACCEPTED': case 'ADVANCED':
      return 'sage'
    case 'SCHEDULED': case 'INTERMEDIATE': case 'MENTOR':
      return 'ink'
    default:
      return 'neutral'
  }
}

export const LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']
