<script lang="ts">
  import { onMount, tick, untrack } from 'svelte'
  import { tasks as api } from '../lib/api/crm'
  import type { Task } from '../lib/api/types'
  import { todayIso } from '../lib/format'
  import { params as query, setSearch } from '../lib/router.svelte'
  import { refreshBadges } from '../lib/session.svelte'
  import { taskStatus } from '../lib/tasks'
  import { confirm } from '../lib/ui/confirm.svelte'
  import EmptyState from '../lib/ui/EmptyState.svelte'
  import ErrorState from '../lib/ui/ErrorState.svelte'
  import Icon from '../lib/ui/Icon.svelte'
  import { openMenu } from '../lib/ui/menu.svelte'
  import Shell from '../lib/ui/Shell.svelte'
  import { errorToast, toast } from '../lib/ui/toast.svelte'
  import { buildGroups, countTasks, viewFromParam, viewOf, viewToParam, VIEWS, type Sticky, type TaskView } from './tasks/groups'
  import TaskFormLocal from './tasks/TaskFormLocal.svelte'
  import TaskItem from './tasks/TaskItem.svelte'

  let { params: _params }: { params: Record<string, string> } = $props()

  const TABS: Record<TaskView, string> = { overdue: 'Overdue', today: 'Today', upcoming: 'Upcoming', completed: 'Completed' }

  let list = $state<Task[] | null>(null)
  let failed = $state(false)
  let today = $state(todayIso())
  /** Форма: null — закрита; task null — нове завдання. */
  let form = $state<{ task: Task | null } | null>(null)
  let newId = $state<number | null>(null)
  /** Перемкнуті в поточній вкладці завдання лишаються на місці до зміни вкладки (як у макеті). */
  let sticky = $state<{ view: TaskView; map: Record<number, Sticky> }>({ view: 'today', map: {} })

  const view = $derived(viewFromParam(query().get('tab')))
  const stickyMap = $derived(sticky.view === view ? sticky.map : {})
  // Зміна вкладки «відпускає» перемкнуті завдання у їхні нові списки.
  $effect(() => {
    const v = view
    untrack(() => {
      if (sticky.view !== v || Object.keys(sticky.map).length) sticky = { view: v, map: {} }
    })
  })
  const counts = $derived(countTasks(list ?? [], today))
  const groups = $derived(list ? buildGroups(view, list, today, stickyMap) : [])

  async function load() {
    failed = false
    try {
      const data = await api.list()
      today = todayIso()
      list = data
    } catch {
      failed = true
      list = null
    }
  }
  onMount(load)

  // /tasks?new=1 відкриває форму нового завдання; параметр прибираємо.
  $effect(() => {
    if (query().get('new') !== '1') return
    form = { task: null }
    setSearch({ new: null })
  })

  // Мобільна «рейка» вкладок: вибрана вкладка має бути видимою (напр. при переході з ?tab=done).
  $effect(() => {
    const v = view
    if (!list) return
    void tick().then(() => {
      const el = document.getElementById(`tab-${v}`)
      const rail = el?.parentElement
      if (!el || !rail || rail.scrollWidth <= rail.clientWidth) return
      const l = el.offsetLeft - rail.offsetLeft
      if (l < rail.scrollLeft || l + el.offsetWidth > rail.scrollLeft + rail.clientWidth) rail.scrollLeft = l - 16
    })
  })

  async function setView(v: TaskView, focusTab = false) {
    setSearch({ tab: viewToParam(v) })
    if (!focusTab) return
    await tick()
    const el = document.getElementById(`tab-${v}`)
    el?.focus()
    el?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  }

  function onTabKey(e: KeyboardEvent) {
    let i = VIEWS.indexOf(view)
    if (e.key === 'ArrowRight') i = (i + 1) % VIEWS.length
    else if (e.key === 'ArrowLeft') i = (i - 1 + VIEWS.length) % VIEWS.length
    else if (e.key === 'Home') i = 0
    else if (e.key === 'End') i = VIEWS.length - 1
    else return
    e.preventDefault()
    void setView(VIEWS[i], true)
  }

  function replace(t: Task) {
    if (!list) return
    const i = list.findIndex((x) => x.id === t.id)
    if (i >= 0) list[i] = t
  }

  async function setDone(t: Task, done: boolean, withToast = true) {
    if (sticky.view !== view) sticky = { view, map: {} }
    if (!(t.id in sticky.map)) sticky.map[t.id] = { status: taskStatus(t, today), completedAt: t.completedAt }
    const before = list?.find((x) => x.id === t.id) ?? t
    replace({ ...before, done, completedAt: done ? new Date().toISOString() : null })
    try {
      replace(await api.setDone(t.id, done))
      void refreshBadges()
      if (withToast) {
        if (done) toast({ type: 'success', text: 'Task completed', action: { label: 'Undo', onClick: () => void setDone(t, false, false) } })
        else toast({ type: 'success', text: 'Task marked as active' })
      }
    } catch (e) {
      replace(before)
      errorToast(e)
    }
  }

  function menu(e: MouseEvent, t: Task) {
    openMenu(e.currentTarget as HTMLElement, [
      { label: 'Edit task', icon: 'edit', onClick: () => (form = { task: t }) },
      {
        label: 'Delete task', icon: 'trash', danger: true,
        onClick: async () => {
          const ok = await confirm({ title: 'Delete task?', text: `“${t.title}” will be permanently deleted. This can’t be undone.`, confirmLabel: 'Delete task', danger: true })
          if (!ok) return
          try {
            await api.remove(t.id)
            if (list) list = list.filter((x) => x.id !== t.id)
            void refreshBadges()
            toast({ type: 'success', text: 'Task deleted' })
          } catch (err) {
            errorToast(err)
          }
        },
      },
    ])
  }

  async function saved(t: Task, isEdit: boolean) {
    if (!list) return void load()
    if (isEdit) replace(t)
    else list.push(t)
    if (!isEdit) {
      const target = viewOf(taskStatus(t, today))
      if (target !== view && !(view === 'today' && target === 'overdue')) await setView(target)
    }
    newId = t.id
    await tick()
    document.querySelector(`#task-panel .task-item[data-id="${t.id}"]`)?.scrollIntoView({ block: 'nearest' })
    setTimeout(() => {
      if (newId === t.id) newId = null
    }, 1700)
  }

  const W_ROWS = ['62%', '48%', '70%', '55%', '66%', '44%']
</script>

{#snippet sk(w: string)}<span class="skeleton skeleton-text" style="--w:{w}"></span>{/snippet}
{#snippet skRows(ws: string[])}
  {#each ws as w}<div class="sk-task"><span class="skeleton sk-box"></span><div style="flex:1;display:grid;gap:8px">{@render sk(w)}{@render sk('34%')}</div></div>{/each}
{/snippet}

<div class="screen" data-screen="tasks">
  <Shell active="tasks" title="Tasks" subtitle="Reminders live here: overdue and today’s tasks also count in the sidebar.">
    {#snippet actions()}
      <button type="button" class="btn btn-primary" onclick={() => (form = { task: null })}><Icon name="plus" size={18} /><span class="btn-label">New task</span></button>
    {/snippet}

    {#if failed}
      <div class="card">
        <ErrorState title="Couldn’t load tasks" text="Something went wrong while loading your tasks. Check your connection and try again." retryLabel="Retry" onretry={load} />
      </div>
    {:else if !list}
      <div class="skeleton-wrap" aria-busy="true">
        <span class="sr-only" role="status">Loading tasks…</span>
        <div class="tasks-toolbar">
          <div class="od-row" style="--od-gap:16px;padding:12px 0">
            <span class="skeleton sk-tab"></span><span class="skeleton sk-tab"></span><span class="skeleton sk-tab"></span><span class="skeleton sk-tab"></span>
          </div>
        </div>
        <div class="task-groups">
          <div class="card"><div class="card-header">{@render sk('18%')}</div>{@render skRows(W_ROWS.slice(0, 3))}</div>
          <div class="card"><div class="card-header">{@render sk('24%')}</div>{@render skRows(W_ROWS.slice(3))}</div>
        </div>
      </div>
    {:else}
      <div class="tasks-toolbar">
        <div class="tabs" role="tablist" aria-label="Task views" tabindex="-1" onkeydown={onTabKey}>
          {#each VIEWS as v (v)}
            {@const sel = v === view}
            {@const n = counts[v]}
            {@const od = v === 'overdue'}
            <button
              type="button"
              class="tab"
              class:is-overdue={od}
              class:has-items={od && !!n}
              role="tab"
              id="tab-{v}"
              aria-selected={sel}
              aria-controls="task-panel"
              tabindex={sel ? 0 : -1}
              onclick={() => void setView(v)}>
              {#if od && n}<Icon name="alert-circle" size={16} />{/if}<span>{TABS[v]}</span>
              <span class="tab-count" class:is-danger={od && !!n} class:is-strong={od && !!n}
                ><span class="sr-only">(</span>{n}<span class="sr-only">{od ? ' overdue' : ''})</span></span>
            </button>
          {/each}
        </div>
        <p class="tasks-summary">
          {#if list.length}
          {#if counts.overdue}<span class="is-danger"><strong class="is-danger">{counts.overdue}</strong> overdue</span>{' · '}{/if}<strong
            >{counts.today}</strong> due today{' · '}<strong>{counts.upcoming}</strong> upcoming
          {/if}
        </p>
      </div>
      <div id="task-panel" role="tabpanel" aria-labelledby="tab-{view}" tabindex="-1">
        {#if !list.length}
          <div class="card">
            <EmptyState
              icon="check-square"
              title="No tasks yet"
              text="Create tasks with a due date and link them to a client or deal. Overdue and today’s tasks show up as reminders here and in the sidebar.">
              {#snippet actions()}
                <button type="button" class="btn btn-primary" onclick={() => (form = { task: null })}><Icon name="plus" size={18} /><span>New task</span></button>
              {/snippet}
            </EmptyState>
          </div>
        {:else}
        <div class="task-groups">
          {#each groups as g (g.key)}
            <section class="card task-group" class:is-overdue={g.danger} aria-label={g.title}>
              <div class="card-header">
                <h2 class="task-group-title">
                  <Icon name={g.icon} size={18} /><span>{g.title}</span>
                  {#if g.range}<span class="task-group-range">{g.range}</span>{/if}
                </h2>
                {#if g.tasks.length}<span class="badge badge-count" class:badge-danger={g.danger} aria-label="{g.tasks.length} tasks">{g.tasks.length}</span>{/if}
                {#if g.viewAll}
                  <button type="button" class="btn btn-ghost btn-sm" style="margin-left:auto" onclick={() => void setView('overdue', true)}
                    >View all<span class="sr-only"> overdue tasks</span></button>
                {/if}
              </div>
              {#if g.tasks.length}
                <ul class="task-list" aria-label={g.title}>
                  {#each g.tasks as t (t.id)}
                    <TaskItem task={t} {today} isNew={newId === t.id} ontoggle={(x, d) => void setDone(x, d)} onmenu={menu} />
                  {/each}
                </ul>
              {:else if g.empty}
                <EmptyState compact icon={g.empty.icon} title={g.empty.title} text={g.empty.text} />
              {/if}
            </section>
          {/each}
        </div>
        {/if}
      </div>
    {/if}
  </Shell>
  {#if form}
    {@const isEdit = !!form.task}
    <TaskFormLocal
      task={form.task}
      onsaved={(t) => void saved(t, isEdit)}
      onclose={() => (form = null)} />
  {/if}
</div>
