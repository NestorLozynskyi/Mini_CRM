// Сценарії екранів угод: ?new=1&client=, меню картки (Move to… / Archive + Undo), мобільний Move,
// список (фільтри, архів, Restore, Export CSV), картка угоди (Move з Lost, Edit, Archive/Restore, взаємодії, завдання).
// Запуск: node deals-flows.mjs [origin=http://127.0.0.1:5173] [outDir=/tmp/mcrm-deals/flows]
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright-core'

const origin = process.argv[2] ?? process.env.ORIGIN ?? 'http://127.0.0.1:5173'
const out = process.argv[3] ?? '/tmp/mcrm-deals/flows'
mkdirSync(out, { recursive: true })

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true })
const page = await ctx.newPage()
page.on('pageerror', (e) => console.error('pageerror:', e.message))

let failures = 0
const check = (ok, msg) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${msg}`)
  if (!ok) failures++
}
const apiGet = (path) => page.evaluate(async (p) => (await fetch(p)).json(), path)
const toast = (text) => page.waitForSelector(`.toast:has-text("${text}")`, { timeout: 5000 }).then(() => true, () => false)
const menuItem = (label) => page.click(`.menu .menu-item:has-text("${label}")`)
const closeToasts = () => page.evaluate(() => document.querySelectorAll('.toast').forEach((t) => t.remove()))

await page.goto(origin + '/sign-in')
await page.click('#demo-btn')
await page.waitForURL(origin + '/')
const clients = await apiGet('/api/clients')
const client = clients.find((c) => c.name === 'Elm Street Yoga') ?? clients[0]

// --- Board: ?new=1&client=<id> ---
await page.goto(`${origin}/deals?new=1&client=${client.id}`)
await page.waitForSelector('.modal-title:has-text("New deal")')
await page.waitForFunction(() => document.querySelector('#df-client')?.value)
check((await page.inputValue('#df-client')) === client.name, `?new=1&client → New deal form with client preselected (${client.name})`)
check(new URL(page.url()).search === '', 'URL params cleaned after opening form')
await page.fill('#df-title', 'E2E yoga retreat')
await page.fill('#df-amount', '3100')
await page.selectOption('#df-stage', 'contacted')
await page.click('.modal .btn-primary:has-text("Create deal")')
check(await toast('Deal created'), 'deal created (toast)')
await page.waitForSelector('.board .kanban-col[data-stage="contacted"] .deal-card:has-text("E2E yoga retreat")')
check(true, 'new deal appears in Contacted column')
const created = (await apiGet('/api/deals?q=E2E%20yoga'))[0]
check(created?.clientId === client.id && created.amount === 3100, 'new deal persisted with client and amount')

// --- Board: card menu → Move to… (keyboard alternative) ---
await page.click(`.deal-card[data-id="${created.id}"] .deal-card-menu`)
await menuItem('Move to…')
await page.waitForSelector('.modal-title:has-text("Move to stage")')
await page.check('input[name="move-stage"][value="won"]', { force: true })
await page.click('.modal .btn-primary:has-text("Move deal")')
check(await toast('Moved to Won'), 'Move to… sheet → toast “Moved to Won”')
await page.waitForSelector(`.kanban-col[data-stage="won"] .deal-card[data-id="${created.id}"]`)
check((await apiGet(`/api/deals/${created.id}`)).stage === 'won', 'Move to… persisted on server')

// --- Board: search + client filter ---
await page.fill('#deal-search', 'yoga retreat')
check((await page.$$('.board .deal-card')).length === 1, 'search filters cards')
await page.fill('#deal-search', '')
await page.selectOption('#deal-client', String(client.id))
const onlyClient = await page.$$eval('.board .deal-card .deal-card-client', (els) => els.map((e) => e.textContent.trim()))
check(onlyClient.length > 0 && onlyClient.every((n) => n === client.name), `client filter shows only ${client.name} (${onlyClient.length})`)
await page.screenshot({ path: `${out}/board-client-filter.png` })
await page.selectOption('#deal-client', '')

// --- Board: archive with confirm + Undo ---
await closeToasts()
await page.click(`.deal-card[data-id="${created.id}"] .deal-card-menu`)
await menuItem('Archive')
await page.waitForSelector('.modal-title:has-text("Archive this deal?")')
await page.screenshot({ path: `${out}/board-archive-confirm.png` })
await page.click('.modal .btn-primary:has-text("Archive deal")')
check(await toast('archived'), 'archive → toast with Undo')
await page.waitForSelector(`.deal-card[data-id="${created.id}"]`, { state: 'detached' })
check((await apiGet(`/api/deals/${created.id}`)).archived === true, 'archived on server, card removed')
await page.click('.toast:has-text("archived") >> text=Undo')
await page.waitForSelector(`.deal-card[data-id="${created.id}"]`)
check((await apiGet(`/api/deals/${created.id}`)).archived === false, 'Undo → restored on server, card back')

// --- Board mobile: stage chips + Move button ---
await page.setViewportSize({ width: 390, height: 844 })
await page.goto(origin + '/deals')
await page.waitForSelector('.stage-chip')
await page.click('.stage-chip[data-tab="proposal"]')
check(await page.$eval('.kanban-col.is-current', (el) => el.dataset.stage) === 'proposal', 'mobile: stage chip switches visible column')
const mid = await page.$eval('.kanban-col.is-current .deal-card', (el) => el.dataset.id)
await page.click(`.deal-card[data-id="${mid}"] .deal-card-move`)
await page.waitForSelector('.modal-title:has-text("Move to stage")')
await page.screenshot({ path: `${out}/mobile-move-sheet.png` })
await page.check('input[name="move-stage"][value="negotiation"]', { force: true })
await page.click('.modal .btn-primary:has-text("Move deal")')
check(await toast('Moved to Negotiation'), 'mobile Move button → moved')
check((await apiGet(`/api/deals/${mid}`)).stage === 'negotiation', 'mobile move persisted')
await page.setViewportSize({ width: 1440, height: 900 })

// --- Board: server error on move → revert + error toast ---
await page.goto(origin + '/deals')
await page.waitForSelector('.board .deal-card')
const leadId = await page.$eval('.kanban-col[data-stage="lead"] .deal-card', (el) => el.dataset.id)
await page.route('**/api/deals/*/stage', (r) => r.fulfill({ status: 500, contentType: 'application/problem+json', body: '{"status":500,"code":"internal"}' }))
await page.click(`.deal-card[data-id="${leadId}"] .deal-card-menu`)
await menuItem('Move to…')
await page.check('input[name="move-stage"][value="proposal"]', { force: true })
await page.click('.modal .btn-primary:has-text("Move deal")')
check(await toast('Couldn’t move the deal'), 'server error → error toast')
await page.waitForTimeout(200)
check((await page.$eval(`.deal-card[data-id="${leadId}"]`, (el) => el.closest('.kanban-col').dataset.stage)) === 'lead', 'server error → card reverted to Lead')
await page.unroute('**/api/deals/*/stage')

// --- List: scope, filters, export, restore ---
await page.goto(origin + '/deals/list')
await page.waitForSelector('.deals-table')
await page.click('[data-chip="won"]')
const rows = await page.$$eval('.deals-table tbody tr', (els) => els.length)
check((await page.textContent('.filter-summary')).includes(`Showing ${rows} of`), `stage chip filter + summary (${rows} rows)`)
const dl = page.waitForEvent('download')
await page.click('button[aria-label="More actions"][data-keep]')
await menuItem('Export CSV')
const file = await dl
check(file.suggestedFilename() === 'deals.csv', 'Export CSV downloads deals.csv')
check(await toast(`Exported ${rows} deal`), `toast “Exported ${rows} deals to deals.csv” (filtered list)`)
await page.click('#scope-switch .segmented-item:has-text("Archived")')
await page.waitForURL(/scope=archived/)
await page.click('.filter-summary .btn-link:has-text("Clear filters")').catch(() => {})
await page.waitForSelector('.deals-table')
const arch = await page.$eval('.deals-table tbody tr', (el) => ({ id: el.dataset.id, title: el.querySelector('.table-link').textContent }))
await page.click(`.deals-table tr[data-id="${arch.id}"] button:has-text("Restore")`)
check(await toast('Deal restored to'), `Restore “${arch.title}” → toast “Deal restored to …”`)
check((await apiGet(`/api/deals/${arch.id}`)).archived === false, 'restored on server')
await page.screenshot({ path: `${out}/list-restored.png` })

// --- Detail: move to Lost with reason, reopen, edit, archive/restore, interactions, tasks ---
const d = (await apiGet('/api/deals?q=Website%20redesign'))[0]
await page.goto(`${origin}/deals/${d.id}`)
await page.waitForSelector('.deal-hero')
await page.click('.page-header-actions .btn-primary:has-text("Move to…")')
await page.check('input[name="move-stage"][value="lost"]', { force: true })
await page.fill('#mv-reason', 'Budget frozen')
await page.click('.modal .btn-primary:has-text("Move deal")')
check(await toast('Moved to Lost'), 'detail: Move to Lost')
await page.waitForSelector('.lost-block-text:has-text("Budget frozen")')
check((await apiGet(`/api/deals/${d.id}`)).lostReason === 'Budget frozen', 'lost reason persisted and shown')
await page.screenshot({ path: `${out}/detail-lost.png` })
await page.click('.toast:has-text("Moved to Lost") >> text=Undo')
await page.waitForSelector('.lost-block', { state: 'detached' })
check((await apiGet(`/api/deals/${d.id}`)).stage === 'negotiation', 'detail: Undo restores Negotiation')

await page.click('.page-header-actions button:has-text("Edit")')
await page.fill('#df-amount', '9100')
await page.click('.modal .btn-primary:has-text("Save changes")')
check(await toast('Deal updated'), 'detail: edit saved')
await page.waitForSelector('.deal-fact-value.is-mono:has-text("$9,100")')
check(true, 'detail: amount updated in summary')

await closeToasts()
await page.click('.page-header-actions button[aria-label="More actions"]')
await menuItem('Archive deal')
await page.click('.modal .btn-primary:has-text("Archive deal")')
await page.waitForSelector('.deal-banner:has-text("This deal is archived")')
check(await page.$eval('.page-header-actions .btn-primary', (b) => b.disabled), 'archived: banner shown, Move disabled')
await page.click('.deal-banner button:has-text("Restore")')
check(await toast('Deal restored to Negotiation'), 'archived: Restore → toast')
await page.waitForSelector('.deal-banner', { state: 'detached' })

const nInts = (await apiGet(`/api/interactions?dealId=${d.id}`)).length
await page.click('button:has-text("Log interaction")')
await page.fill('#if-desc', 'E2E call about scope')
await page.click('.modal .btn-primary:has-text("Log interaction")')
await page.waitForSelector('.timeline-text:has-text("E2E call about scope")')
check((await apiGet(`/api/interactions?dealId=${d.id}`)).length === nInts + 1, 'interaction logged and linked to deal')

await page.click('button:has-text("Add task")')
await page.fill('#tf-title', 'E2E send contract')
await page.click('.modal .btn-primary:has-text("Create task")')
await page.waitForSelector('.task-title:has-text("E2E send contract")')
const task = (await apiGet(`/api/tasks?dealId=${d.id}`)).find((t) => t.title === 'E2E send contract')
check(!!task, 'task created and linked to deal')
await page.click('.task-item:has-text("E2E send contract") input[type="checkbox"]')
check(await toast('Task completed'), 'task toggled done')
await page.waitForTimeout(300)
check((await apiGet(`/api/tasks?dealId=${d.id}`)).find((t) => t.id === task.id)?.done === true, 'task done persisted')
await page.screenshot({ path: `${out}/detail-after.png`, fullPage: true })

await browser.close()
console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll checks passed')
process.exit(failures ? 1 : 0)
