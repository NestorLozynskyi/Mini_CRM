// Спільні помічники екранів угод (deals-board / deals-list / deal-detail).
import { downloadCsv, type DealQuery } from '../../lib/api/crm'
import type { Deal } from '../../lib/api/types'
import { diffDays, plural, todayIso } from '../../lib/format'
import { isOpenStage } from '../../lib/stages'
import { confirm } from '../../lib/ui/confirm.svelte'
import { toast } from '../../lib/ui/toast.svelte'

/** Відкрита угода з очікуваною датою закриття в минулому. */
export function isOverdue(d: Pick<Deal, 'expectedClose' | 'stage'>): boolean {
  return !!d.expectedClose && isOpenStage(d.stage) && diffDays(d.expectedClose) < 0
}

export function sumAmount(list: Deal[]): number {
  return list.reduce((s, d) => s + (Number(d.amount) || 0), 0)
}

/** Порядок як у API: останні зміни етапу першими. */
export function byStageChange(a: Deal, b: Deal): number {
  return Date.parse(b.stageChangedAt) - Date.parse(a.stageChangedAt) || b.id - a.id
}

/** Дата останнього оновлення угоди (зміна запису або остання взаємодія) — локальна дата YYYY-MM-DD. */
export function updatedOn(d: Deal): string {
  const own = todayIso(new Date(d.updatedAt))
  return d.lastActivity && d.lastActivity > own ? d.lastActivity : own
}

export function confirmArchive(): Promise<boolean> {
  return confirm({
    title: 'Archive this deal?',
    text: 'It will be hidden from the board. You can restore it from Archived deals.',
    confirmLabel: 'Archive deal',
    danger: false,
    icon: 'archive',
  })
}

/** Експорт CSV на сервері з тими ж фільтрами, що й на екрані. */
export async function exportDeals(f: DealQuery) {
  try {
    const n = await downloadCsv('deals', { scope: f.scope, q: f.q?.trim() || undefined, clientId: f.clientId ?? undefined, stage: f.stage })
    toast({ type: 'success', text: `Exported ${plural(n, 'deal')} to deals.csv` })
  } catch {
    toast({ type: 'error', text: 'Couldn’t export deals. Check your connection and try again.' })
  }
}

export function matchesQuery(d: Deal, q: string): boolean {
  const s = q.trim().toLowerCase()
  return !s || d.title.toLowerCase().includes(s) || d.clientName.toLowerCase().includes(s)
}

/** Помилка зміни етапу: зрозуміле повідомлення замість технічного тексту відповіді. */
export function moveFailed() {
  toast({ type: 'error', text: 'Couldn’t move the deal. Check your connection and try again.' })
}
