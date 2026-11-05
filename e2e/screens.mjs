// Базові екрани на трьох ширинах + модальні форми: node screens.mjs <origin> <outDir> <psqlCmd...>
import { chromium } from 'playwright-core'
import { execFileSync } from 'node:child_process'
const [origin, out, ...psql] = process.argv.slice(2)
const sql = (q) => execFileSync(psql[0], psql.slice(1), { input: q, encoding: 'utf8' })
const SIZES = [[1440, 900], [834, 1112], [390, 844]]
const browser = await chromium.launch({ channel: 'chrome', headless: true })

for (const [w, h] of SIZES) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h } })
  const page = await ctx.newPage()
  const shot = (n, full = true) => page.screenshot({ path: `${out}/${n}__${w}.png`, fullPage: full })
  const ready = async (sel) => { await page.waitForSelector(sel); await page.waitForLoadState('networkidle'); await page.waitForTimeout(250) }

  await page.goto(origin + '/sign-in'); await ready('#demo-btn'); await shot('sign-in__default')
  await page.goto(origin + '/sign-up'); await ready('#demo-btn'); await shot('sign-up__default')
  await page.goto(origin + '/sign-in'); await page.click('#demo-btn'); await ready('#kpi-tasks'); await shot('dashboard__default__demo-indicator')
  await page.goto(origin + '/clients'); await ready('#client-summary'); await shot('clients__default')
  await page.goto(origin + '/clients?new=1'); await ready('#client-form'); await page.waitForTimeout(200); await shot('clients__new-client-modal', false)
  await page.click('button:has-text("Create client")'); await page.waitForTimeout(300); await shot('clients__new-client-validation', false)
  await page.goto(origin + '/deals'); await ready('.kanban'); await shot('deals-board__default')
  await page.goto(origin + '/deals?new=1'); await ready('[data-form="deal"]'); await page.waitForTimeout(200)
  await page.click('.modal-footer .btn-primary'); await page.waitForTimeout(300); await shot('deals-board__new-deal-validation', false)
  await page.goto(origin + '/tasks'); await ready('.task-item'); await shot('tasks__default')
  // demo-expired: перемотуємо термін цього демо в БД
  const id = sql('SELECT max(id) FROM app_user WHERE demo;').match(/\n\s*(\d+)\s*\n/)[1]
  sql(`UPDATE app_user SET expires_at = now() - interval '1 second' WHERE id = ${id};`)
  await page.reload(); await ready('.expired-card'); await shot('demo-expired__default')
  await ctx.close()
}
await browser.close()
console.log('ok')
