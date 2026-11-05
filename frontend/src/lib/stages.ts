import type { Stage } from './api/types'

export const STAGES: { id: Stage; label: string }[] = [
  { id: 'lead', label: 'Lead' },
  { id: 'contacted', label: 'Contacted' },
  { id: 'proposal', label: 'Proposal' },
  { id: 'negotiation', label: 'Negotiation' },
  { id: 'won', label: 'Won' },
  { id: 'lost', label: 'Lost' },
]

export function stageLabel(id: string): string {
  return STAGES.find((s) => s.id === id)?.label ?? id
}

export const isOpenStage = (s: Stage) => s !== 'won' && s !== 'lost'
