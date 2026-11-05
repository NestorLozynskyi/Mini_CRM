import { describe, expect, it } from 'vitest'
import { match } from './router.svelte'

describe('route matching', () => {
  it('matches static and parameterised paths', () => {
    expect(match('/clients', '/clients')).toEqual({})
    expect(match('/clients/:id', '/clients/42')).toEqual({ id: '42' })
    expect(match('/deals/:id', '/deals/list')).toEqual({ id: 'list' })
    expect(match('/clients/:id', '/clients')).toBeNull()
    expect(match('/', '/')).toEqual({})
  })
})
