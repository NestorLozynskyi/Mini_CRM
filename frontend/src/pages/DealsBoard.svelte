<script lang="ts">
  import { onMount, tick } from 'svelte'
  import { clients as clientsApi, deals as api } from '../lib/api/crm'
  import type { ClientSummary, Deal, Stage } from '../lib/api/types'
  import { money, plural } from '../lib/format'
  import { params, setSearch } from '../lib/router.svelte'
  import { isOpenStage, STAGES, stageLabel } from '../lib/stages'
  import DealForm from '../lib/ui/DealForm.svelte'
  import EmptyState from '../lib/ui/EmptyState.svelte'
  import ErrorState from '../lib/ui/ErrorState.svelte'
  import Icon from '../lib/ui/Icon.svelte'
  import LostDialog from '../lib/ui/LostDialog.svelte'
  import { openMenu, type MenuItem } from '../lib/ui/menu.svelte'
  import MoveSheet from '../lib/ui/MoveSheet.svelte'
  import Shell from '../lib/ui/Shell.svelte'
  import Skeleton from '../lib/ui/Skeleton.svelte'
  import StageBadge from '../lib/ui/StageBadge.svelte'
  import { errorToast, toast } from '../lib/ui/toast.svelte'
  import DealCard from './deals/DealCard.svelte'
  import DealsHeaderActions from './deals/DealsHeaderActions.svelte'
  import { byStageChange, confirmArchive, exportDeals, matchesQuery, moveFailed, sumAmount } from './deals/deals'

  let deals = $state<Deal[] | null>(null)
  let failed = $state(false)
  let clientList = $state<ClientSummary[]>([])
  let q = $state('')
  let clientFilter = $state('')
  let mobileStage = $state<Stage>('lead')
  let flashId = $state<number | null>(null)

  let creating = $state<{ clientId: number | null } | null>(null)
  let editing = $state<Deal | null>(null)
  let moving = $state<Deal | null>(null)
  let losing = $state<Deal | null>(null)

  let root = $state<HTMLDivElement>()

  async function load() {
    failed = false
    try {
      deals = await api.list({ scope: 'active' })
    } catch {
      failed = true
    }
  }

  onMount(() => {
    const p = params()
    if (p.get('new') === '1') {
      creating = { clientId: Number(p.get('client')) || null }
      setSearch({ new: null, client: null })
    }
    void load()
    clientsApi
      .list()
      .then((list) => (clientList = list.slice().sort((a, b) => a.name.localeCompare(b.name))))
      .catch(() => (clientList = []))
  })

  const isMobile = () => window.matchMedia('(max-width: 767.98px)').matches
  const empty = $derived(!!deals && deals.length === 0)
  const filtering = $derived(!!(q.trim() || clientFilter))
  const visible = $derived(
    (deals ?? []).filter((d) => (!clientFilter || d.clientId === Number(clientFilter)) && matchesQuery(d, q)).sort(byStageChange),
  )
  const byStage = $derived(Object.fromEntries(STAGES.map((s) => [s.id, visible.filter((d) => d.stage === s.id)])) as Record<Stage, Deal[]>)
  const open = $derived(visible.filter((d) => isOpenStage(d.stage)))

  /* ----- local list helpers ----- */
  function replace(d: Deal) {
    if (!deals) return
    const i = deals.findIndex((x) => x.id === d.id)
    if (d.archived) {
      if (i >= 0) deals.splice(i, 1)
    } else if (i >= 0) deals[i] = d
    else deals.push(d)
  }
  let flashTimer: ReturnType<typeof setTimeout> | undefined
  async function flash(id: number, focus = false) {
    flashId = null
    await tick()
    flashId = id
    clearTimeout(flashTimer)
    flashTimer = setTimeout(() => (flashId = null), 1700)
    if (focus && !isMobile()) root?.querySelector<HTMLElement>(`.deal-card[data-id="${id}"] .deal-card-menu`)?.focus()
  }

  /* ----- moves (persisted on the server; optimistic UI with revert on error) ----- */
  async function moveDeal(d: Deal, to: Stage, lostReason: string | null, opts: { focus?: boolean; undo?: boolean } = {}) {
    const prev = $state.snapshot(d) as Deal
    const changed = prev.stage !== to
    replace({
      ...prev,
      stage: to,
      lostReason: to === 'lost' ? lostReason : null,
      stageChangedAt: changed ? new Date().toISOString() : prev.stageChangedAt,
    })
    void flash(d.id, opts.focus)
    try {
      const saved = await api.move(d.id, to, lostReason)
      replace(saved)
      if (opts.undo) return
      if (changed)
        toast({
          type: 'success',
          text: `Moved to ${stageLabel(to)}`,
          action: { label: 'Undo', onClick: () => void moveDeal(saved, prev.stage, prev.lostReason, { undo: true }) },
        })
      else toast({ type: 'success', text: 'Lost reason updated' })
    } catch (e) {
      // Повертаємо картку на місце лише якщо її стан не змінили інші дії.
      const cur = deals?.find((x) => x.id === d.id)
      if (cur && cur.stage === to) replace(prev)
      moveFailed()
    }
  }

  async function archiveDeal(d: Deal) {
    if (!(await confirmArchive())) return
    try {
      const saved = await api.archive(d.id)
      replace(saved)
      toast({
        type: 'success',
        text: `“${d.title}” archived`,
        action: {
          label: 'Undo',
          onClick: async () => {
            try {
              replace(await api.restore(d.id))
              void flash(d.id)
            } catch (e) {
              errorToast(e)
            }
          },
        },
      })
    } catch (e) {
      errorToast(e)
    }
  }

  function cardMenu(e: MouseEvent, d: Deal) {
    openMenu(e.currentTarget as HTMLElement, [
      { label: 'Edit', icon: 'edit', onClick: () => (editing = d) },
      { label: 'Move to…', icon: 'move', onClick: () => (moving = d) },
      { divider: true, label: '' },
      { label: 'Archive', icon: 'archive', onClick: () => void archiveDeal(d) },
    ])
  }

  const overflow = (): MenuItem[] => [
    { label: 'Export CSV', icon: 'download', onClick: () => void exportDeals({ scope: 'active' }) },
    { label: 'View archived', icon: 'archive', href: '/deals/list?scope=archived' },
  ]

  function onSaved(d: Deal, isNew: boolean) {
    replace(d)
    if (isNew) mobileStage = d.stage
    if (!d.archived) void flash(d.id)
  }

  /* ----- HTML5 drag & drop (keyboard / touch alternative: card menu → Move to…, mobile “Move” button) ----- */
  let drag = $state<{ id: number; from: Stage; source: boolean } | null>(null)
  let dropTarget = $state<Stage | null>(null)
  let slot = $state<{ stage: Stage; before: number | null } | null>(null)
  const slotText = $derived(slot && drag ? (slot.stage === drag.from ? 'Drop here' : `Drop to move to ${stageLabel(slot.stage)}`) : '')

  const colOf = (t: EventTarget | null) => (t as Element | null)?.closest?.('.kanban-col') as HTMLElement | null

  function cardAfter(body: Element, y: number): number | null {
    for (const c of Array.from(body.querySelectorAll<HTMLElement>('.deal-card:not(.is-dragging)'))) {
      const r = c.getBoundingClientRect()
      if (y < r.top + r.height / 2) return Number(c.dataset.id)
    }
    return null
  }
  function endDrag() {
    drag = null
    dropTarget = null
    slot = null
  }
  function ondragstart(e: DragEvent) {
    const card = (e.target as Element | null)?.closest?.('.deal-card') as HTMLElement | null
    if (!card) return
    const id = Number(card.dataset.id)
    drag = { id, from: card.dataset.stage as Stage, source: false }
    try {
      e.dataTransfer!.effectAllowed = 'move'
      e.dataTransfer!.setData('text/plain', String(id))
    } catch {
      // старі браузери без dataTransfer
    }
    setTimeout(() => {
      if (drag?.id === id) drag.source = true
    }, 0)
  }
  function ondragover(e: DragEvent) {
    if (!drag) return
    const col = colOf(e.target)
    if (!col) return
    e.preventDefault()
    if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
    const stage = col.dataset.stage as Stage
    dropTarget = stage
    const before = cardAfter(col.querySelector('.kanban-col-body')!, e.clientY)
    if (slot?.stage !== stage || slot.before !== before) slot = { stage, before }
  }
  function ondragleave(e: DragEvent) {
    if (drag && (!e.relatedTarget || !root?.contains(e.relatedTarget as Node))) {
      dropTarget = null
      slot = null
    }
  }
  function ondrop(e: DragEvent) {
    if (!drag) return
    const col = colOf(e.target)
    const { id, from } = drag
    endDrag()
    if (!col) return
    e.preventDefault()
    const to = col.dataset.stage as Stage
    const d = deals?.find((x) => x.id === id)
    if (!d || to === from) return
    if (to === 'lost') losing = d
    else void moveDeal(d, to, null)
  }
</script>

{#snippet column(s: { id: Stage; label: string }, items: Deal[])}
  {@const total = money(sumAmount(items))}
  <section
    class="kanban-col"
    class:is-current={s.id === mobileStage}
    class:is-drop-target={dropTarget === s.id}
    data-stage={s.id}
    aria-label="{s.label}, {plural(items.length, 'deal')}, total {total}">
    <header class="kanban-col-header">
      <StageBadge stage={s.id} />
      <span class="kanban-count" aria-hidden="true">{items.length}</span>
      <span class="kanban-sum" aria-hidden="true">{total}</span>
    </header>
    <div class="kanban-col-body">
      {#if !items.length}
        <div class="kanban-empty">{empty ? `Deals in ${s.label} will appear here` : filtering ? 'No matching deals' : 'No deals'}</div>
      {/if}
      {#each items as d (d.id)}
        {#if slot?.stage === s.id && slot.before === d.id}<div class="kanban-drop-slot" aria-hidden="true">{slotText}</div>{/if}
        <DealCard
          deal={d}
          dragging={drag?.id === d.id}
          source={drag?.id === d.id && drag.source}
          flash={flashId === d.id}
          onmenu={(e) => cardMenu(e, d)}
          onmove={() => (moving = d)} />
      {/each}
      {#if slot?.stage === s.id && slot.before === null}<div class="kanban-drop-slot" aria-hidden="true">{slotText}</div>{/if}
    </div>
  </section>
{/snippet}

<div class="screen" data-screen="deals-board">
  <Shell active="deals" title="Deals">
    {#snippet actions()}
      <DealsHeaderActions view="board" {overflow} onnew={() => (creating = { clientId: null })} />
    {/snippet}

    <div class="filter-bar board-toolbar" hidden={empty}>
      <label class="search"
        ><Icon name="search" size={18} /><span class="sr-only">Search deals</span><input
          class="input"
          type="search"
          id="deal-search"
          placeholder="Search deals or clients"
          autocomplete="off"
          disabled={!deals}
          bind:value={q} /></label>
      <select class="select board-client" id="deal-client" aria-label="Filter by client" disabled={!deals} bind:value={clientFilter}>
        <option value="">All clients</option>
        {#each clientList as c (c.id)}<option value={String(c.id)}>{c.name}</option>{/each}
      </select>
      <div class="filter-bar-end board-summary" id="board-summary" aria-live="polite">
        {#if deals && !empty}<strong>{open.length}</strong> open {open.length === 1 ? 'deal' : 'deals'} · <strong>{money(sumAmount(open))}</strong> in pipeline{/if}
      </div>
    </div>

    <div id="board-root">
      {#if failed}
        <div class="card">
          <ErrorState title="Couldn’t load deals" text="Something went wrong while loading your pipeline. Check your connection and try again." retryLabel="Retry" onretry={load} />
        </div>
      {:else if !deals}
        <Skeleton type="board" />
      {:else}
        {#if empty}
          <div class="card board-empty">
            <EmptyState icon="briefcase" title="No deals yet" text="Create your first deal to track it from Lead to Won. Deals are always linked to a client.">
              {#snippet actions()}
                <button type="button" class="btn btn-primary" onclick={() => (creating = { clientId: null })}><Icon name="plus" size={18} /><span>New deal</span></button>
              {/snippet}
            </EmptyState>
          </div>
        {:else}
          <div class="stage-rail od-rail show-mobile" role="group" aria-label="Show stage">
            {#each STAGES as s (s.id)}
              <button type="button" class="chip-filter stage-chip" data-tab={s.id} aria-pressed={s.id === mobileStage} onclick={() => (mobileStage = s.id)}>
                <span class="stage-dot" data-stage={s.id} aria-hidden="true"></span>{s.label}<span class="chip-filter-count"
                  ><span class="sr-only">, </span>{byStage[s.id].length}<span class="sr-only"> deals</span></span>
              </button>
            {/each}
          </div>
        {/if}
        <!-- svelte-ignore a11y_no_static_element_interactions -->
        <div class="kanban board" class:hide-mobile={empty} id="board" bind:this={root} {ondragstart} {ondragover} {ondragleave} {ondrop} ondragend={endDrag}>
          {#each STAGES as s (s.id)}{@render column(s, byStage[s.id])}{/each}
        </div>
      {/if}
    </div>
  </Shell>
</div>

{#if creating}
  <DealForm clientId={creating.clientId} onsaved={onSaved} onclose={() => (creating = null)} />
{/if}
{#if editing}
  <DealForm deal={editing} onsaved={onSaved} onclose={() => (editing = null)} />
{/if}
{#if moving}
  {@const d = moving}
  <MoveSheet deal={d} onmove={(stage, reason) => void moveDeal(d, stage, reason, { focus: true })} onclose={() => (moving = null)} />
{/if}
{#if losing}
  {@const d = losing}
  <LostDialog deal={d} onconfirm={(reason) => void moveDeal(d, 'lost', reason)} onclose={() => (losing = null)} />
{/if}
