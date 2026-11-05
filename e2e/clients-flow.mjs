// Сценарії екранів Clients / Client card: node clients-flow.mjs <outDir> <width> <height>
import { chromium } from 'playwright-core'
const [out = '/tmp/mcrm-clients', w = '1440', h = '900'] = process.argv.slice(2)
const O = process.env.ORIGIN ?? 'http://127.0.0.1:5173'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: +w, height: +h }, acceptDownloads: true })
const page = await ctx.newPage()
page.on('pageerror', (e) => console.error('pageerror:', e.message))
page.on('console', (m) => { if (m.type() === 'error') console.error('console:', m.text()) })
const shot = async (n) => { await page.waitForTimeout(350); await page.screenshot({ path: `${out}/flow-${w}-${n}.png` }) }
const log = (...a) => console.log(...a)

await page.goto(O + '/sign-in'); await page.click('#demo-btn'); await page.waitForURL(O + '/'); await page.waitForSelector('.kpi-grid')
await page.goto(O + '/clients'); await page.waitForSelector('#client-summary')
log('overflow btn visible:', await page.locator('.page-header-overflow').isVisible())
if (+w < 768) { await page.click('.page-header-overflow'); await shot('overflow'); await page.keyboard.press('Escape') }
// filters
await page.click('.segmented-item:has-text("Companies")')
await page.click('.chip-filter:has-text("Local")')
await page.waitForTimeout(400)
log('summary:', (await page.textContent('#client-summary')).replace(/\s+/g, ' '))
await shot('filtered')
await page.click('button:has-text("Clear filters") >> visible=true')
await page.fill('#client-search', 'xyz'); await page.waitForTimeout(500)
log('no-results:', await page.textContent('.empty-state-title'))
await shot('noresults')
await page.fill('#client-search', '555-0193'); await page.waitForTimeout(500)
log('phone search:', (await page.textContent('#client-summary')).replace(/\s+/g, ' '))
await page.fill('#client-search', '')
// export
if (+w >= 768) {
  const [dl] = await Promise.all([page.waitForEvent('download'), page.click('button:has-text("Export CSV")')])
  log('download:', dl.suggestedFilename())
  await page.waitForSelector('.toast'); log('toast:', await page.textContent('.toast'))
}
// new=1
await page.goto(O + '/clients?new=1'); await page.waitForSelector('#client-form'); await page.waitForTimeout(150)
await page.click('button:has-text("Create client")'); await page.waitForTimeout(300)
await shot('validation')
await page.fill('#cf-name', 'Zeta Test Co'); await page.fill('#cf-email', 'zeta@test.com')
await page.click('button:has-text("Create client")'); await page.waitForSelector('#client-form', { state: 'detached' })
log('url after create:', page.url())
await page.waitForTimeout(300)
// open new client card
await page.goto(O + '/clients'); await page.waitForSelector('#client-summary')
await page.click(`a:has-text("Zeta Test Co") >> visible=true`)
await page.waitForSelector('.client-hero')
log('detail url:', page.url())
await shot('detail-empty')
// add contact with validation
const addBtn = page.locator('button:has-text("Add contact person") >> visible=true')
if (await addBtn.count()) {
  await addBtn.click(); await page.waitForSelector('#contact-form'); await page.waitForTimeout(150)
  await page.fill('#ct-email', 'bad'); await page.click(".modal-footer .btn-primary")
  await shot('contact-validation')
  await page.fill('#ct-name', 'Olivia Hart'); await page.fill('#ct-email', 'olivia@zeta.com'); await page.fill('#ct-phone', '+1 555 0100')
  await page.click(".modal-footer .btn-primary"); await page.waitForSelector('#contact-form', { state: 'detached' })
  log('contacts:', await page.locator('.row-item[data-contact]').count())
}
// log interaction
if (+w < 768) await page.click('.client-tabs [data-tab="activity"]')
await page.click('button:has-text("Log interaction") >> visible=true'); await page.waitForSelector('#ix-desc'); await page.waitForTimeout(150)
await page.fill('#ix-desc', 'Intro call'); await page.click('.modal-footer button:has-text("Log interaction")')
await page.waitForSelector('#ix-desc', { state: 'detached' })
log('interactions:', await page.locator('.timeline-item').count())
// add task
if (+w < 768) await page.click('.client-tabs [data-tab="tasks"]')
await page.click('button:has-text("Add task") >> visible=true'); await page.waitForSelector('.modal'); await page.waitForTimeout(150)
await page.fill('.modal input:not([type=date])', 'Send proposal'); await page.click('.modal-footer .btn-primary')
await page.waitForSelector('.modal', { state: 'detached' })
log('tasks:', await page.locator('.task-item').count())
if (+w < 768) await page.click('.client-tabs [data-tab="overview"]')
await shot('detail-filled')
// delete contact
const cm = page.locator('[aria-label="Actions for Olivia Hart"]')
if (await cm.count()) {
  await page.evaluate(() => document.querySelectorAll('.toast-close').forEach((b) => b.click())); await page.waitForTimeout(300)
  await cm.evaluate((el) => el.scrollIntoView({ block: 'center' })); await page.waitForTimeout(300)
  const bx = await cm.boundingBox(); console.log('hit:', await page.evaluate(([x, y]) => { const e = document.elementFromPoint(x, y); return e?.outerHTML.slice(0, 200) }, [bx.x + bx.width / 2, bx.y + bx.height / 2])); await page.mouse.click(bx.x + bx.width / 2, bx.y + bx.height / 2); await page.waitForTimeout(300); await shot('contact-menu')
  console.log('menu items:', await page.locator('.menu-item').count())
  await page.locator('.menu-item:has-text("Delete")').evaluate((el) => el.click()); await page.waitForTimeout(300)
  await shot('contact-delete')
  await page.click('.modal-footer .btn-danger'); await page.waitForTimeout(400)
  log('contacts after delete:', await page.locator('.row-item[data-contact]').count())
}
// delete client
await page.evaluate(() => scrollTo(0, 0)); await page.waitForTimeout(300)
await page.click('[aria-label="More actions"] >> visible=true'); await page.click('.menu >> text=Delete client')
await page.waitForSelector('.delete-impact, .confirm-text:has-text("has no deals")'); await shot('client-delete')
await page.click('.modal-footer .btn-danger'); await page.waitForURL(O + '/clients')
log('after delete url:', page.url())
// full detail of seeded company + person
await page.click('a.table-link >> nth=0', { trial: +w < 768 }).catch(() => {})
await page.goto(O + '/clients'); await page.waitForSelector('#client-summary')
const firstHref = await page.locator('.table-link, .client-card-link').first().getAttribute('href')
await page.goto(O + firstHref); await page.waitForSelector('.client-hero'); await shot('detail-company')
await page.screenshot({ path: `${out}/flow-${w}-detail-company-full.png`, fullPage: true })
await page.goto(O + '/clients/999999'); await page.waitForSelector('.empty-state'); await shot('notfound')
await browser.close()
