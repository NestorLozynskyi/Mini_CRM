<script lang="ts">
  import type { Deal } from '../api/types'
  import { money } from '../format'
  import Modal from './Modal.svelte'

  /** «Mark deal as lost» при перетягуванні в колонку Lost (deals-board.html). */
  let { deal, onconfirm, onclose }: { deal: Deal; onconfirm: (reason: string | null) => void; onclose: () => void } = $props()

  let reason = $state('')
  let modal = $state<ReturnType<typeof Modal>>()
</script>

<Modal bind:this={modal} title="Mark deal as lost" subtitle="{deal.title} · {deal.clientName} · {money(deal.amount)}" size="sm" {onclose}>
  <div class="field">
    <label for="lost-reason">Why was this deal lost? <span class="field-optional">(optional)</span></label>
    <textarea class="textarea" id="lost-reason" placeholder="e.g. Went with a cheaper option" bind:value={reason}></textarea>
    <p class="field-hint">Shown on the deal card and in the deal details.</p>
  </div>
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="button" class="btn btn-primary" onclick={() => { onconfirm(reason.trim() || null); modal?.close('confirm') }}>Mark as lost</button>
  {/snippet}
</Modal>
