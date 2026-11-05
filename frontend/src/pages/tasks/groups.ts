import type { Task } from '../../lib/api/types'
import { addDaysIso, dateShort, dayNum, diffDays, todayIso } from '../../lib/format'
import { taskStatus, type TaskStatus } from '../../lib/tasks'

/** Вкладки екрана (порядок як у макеті). У URL «Completed» — це `?tab=done`. */
export type TaskView = 'overdue' | 'today' | 'upcoming' | 'completed'
export const VIEWS: TaskView[] = ['overdue', 'today', 'upcoming', 'completed']

export function viewFromParam(p: string | null): TaskView {
  if (p === 'done' || p === 'completed') return 'completed'
  return p === 'overdue' || p === 'upcoming' ? p : 'today'
}
export function viewToParam(v: TaskView): string | null {
  return v === 'today' ? null : v === 'completed' ? 'done' : v
}

export type Counts = Record<TaskView, number>

export function countTasks(list: Task[], today: string): Counts {
  const c: Counts = { overdue: 0, today: 0, upcoming: 0, completed: 0 }
  for (const t of list) {
    const s = taskStatus(t, today)
    c[s === 'done' ? 'completed' : s]++
  }
  return c
}

/** Вкладка, у якій живе завдання з таким статусом. */
export function viewOf(s: TaskStatus): TaskView {
  return s === 'done' ? 'completed' : s
}

/** Локальна (браузерна) дата виконання. */
export function completedDay(t: Pick<Task, 'completedAt'>): string | null {
  return t.completedAt ? todayIso(new Date(t.completedAt)) : null
}

/**
 * Завдання, перемкнуті в поточній вкладці, лишаються на місці (перекресленими) до зміни вкладки —
 * як у макеті, щоб Undo мав сенс. Тут зберігаємо статус і дату виконання на момент перемикання.
 */
export interface Sticky {
  status: TaskStatus
  completedAt: string | null
}

export interface Group {
  key: 'overdue' | 'today' | 'tomorrow' | 'week' | 'later' | 'upcoming' | 'completed'
  title: string
  icon: string
  range?: string
  danger?: boolean
  /** «View all» у заголовку (лише група Overdue у вкладці Today). */
  viewAll?: boolean
  tasks: Task[]
  /** Тексти порожнього стану; відсутні — група без завдань не показується. */
  empty?: { icon: string; title: string; text: string }
}

export const EMPTY = {
  today: { icon: 'calendar-check', title: 'Nothing due today', text: 'Enjoy the breathing room — or plan ahead in Upcoming.' },
  overdue: { icon: 'check-circle', title: 'No overdue tasks — you’re all caught up', text: 'Tasks that pass their due date without being completed will show up here.' },
  upcoming: { icon: 'calendar', title: 'Nothing coming up', text: 'Tasks due after today will be grouped here by date.' },
  completed: { icon: 'check-square', title: 'No completed tasks yet', text: 'Tick a task’s checkbox to mark it done. Completed tasks are kept here.' },
}

const SHORT_DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
export function shortWeekday(iso: string): string {
  return SHORT_DAYS[(((dayNum(iso) + 4) % 7) + 7) % 7]
}

const byDue = (a: Task, b: Task) => dayNum(a.due) - dayNum(b.due) || a.id - b.id

/** Групи для вкладки. `sticky` — завдання, перемкнуті в цій вкладці (id → стан до перемикання). */
export function buildGroups(view: TaskView, list: Task[], today: string, sticky: Record<number, Sticky> = {}): Group[] {
  const status = (t: Task) => sticky[t.id]?.status ?? taskStatus(t, today)
  const of = (s: TaskStatus) => list.filter((t) => status(t) === s)

  if (view === 'today') {
    const out: Group[] = []
    const overdue = of('overdue').sort(byDue)
    if (overdue.length) out.push({ key: 'overdue', title: 'Overdue', icon: 'alert-triangle', danger: true, viewAll: true, tasks: overdue })
    out.push({ key: 'today', title: 'Due today', icon: 'clock', range: `${shortWeekday(today)}, ${dateShort(today)}`, tasks: of('today').sort(byDue), empty: EMPTY.today })
    return out
  }
  if (view === 'overdue') {
    return [{ key: 'overdue', title: 'Overdue', icon: 'alert-triangle', danger: true, tasks: of('overdue').sort(byDue), empty: EMPTY.overdue }]
  }
  if (view === 'upcoming') {
    const up = of('upcoming').sort(byDue)
    if (!up.length) return [{ key: 'upcoming', title: 'Upcoming', icon: 'calendar', tasks: [], empty: EMPTY.upcoming }]
    const d = (t: Task) => diffDays(t.due, today)
    const tomorrow = addDaysIso(today, 1)
    const out: Group[] = [
      { key: 'tomorrow', title: 'Tomorrow', icon: 'calendar', range: `${shortWeekday(tomorrow)}, ${dateShort(tomorrow)}`, tasks: up.filter((t) => d(t) <= 1) },
      { key: 'week', title: 'This week', icon: 'calendar', range: `${dateShort(addDaysIso(today, 2))} – ${dateShort(addDaysIso(today, 7))}`, tasks: up.filter((t) => d(t) >= 2 && d(t) <= 7) },
      { key: 'later', title: 'Later', icon: 'calendar', range: `From ${dateShort(addDaysIso(today, 8))}`, tasks: up.filter((t) => d(t) > 7) },
    ]
    return out.filter((g) => g.tasks.length)
  }
  const doneAt = (t: Task) => (sticky[t.id] ? sticky[t.id].completedAt : t.completedAt) ?? ''
  const done = of('done').sort((a, b) => (doneAt(a) < doneAt(b) ? 1 : doneAt(a) > doneAt(b) ? -1 : dayNum(b.due) - dayNum(a.due) || b.id - a.id))
  return [{ key: 'completed', title: 'Completed', icon: 'check-circle', tasks: done, empty: EMPTY.completed }]
}
