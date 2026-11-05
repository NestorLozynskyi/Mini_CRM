// Скриншоти екранів угод у всіх станах на 1440 / 834 / 390 (один демо-акаунт).
// Запуск: node deals-shots.mjs [origin=http://127.0.0.1:5173] [outDir=/tmp/mcrm-deals/app] [widths=1440,834,390]
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright-core'

const origin = process.argv[2] ?? process.env.ORIGIN ?? 'http://127.0.0.1:5173'
const out = process.argv[3] ?? '/tmp/mcrm-deals/app'
const widths = (process.argv[4] ?? '1440,834,390').split(',').map(Number)
const H = { 1440: 900, 834: 1112, 390: 844 }
mkdirSync(out, { recursive: true })

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
page.on('pageerror', (e) => console.error('pageerror:', e.message))

/** Виклик API зі сторінки (з XSRF-токеном, як у фронтенді). */
const api = (method, path, body) =>
  page.evaluate(
    async ({ method, path, body }) => {
      const tok = () => document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1]
      if (!tok()) await fetch('/api/auth/csrf')
      const r = await fetch(path, {
        method,
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(tok() ?? '') },
        body: body ? JSON.stringify(body) : undefined,
      })
      return r.status === 204 ? null : r.json()
    },
    { method, path, body },
  )

async function shot(name, url, w, prep) {
  await page.setViewportSize({ width: w, height: H[w] ?? 900 })
  await page.goto(origin + url)
  if (hanging) await page.waitForTimeout(600)
  else await page.waitForLoadState('networkidle')
  if (prep) await prep()
  await page.waitForTimeout(250)
  await page.screenshot({ path: `${out}/${name}-${w}.png`, fullPage: true })
}
async function withRoute(pattern, handler, fn) {
  hanging = handler === hang
  await page.route(pattern, handler)
  try {
    await fn()
  } finally {
    hanging = false
    await page.unroute(pattern)
  }
}
let hanging = false
const hang = () => new Promise(() => {})

await page.goto(origin + '/sign-in')
await page.click('#demo-btn')
await page.waitForURL(origin + '/')

const all = await api('GET', '/api/deals?scope=all')
const byTitle = (t) => all.find((d) => d.title === t) ?? all[0]
const main = byTitle('Website redesign')
const lost = all.find((d) => d.stage === 'lost' && !d.archived)
const toArchive = byTitle('Social media retainer')
console.log(`deals: ${all.length} (archived ${all.filter((d) => d.archived).length}); detail #${main.id}, lost #${lost?.id}`)

for (const w of widths) {
  await shot('board', '/deals', w)
  await withRoute('**/api/deals?*', hang, () => shot('board-loading', '/deals', w))
  await withRoute('**/api/deals?*', (r) => r.fulfill({ status: 500, body: '{}' }), () => shot('board-error', '/deals', w))
  await shot('list', '/deals/list', w)
  await shot('list-filtered', '/deals/list', w, async () => {
    await page.click('[data-chip="proposal"]')
    await page.click('[data-chip="negotiation"]')
  })
  await shot('list-noresults', '/deals/list', w, () => page.fill('#deal-search', 'acme'))
  await shot('detail', `/deals/${main.id}`, w)
  if (lost) await shot('detail-lost', `/deals/${lost.id}`, w)
  await shot('detail-notfound', '/deals/999999', w)
  await withRoute('**/api/deals/*', hang, () => shot('detail-loading', `/deals/${main.id}`, w))
}

// Архівна угода: деталі й список архіву
await api('POST', `/api/deals/${toArchive.id}/archive`)
for (const w of widths) {
  await shot('list-archived', '/deals/list?scope=archived', w)
  await shot('detail-archived', `/deals/${toArchive.id}`, w)
}
// Порожній стан: архівуємо всі угоди
for (const d of all) if (!d.archived) await api('POST', `/api/deals/${d.id}/archive`)
for (const w of widths) {
  await shot('board-empty', '/deals', w)
  await shot('list-empty', '/deals/list', w)
}
// Порожній архів: відновлюємо всі угоди
for (const d of all) await api('POST', `/api/deals/${d.id}/restore`)
for (const w of widths) await shot('list-archived-empty', '/deals/list?scope=archived', w)

await browser.close()
console.log('done →', out)
