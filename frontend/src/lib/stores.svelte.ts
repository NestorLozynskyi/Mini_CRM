import { tags as tagsApi } from './api/crm'
import type { Tag } from './api/types'

/** Теги акаунта з кольорами (для міток і фільтрів). */
export const tagStore = $state<{ list: Tag[] }>({ list: [] })

export async function refreshTags() {
  try {
    tagStore.list = await tagsApi.list()
  } catch {
    // не критично для відображення
  }
}

export function tagColor(name: string): string | null {
  return tagStore.list.find((t) => t.name.toLowerCase() === name.toLowerCase())?.color ?? null
}
