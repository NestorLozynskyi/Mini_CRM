import { describe, expect, it } from 'vitest'
import { addDaysIso, date, dateShort, diffDays, money, moneyShort, plural, relDate, setCurrency, todayIso, weekday } from './format'

describe('format', () => {
  it('formats money like the mockups', () => {
    setCurrency('USD')
    expect(money(12400)).toBe('$12,400')
    expect(money(0)).toBe('$0')
    expect(moneyShort(12400)).toBe('$12.4k')
    expect(moneyShort(2_000_000)).toBe('$2M')
  })

  it('formats dates and relative days against a given today', () => {
    expect(date('2026-11-04')).toBe('Nov 4, 2026')
    expect(dateShort('2026-11-04')).toBe('Nov 4')
    expect(weekday('2026-11-04')).toBe('Wednesday')
    const today = '2026-11-04'
    expect(relDate('2026-11-04', today)).toBe('Today')
    expect(relDate('2026-11-05', today)).toBe('Tomorrow')
    expect(relDate('2026-11-03', today)).toBe('Yesterday')
    expect(relDate('2026-11-07', today)).toBe('In 3 days')
    expect(relDate('2026-11-01', today)).toBe('3 days ago')
    expect(diffDays('2026-12-01', '2026-11-30')).toBe(1)
    expect(addDaysIso('2026-12-31', 1)).toBe('2027-01-01')
  })

  it('uses the browser local date for today', () => {
    expect(todayIso(new Date(2026, 10, 4, 23, 59))).toBe('2026-11-04')
    expect(todayIso(new Date(2026, 0, 1, 0, 0))).toBe('2026-01-01')
  })

  it('pluralises', () => {
    expect(plural(1, 'client')).toBe('1 client')
    expect(plural(3, 'company', 'companies')).toBe('3 companies')
  })
})
