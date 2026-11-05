// TASK-005 — Settings / валюта: стани, точки входу, суми в іншій валюті, збереження після перезавантаження і повторного входу.
// node settings-currency.mjs <origin> <outDir> [widths=1440,834,390]
import { chromium } from 'playwright-core'
import { writeFileSync } from 'node:fs'
const [origin, out, widthsArg = '1440,834,390'] = process.argv.slice(2)
const H = { 1440: 900, 834: 1112, 390: 844 }
const browser = await chromium.launch({ channel: 'chrome', headless: true })
let failures = 0
const check = (ok, msg) => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${msg}`); if (!ok) failures++ }

for (const w of widthsArg.split(',').map(Number)) {
  const mobile = w < 768
  console.log(`\n## ${w}`)
  const ctx = await browser.newContext({ viewport: { width: w, height: H[w] ?? 900 } })
  const page = await ctx.newPage()
  page.on('pageerror', (e) => console.log('pageerror', e.message))
  const shot = (name, full = false) => page.screenshot({ path: `${out}/${name}__${w}.png`, fullPage: full })
  const settle = async () => { await page.waitForLoadState('networkidle'); await page.waitForTimeout(300) }
  const closeToasts = async () => { for (const b of await page.locator('.toast-close').all()) await b.click().catch(() => {}); await page.waitForTimeout(250) }

  // ---- Зареєстрований акаунт
  const email = `settings-${w}-${Date.now()}@example.com`, password = 'harbor-lights-7'
  await page.goto(origin + '/sign-up')
  await page.fill('#email', email); await page.fill('#password', password); await page.fill('#confirm', password)
  await page.click('button[type=submit]'); await page.waitForSelector('.welcome')

  // Точка входу
  if (mobile) {
    await page.click('.account-btn'); await page.waitForSelector('.menu'); await page.waitForTimeout(200)
    await shot('entry__avatar-menu')
    await page.click('.menu-item:has-text("Settings")')
  } else {
    await page.hover('.nav-link-settings'); await page.waitForTimeout(300)
    await shot('entry__sidebar')
    await page.click('.nav-link-settings')
  }
  await page.waitForSelector('#cur-list'); await settle()
  check(new URL(page.url()).pathname === '/settings', 'entry point opens /settings')
  check(await page.isDisabled('#cur-save') && await page.isDisabled('#cur-reset'), 'Save/Cancel disabled initially')
  await shot('settings__default')
  await shot('settings__default-full', true)

  // error (сервер недоступний) → Retry успішний
  await page.route('**/api/account/currency', (r) => r.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"status":503,"code":"internal_error"}' }))
  await page.check('input[name=currency][value=GBP]')
  check(!(await page.isDisabled('#cur-save')) && !(await page.isDisabled('#cur-reset')), 'Save/Cancel enabled after change')
  await page.click('#cur-save'); await page.click('button:has-text("Change to GBP")')
  await page.waitForSelector('#cur-error'); await page.waitForTimeout(300)
  await shot('settings__error')
  check(await page.isChecked('input[name=currency][value=GBP]'), 'selection kept after error')
  await page.unroute('**/api/account/currency'); await closeToasts()
  await page.click('#cur-retry'); await page.click('button:has-text("Change to GBP")')
  await page.waitForSelector('#cur-error', { state: 'detached' }); await page.waitForTimeout(300)
  check((await page.textContent('#cur-current')).includes('Account currency: GBP'), 'Retry saves GBP')
  await closeToasts()

  // перезавантаження і повторний вхід
  await page.reload(); await page.waitForSelector('#cur-list')
  check((await page.textContent('#cur-current')).includes('GBP'), 'GBP persists after reload')
  const ctx2 = await browser.newContext({ viewport: { width: w, height: H[w] ?? 900 } })
  const p2 = await ctx2.newPage()
  await p2.goto(origin + '/sign-in'); await p2.fill('#email', email); await p2.fill('#password', password)
  await p2.click('button[type=submit]'); await p2.waitForURL(origin + '/')
  await p2.goto(origin + '/settings'); await p2.waitForSelector('#cur-list')
  check((await p2.textContent('#cur-current')).includes('GBP'), 'GBP persists after signing in again')
  await ctx2.close()
  await ctx.close()

  // ---- Демо-акаунт
  const dctx = await browser.newContext({ viewport: { width: w, height: H[w] ?? 900 }, acceptDownloads: true })
  const d = await dctx.newPage()
  const dshot = (name, full = false) => d.screenshot({ path: `${out}/${name}__${w}.png`, fullPage: full })
  const dsettle = async () => { await d.waitForLoadState('networkidle'); await d.waitForTimeout(300) }
  await d.goto(origin + '/sign-in'); await d.click('#demo-btn'); await d.waitForSelector('#kpi-tasks')
  await d.goto(origin + '/settings'); await d.waitForSelector('#cur-list'); await dsettle()
  check((await d.textContent('#cur-current')).includes('USD'), 'new demo starts in USD')
  await dshot('settings__demo')
  await d.fill('#cur-search', 'bitcoin'); await d.waitForTimeout(200)
  await dshot('settings__no-results')
  await d.click('#cur-clear')
  await d.check('input[name=currency][value=EUR]')
  await d.click('#cur-save'); await d.waitForSelector('.modal.confirm'); await d.waitForTimeout(250)
  await dshot('settings__confirm')
  const confirmText = (await d.textContent('.modal.confirm .modal-body')).replace(/\s+/g, ' ').trim()
  console.log('confirm:', confirmText)
  await d.click('button:has-text("Change to EUR")')
  await d.waitForSelector('.toast-success'); await d.waitForTimeout(300)
  await dshot('settings__success')
  for (const b of await d.locator('.toast-close').all()) await b.click().catch(() => {})

  await d.goto(origin + '/'); await d.waitForSelector('#kpi-tasks'); await dsettle()
  const kpi = await d.textContent('.kpi-grid')
  check(kpi.includes('€74,500'), 'dashboard pipeline in EUR (€74,500)')
  await dshot('dashboard__eur', true)

  // UAH для канбану, форми й CSV
  await d.goto(origin + '/settings'); await d.waitForSelector('#cur-list')
  await d.check('input[name=currency][value=UAH]'); await d.click('#cur-save')
  await d.click('button:has-text("Change to UAH")'); await d.waitForSelector('.toast-success')
  await d.goto(origin + '/deals'); await d.waitForSelector('.kanban'); await dsettle()
  const board = (await d.textContent('.kanban')).replace(/ /g, ' ')
  check(board.includes('UAH 13,000') && board.includes('UAH 6,500'), 'kanban column totals and cards in UAH')
  await dshot('deals-board__uah', !mobile)
  await d.goto(origin + '/deals?new=1'); await d.waitForSelector('[data-form="deal"]'); await d.waitForTimeout(300)
  check((await d.textContent('label[for=df-amount]')).includes('Amount (UAH)') && (await d.textContent('.input-affix-text')).trim() === 'UAH', 'deal form: Amount (UAH) + UAH affix')
  await d.fill('#df-amount', '12400')
  await dshot('deal-form__uah')
  await d.keyboard.press('Escape')
  await d.goto(origin + '/deals/list'); await d.waitForSelector('.page-content'); await dsettle()
  await dshot('deals-list__uah', !mobile)
  if (w === 1440) {
    const csv = await d.evaluate(async () => (await fetch('/api/export/deals.csv?scope=all')).text())
    writeFileSync(`${out}/deals-uah.csv`, csv)
    check(csv.includes('Amount (UAH)'), 'deals CSV header in UAH')
  }
  // інший демо не зачеплений
  const other = await (await browser.newContext()).newPage()
  await other.goto(origin + '/sign-in'); await other.click('#demo-btn'); await other.waitForSelector('#kpi-tasks')
  check((await other.textContent('.kpi-grid')).includes('$74,500'), 'another new demo still shows USD')
  await other.context().close()
  await dctx.close()
}
await browser.close()
console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed')
process.exit(failures ? 1 : 0)
