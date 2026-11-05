import type { Task } from './api/types'
import { dayNum, diffDays, todayIso } from './format'

export type TaskStatus = 'done' | 'overdue' | 'today' | 'upcoming'

/** Статус завдання відносно «сьогодні» браузера (як lookup.taskStatus у макеті). */
export function taskStatus(t: Pick<Task, 'done' | 'due'>, today = todayIso()): TaskStatus {
  if (t.done) return 'done'
  const d = diffDays(t.due, today)
  return d < 0 ? 'overdue' : d === 0 ? 'today' : 'upcoming'
}

/** Активні — за дедлайном; виконані — в кінці, найновіші першими. */
export function sortTasks(list: Task[]): Task[] {
  return list.slice().sort((a, b) => {
    if (a.done !== b.done) return a.done ? 1 : -1
    return a.done ? dayNum(b.due) - dayNum(a.due) : dayNum(a.due) - dayNum(b.due) || a.id - b.id
  })
}
