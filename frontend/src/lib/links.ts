import { clients as clientsApi, deals as dealsApi } from './api/crm'
import type { ClientSummary, Deal } from './api/types'

export interface LinkOptions {
  clients: ClientSummary[]
  deals: Deal[]
}

/** Варіанти для поля «Linked to» (клієнти й неархівні угоди). */
export async function loadLinkOptions(): Promise<LinkOptions> {
  const [c, d] = await Promise.all([clientsApi.list(), dealsApi.list({ scope: 'active' })])
  return { clients: c, deals: d }
}

export function linkValue(o: { clientId?: number | null; dealId?: number | null }): string {
  if (o.dealId) return `deal:${o.dealId}`
  if (o.clientId) return `client:${o.clientId}`
  return ''
}

export function parseLink(v: string): { clientId: number | null; dealId: number | null } {
  const [kind, id] = v.split(':')
  return { clientId: kind === 'client' ? Number(id) : null, dealId: kind === 'deal' ? Number(id) : null }
}
