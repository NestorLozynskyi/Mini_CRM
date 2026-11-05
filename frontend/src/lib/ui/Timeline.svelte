<script lang="ts">
  import type { Snippet } from 'svelte'
  import { interactions as api } from '../api/crm'
  import type { Interaction } from '../api/types'
  import { date, relDate } from '../format'
  import { confirm } from './confirm.svelte'
  import EmptyState from './EmptyState.svelte'
  import EntityChip from './EntityChip.svelte'
  import Icon from './Icon.svelte'
  import InteractionForm from './InteractionForm.svelte'
  import { errorToast, toast } from './toast.svelte'

  interface Props {
    items: Interaction[]
    showLink?: boolean
    context?: 'client' | 'deal' | ''
    emptyTitle?: string
    emptyText?: string
    emptyAction?: Snippet
    onchange?: () => void
    /** Власний текст підтвердження видалення (наприклад, з назвою клієнта в client-detail). */
    deleteText?: (it: Interaction, typeLabel: string) => string
    deleteLabel?: string
  }
  let { items, showLink = false, context = '', emptyTitle = 'No interactions yet', emptyText = 'Log calls, meetings, emails and notes to keep the history in one place.', emptyAction, onchange, deleteText, deleteLabel = 'Delete' }: Props = $props()

  const TYPES: Record<string, { label: string; icon: string }> = {
    call: { label: 'Call', icon: 'phone-call' },
    meeting: { label: 'Meeting', icon: 'meeting' },
    email: { label: 'Email', icon: 'mail' },
    note: { label: 'Note', icon: 'note' },
  }
  let editing = $state<Interaction | null>(null)

  async function remove(it: Interaction) {
    const label = `${TYPES[it.type].label.toLowerCase()} from ${date(it.date)}`
    const text = deleteText ? deleteText(it, TYPES[it.type].label) : `This ${label} will be permanently removed from the history.`
    const ok = await confirm({ title: 'Delete interaction?', text, confirmLabel: deleteLabel })
    if (!ok) return
    try {
      await api.remove(it.id)
      toast({ type: 'success', text: 'Interaction deleted' })
      onchange?.()
    } catch (e) {
      errorToast(e)
    }
  }
</script>

{#if !items.length}
  <EmptyState icon="note" compact title={emptyTitle} text={emptyText} actions={emptyAction} />
{:else}
  <ol class="timeline" data-show-link={showLink ? '1' : ''} data-context={context}>
    {#each items as it (it.id)}
      {@const t = TYPES[it.type] ?? TYPES.note}
      {@const when = date(it.date)}
      <li class="timeline-item" data-id={it.id} data-type={it.type}>
        <span class="timeline-icon" aria-hidden="true"><Icon name={t.icon} size={18} /></span>
        <div class="timeline-content">
          <div class="timeline-head">
            <span class="timeline-type">{t.label}</span>
            <time class="timeline-date" datetime={it.date} title={relDate(it.date)}>{when}</time>
            <div class="timeline-actions">
              <button type="button" class="btn btn-ghost btn-icon btn-sm" aria-label="Edit {t.label.toLowerCase()} from {when}" onclick={() => (editing = it)}><Icon name="edit" size={18} /></button>
              <button type="button" class="btn btn-ghost btn-icon btn-sm" aria-label="Delete {t.label.toLowerCase()} from {when}" onclick={() => void remove(it)}><Icon name="trash" size={18} /></button>
            </div>
          </div>
          <p class="timeline-text">{it.description}</p>
          {#if showLink}
            {@const chipDeal = it.dealId && context !== 'deal'}
            {@const chipClient = (!it.dealId && context !== 'client') || (context !== 'client' && context !== 'deal' && !!it.dealId)}
            {#if chipDeal || chipClient}
              <div class="timeline-links">
                {#if chipDeal}<EntityChip kind="deal" id={it.dealId!} label={it.dealTitle ?? ''} />{/if}
                {#if chipClient}<EntityChip kind="client" id={it.clientId} label={it.clientName} clientType={it.clientType} />{/if}
              </div>
            {/if}
          {/if}
        </div>
      </li>
    {/each}
  </ol>
{/if}

{#if editing}
  <InteractionForm interaction={editing} onsaved={() => onchange?.()} onclose={() => (editing = null)} />
{/if}
