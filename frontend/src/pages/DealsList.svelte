<script lang="ts">
  import { onMount, tick } from 'svelte'
  import { deals as api } from '../lib/api/crm'
  import type { Deal, Stage } from '../lib/api/types'
  import { date, dateShort, money, plural, relDate } from '../lib/format'
  import { route, setSearch } from '../lib/router.svelte'
  import { STAGES, stageLabel } from '../lib/stages'
  import Avatar from '../lib/ui/Avatar.svelte'
  import DealForm from '../lib/ui/DealForm.svelte'
  import EmptyState from '../lib/ui/EmptyState.svelte'
  import ErrorState from '../lib/ui/ErrorState.svelte'
  import Icon from '../lib/ui/Icon.svelte'
  import { openMenu, type MenuItem } from '../lib/ui/menu.svelte'
  import Shell from '../lib/ui/Shell.svelte'
  import Skeleton from '../lib/ui/Skeleton.svelte'
  import StageBadge from '../lib/ui/StageBadge.svelte'
  import { errorToast, toast } from '../lib/ui/toast.svelte'
  import DealsHeaderActions from './deals/DealsHeaderActions.svelte'
  import { confirmArchive, exportDeals, isOverdue, matchesQuery, sumAmount, updatedOn } from './deals/deals'

  let all = $state<Deal[] | null>(null)
  let failed = $state(false)
  let q = $state('')
  let stages = $state<Stage[]>([])
  let creating = $state(false)
  let editing = $state<Deal | null>(null)
  let searchEl = $state<HTMLInputElement>()

  async function load() {
    failed = false
    try {
      all = await api.list({ scope: 'all' })
    } catch {
      failed = true
    }
  }
  onMount(load)

  const scope = $derived<'active' | 'archived'>(new URLSearchParams(route.search).get('scope') === 'archived' ? 'archived' : 'active')
  const setScope = (s: 'active' | 'archived') => setSearch({ scope: s === 'archived' ? 'archived' : null })

  const STAGE_IDX = Object.fromEntries(STAGES.map((s, i) => [s.id, i])) as Record<Stage, number>
  const nActive = $derived((all ?? []).filter((d) => !d.archived).length)
  const nArchived = $derived((all ?? []).length - nActive)
  const base = $derived((all ?? []).filter((d) => (scope === 'archived' ? d.archived : !d.archived)))
  const searched = $derived(base.filter((d) => matchesQuery(d, q)))
  const list = $derived(
    searched
      .filter((d) => !stages.length || stages.includes(d.stage))
      .sort((a, b) => STAGE_IDX[a.stage] - STAGE_IDX[b.stage] || (a.expectedClose ?? '9999').localeCompare(b.expectedClose ?? '9999') || b.id - a.id),
  )
  const filtering = $derived(!!q.trim() || stages.length > 0)
  const listLabel = $derived(scope === 'archived' ? 'Archived deals' : 'Active deals')

  function toggleStage(id: Stage) {
    stages = stages.includes(id) ? stages.filter((s) => s !== id) : [...stages, id]
  }
  async function clearFilters() {
    q = ''
    stages = []
    await tick()
    searchEl?.focus()
  }

  function replace(d: Deal) {
    if (!all) return
    const i = all.findIndex((x) => x.id === d.id)
    if (i >= 0) all[i] = d
    else all.push(d)
  }

  async function restore(d: Deal) {
    try {
      const saved = await api.restore(d.id)
      replace(saved)
      toast({
        type: 'success',
        text: `Deal restored to ${stageLabel(saved.stage)}`,
        action: { label: 'Undo', onClick: () => void api.archive(d.id).then(replace, (e) => errorToast(e)) },
      })
    } catch (e) {
      errorToast(e)
    }
  }
  async function archive(d: Deal) {
    if (!(await confirmArchive())) return
    try {
      replace(await api.archive(d.id))
      toast({
        type: 'success',
        text: `“${d.title}” archived`,
        action: { label: 'Undo', onClick: () => void api.restore(d.id).then(replace, (e) => errorToast(e)) },
      })
    } catch (e) {
      errorToast(e)
    }
  }

  function rowMenu(e: MouseEvent, d: Deal) {
    openMenu(e.currentTarget as HTMLElement, [
      { label: 'Edit', icon: 'edit', onClick: () => (editing = d) },
      { label: 'Archive', icon: 'archive', onClick: () => void archive(d) },
    ])
  }

  const overflow = (): MenuItem[] => [
    { label: 'Export CSV', icon: 'download', onClick: () => void exportDeals({ scope, q, stage: stages }) },
    scope === 'archived'
      ? { label: 'View active deals', icon: 'briefcase', onClick: () => setScope('active') }
      : { label: 'View archived', icon: 'archive', onClick: () => setScope('archived') },
  ]
</script>

{#snippet closeCell(d: Deal, short: boolean)}
  {#if !d.expectedClose}<span class="is-none">—</span>
  {:else}<span title="Expected close {date(d.expectedClose)}">{short ? dateShort(d.expectedClose) : date(d.expectedClose)}</span
    >{#if isOverdue(d)}<span class="overdue-flag"><Icon name="alert-circle" size={14} />Overdue</span>{/if}{/if}
{/snippet}

{#snippet action(d: Deal)}
  {#if d.archived}
    <button type="button" class="btn btn-secondary btn-sm" aria-label="Restore {d.title}" onclick={() => void restore(d)}><Icon name="restore" size={16} /><span>Restore</span></button>
  {:else}
    <button type="button" class="btn btn-ghost btn-icon btn-sm" aria-label="Actions for {d.title}" aria-haspopup="menu" aria-expanded="false" onclick={(e) => rowMenu(e, d)}
      ><Icon name="more" size={18} /></button>
  {/if}
{/snippet}

<div class="screen" data-screen="deals-list">
  <Shell active="deals" title="Deals">
    {#snippet actions()}
      <DealsHeaderActions view="list" {overflow} onnew={() => (creating = true)} />
    {/snippet}

    <div class="filter-bar list-toolbar">
      <label class="search"
        ><Icon name="search" size={18} /><span class="sr-only">Search deals</span><input
          class="input"
          type="search"
          id="deal-search"
          placeholder="Search deals or clients"
          autocomplete="off"
          disabled={!all}
          bind:this={searchEl}
          bind:value={q} /></label>
      <div class="filter-bar-chips" id="stage-chips" role="group" aria-label="Filter by stage">
        {#each STAGES as s (s.id)}
          <button type="button" class="chip-filter" data-chip={s.id} aria-pressed={stages.includes(s.id)} disabled={!all} onclick={() => toggleStage(s.id)}>
            <span class="stage-dot" data-stage={s.id} aria-hidden="true"></span>{s.label}{#if all}<span class="chip-filter-count"
                ><span class="sr-only">, </span>{searched.filter((d) => d.stage === s.id).length}</span
              >{/if}
          </button>
        {/each}
      </div>
      <div class="filter-bar-end">
        <div class="segmented" id="scope-switch" role="group" aria-label="Deal status">
          <button type="button" class="segmented-item" aria-pressed={scope === 'active'} onclick={() => setScope('active')}
            >Active{#if all}{' '}<span class="scope-count">{nActive}</span>{/if}</button>
          <button type="button" class="segmented-item" aria-pressed={scope === 'archived'} onclick={() => setScope('archived')}
            ><Icon name="archive" size={16} />Archived{#if all}{' '}<span class="scope-count">{nArchived}</span>{/if}</button>
        </div>
      </div>
    </div>

    <div id="list-notice">
      {#if scope === 'archived'}
        <div class="alert alert-info list-notice" role="note">
          <Icon name="info" size={20} />
          <div class="alert-body">
            <span class="alert-title">Archived deals are hidden from the board.</span>
            <span>Restore a deal to put it back on the board in its last stage.</span>
          </div>
        </div>
      {/if}
    </div>

    <div id="list-summary" aria-live="polite">
      {#if all && filtering && base.length}
        <div class="filter-summary">
          <span>Showing <strong>{list.length}</strong> of {base.length} {scope === 'archived' ? 'archived ' : ''}deals</span>
          <button type="button" class="btn-link" onclick={clearFilters}>Clear filters</button>
        </div>
      {/if}
    </div>

    <div id="list-root">
      {#if failed}
        <div class="card">
          <ErrorState title="Couldn’t load deals" text="Something went wrong while loading your deals. Check your connection and try again." retryLabel="Retry" onretry={load} />
        </div>
      {:else if !all}
        <Skeleton type="table" />
      {:else if !base.length}
        <div class="card">
          {#if scope === 'archived'}
            <EmptyState icon="archive" title="No archived deals" text="Deals you archive will appear here. You can restore them at any time.">
              {#snippet actions()}<button type="button" class="btn btn-secondary" onclick={() => setScope('active')}>View active deals</button>{/snippet}
            </EmptyState>
          {:else}
            <EmptyState icon="briefcase" title="No deals yet" text="Create your first deal to track it from Lead to Won.">
              {#snippet actions()}
                <button type="button" class="btn btn-primary" onclick={() => (creating = true)}><Icon name="plus" size={18} /><span>New deal</span></button>
              {/snippet}
            </EmptyState>
          {/if}
        </div>
      {:else if !list.length}
        <div class="card">
          <EmptyState
            icon="search"
            title="No deals found"
            text={q.trim()
              ? `Nothing matches “${q.trim()}”${stages.length ? ' in the selected stages' : ''}. Try a different search or clear the filters.`
              : 'There are no deals in the selected stages.'}>
            {#snippet actions()}<button type="button" class="btn btn-secondary" onclick={clearFilters}>Clear filters</button>{/snippet}
          </EmptyState>
        </div>
      {:else}
        <div class="table-wrap">
          <table class="table deals-table">
            <caption class="sr-only">{listLabel}</caption>
            <thead>
              <tr>
                <th scope="col">Deal</th>
                <th scope="col">Client</th>
                <th scope="col">Stage</th>
                <th scope="col" class="num">Amount</th>
                <th scope="col">Expected close</th>
                <th scope="col" class="hide-tablet">Updated</th>
                <th scope="col" class="col-actions"><span class="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {#each list as d (d.id)}
                {@const u = updatedOn(d)}
                <tr data-id={d.id} class:is-archived={d.archived}>
                  <td class="col-deal">
                    <a class="table-link" href="/deals/{d.id}">{d.title}</a>
                    {#if d.stage === 'lost' && d.lostReason}<div class="table-cell-sub" title={d.lostReason}>Lost: {d.lostReason}</div>{/if}
                  </td>
                  <td>
                    <a class="client-cell" href="/clients/{d.clientId}"><Avatar name={d.clientName} size="sm" square={d.clientType === 'company'} /><span>{d.clientName}</span></a>
                  </td>
                  <td><StageBadge stage={d.stage} /></td>
                  <td class="num">{money(d.amount)}</td>
                  <td class="is-date">{@render closeCell(d, false)}</td>
                  <td class="is-date hide-tablet text-muted" title={date(u)}>{relDate(u)}</td>
                  <td class="col-actions">{@render action(d)}</td>
                </tr>
              {/each}
            </tbody>
          </table>
          <div class="table-footer">
            <span>{plural(list.length, 'deal')}</span><span>Total <strong>{money(sumAmount(list))}</strong></span>
          </div>
        </div>
        <ul class="list-cards" aria-label={listLabel}>
          {#each list as d (d.id)}
            <li class="list-card deal-lc" class:is-archived={d.archived} data-id={d.id}>
              <div class="list-card-main">
                <a class="list-card-title deal-lc-link" href="/deals/{d.id}">{d.title}</a>
                <div class="list-card-meta"><Icon name={d.clientType === 'company' ? 'building' : 'user'} size={14} /><span>{d.clientName}</span></div>
                <div class="list-card-meta"><StageBadge stage={d.stage} /><span class="deal-lc-date">{@render closeCell(d, true)}</span></div>
              </div>
              <div class="list-card-trailing"><span class="deal-lc-amount">{money(d.amount)}</span>{@render action(d)}</div>
            </li>
          {/each}
        </ul>
      {/if}
    </div>
  </Shell>
</div>

{#if creating}
  <DealForm
    onsaved={(d) => {
      replace(d)
      setScope('active')
    }}
    onclose={() => (creating = false)} />
{/if}
{#if editing}
  <DealForm deal={editing} onsaved={(d) => replace(d)} onclose={() => (editing = null)} />
{/if}
