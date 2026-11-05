import { render, screen, waitFor } from '@testing-library/svelte'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getCurrency, setCurrency } from '../lib/format'
import { session } from '../lib/session.svelte'
import { toasts } from '../lib/ui/toast.svelte'
import Settings from './Settings.svelte'

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const deal = (id: number, amount: number, extra: object = {}) => ({ id, amount, stage: 'lead', archived: false, ...extra })

describe('Settings › currency', () => {
  let fail = false
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    session.status = 'authed'
    session.me = { email: 'alex@example.com', demo: false, currency: 'USD', demoExpiresAt: null }
    setCurrency('USD')
    fail = false
    fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.startsWith('/api/deals')) return json(200, [deal(1, 18500), deal(2, 4000), deal(3, 900, { archived: true }), deal(4, 99999, { stage: 'won' })])
      if (url === '/api/account/currency') {
        if (fail) return json(503, { status: 503, code: 'internal_error' })
        const { currency } = JSON.parse(String(init?.body))
        return json(200, { email: 'alex@example.com', demo: false, currency, demoExpiresAt: null })
      }
      return json(200, { dueCount: 0, todayOpen: 0, todayDone: 0, overdue: 0 })
    })
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    toasts.splice(0)
    setCurrency('USD')
  })

  const save = () => screen.getByRole('button', { name: 'Save' })
  const cancel = () => document.getElementById('cur-reset') as HTMLButtonElement

  it('enables Save and Cancel only while the selection differs; Cancel restores the saved currency', async () => {
    render(Settings)
    expect(save()).toBeDisabled()
    expect(cancel()).toBeDisabled()
    await userEvent.click(screen.getByRole('radio', { name: /EUR/ }))
    expect(save()).toBeEnabled()
    expect(cancel()).toBeEnabled()
    expect(screen.getByText(/Not saved yet/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('radio', { name: /USD/ }))
    expect(save()).toBeDisabled()
    await userEvent.click(screen.getByRole('radio', { name: /UAH/ }))
    await userEvent.click(cancel())
    expect(save()).toBeDisabled()
    expect(cancel()).toBeDisabled()
    expect((screen.getByRole('radio', { name: /USD/ }) as HTMLInputElement).checked).toBe(true)
  })

  it('confirms with real deal counts, saves on the server and applies the currency app-wide', async () => {
    render(Settings)
    await waitFor(() => expect(screen.getByText('$18,500')).toBeInTheDocument())
    await userEvent.click(screen.getByRole('radio', { name: /EUR/ }))
    await userEvent.click(save())
    expect(await screen.findByText('Change currency to EUR?')).toBeInTheDocument()
    expect(screen.getByText(/All 4 deals, including archived ones, will be shown in EUR/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Change to EUR' }))
    await waitFor(() => expect(getCurrency()).toBe('EUR'))
    const call = fetchMock.mock.calls.find(([u]) => u === '/api/account/currency')!
    expect(call[1]?.method).toBe('PUT')
    expect(call[1]?.body).toBe('{"currency":"EUR"}')
    expect(session.me?.currency).toBe('EUR')
    expect(toasts[0].text).toBe('Currency changed to EUR — Euro. Amounts now show in EUR.')
    await waitFor(() => expect(save()).toBeDisabled())
  })

  it('keeps the selection and offers Retry when saving fails', async () => {
    fail = true
    render(Settings)
    await userEvent.click(screen.getByRole('radio', { name: /GBP/ }))
    await userEvent.click(save())
    await userEvent.click(await screen.findByRole('button', { name: 'Change to GBP' }))
    expect(await screen.findByText('Couldn’t save the currency')).toBeInTheDocument()
    expect(getCurrency()).toBe('USD')
    expect((screen.getByRole('radio', { name: /GBP/ }) as HTMLInputElement).checked).toBe(true)
    expect(save()).toBeEnabled()
    fail = false
    // Діалог закривається з анімацією (120 мс); поки він відкритий, сторінка під ним недоступна.
    await waitFor(() => expect(screen.queryByText('Change currency to GBP?')).toBeNull())
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Change to GBP' }))
    await waitFor(() => expect(getCurrency()).toBe('GBP'))
  })

  it('shows the empty search state with Clear search', async () => {
    render(Settings)
    await userEvent.type(screen.getByLabelText('Search currencies'), 'bitcoin')
    expect(screen.getByText('No currencies match “bitcoin”')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }))
    expect(screen.getByText('155 currencies')).toBeInTheDocument()
  })
})
