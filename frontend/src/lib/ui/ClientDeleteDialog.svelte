<script lang="ts">
  import { onMount } from 'svelte'
  import { clients as api } from '../api/crm'
  import type { DeletePreview } from '../api/types'
  import { plural } from '../format'
  import { refreshBadges } from '../session.svelte'
  import { refreshTags } from '../stores.svelte'
  import Icon from './Icon.svelte'
  import Modal from './Modal.svelte'
  import { errorToast, toast } from './toast.svelte'

  /** Підтвердження каскадного видалення клієнта з переліком і кількостями пов'язаних записів (дані — з сервера). */
  let { client, ondeleted, onclose }: { client: { id: number; name: string }; ondeleted: () => void; onclose: () => void } = $props()

  let n = $state<DeletePreview | null>(null)
  let deleting = $state(false)
  let modal = $state<ReturnType<typeof Modal>>()

  onMount(async () => {
    try {
      n = await api.deletePreview(client.id)
    } catch (e) {
      errorToast(e, 'Couldn’t load linked records. Check your connection and try again.')
      modal?.close('error')
    }
  })

  const rows = $derived.by(() => {
    if (!n) return []
    const r: { icon: string; strong: string; rest: string }[] = []
    const allDeals = n.deals + n.archivedDeals
    if (allDeals) r.push({ icon: 'briefcase', strong: plural(allDeals, 'deal'), rest: n.archivedDeals ? ` (incl. ${n.archivedDeals} archived)` : '' })
    if (n.interactions) r.push({ icon: 'note', strong: plural(n.interactions, 'interaction'), rest: ' in the history' })
    if (n.tasks) r.push({ icon: 'check-square', strong: plural(n.tasks, 'task'), rest: ', including tasks on its deals' })
    if (n.contacts) r.push({ icon: 'users', strong: plural(n.contacts, 'contact person', 'contact persons'), rest: '' })
    return r
  })

  async function remove() {
    deleting = true
    try {
      await api.remove(client.id)
      void refreshBadges()
      void refreshTags()
      toast({ type: 'success', text: `“${client.name}” and its linked records were deleted` })
      ondeleted()
      modal?.close('confirm')
    } catch (e) {
      deleting = false
      errorToast(e)
    }
  }
</script>

<Modal bind:this={modal} title="Delete {client.name}?" size="sm" role="alertdialog" className="confirm" icon="alert-triangle" closeButton={false} initialFocus="[data-close]" {onclose}>
  {#if !n}
    <p class="confirm-text">Checking linked records…</p>
  {:else if rows.length}
    <p class="confirm-text">{client.name} will be permanently deleted together with everything linked to it:</p>
    <ul class="delete-impact">
      {#each rows as r}<li><Icon name={r.icon} size={16} /><span><strong>{r.strong}</strong>{r.rest}</span></li>{/each}
    </ul>
    <p class="confirm-text">Everything listed above will be deleted with the client. This can’t be undone.</p>
  {:else}
    <p class="confirm-text">{client.name} has no deals, interactions or tasks. The client will be permanently deleted. This can’t be undone.</p>
  {/if}
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="button" class="btn btn-danger" class:is-loading={deleting} aria-busy={deleting || undefined} disabled={!n || deleting} onclick={remove}>
      <Icon name="trash" size={18} />{deleting ? 'Deleting…' : 'Delete client'}
    </button>
  {/snippet}
</Modal>
