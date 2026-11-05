<script lang="ts">
  import { untrack } from 'svelte'
  import type { Deal, Stage } from '../api/types'
  import { STAGES } from '../stages'
  import Icon from './Icon.svelte'
  import Modal from './Modal.svelte'
  import StageBadge from './StageBadge.svelte'

  /** «Move to stage» (CRM.openMoveSheet макета): вибір етапу; для Lost — необов'язкова причина. */
  let { deal, onmove, onclose }: { deal: Deal; onmove: (stage: Stage, lostReason: string | null) => void; onclose: () => void } = $props()

  let stage = $state<Stage>(untrack(() => deal.stage))
  let reason = $state(untrack(() => deal.lostReason ?? ''))
  let modal = $state<ReturnType<typeof Modal>>()

  function save() {
    if (stage !== deal.stage || (stage === 'lost' && reason.trim() !== (deal.lostReason ?? ''))) onmove(stage, stage === 'lost' ? reason.trim() || null : null)
    modal?.close('save')
  }
</script>

<Modal bind:this={modal} title="Move to stage" {onclose}>
  <p style="margin-bottom:12px;color:var(--muted)">{deal.title}</p>
  <div class="stage-options" role="radiogroup" aria-label="Stage">
    {#each STAGES as s}
      <label class="stage-option">
        <input type="radio" name="move-stage" value={s.id} bind:group={stage} />
        <StageBadge stage={s.id} />
        <span class="od-row" style="--od-gap:8px">
          {#if s.id === deal.stage}<span class="stage-option-current">Current</span>{/if}
          <Icon name="check" size={18} />
        </span>
      </label>
    {/each}
  </div>
  {#if stage === 'lost'}
    <div class="field" style="margin-top:16px">
      <label for="mv-reason">Reason for losing <span class="field-optional">(optional)</span></label>
      <textarea class="textarea" id="mv-reason" placeholder="e.g. Went with a cheaper option" bind:value={reason}></textarea>
    </div>
  {/if}
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="button" class="btn btn-primary" onclick={save}>Move deal</button>
  {/snippet}
</Modal>
