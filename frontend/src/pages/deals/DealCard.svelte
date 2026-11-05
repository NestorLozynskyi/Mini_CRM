<script lang="ts">
  import type { Deal } from '../../lib/api/types'
  import { date, dateShort, money } from '../../lib/format'
  import Icon from '../../lib/ui/Icon.svelte'
  import { isOverdue } from './deals'

  interface Props {
    deal: Deal
    dragging?: boolean
    source?: boolean
    flash?: boolean
    onmenu: (e: MouseEvent) => void
    onmove: () => void
  }
  let { deal: d, dragging = false, source = false, flash = false, onmenu, onmove }: Props = $props()
</script>

<article
  class="deal-card"
  class:is-lost={d.stage === 'lost'}
  class:is-dragging={dragging}
  class:is-drag-source={source}
  class:is-flash={flash}
  draggable="true"
  data-id={d.id}
  data-stage={d.stage}
  data-amount={d.amount}>
  <div class="deal-card-top">
    <span class="deal-card-grip" title="Drag to move" aria-hidden="true"><Icon name="grip" size={16} /></span>
    <a class="deal-card-title" draggable="false" href="/deals/{d.id}">{d.title}</a>
    <button
      type="button"
      class="btn btn-ghost btn-icon btn-sm deal-card-menu"
      aria-label="Actions for {d.title}"
      aria-haspopup="menu"
      aria-expanded="false"
      onclick={(e) => { e.preventDefault(); onmenu(e) }}><Icon name="more" size={18} /></button>
  </div>
  <div class="deal-card-client"><Icon name={d.clientType === 'company' ? 'building' : 'user'} size={14} /><span>{d.clientName}</span></div>
  {#if d.stage === 'lost' && d.lostReason}<p class="deal-card-note">{d.lostReason}</p>{/if}
  <div class="deal-card-foot">
    <span class="deal-card-amount">{money(d.amount)}</span>
    {#if !d.expectedClose}
      <span class="deal-card-date is-none">No close date</span>
    {:else if isOverdue(d)}
      <span class="deal-card-date is-overdue" title="Expected close {date(d.expectedClose)} has passed"
        ><Icon name="alert-circle" size={14} /><strong>Overdue</strong> · {dateShort(d.expectedClose)}</span>
    {:else}
      <span class="deal-card-date" title="Expected close {date(d.expectedClose)}"><Icon name="calendar" size={14} />{dateShort(d.expectedClose)}</span>
    {/if}
  </div>
  <button type="button" class="btn btn-secondary btn-sm deal-card-move" aria-label="Move {d.title} to another stage" onclick={(e) => { e.preventDefault(); onmove() }}>
    <span class="od-row" style="--od-gap:6px"><Icon name="move" size={16} />Move</span><Icon name="chevron-down" size={16} />
  </button>
</article>
