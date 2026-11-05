import { render, screen, waitFor } from '@testing-library/svelte'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ClientDeleteDialog from './ClientDeleteDialog.svelte'
import TaskList from './TaskList.svelte'
import { toasts } from './toast.svelte'

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

afterEach(() => {
  vi.unstubAllGlobals()
  toasts.splice(0)
})

describe('ClientDeleteDialog', () => {
  it('lists linked records with counts from the server and deletes on confirm', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/delete-preview')) return json(200, { contacts: 2, deals: 1, archivedDeals: 1, interactions: 3, tasks: 2 })
      if (init?.method === 'DELETE') return json(200, { contacts: 2, deals: 1, archivedDeals: 1, interactions: 3, tasks: 2 })
      return json(200, [])
    })
    vi.stubGlobal('fetch', fetchMock)
    const ondeleted = vi.fn()
    render(ClientDeleteDialog, { client: { id: 7, name: 'Acme' }, ondeleted, onclose: () => {} })

    expect(await screen.findByText('2 deals')).toBeInTheDocument()
    expect(screen.getByText('(incl. 1 archived)', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('3 interactions')).toBeInTheDocument()
    expect(screen.getByText('2 tasks')).toBeInTheDocument()
    expect(screen.getByText('2 contact persons')).toBeInTheDocument()
    expect(screen.getByText(/can’t be undone/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Delete client/ }))
    await waitFor(() => expect(ondeleted).toHaveBeenCalled())
    expect(fetchMock.mock.calls.some(([u, i]) => u === '/api/clients/7' && i?.method === 'DELETE')).toBe(true)
    expect(toasts[0].text).toBe('“Acme” and its linked records were deleted')
  })
})

describe('TaskList', () => {
  it('completes a task on the server and offers Undo', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    const fetchMock = vi.fn(async (_url: string, _init?: RequestInit) => json(200, {}))
    vi.stubGlobal('fetch', fetchMock)
    const onchange = vi.fn()
    const task = { id: 3, title: 'Call Jenna', due: '2000-01-01', done: false, completedAt: null, clientId: 1, clientName: 'Copperleaf', clientType: 'company', dealId: null, dealTitle: null } as const
    render(TaskList, { tasks: [task], showLink: true, onchange })

    expect(screen.getByText('Overdue')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Copperleaf/ })).toHaveAttribute('href', '/clients/1')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Mark as complete: Call Jenna' }))
    await waitFor(() => expect(onchange).toHaveBeenCalled())
    const call = fetchMock.mock.calls.find(([u]) => u === '/api/tasks/3/done')!
    expect(call[1]?.method).toBe('PATCH')
    expect(call[1]?.body).toBe('{"done":true}')
    expect(toasts[0]).toMatchObject({ text: 'Task completed', action: { label: 'Undo' } })
  })
})
