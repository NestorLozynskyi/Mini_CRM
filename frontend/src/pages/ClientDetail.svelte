<script lang="ts">
  import { onMount, untrack, type Snippet } from 'svelte'
  import { ApiError } from '../lib/api/client'
  import { clients as api, deals as dealsApi, interactions as interactionsApi, tasks as tasksApi } from '../lib/api/crm'
  import type { Client, Contact, Deal, Interaction, Task } from '../lib/api/types'
  import { date, dateShort, money } from '../lib/format'
  import { navigate } from '../lib/router.svelte'
  import { STAGES, isOpenStage } from '../lib/stages'
  import Avatar from '../lib/ui/Avatar.svelte'
  import ClientDeleteDialog from '../lib/ui/ClientDeleteDialog.svelte'
  import ClientForm from '../lib/ui/ClientForm.svelte'
  import { confirm } from '../lib/ui/confirm.svelte'
  import EmptyState from '../lib/ui/EmptyState.svelte'
  import ErrorState from '../lib/ui/ErrorState.svelte'
  import Icon from '../lib/ui/Icon.svelte'
  import LogInteractionForm from './clients/LogInteractionForm.svelte'
  import { openMenu } from '../lib/ui/menu.svelte'
  import Shell from '../lib/ui/Shell.svelte'
  import Skeleton from '../lib/ui/Skeleton.svelte'
  import StageBadge from '../lib/ui/StageBadge.svelte'
  import TagChip from '../lib/ui/TagChip.svelte'
  import TaskForm from '../lib/ui/TaskForm.svelte'
  import TaskList from '../lib/ui/TaskList.svelte'
  import Timeline from '../lib/ui/Timeline.svelte'
  import { errorToast, toast } from '../lib/ui/toast.svelte'
  import ContactForm from './clients/ContactForm.svelte'

  let { params }: { params: Record<string, string> } = $props()
  // Сторінка перемонтовується при зміні шляху (key route.path), тож id сталий.
  const id = untrack(() => Number(params.id))

  let client = $state<Client | null>(null)
  let deals = $state<Deal[]>([])
  let interactions = $state<Interaction[]>([])
  let tasks = $state<Task[]>([])
  let status = $state<'loading' | 'ready' | 'notFound' | 'error'>('loading')

  type Tab = 'overview' | 'deals' | 'activity' | 'tasks'
  let tab = $state<Tab>('overview')

  let editOpen = $state(false)
  let deleteOpen = $state(false)
  let logOpen = $state(false)
  let taskOpen = $state(false)
  let contactForm = $state<{ contact: Contact | null } | null>(null)

  async function load() {
    if (!Number.isInteger(id) || id <= 0) {
      status = 'notFound'
      return
    }
    status = client ? status : 'loading'
    try {
      const [c, d, i, t] = await Promise.all([
        api.get(id),
        dealsApi.list({ clientId: id, scope: 'all' }),
        interactionsApi.list({ clientId: id }),
        tasksApi.list({ clientId: id }),
      ])
      client = c
      deals = d
      interactions = i
      tasks = t
      status = 'ready'
    } catch (e) {
      status = e instanceof ApiError && e.status === 404 ? 'notFound' : 'error'
    }
  }
  onMount(load)

  async function reloadClient() {
    try {
      client = await api.get(id)
    } catch (e) {
      errorToast(e, 'Couldn’t refresh the client. Reload the page to see the latest data.')
    }
  }
  async function reloadInteractions() {
    try {
      interactions = await interactionsApi.list({ clientId: id })
    } catch (e) {
      errorToast(e, 'Couldn’t refresh interactions. Reload the page to see the latest data.')
    }
  }
  async function reloadTasks() {
    try {
      tasks = await tasksApi.list({ clientId: id })
    } catch (e) {
      errorToast(e, 'Couldn’t refresh tasks. Reload the page to see the latest data.')
    }
  }

  const isCompany = $derived(client?.type === 'company')
  const ORDER = STAGES.map((s) => s.id)
  const activeDeals = $derived(
    deals
      .filter((d) => !d.archived)
      .sort((a, b) => {
        const ca = !isOpenStage(a.stage)
        const cb = !isOpenStage(b.stage)
        if (ca !== cb) return ca ? 1 : -1
        return ORDER.indexOf(b.stage) - ORDER.indexOf(a.stage)
      }),
  )
  const openDeals = $derived(activeDeals.filter((d) => isOpenStage(d.stage)))
  const openSum = $derived(openDeals.reduce((s, d) => s + d.amount, 0))
  const archivedCount = $derived(deals.filter((d) => d.archived).length)
  const openTasks = $derived(tasks.filter((t) => !t.done).length)

  const TABS = $derived<{ id: Tab; label: string; count: number }[]>([
    { id: 'overview', label: 'Overview', count: 0 },
    { id: 'deals', label: 'Deals', count: activeDeals.length },
    { id: 'activity', label: 'Activity', count: interactions.length },
    { id: 'tasks', label: 'Tasks', count: openTasks },
  ])

  function tabKey(e: KeyboardEvent) {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return
    const i = TABS.findIndex((t) => t.id === tab)
    const next = TABS[(i + (e.key === 'ArrowRight' ? 1 : TABS.length - 1)) % TABS.length]
    tab = next.id
    ;(e.currentTarget as HTMLElement).parentElement?.querySelector<HTMLElement>(`[data-tab="${next.id}"]`)?.focus()
  }

  function moreMenu(e: MouseEvent) {
    openMenu(e.currentTarget as HTMLElement, [
      { label: 'Log interaction', icon: 'plus', onClick: () => (logOpen = true) },
      { label: 'Add task', icon: 'check-square', onClick: () => (taskOpen = true) },
      { divider: true, label: '' },
      { label: 'Delete client', icon: 'trash', danger: true, onClick: () => (deleteOpen = true) },
    ])
  }

  function contactMenu(e: MouseEvent, p: Contact) {
    openMenu(e.currentTarget as HTMLElement, [
      { label: 'Edit', icon: 'edit', onClick: () => (contactForm = { contact: p }) },
      { label: 'Delete', icon: 'trash', danger: true, onClick: () => void deleteContact(p) },
    ])
  }

  async function deleteContact(p: Contact) {
    if (!client) return
    const ok = await confirm({
      title: `Delete ${p.name}?`,
      text: `${p.name} will be removed from ${client.name}’s contact persons. The client, its deals, interactions and tasks are not affected. This can’t be undone.`,
      confirmLabel: 'Delete contact',
      danger: true,
    })
    if (!ok) return
    try {
      await api.removeContact(p.id)
      if (client) client.contacts = client.contacts.filter((x) => x.id !== p.id)
      toast({ type: 'success', text: `${p.name} deleted` })
    } catch (e) {
      errorToast(e)
    }
  }

  function onContactSaved(c: Contact, isNew: boolean) {
    if (!client) return
    client.contacts = isNew ? [...client.contacts, c] : client.contacts.map((x) => (x.id === c.id ? c : x))
  }

  function onClientSaved(c: Client) {
    client = c
    toast({ type: 'success', text: `Changes to “${c.name}” saved` })
  }

  const tel = (s: string) => 'tel:' + s.replace(/[^\d+]/g, '')
</script>

{#snippet sectionHeader(title: string, hid: string, count: number)}
  <h2 class="card-title" id={hid}>{title}{#if count}<span class="badge badge-count">{count}</span>{/if}</h2>
{/snippet}

{#snippet dlRow(icon: string, label: string, value: Snippet | null)}
  <div class="dl-row">
    <dt><Icon name={icon} size={16} />{label}</dt>
    {#if value}<dd>{@render value()}</dd>{:else}<dd class="is-empty">Not added</dd>{/if}
  </div>
{/snippet}

{#snippet hero()}
  {#if client}
    <div class="client-hero">
      <Avatar name={client.name} size="xl" square={isCompany} />
      <div class="client-hero-text">
        <h1 class="page-title">{client.name}</h1>
        <div class="client-hero-meta">
          <span class="badge badge-outline"><Icon name={isCompany ? 'building' : 'user'} size={14} />{isCompany ? 'Company' : 'Person'}</span>
          {#if client.tags.length}<span class="tag-list">{#each client.tags as t (t)}<TagChip name={t} />{/each}</span>{/if}
          <span class="client-hero-since">Client since {date(client.createdAt)}</span>
        </div>
      </div>
    </div>
  {/if}
{/snippet}

{#snippet logButton()}
  <button type="button" class="btn btn-secondary btn-sm" onclick={() => (logOpen = true)}><Icon name="plus" size={16} />Log interaction</button>
{/snippet}
{#snippet taskButton()}
  <button type="button" class="btn btn-secondary btn-sm" onclick={() => (taskOpen = true)}><Icon name="plus" size={16} />Add task</button>
{/snippet}

<div class="screen" data-screen="client-detail">
  <Shell
    active="clients"
    title={client?.name ?? (status === 'notFound' ? 'Client not found' : 'Client')}
    back={{ href: '/clients', label: 'Clients' }}
    heading={client ? hero : undefined}>
    {#snippet actions()}
      {#if client}
        <button type="button" class="btn btn-secondary btn-collapse" data-keep aria-label="Edit client" onclick={() => (editOpen = true)}
          ><Icon name="edit" size={18} /><span class="btn-label">Edit</span></button>
        <button type="button" class="btn btn-secondary btn-icon" data-keep aria-label="More actions" aria-haspopup="menu" aria-expanded="false" onclick={moreMenu}
          ><Icon name="more" size={20} /></button>
      {/if}
    {/snippet}

    {#if status === 'loading'}
      <Skeleton type="detail" />
    {:else if status === 'error'}
      <div class="card">
        <ErrorState title="Couldn’t load client" text="Something went wrong while loading this client. Check your connection and try again." retryLabel="Retry" onretry={load} />
      </div>
    {:else if status === 'notFound' || !client}
      <div class="card">
        <EmptyState icon="users" title="This client doesn’t exist" text="This client doesn’t exist or was deleted. It may have been removed together with its deals and tasks.">
          {#snippet actions()}<a class="btn btn-primary" href="/clients"><Icon name="arrow-left" size={18} />Back to clients</a>{/snippet}
        </EmptyState>
      </div>
    {:else}
      {@const c = client}
      <div class="tabs client-tabs show-mobile" role="tablist" aria-label="Client sections">
        {#each TABS as t (t.id)}
          <button
            type="button"
            class="tab"
            role="tab"
            data-tab={t.id}
            aria-selected={tab === t.id}
            tabindex={tab === t.id ? 0 : -1}
            onclick={() => (tab = t.id)}
            onkeydown={tabKey}>{t.label}{#if t.count}<span class="tab-count">{t.count}</span>{/if}</button>
        {/each}
      </div>
      <div class="client-layout" data-tab={tab}>
        <aside class="client-aside" class:has-contacts={isCompany} data-panel="overview" aria-label="Client details">
          <section class="card" aria-labelledby="h-details">
            <div class="card-header">
              <h2 class="card-title" id="h-details">Contact details</h2>
              <button type="button" class="btn btn-ghost btn-sm" onclick={() => (editOpen = true)}><Icon name="edit" size={16} />Edit</button>
            </div>
            <div class="card-body">
              <dl class="dl">
                {#snippet phoneV()}<a class="mono" href={tel(c.phone ?? '')}>{c.phone}</a>{/snippet}
                {#snippet emailV()}<a href="mailto:{c.email}">{c.email}</a>{/snippet}
                {#snippet addressV()}{c.address}{/snippet}
                {#snippet webV()}<a class="dl-link" href="https://{c.website}" target="_blank" rel="noopener" data-external>{c.website}<Icon name="external-link" size={14} /></a>{/snippet}
                {#snippet notesV()}<span class="dl-notes">{c.notes}</span>{/snippet}
                {@render dlRow('phone', 'Phone', c.phone ? phoneV : null)}
                {@render dlRow('mail', 'Email', c.email ? emailV : null)}
                {@render dlRow('map-pin', 'Address', c.address ? addressV : null)}
                {@render dlRow('globe', 'Website', c.website ? webV : null)}
                {@render dlRow('note', 'Notes', c.notes ? notesV : null)}
              </dl>
            </div>
          </section>

          {#if isCompany}
            <section class="card" aria-labelledby="h-contacts">
              <div class="card-header">
                {@render sectionHeader('Contact persons', 'h-contacts', c.contacts.length)}
                {#if c.contacts.length}
                  <button type="button" class="btn btn-secondary btn-sm" onclick={() => (contactForm = { contact: null })}><Icon name="user-plus" size={16} />Add</button>
                {/if}
              </div>
              <div class="card-body is-flush">
                {#if c.contacts.length}
                  <ul class="row-list">
                    {#each c.contacts as p (p.id)}
                      <li class="row-item" data-contact={p.id}>
                        <Avatar name={p.name} />
                        <div class="row-item-main">
                          <span class="row-item-title">{p.name}</span>
                          <div class="row-item-meta">
                            {#if p.phone}<a class="mono" href={tel(p.phone)}><Icon name="phone" size={14} />{p.phone}</a>{/if}
                            {#if p.email}<a href="mailto:{p.email}"><Icon name="mail" size={14} /><span class="truncate">{p.email}</span></a>{/if}
                          </div>
                        </div>
                        <button type="button" class="btn btn-ghost btn-icon btn-sm" aria-label="Actions for {p.name}" aria-haspopup="menu" aria-expanded="false" onclick={(e) => contactMenu(e, p)}
                          ><Icon name="more-vertical" size={18} /></button>
                      </li>
                    {/each}
                  </ul>
                {:else}
                  <EmptyState icon="users" compact title="No contact persons yet" text="Add the people you talk to at {c.name}.">
                    {#snippet actions()}
                      <button type="button" class="btn btn-secondary btn-sm" onclick={() => (contactForm = { contact: null })}><Icon name="user-plus" size={16} />Add contact person</button>
                    {/snippet}
                  </EmptyState>
                {/if}
              </div>
            </section>
          {/if}
        </aside>

        <div class="client-main">
          <section class="card" data-panel="deals" aria-labelledby="h-deals">
            <div class="card-header">
              <div>
                {@render sectionHeader('Deals', 'h-deals', activeDeals.length)}
                {#if openDeals.length}<p class="deals-total">{openDeals.length} open · <span class="mono">{money(openSum)}</span></p>{/if}
              </div>
              {#if activeDeals.length}
                <a class="btn btn-secondary btn-sm" href="/deals?new=1&client={c.id}"><Icon name="plus" size={16} />New deal</a>
              {/if}
            </div>
            <div class="card-body is-flush">
              {#if activeDeals.length}
                <ul class="row-list">
                  {#each activeDeals as d (d.id)}
                    {@const closed = !isOpenStage(d.stage)}
                    {@const when = closed ? d.stageChangedAt : d.expectedClose}
                    <li class="row-item deal-row" class:is-closed={closed}>
                      <div class="row-item-main">
                        <a class="row-item-title deal-row-link" href="/deals/{d.id}">{d.title}</a>
                        <div class="row-item-meta">
                          <StageBadge stage={d.stage} />
                          {#if when}<span><Icon name="calendar" size={14} />{closed ? 'Closed ' : 'Expected close '}{dateShort(when)}</span>{/if}
                        </div>
                      </div>
                      <span class="deal-row-amount">{money(d.amount)}</span>
                    </li>
                  {/each}
                </ul>
              {:else}
                <EmptyState icon="briefcase" compact title="No deals yet" text="Create a deal to track what you’re selling to this client.">
                  {#snippet actions()}
                    <a class="btn btn-secondary btn-sm" href="/deals?new=1&client={c.id}"><Icon name="plus" size={16} />New deal</a>
                  {/snippet}
                </EmptyState>
              {/if}
            </div>
            {#if archivedCount}
              <div class="card-footer is-start">
                <Icon name="archive" size={16} /><span>{archivedCount} archived {archivedCount === 1 ? 'deal' : 'deals'} hidden ·</span><a href="/deals/list?scope=archived">View archive</a>
              </div>
            {/if}
          </section>

          <section class="card" data-panel="activity" aria-labelledby="h-activity">
            <div class="card-header">
              {@render sectionHeader('Interactions', 'h-activity', interactions.length)}
              {#if interactions.length}{@render logButton()}{/if}
            </div>
            <div class="card-body" class:is-flush={!interactions.length}>
              <Timeline
                items={interactions}
                showLink
                context="client"
                emptyTitle="No interactions yet"
                emptyText="Log calls, meetings, emails and notes with this client. Interactions on its deals show up here too."
                emptyAction={logButton}
                deleteLabel="Delete interaction"
                deleteText={(it, t) =>
                  `This ${t.toLowerCase()} from ${date(it.date)} will be permanently removed from ${client?.name}’s history` +
                  (it.dealTitle ? ` and from the “${it.dealTitle}” deal` : '') + '. This can’t be undone.'}
                onchange={reloadInteractions} />
            </div>
          </section>

          <section class="card client-tasks" data-panel="tasks" aria-labelledby="h-tasks" id="tasks-card">
            <div class="card-header">
              {@render sectionHeader('Tasks', 'h-tasks', openTasks)}
              {#if tasks.length}{@render taskButton()}{/if}
            </div>
            <div class="card-body is-flush">
              <TaskList
                {tasks}
                showLink
                emptyTitle="No tasks yet"
                emptyText="Add a task with a deadline so the next step with this client doesn’t slip."
                emptyAction={taskButton}
                onchange={reloadTasks} />
            </div>
          </section>
        </div>
      </div>
    {/if}
  </Shell>

  {#if client}
    {#if editOpen}
      <ClientForm client={client} onsaved={onClientSaved} onclose={() => (editOpen = false)} />
    {/if}
    {#if deleteOpen}
      <ClientDeleteDialog client={client} ondeleted={() => navigate('/clients', { replace: true })} onclose={() => (deleteOpen = false)} />
    {/if}
    {#if logOpen}
      <LogInteractionForm {client} {deals} onsaved={() => void reloadInteractions()} onclose={() => (logOpen = false)} />
    {/if}
    {#if taskOpen}
      <TaskForm ctx={{ clientId: client.id }} onsaved={() => void reloadTasks()} onclose={() => (taskOpen = false)} />
    {/if}
    {#if contactForm}
      <ContactForm clientId={client.id} clientName={client.name} contact={contactForm.contact} onsaved={onContactSaved} onclose={() => (contactForm = null)} />
    {/if}
  {/if}
</div>

<style>
  /* Як у макеті (tidyTaskChips): завдання в картці клієнта не показують чип із посиланням на цього ж клієнта,
     а рядки мають відступи .task-list.in-card. */
  .client-tasks :global(.task-list .entity-chip[href^='/clients/']) {
    display: none;
  }
  .client-tasks :global(.task-list .task-item) {
    padding-left: var(--sp-3);
    padding-right: var(--sp-4);
  }
</style>
