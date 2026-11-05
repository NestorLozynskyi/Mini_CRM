import { afterEach, describe, expect, it } from 'vitest'
import { CURRENCIES, POPULAR, currencyActions, currencyGroups, currencyName } from './currencies'
import { currencySymbol, getCurrency, money, moneyShort, setCurrency } from './format'

afterEach(() => setCurrency('USD'))

// Нерозривний пробіл між кодом і числом (як дає Intl) нормалізуємо для читабельності перевірок.
const n = (s: string) => s.replace(/ /g, ' ')

describe('money in the account currency (en-US, no minor units)', () => {
  it.each([
    ['USD', '$12,400', '$12.4k', '$'],
    ['EUR', '€12,400', '€12.4k', '€'],
    ['UAH', 'UAH 12,400', 'UAH 12.4k', 'UAH'],
    ['JPY', '¥12,400', '¥12.4k', '¥'],
    ['GBP', '£12,400', '£12.4k', '£'],
    ['CAD', 'CA$12,400', 'CA$12.4k', 'CA$'],
  ])('%s', (code, full, short, symbol) => {
    setCurrency(code)
    expect(getCurrency()).toBe(code)
    expect(n(money(12400))).toBe(full)
    expect(n(moneyShort(12400))).toBe(short)
    expect(currencySymbol()).toBe(symbol)
  })

  it('never shows minor units and keeps the stored number unchanged', () => {
    setCurrency('JPY')
    expect(n(money(1999.6))).toBe('¥2,000')
    setCurrency('UAH')
    expect(n(money(0))).toBe('UAH 0')
    expect(n(moneyShort(2_500_000))).toBe('UAH 2.5M')
  })

  it('can format in an explicit currency without changing the account one (Settings preview)', () => {
    setCurrency('USD')
    expect(n(money(18500, 'EUR'))).toBe('€18,500')
    expect(currencySymbol('PLN')).toBe('PLN')
    expect(getCurrency()).toBe('USD')
  })
})

describe('currency list', () => {
  it('has the active ISO 4217 codes with names and the popular group first', () => {
    expect(CURRENCIES).toHaveLength(155)
    expect(currencyName('UAH')).toBe('Ukrainian Hryvnia')
    const [popular, all] = currencyGroups('')
    expect(popular).toEqual({ label: 'Popular', codes: POPULAR })
    expect(all.codes).toHaveLength(155 - POPULAR.length)
    expect(all.codes).not.toContain('USD')
  })

  it('searches by code and by name, exact code first, empty for no matches', () => {
    expect(currencyGroups('eur')[0].codes[0]).toBe('EUR')
    expect(currencyGroups('hryvnia')[0].codes).toEqual(['UAH'])
    expect(currencyGroups('  Yen ')[0].codes).toEqual(['JPY'])
    expect(currencyGroups('bitcoin')).toEqual([])
  })
})

describe('Save / Cancel availability', () => {
  it('both are disabled while the selection equals the saved currency', () => {
    expect(currencyActions('USD', 'USD')).toEqual({ dirty: false, canSave: false, canCancel: false })
  })
  it('both are enabled once a different currency is selected', () => {
    expect(currencyActions('EUR', 'USD')).toEqual({ dirty: true, canSave: true, canCancel: true })
  })
  it('both are disabled while saving', () => {
    expect(currencyActions('EUR', 'USD', true)).toEqual({ dirty: true, canSave: false, canCancel: false })
  })
})
