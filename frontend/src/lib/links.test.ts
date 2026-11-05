import { describe, expect, it } from 'vitest'
import { linkValue, parseLink } from './links'

describe('link select values', () => {
  it('round-trips client and deal links', () => {
    expect(linkValue({ dealId: 5, clientId: 2 })).toBe('deal:5')
    expect(linkValue({ clientId: 2 })).toBe('client:2')
    expect(linkValue({})).toBe('')
    expect(parseLink('deal:5')).toEqual({ clientId: null, dealId: 5 })
    expect(parseLink('client:2')).toEqual({ clientId: 2, dealId: null })
  })
})
