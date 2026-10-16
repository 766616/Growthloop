import { useEffect, useRef } from 'react'
import { AlertTriangle, Loader2, RefreshCw, X } from 'lucide-react'
import { errorMessage, isAiDown } from '../api/client'
import { toneOf, sentence } from '../lib/format'

const cx = (...c) => c.filter(Boolean).join(' ')
export { cx }

// ---------------------------------------------------------------- Button
const BTN = {
  primary: 'bg-ink text-white hover:bg-ink-700 disabled:bg-ink-300',
  accent: 'bg-marigold text-ink hover:bg-marigold-600 disabled:opacity-60',
  outline: 'border border-chalk-300 bg-white text-ink hover:border-ink-300 hover:bg-chalk disabled:opacity-60',
  ghost: 'text-ink-700 hover:bg-ink-50 disabled:opacity-50',
  danger: 'bg-coral text-white hover:bg-coral-700 disabled:opacity-60',
}
const SIZE = { sm: 'h-8 px-3 text-sm gap-1.5', md: 'h-10 px-4 text-sm gap-2', lg: 'h-12 px-6 text-base gap-2' }

export function Button({ variant = 'primary', size = 'md', loading, icon: Icon, className, children, ...props }) {
  return (
    <button
      className={cx('inline-flex items-center justify-center rounded-lg font-semibold transition-colors disabled:cursor-not-allowed', BTN[variant], SIZE[size], className)}
      disabled={loading || props.disabled}
      {...props}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : Icon ? <Icon className="h-4 w-4" /> : null}
      {children}
    </button>
  )
}

// ---------------------------------------------------------------- Card
export function Card({ title, subtitle, action, className, bodyClass, children, tone = 'plain' }) {
  const tones = {
    plain: 'bg-white border border-chalk-300',
    ink: 'bg-ink text-white',
    tint: 'bg-marigold-50 border border-marigold-100',
  }
  return (
    <section className={cx('rounded-2xl', tones[tone], className)}>
      {(title || action) && (
        <header className="flex items-start justify-between gap-3 px-5 pt-5">
          <div>
            {title && <h3 className={cx('text-lg font-bold leading-tight', tone === 'ink' ? 'text-white' : 'text-ink')}>{title}</h3>}
            {subtitle && <p className={cx('mt-0.5 text-sm', tone === 'ink' ? 'text-ink-100' : 'text-slateink')}>{subtitle}</p>}
          </div>
          {action}
        </header>
      )}
      <div className={cx('p-5', bodyClass)}>{children}</div>
    </section>
  )
}

// ---------------------------------------------------------------- Badge
const BADGE = {
  coral: 'bg-coral-50 text-coral-700 ring-coral-100',
  marigold: 'bg-marigold-50 text-marigold-700 ring-marigold-100',
  sage: 'bg-sage-50 text-sage-700 ring-sage-100',
  ink: 'bg-ink-50 text-ink-700 ring-ink-100',
  neutral: 'bg-chalk text-slateink ring-chalk-300',
}
export function Badge({ children, tone, value, className }) {
  const t = tone || toneOf(value)
  return (
    <span className={cx('inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset', BADGE[t], className)}>
      {children ?? sentence(value)}
    </span>
  )
}

// ---------------------------------------------------------------- ProgressBar
export function ProgressBar({ value = 0, tone = 'sage', label, className }) {
  const v = Math.max(0, Math.min(100, Number(value) || 0))
  const bar = { sage: 'bg-sage', marigold: 'bg-marigold', coral: 'bg-coral', ink: 'bg-ink' }[tone]
  return (
    <div className={className}>
      {label && (
        <div className="mb-1 flex justify-between text-xs text-slateink">
          <span>{label}</span>
          <span className="font-semibold text-ink">{Math.round(v)}%</span>
        </div>
      )}
      <div className="h-2 w-full overflow-hidden rounded-full bg-chalk-200" role="progressbar" aria-valuenow={v} aria-valuemin={0} aria-valuemax={100}>
        <div className={cx('h-full rounded-full transition-[width] duration-700', bar)} style={{ width: `${v}%` }} />
      </div>
    </div>
  )
}

// ---------------------------------------------------------------- Loading / Error / Empty
export function Loading({ label = 'Loading', className }) {
  return (
    <div className={cx('flex items-center justify-center gap-3 py-16 text-slateink', className)} role="status">
      <svg className="h-6 w-6 animate-spin" viewBox="0 0 24 24" aria-hidden>
        <circle cx="12" cy="12" r="9" fill="none" stroke="#D2D8CE" strokeWidth="3" />
        <path d="M12 3a9 9 0 0 1 9 9" fill="none" stroke="#F2A93B" strokeWidth="3" strokeLinecap="round" />
      </svg>
      <span className="text-sm">{label}…</span>
    </div>
  )
}

export function ErrorState({ error, onRetry, className }) {
  const ai = isAiDown(error)
  return (
    <div className={cx('rounded-2xl border px-5 py-6', ai ? 'border-marigold-100 bg-marigold-50' : 'border-coral-100 bg-coral-50', className)} role="alert">
      <div className="flex items-start gap-3">
        <AlertTriangle className={cx('mt-0.5 h-5 w-5 shrink-0', ai ? 'text-marigold-700' : 'text-coral-700')} />
        <div className="flex-1">
          <p className="font-semibold text-ink">{ai ? 'The AI service is offline' : 'This section could not load'}</p>
          <p className="mt-1 text-sm text-slateink">{errorMessage(error)}</p>
          {ai && <p className="mt-1 text-sm text-slateink">Start it with <code className="rounded bg-white px-1">uvicorn app.main:app --port 8000</code>. Your saved data is safe.</p>}
        </div>
        {onRetry && <Button size="sm" variant="outline" icon={RefreshCw} onClick={() => onRetry()}>Retry</Button>}
      </div>
    </div>
  )
}

export function EmptyState({ icon: Icon, title, children, action, className }) {
  return (
    <div className={cx('flex flex-col items-center rounded-2xl border border-dashed border-chalk-300 px-6 py-10 text-center', className)}>
      {Icon && <div className="mb-3 grid h-12 w-12 place-items-center rounded-full bg-marigold-50 text-marigold-700"><Icon className="h-6 w-6" /></div>}
      <p className="font-display text-lg font-bold text-ink">{title}</p>
      {children && <p className="mt-1 max-w-md text-sm text-slateink">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

// ---------------------------------------------------------------- Modal
export function Modal({ open, onClose, title, children, footer, wide }) {
  const ref = useRef(null)
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose
  useEffect(() => {
    if (!open) return
    const onKey = (e) => e.key === 'Escape' && onCloseRef.current?.()
    document.addEventListener('keydown', onKey)
    ref.current?.focus()
    return () => document.removeEventListener('keydown', onKey)
  }, [open])
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink-900/50 p-0 sm:items-center sm:p-6" onMouseDown={(e) => e.target === e.currentTarget && onClose?.()}>
      <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-label={title}
        className={cx('max-h-[92vh] w-full overflow-y-auto rounded-t-2xl bg-white shadow-lift outline-none sm:rounded-2xl', wide ? 'sm:max-w-3xl' : 'sm:max-w-lg')}>
        <div className="flex items-center justify-between border-b border-chalk-200 px-6 py-4">
          <h2 className="text-xl font-bold">{title}</h2>
          <button onClick={onClose} className="rounded-lg p-1 text-slateink hover:bg-chalk" aria-label="Close"><X className="h-5 w-5" /></button>
        </div>
        <div className="px-6 py-5">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-chalk-200 px-6 py-4">{footer}</div>}
      </div>
    </div>
  )
}

export function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', danger, loading, onConfirm, onClose }) {
  return (
    <Modal open={open} onClose={onClose} title={title}
      footer={<><Button variant="outline" onClick={onClose}>Keep it</Button><Button variant={danger ? 'danger' : 'primary'} loading={loading} onClick={onConfirm}>{confirmLabel}</Button></>}>
      <p className="text-sm text-slateink">{message}</p>
    </Modal>
  )
}

// ---------------------------------------------------------------- Table
export function Table({ columns, rows, empty = 'Nothing here yet.', rowKey = (r) => r.id, onRowClick }) {
  if (!rows?.length) return <p className="py-8 text-center text-sm text-slateink">{empty}</p>
  return (
    <div className="-mx-5 overflow-x-auto">
      <table className="min-w-full text-left text-sm">
        <thead>
          <tr className="border-b border-chalk-200 text-slateink">
            {columns.map((c) => <th key={c.key} className={cx('px-5 py-2.5 font-medium', c.className)}>{c.label}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={rowKey(r)} onClick={onRowClick ? () => onRowClick(r) : undefined}
              className={cx('border-b border-chalk-200 last:border-0', onRowClick && 'cursor-pointer hover:bg-chalk')}>
              {columns.map((c) => <td key={c.key} className={cx('px-5 py-3 align-middle', c.className)}>{c.render ? c.render(r) : r[c.key] ?? '—'}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ---------------------------------------------------------------- Form fields
export function Field({ label, error, hint, children, className }) {
  return (
    <label className={cx('block', className)}>
      {label && <span className="label">{label}</span>}
      {children}
      {hint && !error && <span className="mt-1 block text-xs text-slateink">{hint}</span>}
      {error && <span className="mt-1 block text-xs font-medium text-coral-700">{error}</span>}
    </label>
  )
}
export const Input = ({ className, ...p }) => <input className={cx('field', className)} {...p} />
export const Textarea = ({ className, ...p }) => <textarea className={cx('field min-h-[88px]', className)} {...p} />
export function Select({ options = [], placeholder, className, ...p }) {
  return (
    <select className={cx('field', className)} {...p}>
      {placeholder !== undefined && <option value="">{placeholder}</option>}
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  )
}

// ---------------------------------------------------------------- Page header & stat
export function PageHeader({ title, children, actions }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-3xl font-extrabold text-ink sm:text-[2.1rem]">{title}</h1>
        {children && <p className="mt-1 max-w-2xl text-slateink">{children}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function Stat({ label, value, note, tone }) {
  const color = { coral: 'text-coral-700', sage: 'text-sage-700', marigold: 'text-marigold-700' }[tone] || 'text-ink'
  return (
    <div className="rounded-2xl border border-chalk-300 bg-white px-5 py-4">
      <p className="text-sm text-slateink">{label}</p>
      <p className={cx('mt-1 font-display text-3xl font-extrabold', color)}>{value}</p>
      {note && <p className="mt-0.5 text-xs text-slateink">{note}</p>}
    </div>
  )
}

export function Avatar({ name, className }) {
  const letters = (name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('')
  return <span className={cx('grid h-9 w-9 shrink-0 place-items-center rounded-full bg-ink-50 font-display text-sm font-bold text-ink', className)}>{letters}</span>
}
