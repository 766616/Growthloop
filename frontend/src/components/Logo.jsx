export default function Logo({ light, className = '' }) {
  return (
    <span className={`inline-flex items-center gap-2 font-display text-xl font-extrabold tracking-tight ${light ? 'text-white' : 'text-ink'} ${className}`}>
      <svg width="26" height="26" viewBox="0 0 32 32" aria-hidden>
        <circle cx="16" cy="16" r="11" fill="none" stroke={light ? 'rgba(255,255,255,.25)' : '#1B2A4A'} strokeWidth="5" />
        <path d="M16 5a11 11 0 0 1 11 11" fill="none" stroke="#F2A93B" strokeWidth="5" strokeLinecap="round" />
      </svg>
      GrowthLoop
    </span>
  )
}
