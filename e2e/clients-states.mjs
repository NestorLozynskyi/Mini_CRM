// Стани завантаження/помилки/порожнього списку через перехоплення API: node clients-states.mjs <outDir> <width> <height>
import { chromium } from 'playwright-core'
const [out = '/tmp/mcrm-clients', w = '1440', h = '900'] = process.argv.slice(2)
const O = process.env.ORIGIN ?? 'http://127.0.0.1:5173'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await (await browser.newContext({ viewport: { width: +w, height: +h } })).newPage()
await page.goto(O + '/sign-in'); await page.click('#demo-btn'); await page.waitForURL(O + '/'); await page.waitForSelector('.kpi-grid')
const shot = async (n) => { await page.waitForTimeout(400); await page.screenshot({ path: `${out}/state-${w}-${n}.png` }) }
const listRe = /\/api\/clients(\?.*)?$/
// loading
await page.route(listRe, async (r) => { await new Promise((ok) => setTimeout(ok, 4000)); await r.continue().catch(() => {}) })
await page.goto(O + '/clients'); await shot('list-loading'); await page.unroute(listRe)
// error + retry
await page.route(listRe, (r) => r.fulfill({ status: 500, contentType: 'application/problem+json', body: '{"status":500,"code":"internal_error"}' }))
await page.goto(O + '/clients'); await page.waitForSelector('.empty-state.is-error'); await shot('list-error')
await page.unroute(listRe); await page.click('button:has-text("Retry")'); await page.waitForSelector('#client-summary')
console.log('retry ok')
// empty
await page.route(listRe, (r) => r.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
await page.goto(O + '/clients'); await page.waitForSelector('.empty-state'); await shot('list-empty')
console.log('export disabled:', await page.locator('button:has-text("Export CSV")').isDisabled())
await page.unroute(listRe)
// detail loading + error
await page.goto(O + '/clients'); await page.waitForSelector('#client-summary')
const href = await page.locator('.table-link, .client-card-link').first().getAttribute('href')
const detRe = /\/api\/clients\/\d+$/
await page.route(detRe, async (r) => { await new Promise((ok) => setTimeout(ok, 4000)); await r.continue().catch(() => {}) })
await page.goto(O + href); await shot('detail-loading'); await page.unroute(detRe)
await page.route(detRe, (r) => r.fulfill({ status: 500, contentType: 'application/problem+json', body: '{"status":500,"code":"internal_error"}' }))
await page.goto(O + href); await page.waitForSelector('.empty-state.is-error'); await shot('detail-error')
await page.unroute(detRe); await page.click('button:has-text("Retry")'); await page.waitForSelector('.client-hero'); console.log('detail retry ok')
// person card
await page.goto(O + '/clients'); await page.click('.segmented-item:has-text("People")'); await page.waitForTimeout(500)
const ph = await page.locator('.table-link, .client-card-link').first().getAttribute('href')
await page.goto(O + ph); await page.waitForSelector('.client-hero'); await shot('detail-person')
console.log('person has contacts card:', await page.locator('#h-contacts').count())
await browser.close()
