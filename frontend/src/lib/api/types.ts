export type ClientType = 'company' | 'person'
export type Stage = 'lead' | 'contacted' | 'proposal' | 'negotiation' | 'won' | 'lost'
export type InteractionType = 'call' | 'meeting' | 'email' | 'note'

export interface Me {
  email: string | null
  demo: boolean
  currency: string
  demoExpiresAt: string | null
}

export interface Tag {
  name: string
  color: string
}

export interface Contact {
  id: number
  name: string
  phone: string | null
  email: string | null
}

export interface ClientSummary {
  id: number
  type: ClientType
  name: string
  phone: string | null
  email: string | null
  tags: string[]
  contactCount: number
  openDealCount: number
  openDealValue: number
  nextTaskTitle: string | null
  nextTaskDue: string | null
  lastInteraction: string | null
  createdAt: string
}

export interface Client {
  id: number
  type: ClientType
  name: string
  phone: string | null
  email: string | null
  address: string | null
  website: string | null
  notes: string | null
  tags: string[]
  contacts: Contact[]
  createdAt: string
}

export interface ClientInput {
  type: ClientType
  name: string
  phone?: string
  email?: string
  address?: string
  website?: string
  notes?: string
  tags: string[]
}

export interface DeletePreview {
  contacts: number
  deals: number
  archivedDeals: number
  interactions: number
  tasks: number
}

export interface Deal {
  id: number
  title: string
  clientId: number
  clientName: string
  clientType: ClientType
  amount: number
  stage: Stage
  expectedClose: string | null
  lostReason: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
  stageChangedAt: string
  lastActivity: string | null
}

export interface DealInput {
  title: string
  clientId: number | null
  amount: number | null
  stage: Stage
  expectedClose: string | null
  lostReason: string | null
}

export interface Interaction {
  id: number
  type: InteractionType
  date: string
  description: string
  clientId: number
  clientName: string
  clientType: ClientType
  dealId: number | null
  dealTitle: string | null
  createdAt: string
}

export interface InteractionInput {
  type: InteractionType
  date: string
  description: string
  clientId?: number | null
  dealId?: number | null
}

export interface Task {
  id: number
  title: string
  due: string
  done: boolean
  completedAt: string | null
  clientId: number
  clientName: string
  clientType: ClientType
  dealId: number | null
  dealTitle: string | null
}

export interface TaskInput {
  title: string
  due: string
  clientId?: number | null
  dealId?: number | null
}

export interface TaskSummary {
  dueCount: number
  todayOpen: number
  todayDone: number
  overdue: number
}

export interface StageStat {
  stage: Stage
  count: number
  amount: number
}

export interface Dashboard {
  today: string
  activeDeals: number
  activeValue: number
  boardDeals: number
  wonValue: number
  stages: StageStat[]
  tasks: TaskSummary
  todayTasks: Task[]
  recentInteractions: Interaction[]
  empty: boolean
}
