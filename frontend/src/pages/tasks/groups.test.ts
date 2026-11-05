import { describe, expect, it } from 'vitest'
import type { Task } from '../../lib/api/types'
import { buildGroups, countTasks, shortWeekday, viewFromParam, viewToParam } from './groups'

const TODAY = '2026-11-05'
let seq = 0
function task(due: string, done = false, completedAt: string | null = null): Task {
  return { id: ++seq, title: 't' + seq, due, done, completedAt, clientId: 1, clientName: 'C', clientType: 'company', dealId: null, dealTitle: null }
}

describe('tasks groups', () => {
  const list = [
    task('2026-11-03'), task('2026-11-04'), task('2026-11-05'),
    task('2026-11-06'), task('2026-11-07'), task('2026-11-12'), task('2026-11-13'), task('2026-12-01'),
    task('2026-11-01', true, '2026-11-02T10:00:00Z'), task('2026-11-02', true, '2026-11-04T10:00:00Z'),
  ]

  it('maps URL params', () => {
    expect(viewFromParam(null)).toBe('today')
    expect(viewFromParam('done')).toBe('completed')
    expect(viewFromParam('overdue')).toBe('overdue')
    expect(viewFromParam('bogus')).toBe('today')
    expect(viewToParam('completed')).toBe('done')
    expect(viewToParam('today')).toBeNull()
  })

  it('counts by status', () => {
    expect(countTasks(list, TODAY)).toEqual({ overdue: 2, today: 1, upcoming: 5, completed: 2 })
  })

  it('today view shows overdue group first, then due today', () => {
    const g = buildGroups('today', list, TODAY)
    expect(g.map((x) => x.key)).toEqual(['overdue', 'today'])
    expect(g[0].tasks.map((t) => t.due)).toEqual(['2026-11-03', '2026-11-04'])
    expect(g[1].range).toBe('Thu, Nov 5')
  })

  it('upcoming grouped into tomorrow / this week (2–7 days) / later', () => {
    const g = buildGroups('upcoming', list, TODAY)
    expect(g.map((x) => [x.key, x.tasks.length, x.range])).toEqual([
      ['tomorrow', 1, 'Fri, Nov 6'],
      ['week', 2, 'Nov 7 – Nov 12'],
      ['later', 2, 'From Nov 13'],
    ])
  })

  it('empty upcoming shows a single empty group', () => {
    const g = buildGroups('upcoming', [], TODAY)
    expect(g).toHaveLength(1)
    expect(g[0].empty?.title).toBe('Nothing coming up')
  })

  it('completed sorted by completion time, newest first', () => {
    const g = buildGroups('completed', list, TODAY)
    expect(g[0].tasks.map((t) => t.due)).toEqual(['2026-11-02', '2026-11-01'])
  })

  it('sticky tasks stay in the list where they were toggled', () => {
    const t = list[2]
    const done = { ...t, done: true, completedAt: '2026-11-05T09:00:00Z' }
    const next = list.map((x) => (x.id === t.id ? done : x))
    expect(buildGroups('today', next, TODAY)[1].tasks).toHaveLength(0)
    expect(buildGroups('today', next, TODAY, { [t.id]: { status: 'today', completedAt: null } })[1].tasks).toHaveLength(1)
  })

  it('short weekday', () => {
    expect(shortWeekday('2026-11-05')).toBe('Thu')
    expect(shortWeekday('1970-01-01')).toBe('Thu')
    expect(shortWeekday('2026-11-08')).toBe('Sun')
  })
})
