import { ApiError, setUnauthorizedHandler } from './api/client'
import { auth, tasks } from './api/crm'
import type { Me } from './api/types'
import { setCurrency } from './format'
import { navigate } from './router.svelte'

type Status = 'loading' | 'anon' | 'authed' | 'demoExpired'

export const session = $state<{ status: Status; me: Me | null }>({ status: 'loading', me: null })

/** Лічильник у навігації: невиконані завдання на сьогодні та прострочені. */
export const badges = $state({ tasksDue: 0 })

export function setMe(me: Me) {
  session.me = me
  session.status = 'authed'
  setCurrency(me.currency)
  void refreshBadges()
}

/** Після збереження валюти в Settings: нове позначення для всіх сум застосунку. */
export function setAccountCurrency(code: string) {
  if (session.me) session.me = { ...session.me, currency: code }
  setCurrency(code)
}

export async function bootstrap() {
  setUnauthorizedHandler((code) => {
    if (session.status !== 'authed') return
    session.me = null
    if (code === 'demo_expired') {
      session.status = 'demoExpired'
      navigate('/demo-expired', { replace: true })
    } else {
      session.status = 'anon'
      navigate('/sign-in', { replace: true })
    }
  })
  try {
    setMe(await auth.me())
  } catch (e) {
    session.status = e instanceof ApiError && e.code === 'demo_expired' ? 'demoExpired' : 'anon'
  }
}

export async function refreshBadges() {
  if (session.status !== 'authed') return
  try {
    badges.tasksDue = (await tasks.summary()).dueCount
  } catch {
    // лічильник не критичний
  }
}

export async function signOut() {
  try {
    await auth.signOut()
  } finally {
    session.me = null
    session.status = 'anon'
    navigate('/sign-in', { replace: true })
  }
}
