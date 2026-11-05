<script lang="ts">
  import { onMount } from 'svelte'
  import { dashboard as api } from '../lib/api/crm'
  import type { Dashboard } from '../lib/api/types'
  import { date, money, plural, todayIso, weekday } from '../lib/format'
  import ErrorState from '../lib/ui/ErrorState.svelte'
  import Icon from '../lib/ui/Icon.svelte'
  import Shell from '../lib/ui/Shell.svelte'
  import Skeleton from '../lib/ui/Skeleton.svelte'
  import StageChart from '../lib/ui/StageChart.svelte'
  import TaskList from '../lib/ui/TaskList.svelte'
  import Timeline from '../lib/ui/Timeline.svelte'

  let data = $state<Dashboard | null>(null)
  let failed = $state(false)

  async function load() {
    failed = false
    try {
      data = await api.get()
    } catch {
      failed = true
    }
  }
  onMount(load)

  const today = todayIso()
  const subtitle = `${weekday(today)}, ${date(today)}`
  const openToday = $derived(data?.tasks.todayOpen ?? 0)
  const todayTotal = $derived((data?.tasks.todayOpen ?? 0) + (data?.tasks.todayDone ?? 0))
  const overdue = $derived(data?.tasks.overdue ?? 0)
  const W_BARS = ['70%', '45%', '55%', '30%', '60%', '20%']
  const W_ROWS = ['70%', '55%', '62%', '48%']
</script>

{#snippet sk(w: string)}<span class="skeleton skeleton-text" style="--w:{w}"></span>{/snippet}

{#snippet cardHeader(title: string, id: string, sub: string | null, href: string | null, linkLabel: string, count?: number)}
  <div class="card-header">
    <div class="card-heading">
      <h2 class="card-title"
        ><span {id}>{title}</span>{#if count !== undefined}
          <span class="badge badge-count" id="today-count" hidden={!count}><span class="sr-only">Open: </span>{count}</span>{/if}</h2>
      {#if sub}<p class="card-subtitle">{sub}</p>{/if}
    </div>
    {#if href}<a class="btn-link" {href}>{linkLabel}<Icon name="arrow-right" size={16} /></a>{/if}
  </div>
{/snippet}

{#snippet kpis(d: Dashboard)}
  <section aria-label="Key figures">
    <div class="kpi-grid">
      <div class="kpi">
        <span class="kpi-label"><Icon name="briefcase" size={18} />Active deals</span>
        <span class="kpi-value">{d.activeDeals}</span>
        <span class="kpi-meta">{d.activeDeals ? 'Excludes won, lost and archived' : 'No open deals yet'}</span>
      </div>
      <div class="kpi">
        <span class="kpi-label"><Icon name="dollar" size={18} />Active pipeline value</span>
        <span class="kpi-value">{money(d.activeValue)}</span>
        <span class="kpi-meta">{d.activeDeals ? 'Sum of ' + plural(d.activeDeals, 'open deal') : 'Sum of open deals'}</span>
      </div>
      <div class="kpi" id="kpi-tasks">
        <span class="kpi-label"><Icon name="calendar-check" size={18} />Tasks due today</span>
        <span class="kpi-value">{openToday}</span>
        <span class="kpi-meta"
          >{todayTotal ? `${d.tasks.todayDone} of ${todayTotal} completed` : 'Nothing due today'}{#if overdue}{' · '}<a
              class="is-overdue"
              href="/tasks?tab=overdue">{overdue} overdue</a>{/if}</span>
      </div>
    </div>
  </section>
{/snippet}

{#snippet chartCard(d: Dashboard, empty: boolean)}
  <section class="card" aria-labelledby="chart-title">
    {@render cardHeader('Deals by stage', 'chart-title', 'Count and total value per stage · archived deals excluded', '/deals', 'Open board')}
    <div class="card-body">
      {#if !empty}
        <p class="chart-summary">
          <span><strong>{d.boardDeals}</strong> deals on the board</span>
          <span><strong>{money(d.wonValue)}</strong> won</span>
        </p>
      {/if}
      <StageChart stats={d.stages} />
      {#if empty}<p class="text-sm text-muted" style="margin-top:var(--sp-4)">Your pipeline will appear here once you create deals.</p>{/if}
    </div>
  </section>
{/snippet}

{#snippet tasksCard(d: Dashboard, empty: boolean)}
  <section class="card" aria-labelledby="tasks-title">
    {@render cardHeader('Today’s tasks', 'tasks-title', null, '/tasks', 'All tasks', openToday)}
    <div class="card-body is-flush" id="today-tasks">
      <TaskList
        tasks={d.todayTasks}
        showLink
        padded
        emptyTitle="No tasks due today"
        emptyText={empty ? 'Tasks with today’s deadline will show up here.' : 'You’re all caught up for today.'}
        onchange={load} />
    </div>
  </section>
{/snippet}

<div class="screen" data-screen="dashboard">
  <Shell active="dashboard" title="Dashboard" {subtitle}>
    {#snippet actions()}
      <a class="btn btn-primary btn-collapse" href="/deals?new=1"><Icon name="plus" size={18} /><span class="btn-label">New deal</span></a>
    {/snippet}

    {#if failed}
      <div class="card">
        <ErrorState title="Couldn’t load dashboard" text="Something went wrong while loading your deals and tasks. Check your connection and try again." retryLabel="Retry" onretry={load} />
      </div>
    {:else if !data}
      <div class="dash" aria-busy="true">
        <Skeleton type="kpis" />
        <div class="dash-grid">
          <div class="card">
            <div class="card-header">{@render sk('30%')}</div>
            <div class="card-body sk-chart">{#each W_BARS as w}<div class="sk-bar">{@render sk('80%')}{@render sk(w)}{@render sk('90%')}</div>{/each}</div>
          </div>
          <div class="card">
            <div class="card-header">{@render sk('40%')}</div>
            {#each W_ROWS as w}<div class="skeleton-row"><span class="skeleton" style="width:20px;height:20px;border-radius:6px;flex:none"></span>{@render sk(w)}</div>{/each}
          </div>
        </div>
      </div>
    {:else if data.empty}
      <div class="dash">
        <section class="card welcome" aria-labelledby="welcome-title">
          <div class="welcome-head">
            <span class="welcome-icon"><Icon name="layers" size={24} /></span>
            <div>
              <h2 class="welcome-title" id="welcome-title">Welcome to Mini CRM</h2>
              <p class="welcome-text">Your account is ready and empty. Start with these three steps — your dashboard fills in as you go.</p>
            </div>
          </div>
          <ol class="steps">
            {#each [
              { icon: 'user-plus', title: 'Add a client', text: 'A company or a person, with contact details, tags and notes.', href: '/clients?new=1', label: 'Add client' },
              { icon: 'briefcase', title: 'Create a deal', text: 'Link it to a client and track it from Lead to Won.', href: '/deals?new=1', label: 'Create deal' },
              { icon: 'check-square', title: 'Add a task', text: 'Set a deadline so follow-ups show up in Today and Overdue.', href: '/tasks?new=1', label: 'Add task' },
            ] as s, i}
              <li class="step">
                <span class="step-num" aria-hidden="true">{i + 1}</span>
                <div><h3 class="step-title"><span class="sr-only">Step {i + 1}: </span>{s.title}</h3><p class="step-text">{s.text}</p></div>
                <a class="btn {i === 0 ? 'btn-primary' : 'btn-secondary'} btn-sm" href={s.href}><Icon name={s.icon} size={16} /><span>{s.label}</span></a>
              </li>
            {/each}
          </ol>
        </section>
        {@render kpis(data)}
        <div class="dash-grid">{@render chartCard(data, true)}{@render tasksCard(data, true)}</div>
      </div>
    {:else}
      <div class="dash">
        {#if overdue}
          <div class="alert alert-error overdue-callout" role="status">
            <Icon name="alert-circle" size={20} />
            <div class="alert-body">
              <p class="alert-title">{plural(overdue, 'task is', 'tasks are')} overdue</p>
              <p>Follow-ups past their deadline. Complete or reschedule them.</p>
            </div>
            <a class="btn btn-secondary btn-sm" href="/tasks?tab=overdue">View overdue</a>
          </div>
        {/if}
        {@render kpis(data)}
        <div class="dash-grid">
          {@render chartCard(data, false)}
          <div class="dash" style="gap:inherit">
            {@render tasksCard(data, false)}
            <section class="card" aria-labelledby="recent-title">
              {@render cardHeader('Recent interactions', 'recent-title', 'Latest calls, meetings, emails and notes', '/clients', 'Clients')}
              <div class="card-body"><Timeline items={data.recentInteractions} showLink onchange={load} /></div>
            </section>
          </div>
        </div>
      </div>
    {/if}
  </Shell>
</div>
