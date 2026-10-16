import { createContext, useCallback, useContext, useState } from 'react'
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react'

const ToastCtx = createContext({ push: () => {} })

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), [])
  const push = useCallback((message, kind = 'success') => {
    const id = Math.random().toString(36).slice(2)
    setToasts((t) => [...t.slice(-3), { id, message, kind }])
    setTimeout(() => dismiss(id), kind === 'error' ? 6000 : 3500)
  }, [dismiss])
  const api = { push, success: (m) => push(m, 'success'), error: (m) => push(m, 'error'), info: (m) => push(m, 'info') }
  const icon = { success: CheckCircle2, error: AlertCircle, info: Info }
  const tone = { success: 'border-l-sage', error: 'border-l-coral', info: 'border-l-marigold' }
  return (
    <ToastCtx.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[min(92vw,380px)] flex-col gap-2" aria-live="polite">
        {toasts.map((t) => {
          const Icon = icon[t.kind]
          return (
            <div key={t.id} className={`pointer-events-auto flex items-start gap-3 rounded-xl border border-chalk-300 border-l-4 bg-white px-4 py-3 shadow-lift ${tone[t.kind]}`}>
              <Icon className="mt-0.5 h-5 w-5 shrink-0 text-ink" />
              <p className="flex-1 text-sm text-ink">{t.message}</p>
              <button onClick={() => dismiss(t.id)} className="text-slateink hover:text-ink" aria-label="Dismiss"><X className="h-4 w-4" /></button>
            </div>
          )
        })}
      </div>
    </ToastCtx.Provider>
  )
}

export const useToast = () => useContext(ToastCtx)
