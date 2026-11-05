// Tasks screen flows: node tasks-flow.mjs <outDir> [width=1440] [height=900]
// Fresh demo account → screenshots of every tab, complete+undo, edit form, new=1, delete, empty states.
import { chromium } from 'playwright-core'

const [dir = '/tmp/mcrm-tasks', w = '1440', h = '900'] = process.argv.slice(2)
const origin = process.env.ORIGIN ?? 'http://127.0.0.1:5173'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await (await browser.newContext({ viewport: { width: +w, height: +h } })).newPage()
page.on('pageerror', (e) => console.error('pageerror:', e.message))
const shot = async (name) => { await page.waitForTimeout(350); await page.screenshot({ path: `${dir}/flow-${name}-${w}.png`, fullPage: true }) }
const log = (...a) => console.log(...a)
const badge = () => page.locator('[data-badge=tasks]').first().textContent().catch(() => '?')
const counts = () => page.$$eval('.tab-count', (els) => els.map((e) => e.textContent.replace(/\D/g, '')).join('/'))

await page.goto(origin + '/sign-in')
await page.click('#demo-btn')
await page.waitForURL(origin + '/')
await page.waitForSelector('.kpi-grid')

for (const tab of ['overdue', 'today', 'upcoming', 'done']) {
  await page.goto(`${origin}/tasks?tab=${tab}`)
  await page.waitForSelector('.task-groups')
  await shot('tab-' + tab)
}

// tab click + URL
await page.goto(origin + '/tasks')
await page.waitForSelector('.task-groups')
await page.click('#tab-upcoming')
log('url after click upcoming:', page.url())
await page.keyboard.press('ArrowRight')
log('url after ArrowRight:', page.url(), 'focused:', await page.evaluate(() => document.activeElement?.id))

// complete a task in Today
await page.click('#tab-today')
log('counts before:', await counts(), 'badge:', await badge())
const first = page.locator('.task-group:not(.is-overdue) .task-item').first()
await first.locator('input[type=checkbox]').check()
await page.waitForTimeout(600)
log('counts after complete:', await counts(), 'badge:', await badge())
await shot('completed-toast')
await page.click('.toast button:has-text("Undo")')
await page.waitForTimeout(600)
log('counts after undo:', await counts(), 'badge:', await badge())
// complete again, then switch tabs → moved to Completed
await first.locator('input[type=checkbox]').check()
await page.waitForTimeout(600)
await page.click('#tab-completed')
log('completed list:', await page.$$eval('.task-item .task-title', (e) => e.map((x) => x.textContent)))
await shot('completed-after')

// edit via menu
await page.click('#tab-upcoming')
await page.locator('.task-more').first().click()
await page.click('.menu >> text=Edit task')
await page.waitForSelector('#nt-title')
await shot('edit-form')
await page.keyboard.press('Escape')
await page.waitForTimeout(300)

// new=1
await page.goto(origin + '/tasks?new=1')
await page.waitForSelector('#nt-title')
log('url after new=1:', page.url())
await shot('new-form')
await page.fill('#nt-title', 'E2E task due today')
const d = new Date(); const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
await page.fill('#nt-due', iso)
await page.click('#nt-rel'); await page.click('#nt-rel-list .combo-option >> nth=0')
await page.click('button:has-text("Create task")')
await page.waitForTimeout(800)
log('counts after create:', await counts(), 'tab url:', page.url())

// delete (dismiss toasts first so they don't cover the row on mobile)
for (const x of await page.locator('.toast button[aria-label]').all()) await x.click().catch(() => {})
await page.waitForTimeout(300)
await page.locator('.task-item:has-text("E2E task due today") .task-more').click()
await page.click('.menu >> text=Delete task')
await page.waitForTimeout(300)
await shot('delete-confirm')
await page.locator('.modal button:has-text("Delete task"), dialog button:has-text("Delete task")').last().click()
await page.waitForTimeout(600)
log('counts after delete:', await counts())

// empty per-tab: overdue tab after completing all overdue tasks
await page.click('#tab-overdue')
for (let i = 0; i < 10; i++) {
  const cb = page.locator('.task-item:not(.is-done) input[type=checkbox]').first()
  if (!(await cb.count())) break
  await cb.check()
  await page.waitForTimeout(400)
}
await page.click('#tab-today')
await page.click('#tab-overdue')
await shot('overdue-empty')
log('final counts:', await counts(), 'badge:', await badge())
await browser.close()
