export interface ToastItem {
  id: number
  type: 'success' | 'error'
  text: string
  action?: { label: string; onClick: () => void }
  duration?: number
  leaving?: boolean
}

export const toasts = $state<ToastItem[]>([])
let seq = 0

export function toast(t: Omit<ToastItem, 'id'>) {
  const item: ToastItem = { ...t, id: ++seq }
  toasts.push(item)
  return item.id
}

export function dismissToast(id: number) {
  const t = toasts.find((x) => x.id === id)
  if (!t || t.leaving) return
  t.leaving = true
  setTimeout(() => {
    const i = toasts.findIndex((x) => x.id === id)
    if (i >= 0) toasts.splice(i, 1)
  }, 200)
}

/** Тост помилки: зрозумілі повідомлення сервера (4xx) показуємо як є, мережеві й серверні збої (0, 5xx) — загальним текстом. */
export function errorToast(e: unknown, fallback = 'Couldn’t save changes. Check your connection and try again.') {
  const status = e instanceof Error && 'problem' in e ? (e as { problem: { status: number } }).problem.status : 0
  const msg = status >= 400 && status < 500 && status !== 401 ? (e as Error).message : ''
  toast({ type: 'error', text: msg || fallback })
}
