// Tasks screen data-driven states: node tasks-states.mjs <outDir>
// loading (delayed API), error (+Retry), empty (all tasks deleted) at 1440/834/390.
import { chromium } from 'playwright-core'

const [dir = '/tmp/mcrm-tasks'] = process.argv.slice(2)
const origin = process.env.ORIGIN ?? 'http://127.0.0.1:5173'
const WIDTHS = [[1440, 900], [834, 1112], [390, 844]]
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
page.on('pageerror', (e) => console.error('pageerror:', e.message))
const shots = async (name) => {
  for (const [w, h] of WIDTHS) {
    await page.setViewportSize({ width: w, height: h })
    await page.waitForTimeout(300)
    await page.screenshot({ path: `${dir}/state-${name}-${w}.png`, fullPage: true })
  }
  await page.setViewportSize({ width: 1440, height: 900 })
}

await page.goto(origin + '/sign-in')
await page.click('#demo-btn')
await page.waitForURL(origin + '/')
await page.waitForSelector('.kpi-grid')

// loading: hold the list request
let release
const held = new Promise((r) => (release = r))
await page.route('**/api/tasks', async (route) => { await held; await route.continue().catch(() => {}) })
await page.goto(origin + '/tasks')
await page.waitForSelector('.skeleton-wrap')
await shots('loading')
release()
await page.unroute('**/api/tasks')

// error → Retry
await page.route('**/api/tasks', (route) => route.abort())
await page.goto(origin + '/tasks')
await page.waitForSelector('.empty-state.is-error')
await shots('error')
await page.unroute('**/api/tasks')
await page.click('button:has-text("Retry")')
await page.waitForSelector('.task-groups')
console.log('retry ok')

// empty: delete everything through the UI
for (const tab of ['overdue', 'today', 'upcoming', 'done']) {
  await page.goto(`${origin}/tasks?tab=${tab}`)
  await page.waitForSelector('#task-panel, .empty-state')
  await page.waitForTimeout(300)
  console.log(tab, 'items', await page.locator('#task-panel .task-more').count())
  for (;;) {
    const more = page.locator('#task-panel .task-more').first()
    if (!(await more.count())) break
    await more.click()
    await page.click('.menu >> text=Delete task')
    await page.locator('.modal button:has-text("Delete task")').last().click()
    await page.waitForTimeout(500)
  }
}
await page.goto(origin + '/tasks')
await page.waitForSelector('.empty-state')
await page.waitForTimeout(300)
await shots('empty')
await page.click('.empty-state .btn-primary')
await page.waitForSelector('#nt-title')
console.log('empty New task opens form')
await browser.close()
