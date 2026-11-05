<script lang="ts">
  import { onMount, tick } from 'svelte'
  import { ApiError } from '../lib/api/client'
  import { clients as clientsApi, deals as api, interactions as interactionsApi, tasks as tasksApi } from '../lib/api/crm'
  import type { Client, Deal, Interaction, Stage, Task } from '../lib/api/types'
  import { date, money, relDate } from '../lib/format'
  import { isOpenStage, stageLabel } from '../lib/stages'
  import DealForm from '../lib/ui/DealForm.svelte'
  import EmptyState from '../lib/ui/EmptyState.svelte'
  import EntityChip from '../lib/ui/EntityChip.svelte'
  import ErrorState from '../lib/ui/ErrorState.svelte'
  import Icon from '../lib/ui/Icon.svelte'
  import InteractionForm from '../lib/ui/InteractionForm.svelte'
  import { openMenu } from '../lib/ui/menu.svelte'
  import MoveSheet from '../lib/ui/MoveSheet.svelte'
  import Shell from '../lib/ui/Shell.svelte'
  import Skeleton from '../lib/ui/Skeleton.svelte'
  import StageBadge from '../lib/ui/StageBadge.svelte'
  import TaskForm from '../lib/ui/TaskForm.svelte'
  import TaskList from '../lib/ui/TaskList.svelte'
  import Timeline from '../lib/ui/Timeline.svelte'
  import { errorToast, toast } from '../lib/ui/toast.svelte'
  import { confirmArchive, isOverdue, moveFailed } from './deals/deals'

  let { params }: { params: Record<string, string> } = $props()

  const OPEN: Stage[] = ['lead', 'contacted', 'proposal', 'negotiation']
  const TABS = [
    { id: 'overview', label: 'Overview' },
    { id: 'interactions', label: 'Interactions' },
    { id: 'tasks', label: 'Tasks' },
  ] as const
  type Tab = (typeof TABS)[number]['id']

  let deal = $state<Deal | null>(null)
  let client = $state<Client | null>(null)
  let ints = $state<Interaction[]>([])
  let tks = $state<Task[]>([])
  let status = $state<'loading' | 'ready' | 'error' | 'notfound'>('loading')
  let tab = $state<Tab>('overview')

  let editing = $state(false)
  let moving = $state(false)
  let logging = $state(false)
  let addingTask = $state(false)

  const id = $derived(Number(params.id))

  async function loadClient(clientId: number) {
    try {
      client = await clientsApi.get(clientId)
    } catch {
      client = null // необов'язкові дані (контакт / email у Details)
    }
  }
  async function loadLists() {
    const [i, t] = await Promise.all([interactionsApi.list({ dealId: id }), tasksApi.list({ dealId: id })])
    ints = i
    tks = t
  }
  async function load() {
    if (!Number.isInteger(id) || id <= 0) {
      status = 'notfound'
      return
    }
    status = 'loading'
    try {
      const [d] = await Promise.all([api.get(id), loadLists()])
      deal = d
      await loadClient(d.clientId)
      status = 'ready'
    } catch (e) {
      status = e instanceof ApiError && e.status === 404 ? 'notfound' : 'error'
    }
  }
  onMount(load)

  async function refreshLists() {
    try {
      await loadLists()
    } catch {
      errorToast(null, 'Couldn’t refresh the deal’s history. Reload the page to try again.')
    }
  }

  /* ----- actions ----- */
  async function move(to: Stage, lostReason: string | null, undo = false) {
    if (!deal) return
    const prev = { stage: deal.stage, lostReason: deal.lostReason }
    try {
      deal = await api.move(deal.id, to, lostReason)
      if (undo) return
      if (prev.stage === to) toast({ type: 'success', text: 'Lost reason updated' })
      else toast({ type: 'success', text: `Moved to ${stageLabel(to)}`, action: { label: 'Undo', onClick: () => void move(prev.stage, prev.lostReason, true) } })
    } catch (e) {
      moveFailed()
    }
  }
  async function archive() {
    if (!deal || !(await confirmArchive())) return
    try {
      deal = await api.archive(deal.id)
      toast({ type: 'success', text: 'Deal archived', action: { label: 'Undo', onClick: () => void restore(true) } })
    } catch (e) {
      errorToast(e)
    }
  }
  async function restore(quiet = false) {
    if (!deal) return
    try {
      deal = await api.restore(deal.id)
      if (!quiet) toast({ type: 'success', text: `Deal restored to ${stageLabel(deal.stage)}` })
    } catch (e) {
      errorToast(e)
    }
  }
  function overflow(e: MouseEvent) {
    openMenu(
      e.currentTarget as HTMLElement,
      deal?.archived
        ? [{ label: 'Restore deal', icon: 'restore', onClick: () => void restore() }]
        : [{ label: 'Archive deal', icon: 'archive', onClick: () => void archive() }],
    )
  }
  function onEdited(d: Deal) {
    const clientChanged = d.clientId !== deal?.clientId
    deal = d
    if (clientChanged) {
      void loadClient(d.clientId)
      void refreshLists()
    }
  }

  async function setTab(t: Tab, focus = false) {
    tab = t
    if (focus) {
      await tick()
      document.getElementById(`tab-${t}`)?.focus()
    }
  }
  function onTabKey(e: KeyboardEvent) {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return
    e.preventDefault()
    const i = TABS.findIndex((t) => t.id === tab)
    void setTab(TABS[(i + (e.key === 'ArrowRight' ? 1 : TABS.length - 1)) % TABS.length].id, true)
  }

  const title = $derived(status === 'notfound' ? 'Deal not found' : (deal?.title ?? 'Deal'))
  const counts = $derived<Record<Tab, number | null>>({ overview: null, interactions: ints.length, tasks: tks.length })
  const contact = $derived(client?.contacts[0] ?? null)
</script>

{#snippet closeValue(d: Deal)}
  {#if !d.expectedClose}<span class="deal-fact-value">Not set</span>
  {:else}
    <span class="deal-fact-value">{date(d.expectedClose)}</span>
    {#if isOverdue(d)}<span class="deal-fact-flag"><Icon name="alert-circle" size={16} />Overdue · {relDate(d.expectedClose)}</span>
    {:else if isOpenStage(d.stage)}<span class="deal-fact-sub">{relDate(d.expectedClose)}</span>{/if}
  {/if}
{/snippet}

{#snippet progress(d: Deal)}
  {@const idx = OPEN.indexOf(d.stage)}
  <div class="stage-progress" role="group" aria-label="Stage progress">
    <div class="sp-head">
      <span class="sp-caption">Stage progress</span>
      <span class="sp-hint">{d.stage === 'won' ? 'Closed as Won' : d.stage === 'lost' ? 'Closed as Lost' : `Step ${idx + 1} of 4 · then Won or Lost`}</span>
    </div>
    <ol class="sp-steps">
      {#each OPEN as s, i}
        {@const cls = d.stage === 'won' ? 'is-done' : d.stage === 'lost' ? 'is-closed' : i < idx ? 'is-done' : i === idx ? 'is-current' : 'is-upcoming'}
        {@const sr = { 'is-done': 'completed', 'is-current': 'current stage', 'is-upcoming': 'not reached', 'is-closed': 'deal closed' }[cls]}
        <li class="sp-step {cls}" data-stage={s} aria-current={cls === 'is-current' ? 'step' : undefined}>
          <span class="sp-label"
            ><span class="sp-mark" aria-hidden="true">{#if cls === 'is-done'}<Icon name="check" size={10} />{/if}</span><span class="sp-text">{stageLabel(s)}</span><span
              class="sr-only">, {sr}</span
            ></span>
        </li>
      {/each}
    </ol>
    <div class="sp-outcome">
      <span class="sp-outcome-label">Outcome</span>
      {#each ['won', 'lost'] as s}
        {@const cur = d.stage === s}
        <span class="sp-term" class:is-current={cur} data-stage={s} aria-current={cur ? 'step' : undefined}
          ><Icon name={s === 'won' ? 'check-circle' : 'x'} size={14} />{stageLabel(s)}{#if cur}<span class="sr-only">, current stage</span>{/if}</span>
      {/each}
    </div>
  </div>
{/snippet}

{#snippet summary(d: Deal)}
  {@const arch = d.archived}
  {@const lost = d.stage === 'lost'}
  {#if arch}
    <div class="alert alert-warning deal-banner" role="status">
      <Icon name="archive" size={20} />
      <div class="alert-body">
        <span class="alert-title">This deal is archived</span>
        <span>It’s hidden from the board and pipeline totals. Restore it to change its stage.</span>
      </div>
      <button type="button" class="btn btn-secondary btn-sm" onclick={() => void restore()}><Icon name="restore" size={16} /><span>Restore</span></button>
    </div>
  {/if}
  <section class="card deal-hero" aria-label="Deal summary">
    <div class="deal-hero-title show-mobile" aria-hidden="true">{d.title}</div>
    <div class="deal-hero-top">
      <StageBadge stage={d.stage} />
      <EntityChip kind="client" id={d.clientId} label={d.clientName} clientType={d.clientType} />
      {#if arch}<span class="badge"><Icon name="archive" size={14} />Archived</span>{/if}
    </div>
    <dl class="deal-facts">
      <div class="deal-fact"><dt>Amount</dt><dd><span class="deal-fact-value is-mono" class:is-lost={lost}>{money(d.amount)}</span></dd></div>
      <div class="deal-fact"><dt>Expected close</dt><dd>{@render closeValue(d)}</dd></div>
    </dl>
    {@render progress(d)}
  </section>
  {#if lost}
    <section class="card lost-block" aria-labelledby="h-lost">
      <div class="lost-block-inner">
        <span class="lost-block-icon" aria-hidden="true"><Icon name="x" size={18} /></span>
        <div class="lost-block-body">
          <h2 class="lost-block-title" id="h-lost">Lost reason</h2>
          <p class="lost-block-text">{#if d.lostReason}{d.lostReason}{:else}<span class="text-muted">No reason was recorded.</span>{/if}</p>
          <p class="lost-block-meta">To reopen, move the deal back to an open stage.</p>
        </div>
        <button
          type="button"
          class="btn btn-secondary btn-sm"
          disabled={arch}
          title={arch ? 'Restore the deal to change its stage' : undefined}
          onclick={() => (moving = true)}><Icon name="restore" size={16} /><span>Reopen via Move to…</span></button>
      </div>
    </section>
  {/if}
{/snippet}

{#snippet details(d: Deal)}
  <dl class="dl deal-dl">
    <div class="dl-row"><dt><Icon name="layers" size={16} />Stage</dt><dd><StageBadge stage={d.stage} /></dd></div>
    <div class="dl-row"><dt><Icon name="dollar" size={16} />Amount</dt><dd><span class="mono">{money(d.amount)}</span></dd></div>
    <div class="dl-row">
      <dt><Icon name={d.clientType === 'company' ? 'building' : 'user'} size={16} />Client</dt>
      <dd><EntityChip kind="client" id={d.clientId} label={d.clientName} clientType={d.clientType} /></dd>
    </div>
    {#if contact}
      <div class="dl-row">
        <dt><Icon name="user" size={16} />Contact</dt>
        <dd>{contact.name}{#if contact.email}<a class="dd-sub" href="mailto:{contact.email}">{contact.email}</a>
          {:else if contact.phone}<a class="dd-sub" href="tel:{contact.phone}">{contact.phone}</a>{/if}</dd>
      </div>
    {:else if client?.email}
      <div class="dl-row"><dt><Icon name="mail" size={16} />Email</dt><dd><a href="mailto:{client.email}">{client.email}</a></dd></div>
    {/if}
    <div class="dl-row">
      <dt><Icon name="calendar" size={16} />Expected close</dt>
      <dd>
        {#if d.expectedClose}{date(d.expectedClose)}{#if isOverdue(d)}<span class="dd-sub" style="color:var(--danger);font-weight:600">Overdue</span>{/if}
        {:else}<span class="text-muted">Not set</span>{/if}
      </dd>
    </div>
    <div class="dl-row">
      <dt><Icon name="info" size={16} />Status</dt>
      <dd>{d.archived ? 'Archived' : isOpenStage(d.stage) ? 'Active' : `Closed (${stageLabel(d.stage)})`}</dd>
    </div>
    {#if d.stage === 'lost'}
      <div class="dl-row">
        <dt><Icon name="x" size={16} />Lost reason</dt>
        <dd>{#if d.lostReason}{d.lostReason}{:else}<span class="text-muted">Not recorded</span>{/if}</dd>
      </div>
    {/if}
  </dl>
{/snippet}

<div class="screen" data-screen="deal-detail">
  <Shell active="deals" {title} back={{ href: '/deals', label: 'Deals' }}>
    {#snippet actions()}
      {#if status === 'ready' && deal}
        <button type="button" class="btn btn-secondary btn-collapse" data-keep onclick={() => (editing = true)}><Icon name="edit" size={18} /><span class="btn-label">Edit</span></button>
        <button
          type="button"
          class="btn btn-primary"
          aria-haspopup="dialog"
          disabled={deal.archived}
          title={deal.archived ? 'Restore the deal to change its stage' : undefined}
          onclick={() => (moving = true)}><Icon name="move" size={18} /><span>Move to…</span></button>
        <button type="button" class="btn btn-secondary btn-icon" data-keep aria-label="More actions" aria-haspopup="menu" aria-expanded="false" onclick={overflow}
          ><Icon name="more" size={20} /></button>
      {/if}
    {/snippet}

    {#if status === 'loading'}
      <Skeleton type="detail" />
    {:else if status === 'error'}
      <div class="card">
        <ErrorState title="Couldn’t load deal" text="Something went wrong while loading this deal. Check your connection and try again." retryLabel="Retry" onretry={load} />
      </div>
    {:else if status === 'notfound' || !deal}
      <div class="card">
        <EmptyState icon="briefcase" title="This deal doesn’t exist" text="The link may be broken, or the deal was deleted together with its client.">
          {#snippet actions()}<a class="btn btn-primary" href="/deals">Go to deals</a>{/snippet}
        </EmptyState>
      </div>
    {:else}
      <div id="deal-summary">{@render summary(deal)}</div>
      <!-- svelte-ignore a11y_interactive_supports_focus -->
      <div class="tabs deal-tabs show-mobile" role="tablist" aria-label="Deal sections" onkeydown={onTabKey}>
        {#each TABS as t (t.id)}
          <button
            type="button"
            class="tab"
            role="tab"
            id="tab-{t.id}"
            aria-controls="panel-{t.id}"
            aria-selected={tab === t.id}
            tabindex={tab === t.id ? 0 : -1}
            onclick={() => void setTab(t.id)}>{t.label}{#if counts[t.id] !== null}<span class="tab-count">{counts[t.id]}</span>{/if}</button>
        {/each}
      </div>
      <div class="split deal-split">
        <div class="deal-main">
          <section class="card deal-panel" class:is-current={tab === 'interactions'} id="panel-interactions" aria-labelledby="h-interactions">
            <div class="card-header">
              <h2 class="card-title" id="h-interactions">Interactions</h2>
              <button type="button" class="btn btn-secondary btn-sm" onclick={() => (logging = true)}><Icon name="plus" size={16} /><span>Log interaction</span></button>
            </div>
            <div class="card-body" id="host-interactions">
              <Timeline
                items={ints}
                context="deal"
                emptyText="Log calls, meetings, emails and notes about this deal. They also appear in the client’s history."
                onchange={refreshLists} />
            </div>
          </section>
          <section class="card deal-panel" class:is-current={tab === 'tasks'} id="panel-tasks" aria-labelledby="h-tasks">
            <div class="card-header">
              <h2 class="card-title" id="h-tasks">Tasks</h2>
              <button type="button" class="btn btn-secondary btn-sm" onclick={() => (addingTask = true)}><Icon name="plus" size={16} /><span>Add task</span></button>
            </div>
            <div class="card-body is-flush deal-tasks" id="host-tasks">
              <TaskList tasks={tks} emptyText="Add a task with a due date so the next step doesn’t slip." onchange={refreshLists} />
            </div>
          </section>
        </div>
        <aside class="deal-side deal-panel" class:is-current={tab === 'overview'} id="panel-overview" aria-labelledby="h-details">
          <section class="card">
            <div class="card-header"><h2 class="card-title" id="h-details">Details</h2></div>
            <div class="card-body" id="deal-details">{@render details(deal)}</div>
          </section>
        </aside>
      </div>
    {/if}
  </Shell>
</div>

{#if deal}
  {#if editing}<DealForm {deal} onsaved={onEdited} onclose={() => (editing = false)} />{/if}
  {#if moving}<MoveSheet {deal} onmove={(s, r) => void move(s, r)} onclose={() => (moving = false)} />{/if}
  {#if logging}<InteractionForm ctx={{ dealId: deal.id }} onsaved={refreshLists} onclose={() => (logging = false)} />{/if}
  {#if addingTask}<TaskForm ctx={{ dealId: deal.id }} onsaved={refreshLists} onclose={() => (addingTask = false)} />{/if}
{/if}
