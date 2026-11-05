<script lang="ts">
  import { onMount, untrack } from 'svelte'
  import { clients as api, downloadCsv } from '../lib/api/crm'
  import type { Client, ClientSummary, ClientType } from '../lib/api/types'
  import { money, plural, relDate } from '../lib/format'
  import { navigate, params, route, setSearch } from '../lib/router.svelte'
  import { tagStore } from '../lib/stores.svelte'
  import { taskStatus } from '../lib/tasks'
  import Avatar from '../lib/ui/Avatar.svelte'
  import ClientDeleteDialog from '../lib/ui/ClientDeleteDialog.svelte'
  import ClientForm from '../lib/ui/ClientForm.svelte'
  import EmptyState from '../lib/ui/EmptyState.svelte'
  import ErrorState from '../lib/ui/ErrorState.svelte'
  import Icon from '../lib/ui/Icon.svelte'
  import { openMenu } from '../lib/ui/menu.svelte'
  import Shell from '../lib/ui/Shell.svelte'
  import Skeleton from '../lib/ui/Skeleton.svelte'
  import TagChip from '../lib/ui/TagChip.svelte'
  import { errorToast, toast } from '../lib/ui/toast.svelte'

  let { params: _params }: { params: Record<string, string> } = $props()

  /** Усі клієнти (для підсумків, порожнього стану й експорту). */
  let all = $state<ClientSummary[] | null>(null)
  /** Результати з урахуванням фільтрів (пошук і фільтри — на сервері). */
  let results = $state<ClientSummary[] | null>(null)
  let failed = $state(false)

  let q = $state('')
  let type = $state<ClientType | 'all'>('all')
  let tags = $state<string[]>([])
  let sort = $state<'asc' | 'desc'>('asc')

  let formOpen = $state(false)
  let editing = $state<Client | null>(null)
  let deleting = $state<{ id: number; name: string } | null>(null)
  let exporting = $state(false)
  let flashId = $state<number | null>(null)
  let searchEl = $state<HTMLInputElement>()
  let sortBtn = $state<HTMLButtonElement>()

  const hasFilters = $derived(!!(q.trim() || type !== 'all' || tags.length))
  let seq = 0

  async function loadAll() {
    failed = false
    try {
      const list = await api.list()
      all = list
      if (!hasFilters) results = list
      else await loadResults()
    } catch {
      failed = true
    }
  }

  async function loadResults() {
    const my = ++seq
    if (!hasFilters) {
      results = all
      return
    }
    try {
      const list = await api.list({ q, type, tag: tags })
      if (my === seq) results = list
    } catch (e) {
      if (my === seq) errorToast(e, 'Couldn’t load clients. Check your connection and try again.')
    }
  }

  /** Повторний запит при зміні фільтрів (пошук — з невеликою затримкою). */
  let timer: ReturnType<typeof setTimeout> | undefined
  function refilter(delay = 0) {
    clearTimeout(timer)
    timer = setTimeout(() => void loadResults(), delay)
  }

  onMount(() => {
    void loadAll()
    return () => clearTimeout(timer)
  })

  // /clients?new=1 — відкриває форму нового клієнта (з дашборда та форми угоди).
  $effect(() => {
    if (route.search && params().get('new') === '1') untrack(() => openNew())
  })

  function openNew() {
    editing = null
    formOpen = true
  }
  function closeForm() {
    formOpen = false
    editing = null
    if (params().has('new')) setSearch({ new: null })
  }

  const sorted = $derived.by(() => {
    if (!results) return []
    const list = results.slice().sort((a, b) => a.name.localeCompare(b.name))
    return sort === 'asc' ? list : list.reverse()
  })

  const subtitle = $derived.by(() => {
    if (!all) return undefined
    if (!all.length) return 'No clients yet'
    const co = all.filter((c) => c.type === 'company').length
    return `${plural(all.length, 'client')} · ${plural(co, 'company', 'companies')}, ${plural(all.length - co, 'person', 'people')}`
  })

  function clearFilters() {
    q = ''
    type = 'all'
    tags = []
    refilter()
    searchEl?.focus()
  }
  function setType(t: ClientType | 'all') {
    type = t
    refilter()
  }
  function toggleTag(name: string) {
    tags = tags.includes(name) ? tags.filter((t) => t !== name) : [...tags, name]
    refilter()
  }

  async function onSaved(c: Client, isNew: boolean) {
    if (isNew) {
      q = ''
      type = 'all'
      tags = []
    }
    await loadAll()
    flashId = c.id
    setTimeout(() => {
      if (flashId === c.id) flashId = null
    }, 1600)
    toast({
      type: 'success',
      text: isNew ? `“${c.name}” added to clients` : `Changes to “${c.name}” saved`,
      action: isNew ? { label: 'Open', onClick: () => navigate(`/clients/${c.id}`) } : undefined,
    })
  }

  async function edit(id: number) {
    try {
      editing = await api.get(id)
      formOpen = true
    } catch (e) {
      errorToast(e, 'Couldn’t load the client. Check your connection and try again.')
    }
  }

  async function exportCsv() {
    if (exporting) return
    exporting = true
    try {
      const n = await downloadCsv('clients')
      toast({ type: 'success', text: `Exported ${plural(n, 'client')} to clients.csv` })
    } catch (e) {
      errorToast(e, 'Couldn’t export clients. Check your connection and try again.')
    } finally {
      exporting = false
    }
  }

  function rowMenu(e: MouseEvent, c: ClientSummary) {
    e.preventDefault()
    e.stopPropagation()
    openMenu(e.currentTarget as HTMLElement, [
      { label: 'Open client card', icon: 'arrow-right', onClick: () => navigate(`/clients/${c.id}`) },
      { label: 'Edit', icon: 'edit', onClick: () => void edit(c.id) },
      { divider: true, label: '' },
      { label: 'Delete', icon: 'trash', danger: true, onClick: () => (deleting = { id: c.id, name: c.name }) },
    ])
  }

  function rowClick(e: MouseEvent, c: ClientSummary) {
    if ((e.target as HTMLElement).closest('a, button, input, label')) return
    if (String(window.getSelection?.() ?? '')) return
    navigate(`/clients/${c.id}`)
  }

  function nextTask(c: ClientSummary) {
    if (!c.nextTaskDue || !c.nextTaskTitle) return null
    return { title: c.nextTaskTitle, due: c.nextTaskDue, st: taskStatus({ done: false, due: c.nextTaskDue }) }
  }

  const overflow = $derived(all?.length ? [{ label: 'Export CSV', icon: 'download', onClick: () => void exportCsv() }] : [])
</script>

{#snippet typeLabel(c: ClientSummary)}
  <span class="type-label"><Icon name={c.type === 'company' ? 'building' : 'user'} size={14} />{c.type === 'company' ? 'Company' : 'Person'}</span>
{/snippet}

{#snippet dash(text: string)}<span class="muted-dash">{text}</span>{/snippet}

<div class="screen" data-screen="clients">
  <Shell active="clients" title="Clients" {subtitle} {overflow}>
    {#snippet actions()}
      <button type="button" class="btn btn-secondary" class:is-loading={exporting} aria-busy={exporting || undefined} disabled={!all?.length} onclick={exportCsv}
        ><Icon name="download" size={18} /><span>Export CSV</span></button>
      <button type="button" class="btn btn-primary btn-collapse" aria-label="New client" onclick={openNew}><Icon name="plus" size={18} /><span class="btn-label">New client</span></button>
    {/snippet}

    {#if failed}
      <div class="card">
        <ErrorState title="Couldn’t load clients" text="Something went wrong while loading your clients. Check your connection and try again." retryLabel="Retry" onretry={loadAll} />
      </div>
    {:else if !all || !results}
      <Skeleton type="table" />
    {:else if !all.length}
      <div class="card">
        <EmptyState icon="users" title="No clients yet" text="Add the people and companies you work with. Their deals, interactions and tasks will all live in one place.">
          {#snippet actions()}
            <button type="button" class="btn btn-primary" onclick={openNew}><Icon name="plus" size={18} />Add your first client</button>
          {/snippet}
        </EmptyState>
      </div>
    {:else}
      <div class="filter-bar client-filters" role="search">
        <label class="search"
          ><span class="sr-only">Search clients</span><Icon name="search" size={18} /><input
            class="input"
            type="search"
            id="client-search"
            placeholder="Search name, email or phone"
            autocomplete="off"
            bind:this={searchEl}
            bind:value={q}
            oninput={() => refilter(200)}
            onkeydown={(e) => {
              if (e.key === 'Escape' && q) {
                e.preventDefault()
                q = ''
                refilter()
              }
            }} /></label>
        <div class="segmented" role="group" aria-label="Client type">
          {#each [['all', 'All'], ['company', 'Companies'], ['person', 'People']] as const as [id, label]}
            <button type="button" class="segmented-item" aria-pressed={type === id} onclick={() => setType(id)}>{label}</button>
          {/each}
        </div>
        {#if tagStore.list.length}
          <div class="filter-bar-chips" role="group" aria-label="Filter by tag">
            <span class="filter-label">Tags</span>
            {#each tagStore.list as t (t.name)}
              {@const on = tags.includes(t.name)}
              <button type="button" class="chip-filter" aria-pressed={on} onclick={() => toggleTag(t.name)}
                >{#if on}<Icon name="check" size={16} />{:else}<span class="tag-dot" style="--tag-color:{t.color}"></span>{/if}{t.name}</button>
            {/each}
          </div>
        {/if}
        <div class="filter-bar-end">
          <button type="button" class="btn btn-ghost btn-sm" hidden={!hasFilters} onclick={clearFilters}><Icon name="x" size={16} />Clear filters</button>
        </div>
      </div>
      <p class="filter-summary" id="client-summary" aria-live="polite">
        <strong>{plural(sorted.length, 'client')}</strong>
        {#if hasFilters}
          <span>of {all.length}</span>
          {#if type !== 'all' || tags.length}
            <span aria-hidden="true">·</span>
            {#if type !== 'all'}<span>{type === 'company' ? 'Companies' : 'People'}</span>{/if}
            {#each tags as t (t)}<span><TagChip name={t} /></span>{/each}
          {/if}
        {/if}
      </p>
      <div id="client-results">
        {#if !sorted.length}
          {@const query = q.trim()}
          <div class="card">
            <EmptyState
              icon="search"
              title={query ? `No clients match “${query}”` : 'No clients match these filters'}
              text={query
                ? 'Check the spelling or search by email or phone instead. Filters may also be hiding results.'
                : 'Try another type or tag, or clear the filters to see all clients.'}>
              {#snippet actions()}
                <button type="button" class="btn btn-secondary" onclick={clearFilters}><Icon name="x" size={18} />Clear filters</button>
              {/snippet}
            </EmptyState>
          </div>
        {:else}
          <div class="table-wrap">
            <table class="table clients-table">
              <caption class="sr-only">Clients</caption>
              <thead>
                <tr>
                  <th scope="col" aria-sort={sort === 'asc' ? 'ascending' : 'descending'}
                    ><button type="button" class="th-sort" bind:this={sortBtn} onclick={() => { sort = sort === 'asc' ? 'desc' : 'asc'; sortBtn?.focus() }}
                      >Name<Icon name={sort === 'asc' ? 'arrow-up' : 'arrow-down'} size={14} /></button></th>
                  <th scope="col">Contact</th>
                  <th scope="col">Tags</th>
                  <th scope="col" class="num">Open deals</th>
                  <th scope="col" class="hide-tablet">Next task</th>
                  <th scope="col" class="col-actions"><span class="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {#each sorted as c (c.id)}
                  {@const t = nextTask(c)}
                  <tr data-id={c.id} style:background={flashId === c.id ? 'var(--accent-soft)' : undefined} onclick={(e) => rowClick(e, c)}>
                    <td class="col-name">
                      <div class="table-cell-main">
                        <Avatar name={c.name} square={c.type === 'company'} />
                        <div class="cell-stack"><a class="table-link" href="/clients/{c.id}">{c.name}</a>{@render typeLabel(c)}</div>
                      </div>
                    </td>
                    <td>
                      <div class="cell-stack contact-cell">
                        {#if c.email}<span>{c.email}</span>{:else}{@render dash('No email')}{/if}
                        {#if c.phone}<span class="mono">{c.phone}</span>{/if}
                      </div>
                    </td>
                    <td>
                      {#if c.tags.length}<div class="tag-list">{#each c.tags as tg (tg)}<TagChip name={tg} />{/each}</div>{:else}{@render dash('—')}{/if}
                    </td>
                    <td class="num deals-cell">
                      {#if c.openDealCount}
                        <div class="cell-stack"><span>{money(c.openDealValue)}</span><span class="deals-count">{plural(c.openDealCount, 'open deal')}</span></div>
                      {:else}{@render dash('—')}{/if}
                    </td>
                    <td class="hide-tablet">
                      {#if t}
                        <div class="next-task is-{t.st}">
                          <span class="next-task-title" title={t.title}>{t.title}</span>
                          <span class="due-chip"
                            >{#if t.st === 'overdue'}<Icon name="alert-circle" size={14} /><strong>Overdue</strong> · {relDate(t.due)}{:else if t.st === 'today'}<Icon
                                name="clock"
                                size={14} />Today{:else}<Icon name="calendar" size={14} />{relDate(t.due)}{/if}</span>
                        </div>
                      {:else}{@render dash('No open tasks')}{/if}
                    </td>
                    <td class="col-actions">
                      <button type="button" class="btn btn-ghost btn-icon btn-sm" aria-label="Actions for {c.name}" aria-haspopup="menu" aria-expanded="false" onclick={(e) => rowMenu(e, c)}
                        ><Icon name="more-vertical" size={18} /></button>
                    </td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
          <ul class="list-cards" aria-label="Clients">
            {#each sorted as c (c.id)}
              {@const t = nextTask(c)}
              <li class="list-card client-card" data-id={c.id} style:background={flashId === c.id ? 'var(--accent-soft)' : undefined}>
                <Avatar name={c.name} square={c.type === 'company'} />
                <div class="list-card-main">
                  <a class="list-card-title client-card-link" href="/clients/{c.id}">{c.name}</a>
                  <div class="list-card-meta">
                    {@render typeLabel(c)}
                    {#if c.email}<span class="truncate">{c.email}</span>{:else if c.phone}<span class="mono">{c.phone}</span>{/if}
                  </div>
                  {#if c.openDealCount || t}
                    <div class="list-card-meta">
                      {#if c.openDealCount}<span><Icon name="briefcase" size={14} /> {plural(c.openDealCount, 'open deal')} · <span class="mono">{money(c.openDealValue)}</span></span>{/if}
                      {#if t}<span class={t.st === 'overdue' ? 'is-overdue' : ''}
                          ><Icon name={t.st === 'overdue' ? 'alert-circle' : 'check-square'} size={14} />
                          {t.st === 'overdue' ? 'Overdue task' : 'Task ' + relDate(t.due).toLowerCase()}</span>{/if}
                    </div>
                  {/if}
                  {#if c.tags.length}<div class="tag-list">{#each c.tags as tg (tg)}<TagChip name={tg} />{/each}</div>{/if}
                </div>
                <button type="button" class="btn btn-ghost btn-icon btn-sm" aria-label="Actions for {c.name}" aria-haspopup="menu" aria-expanded="false" onclick={(e) => rowMenu(e, c)}
                  ><Icon name="more-vertical" size={18} /></button>
              </li>
            {/each}
          </ul>
        {/if}
      </div>
    {/if}
  </Shell>

  {#if formOpen}
    <ClientForm client={editing} onsaved={(c, isNew) => void onSaved(c, isNew)} onclose={closeForm} />
  {/if}
  {#if deleting}
    <ClientDeleteDialog client={deleting} ondeleted={() => void loadAll()} onclose={() => (deleting = null)} />
  {/if}
</div>
