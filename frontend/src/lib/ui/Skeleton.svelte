<script lang="ts">
  let { type }: { type: 'table' | 'cards' | 'kpis' | 'board' | 'detail' } = $props()
  const W5 = ['60%', '48%', '70%', '55%', '42%']
  const W7 = ['70%', '55%', '62%', '48%', '66%', '58%', '52%']
  const BOARD = [3, 2, 2, 1, 2, 1]
</script>

{#snippet sk(w: string, cls = '')}<span class="skeleton skeleton-text {cls}" style="--w:{w}"></span>{/snippet}
{#snippet cards(isStatic: boolean)}
  <ul class="list-cards" class:is-static={isStatic}>
    {#each W5 as w}
      <li class="list-card">
        <span class="skeleton skeleton-circle"></span>
        <div class="list-card-main" style="gap:8px">{@render sk(w)}{@render sk('36%')}</div>
        {@render sk('48px')}
      </li>
    {/each}
  </ul>
{/snippet}

<div class="skeleton-wrap" aria-busy="true">
  <span class="sr-only" role="status">Loading…</span>
  {#if type === 'table'}
    <div class="table-wrap">
      <div class="skeleton-row" style="background:var(--surface-2)">{@render sk('20%')}{@render sk('15%')}{@render sk('15%')}{@render sk('10%')}</div>
      {#each W7 as w}
        <div class="skeleton-row"><span class="skeleton skeleton-circle"></span>{@render sk(w)}{@render sk('60%')}{@render sk('50%')}{@render sk('40%')}</div>
      {/each}
    </div>
    {@render cards(false)}
  {:else if type === 'cards'}
    {@render cards(true)}
  {:else if type === 'kpis'}
    <div class="kpi-grid">
      {#each [0, 1, 2] as _}<div class="kpi">{@render sk('45%')}{@render sk('60%', 'is-lg')}{@render sk('35%')}</div>{/each}
    </div>
  {:else if type === 'board'}
    <div class="kanban">
      {#each BOARD as n}
        <div class="kanban-col">
          <div class="kanban-col-header">{@render sk('40%')}</div>
          <div class="kanban-col-body">
            {#each Array(n) as _}<div class="deal-card" style="cursor:default">{@render sk('80%')}{@render sk('55%')}{@render sk('35%')}</div>{/each}
          </div>
        </div>
      {/each}
    </div>
  {:else if type === 'detail'}
    <div class="split">
      <div style="display:grid;gap:24px">
        <div class="card card-body" style="display:flex;gap:16px;align-items:center">
          <span class="skeleton" style="width:64px;height:64px;border-radius:12px;flex:none"></span>
          <div style="flex:1;display:grid;gap:10px">{@render sk('40%', 'is-lg')}{@render sk('25%')}</div>
        </div>
        <div class="card">
          <div class="card-header">{@render sk('20%')}</div>
          <div class="skeleton-row">{@render sk('90%')}</div>
          <div class="skeleton-row">{@render sk('75%')}</div>
          <div class="skeleton-row">{@render sk('82%')}</div>
        </div>
      </div>
      <div class="card card-body" style="display:grid;gap:14px">{@render sk('50%')}{@render sk('90%')}{@render sk('80%')}{@render sk('85%')}{@render sk('60%')}</div>
    </div>
  {/if}
</div>
