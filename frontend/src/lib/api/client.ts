import { timeZone } from '../format'

/** Помилка поля з відповіді валідації бекенду. */
export interface FieldViolation {
  field: string
  message: string
}

/** RFC 9457 problem+json — єдиний формат помилок API бекенду. */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  code: string
  errors?: FieldViolation[]
}

export class ApiError extends Error {
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`)
    this.name = 'ApiError'
    this.problem = problem
  }

  get status() {
    return this.problem.status
  }

  get code() {
    return this.problem.code
  }

  get isValidation(): boolean {
    return this.problem.code === 'validation_failed'
  }

  /** Перше повідомлення для кожного поля: `{ email: 'Enter a valid email address' }`. */
  fieldErrors(): Record<string, string> {
    const result: Record<string, string> = {}
    for (const { field, message } of this.problem.errors ?? []) result[field] ??= message
    return result
  }
}

type UnauthorizedHandler = (code: string) => void
let onUnauthorized: UnauthorizedHandler = () => {}

/** Глобальна реакція на 401 (сесія скінчилась / демо прострочене). */
export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  onUnauthorized = handler
}

function readCookie(name: string): string | null {
  const m = document.cookie.match(new RegExp('(?:^|; )' + name.replace(/[-.]/g, '\\$&') + '=([^;]*)'))
  return m ? decodeURIComponent(m[1]) : null
}

function isProblem(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null && 'status' in value && 'code' in value
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: unknown = null
  try {
    body = await response.json()
  } catch {
    // Відповідь не JSON (наприклад, 502 від проксі) — формуємо помилку зі статусу.
  }
  if (isProblem(body)) return new ApiError(body)
  return new ApiError({
    status: response.status,
    code: response.status >= 500 ? 'internal_error' : `http_${response.status}`,
    title: response.statusText || 'Request failed',
    detail: `Server responded with HTTP ${response.status}`,
  })
}

let csrfReady: Promise<void> | null = null

/** Отримує cookie XSRF-TOKEN (Spring Security, SPA-режим) перед першим запитом, що змінює стан. */
export function ensureCsrf(): Promise<void> {
  if (readCookie('XSRF-TOKEN')) return Promise.resolve()
  csrfReady ??= fetch('/api/auth/csrf', { credentials: 'same-origin', headers: { 'X-Time-Zone': timeZone() } })
    .then(() => undefined)
    .finally(() => {
      csrfReady = null
    })
  return csrfReady
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  /** Не викликати глобальний обробник 401 (наприклад, для перевірки сесії при старті). */
  quiet401?: boolean
}

export async function apiRaw(path: string, options: RequestOptions = {}): Promise<Response> {
  const { body, quiet401, ...init } = options
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  headers.set('X-Time-Zone', timeZone())
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  if (method !== 'GET' && method !== 'HEAD') {
    await ensureCsrf()
    const token = readCookie('XSRF-TOKEN')
    if (token) headers.set('X-XSRF-TOKEN', token)
  }

  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (cause) {
    throw new ApiError({
      status: 0,
      code: 'network_error',
      title: 'Network error',
      detail: cause instanceof Error ? cause.message : 'Server is unreachable',
    })
  }

  if (!response.ok) {
    const error = await toApiError(response)
    if (error.status === 401 && !quiet401) onUnauthorized(error.code)
    throw error
  }
  return response
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await apiRaw(path, options)
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const get = <T>(path: string, options?: RequestOptions) => api<T>(path, options)
export const post = <T>(path: string, body?: unknown) => api<T>(path, { method: 'POST', body: body ?? {} })
export const put = <T>(path: string, body: unknown) => api<T>(path, { method: 'PUT', body })
export const patch = <T>(path: string, body: unknown) => api<T>(path, { method: 'PATCH', body })
export const del = <T>(path: string) => api<T>(path, { method: 'DELETE' })

/** Рядок запиту без порожніх значень; масиви — повтором параметра. */
export function query(params: Record<string, string | number | boolean | null | undefined | (string | number)[]>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === null || v === undefined || v === '') continue
    if (Array.isArray(v)) v.forEach((x) => sp.append(k, String(x)))
    else sp.set(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}
