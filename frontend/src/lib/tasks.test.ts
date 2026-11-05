import { describe, expect, it } from 'vitest'
import type { Task } from './api/types'
import { sortTasks, taskStatus } from './tasks'

const t = (id: number, due: string, done = false) => ({ id, due, done, title: `t${id}` }) as Task

describe('task status', () => {
  const today = '2026-11-04'
  it('classifies relative to local today', () => {
    expect(taskStatus(t(1, '2026-11-03'), today)).toBe('overdue')
    expect(taskStatus(t(1, '2026-11-04'), today)).toBe('today')
    expect(taskStatus(t(1, '2026-11-05'), today)).toBe('upcoming')
    expect(taskStatus(t(1, '2026-11-01', true), today)).toBe('done')
  })
  it('sorts open by due date, then done newest first', () => {
    const sorted = sortTasks([t(1, '2026-11-09'), t(2, '2026-10-01', true), t(3, '2026-11-02'), t(4, '2026-11-01', true)])
    expect(sorted.map((x) => x.id)).toEqual([3, 1, 4, 2])
  })
})
