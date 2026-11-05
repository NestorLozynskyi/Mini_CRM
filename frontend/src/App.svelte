<script lang="ts">
  import type { Component } from 'svelte'
  import { onMount } from 'svelte'
  import { match, navigate, route, startRouter } from './lib/router.svelte'
  import { bootstrap, session } from './lib/session.svelte'
  import { refreshTags } from './lib/stores.svelte'
  import ConfirmHost from './lib/ui/ConfirmHost.svelte'
  import MenuHost from './lib/ui/MenuHost.svelte'
  import Toasts from './lib/ui/Toasts.svelte'
  import ClientDetail from './pages/ClientDetail.svelte'
  import Clients from './pages/Clients.svelte'
  import Dashboard from './pages/Dashboard.svelte'
  import DealDetail from './pages/DealDetail.svelte'
  import DealsBoard from './pages/DealsBoard.svelte'
  import DealsList from './pages/DealsList.svelte'
  import DemoExpired from './pages/DemoExpired.svelte'
  import NotFound from './pages/NotFound.svelte'
  import SignIn from './pages/SignIn.svelte'
  import SignUp from './pages/SignUp.svelte'
  import Settings from './pages/Settings.svelte'
  import Tasks from './pages/Tasks.svelte'

  type Route = { pattern: string; component: Component<any>; auth: 'public' | 'app' | 'expired' }
  const ROUTES: Route[] = [
    { pattern: '/sign-in', component: SignIn, auth: 'public' },
    { pattern: '/sign-up', component: SignUp, auth: 'public' },
    { pattern: '/demo-expired', component: DemoExpired, auth: 'expired' },
    { pattern: '/', component: Dashboard, auth: 'app' },
    { pattern: '/clients', component: Clients, auth: 'app' },
    { pattern: '/clients/:id', component: ClientDetail, auth: 'app' },
    { pattern: '/deals', component: DealsBoard, auth: 'app' },
    { pattern: '/deals/list', component: DealsList, auth: 'app' },
    { pattern: '/deals/:id', component: DealDetail, auth: 'app' },
    { pattern: '/tasks', component: Tasks, auth: 'app' },
    { pattern: '/settings', component: Settings, auth: 'app' },
  ]

  const current = $derived.by(() => {
    for (const r of ROUTES) {
      const p = match(r.pattern, route.path)
      if (p) return { ...r, params: p }
    }
    return { pattern: '*', component: NotFound as Component<any>, auth: 'app' as const, params: {} as Record<string, string> }
  })

  /** Куди перенаправити з поточного маршруту з огляду на стан сесії (null — лишаємось). */
  const redirect = $derived.by(() => {
    const st = session.status
    if (st === 'loading') return null
    const auth = current.auth
    if (st === 'authed') {
      // Демо-користувач може відкрити sign-up / sign-in (кнопка «Create your account» у демо-панелі).
      if (auth === 'public' && !session.me?.demo) return '/'
      if (auth === 'expired') return '/'
      return null
    }
    if (st === 'demoExpired') return auth === 'app' ? '/demo-expired' : null
    return auth === 'app' || auth === 'expired' ? '/sign-in' : null
  })

  $effect(() => {
    if (redirect) navigate(redirect, { replace: true })
  })

  $effect(() => {
    if (session.status === 'authed') void refreshTags()
  })

  onMount(() => {
    startRouter()
    void bootstrap()
  })
</script>

{#if session.status !== 'loading' && !redirect}
  {@const Page = current.component}
  {#key route.path}
    <Page params={current.params} />
  {/key}
{/if}

<Toasts />
<ConfirmHost />
<MenuHost />
