<script lang="ts">
  import type { StageStat } from '../api/types'
  import { money } from '../format'
  import StageBadge from './StageBadge.svelte'

  let { stats }: { stats: StageStat[] } = $props()
  const max = $derived(Math.max(1, ...stats.map((r) => r.count)))
</script>

<ul class="bar-chart" aria-label="Deals by stage">
  {#each stats as r}
    <li class="bar-row" data-stage={r.stage}>
      <span class="bar-label"><StageBadge stage={r.stage} /></span>
      <span class="bar-track" aria-hidden="true"><span class="bar-fill" class:is-zero={!r.count} style="--value:{Math.round((r.count / max) * 100)}%"></span></span>
      <span class="bar-value"><span class="bar-count">{r.count}{r.count === 1 ? ' deal' : ' deals'}</span><span>{money(r.amount)}</span></span>
    </li>
  {/each}
</ul>
