// Мінімальний history-роутер (SvelteKit не використовується за погодженим стеком).
export const route = $state({ path: location.pathname, search: location.search })

export function params(): URLSearchParams {
  return new URLSearchParams(route.search)
}

export function navigate(to: string, opts: { replace?: boolean; keepScroll?: boolean } = {}) {
  const url = new URL(to, location.origin)
  if (url.pathname === route.path && url.search === route.search) return
  if (opts.replace) history.replaceState(null, '', url.pathname + url.search + url.hash)
  else history.pushState(null, '', url.pathname + url.search + url.hash)
  route.path = url.pathname
  route.search = url.search
  if (!opts.keepScroll) window.scrollTo(0, 0)
}

/** Оновлює query без нового запису в історії (фільтри/вкладки). */
export function setSearch(next: Record<string, string | null | undefined>) {
  const sp = new URLSearchParams(route.search)
  for (const [k, v] of Object.entries(next)) {
    if (v === null || v === undefined || v === '') sp.delete(k)
    else sp.set(k, v)
  }
  const s = sp.toString()
  navigate(route.path + (s ? `?${s}` : ''), { replace: true, keepScroll: true })
}

export function startRouter() {
  window.addEventListener('popstate', () => {
    route.path = location.pathname
    route.search = location.search
  })
  // Внутрішні посилання <a href="/..."> працюють без перезавантаження сторінки.
  document.addEventListener('click', (e) => {
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
    const a = (e.target as Element | null)?.closest?.('a[href]') as HTMLAnchorElement | null
    if (!a || a.target || a.hasAttribute('download') || a.dataset.external !== undefined) return
    const url = new URL(a.href, location.href)
    if (url.origin !== location.origin || url.pathname.startsWith('/api/')) return
    if (url.hash && url.pathname === location.pathname && url.search === location.search) return
    e.preventDefault()
    navigate(url.pathname + url.search)
  })
}

/** Співставлення шаблону "/clients/:id" зі шляхом. */
export function match(pattern: string, path: string): Record<string, string> | null {
  const p = pattern.split('/').filter(Boolean)
  const s = path.split('/').filter(Boolean)
  if (p.length !== s.length) return null
  const out: Record<string, string> = {}
  for (let i = 0; i < p.length; i++) {
    if (p[i].startsWith(':')) out[p[i].slice(1)] = decodeURIComponent(s[i])
    else if (p[i] !== s[i]) return null
  }
  return out
}
