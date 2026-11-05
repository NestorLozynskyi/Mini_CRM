import { apiRaw, del, get, patch, post, put, query } from './client'
import type {
  Client, ClientInput, ClientSummary, ClientType, Contact, Dashboard, Deal, DealInput, DeletePreview, Interaction,
  InteractionInput, Me, Stage, Tag, Task, TaskInput, TaskSummary,
} from './types'

export const auth = {
  me: () => get<Me>('/api/auth/me', { quiet401: true }),
  signIn: (email: string, password: string) => post<Me>('/api/auth/sign-in', { email, password }),
  signUp: (email: string, password: string) => post<Me>('/api/auth/sign-up', { email, password }),
  demo: () => post<Me>('/api/auth/demo'),
  signOut: () => post<void>('/api/auth/sign-out'),
}

export interface ClientQuery {
  q?: string
  type?: ClientType | 'all'
  tag?: string[]
}

export const clients = {
  list: (f: ClientQuery = {}) => get<ClientSummary[]>('/api/clients' + query({ q: f.q?.trim(), type: f.type === 'all' ? undefined : f.type, tag: f.tag })),
  get: (id: number) => get<Client>(`/api/clients/${id}`),
  create: (input: ClientInput) => post<Client>('/api/clients', input),
  update: (id: number, input: ClientInput) => put<Client>(`/api/clients/${id}`, input),
  deletePreview: (id: number) => get<DeletePreview>(`/api/clients/${id}/delete-preview`),
  remove: (id: number) => del<DeletePreview>(`/api/clients/${id}`),
  addContact: (clientId: number, c: Omit<Contact, 'id'>) => post<Contact>(`/api/clients/${clientId}/contacts`, c),
  updateContact: (id: number, c: Omit<Contact, 'id'>) => put<Contact>(`/api/contacts/${id}`, c),
  removeContact: (id: number) => del<void>(`/api/contacts/${id}`),
}

export const tags = { list: () => get<Tag[]>('/api/tags') }

export interface DealQuery {
  scope?: 'active' | 'archived' | 'all'
  q?: string
  clientId?: number | null
  stage?: Stage[]
}

const dealParams = (f: DealQuery) => query({ scope: f.scope, q: f.q?.trim(), clientId: f.clientId ?? undefined, stage: f.stage })

export const deals = {
  list: (f: DealQuery = {}) => get<Deal[]>('/api/deals' + dealParams(f)),
  get: (id: number) => get<Deal>(`/api/deals/${id}`),
  create: (input: DealInput) => post<Deal>('/api/deals', input),
  update: (id: number, input: DealInput) => put<Deal>(`/api/deals/${id}`, input),
  move: (id: number, stage: Stage, lostReason?: string | null) => patch<Deal>(`/api/deals/${id}/stage`, { stage, lostReason: lostReason ?? null }),
  archive: (id: number) => post<Deal>(`/api/deals/${id}/archive`),
  restore: (id: number) => post<Deal>(`/api/deals/${id}/restore`),
}

export const interactions = {
  list: (f: { clientId?: number; dealId?: number; limit?: number } = {}) => get<Interaction[]>('/api/interactions' + query(f)),
  create: (input: InteractionInput) => post<Interaction>('/api/interactions', input),
  update: (id: number, input: InteractionInput) => put<Interaction>(`/api/interactions/${id}`, input),
  remove: (id: number) => del<void>(`/api/interactions/${id}`),
}

export const tasks = {
  list: (f: { clientId?: number; dealId?: number } = {}) => get<Task[]>('/api/tasks' + query(f)),
  summary: () => get<TaskSummary>('/api/tasks/summary'),
  create: (input: TaskInput) => post<Task>('/api/tasks', input),
  update: (id: number, input: TaskInput) => put<Task>(`/api/tasks/${id}`, input),
  setDone: (id: number, done: boolean) => patch<Task>(`/api/tasks/${id}/done`, { done }),
  remove: (id: number) => del<void>(`/api/tasks/${id}`),
}

export const dashboard = { get: () => get<Dashboard>('/api/dashboard') }

/** Завантажує CSV із сервера й зберігає файлом; повертає кількість рядків. */
export async function downloadCsv(kind: 'clients' | 'deals', params: Record<string, string | number | undefined | string[]> = {}): Promise<number> {
  const res = await apiRaw(`/api/export/${kind}.csv` + query(params))
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${kind}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  return Number(res.headers.get('X-Row-Count') ?? 0)
}

export const account = {
  /** Змінює лише позначення валюти акаунта; суми не конвертуються. */
  changeCurrency: (currency: string) => put<Me>('/api/account/currency', { currency }),
}
