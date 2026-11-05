<script lang="ts">
  import type { Snippet } from 'svelte'
  import { onMount } from 'svelte'
  import { BRAND_MARK } from '../icons'
  import { badges, refreshBadges, session, signOut } from '../session.svelte'
  import Avatar from './Avatar.svelte'
  import Icon from './Icon.svelte'
  import { openMenu, type MenuItem } from './menu.svelte'

  interface Props {
    active: 'dashboard' | 'clients' | 'deals' | 'tasks' | 'settings' | ''
    title: string
    subtitle?: string
    back?: { href: string; label: string }
    /** Кнопки заголовка сторінки. На мобільному другорядні (не .btn-primary, без data-keep) ховає CSS. */
    actions?: Snippet
    /** Ті самі другорядні дії для меню «More actions» на мобільному. */
    overflow?: MenuItem[]
    /** Власний вміст заголовка замість h1/subtitle (напр. hero картки клієнта). Необов'язковий. */
    heading?: Snippet
    children: Snippet
  }

  let { active, title, subtitle, back, actions, overflow = [], heading, children }: Props = $props()

  const NAV = [
    { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', href: '/' },
    { id: 'clients', label: 'Clients', icon: 'users', href: '/clients' },
    { id: 'deals', label: 'Deals', icon: 'kanban', href: '/deals' },
    { id: 'tasks', label: 'Tasks', icon: 'check-square', href: '/tasks', badge: true },
  ] as const

  const demo = $derived(!!session.me?.demo)
  const email = $derived(session.me?.email ?? 'Demo visitor')
  const due = $derived(badges.tasksDue)

  let now = $state(Date.now())
  onMount(() => {
    document.body.classList.add('has-shell')
    void refreshBadges()
    const t = setInterval(() => (now = Date.now()), 30_000)
    return () => {
      clearInterval(t)
      document.body.classList.remove('has-shell')
    }
  })

  /** Залишок демо-сесії: «18h left» / «45m left». */
  const timeLeft = $derived.by(() => {
    const exp = session.me?.demoExpiresAt ? Date.parse(session.me.demoExpiresAt) : 0
    const ms = Math.max(0, exp - now)
    const h = Math.floor(ms / 3_600_000)
    return h >= 1 ? `${h}h left` : `${Math.max(1, Math.ceil(ms / 60_000))}m left`
  })

  $effect(() => {
    document.title = title ? `${title} · Mini CRM` : 'Mini CRM'
  })

  function accountMenu(e: MouseEvent) {
    openMenu(e.currentTarget as HTMLElement, [
      { header: true, label: demo ? 'Demo account' : email },
      { label: 'Settings', icon: 'settings', href: '/settings' },
      { label: 'Sign out', icon: 'log-out', onClick: () => void signOut() },
    ])
  }
</script>

<a class="sr-only skip-link" href="#main">Skip to content</a>
<div class="app" data-active={active}>
  <aside class="sidebar" aria-label="Main navigation">
    <a class="brand" href="/" aria-label="Mini CRM, go to dashboard">{@html BRAND_MARK}<span class="brand-name">Mini CRM</span></a>
    <nav>
      <ul class="nav">
        {#each NAV as n}
          <li>
            <a
              class="nav-link"
              data-nav={n.id}
              href={n.href}
              data-tooltip={n.label}
              aria-label={n.label + ('badge' in n && due ? `, ${due} due today or overdue` : '')}
              aria-current={n.id === active ? 'page' : undefined}>
              <Icon name={n.icon} size={20} /><span class="nav-label">{n.label}</span>
              {#if 'badge' in n}<span class="nav-badge" data-badge="tasks" hidden={!due}>{due}</span>{/if}
            </a>
          </li>
        {/each}
      </ul>
    </nav>
    <div class="sidebar-footer">
      <div class="user-block" title={email}>
        <Avatar name={email} />
        <div class="user-block-text">
          <span class="user-block-email">{email}</span>
          {#if demo}<span class="user-block-meta">Demo account</span>{/if}
        </div>
      </div>
      <a
        class="nav-link nav-link-settings"
        data-nav="settings"
        href="/settings"
        data-tooltip="Settings"
        aria-label="Settings"
        aria-current={active === 'settings' ? 'page' : undefined}><Icon name="settings" size={20} /><span class="nav-label">Settings</span></a>
      <button type="button" class="btn btn-ghost signout" data-tooltip="Sign out" aria-label="Sign out" onclick={() => void signOut()}>
        <Icon name="log-out" size={20} /><span class="signout-label">Sign out</span>
      </button>
    </div>
  </aside>

  <header class="topbar">
    <a class="brand" href="/" aria-label="Mini CRM, go to dashboard" style="padding:0">{@html BRAND_MARK}</a>
    <div class="topbar-title" aria-hidden="true">{title}</div>
    <button type="button" class="btn btn-ghost btn-icon account-btn" aria-label="Account: {email}" aria-haspopup="menu" aria-expanded="false" onclick={accountMenu}>
      <Avatar name={email} size="sm" />
    </button>
  </header>

  <main class="main" id="main" tabindex="-1">
    {#if demo}
      <div class="demo-bar" role="status">
        <Icon name="info" size={18} />
        <span class="demo-bar-text"
          ><span class="demo-bar-full">Temporary demo — sample data, deleted 24 hours after start.</span><span class="demo-bar-short">Demo ·</span></span>
        <span class="demo-bar-time" title="Time left in this demo session">{timeLeft}</span>
        <a class="btn-link" href="/sign-up"><span class="demo-bar-full">Create your account</span><span class="demo-bar-short">Sign up</span></a>
      </div>
    {/if}
    <div class="page">
      <div class="page-header">
        <div class="page-heading">
          {#if back}<a class="page-back" href={back.href}><Icon name="arrow-left" size={16} />{back.label}</a>{/if}
          {#if heading}
            {@render heading()}
          {:else}
            <h1 class="page-title">{title}</h1>
            {#if subtitle}<p class="page-subtitle">{subtitle}</p>{/if}
          {/if}
        </div>
        {#if actions || overflow.length}
          <div class="page-header-actions">
            {@render actions?.()}
            {#if overflow.length}
              <button
                type="button"
                class="btn btn-secondary btn-icon page-header-overflow"
                data-keep
                aria-label="More actions"
                aria-haspopup="menu"
                aria-expanded="false"
                onclick={(e) => openMenu(e.currentTarget as HTMLElement, overflow)}><Icon name="more" size={20} /></button>
            {/if}
          </div>
        {/if}
      </div>
      <div class="page-content">{@render children()}</div>
    </div>
  </main>

  <nav class="bottom-nav" aria-label="Main navigation">
    {#each NAV as n}
      <a class="bottom-nav-link" href={n.href} aria-current={n.id === active ? 'page' : undefined}>
        <Icon name={n.icon} size={22} /><span>{n.label}</span>
        {#if 'badge' in n}<span class="nav-badge" data-badge="tasks" hidden={!due}>{due}<span class="sr-only"> due</span></span>{/if}
      </a>
    {/each}
  </nav>
</div>
