// Форматування як у макетах (assets/app.js): $12,400 · Nov 4, 2026 · Today / In 3 days.
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

let currency = 'USD'
const formats = new Map<string, Intl.NumberFormat>()

/** Валюта акаунта (ISO 4217). Суми не конвертуються — змінюється лише позначення. */
export function setCurrency(code: string) {
  currency = code || 'USD'
}

export function getCurrency(): string {
  return currency
}

/** en-US, без копійок; для валют без однозначного символу — ISO-код (assets/app.js макета). */
function formatter(code: string, short = false): Intl.NumberFormat {
  const key = code + (short ? ':s' : '')
  let f = formats.get(key)
  if (!f) {
    const opts: Intl.NumberFormatOptions = { style: 'currency', currency: code, minimumFractionDigits: 0, maximumFractionDigits: short ? 1 : 0 }
    if (short) opts.notation = 'compact'
    try {
      f = new Intl.NumberFormat('en-US', opts)
    } catch {
      f = new Intl.NumberFormat('en-US', { ...opts, currency: 'USD' })
    }
    formats.set(key, f)
  }
  return f
}

/** €12,400 · UAH 12,400 · ¥12,400 у вказаній валюті (за замовчуванням — валюта акаунта). */
export function money(n: number | null | undefined, code: string = currency): string {
  return formatter(code).format(Math.round(Number(n) || 0))
}

/** Коротка сума: €12.4k · UAH 1.2M. */
export function moneyShort(n: number, code: string = currency): string {
  return formatter(code, true).format(Number(n) || 0).replace(/K$/, 'k')
}

/** Символ валюти: "$", "€", "UAH", "CA$". */
export function currencySymbol(code: string = currency): string {
  return formatter(code).formatToParts(0).find((p) => p.type === 'currency')?.value ?? code
}

function parts(iso: string) {
  const s = String(iso).slice(0, 10).split('-')
  return { y: +s[0], m: +s[1], d: +s[2] }
}

export function dayNum(iso: string): number {
  const p = parts(iso)
  return Math.round(Date.UTC(p.y, p.m - 1, p.d) / 864e5)
}

const pad = (n: number) => (n < 10 ? '0' : '') + n

/** Локальна дата браузера у форматі YYYY-MM-DD (межі «сьогодні» — за поясом браузера). */
export function todayIso(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

export function addDaysIso(iso: string, days: number): string {
  const d = new Date(Date.UTC(parts(iso).y, parts(iso).m - 1, parts(iso).d) + days * 864e5)
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`
}

export function diffDays(iso: string, today: string = todayIso()): number {
  return dayNum(iso) - dayNum(today)
}

export function date(iso?: string | null): string {
  if (!iso) return ''
  const p = parts(iso)
  return `${MONTHS[p.m - 1]} ${p.d}, ${p.y}`
}

export function dateShort(iso?: string | null): string {
  if (!iso) return ''
  const p = parts(iso)
  return `${MONTHS[p.m - 1]} ${p.d}`
}

export function relDate(iso: string, today: string = todayIso()): string {
  const d = diffDays(iso, today)
  if (d === 0) return 'Today'
  if (d === 1) return 'Tomorrow'
  if (d === -1) return 'Yesterday'
  return d > 0 ? `In ${d} days` : `${-d} days ago`
}

export function weekday(iso: string): string {
  const p = parts(iso)
  return WEEKDAYS[new Date(Date.UTC(p.y, p.m - 1, p.d)).getUTCDay()]
}

export function plural(n: number, one: string, many?: string): string {
  return `${n} ${n === 1 ? one : (many ?? one + 's')}`
}

export function timeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}
