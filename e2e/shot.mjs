// Швидкий скриншот сторінки: node shot.mjs <url> <out.png> [width=1440] [height=900] [--demo] [--full]
// --demo: спершу запускає новий демо-акаунт (POST /api/auth/demo через UI-кнопку Try demo).
import { chromium } from 'playwright-core'

const [url, out, w = '1440', h = '900', ...flags] = process.argv.slice(2)
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: +w, height: +h }, deviceScaleFactor: 1 })
const page = await ctx.newPage()
page.on('pageerror', (e) => console.error('pageerror:', e.message))
page.on('console', (m) => { if (m.type() === 'error') console.error('console:', m.text()) })
const origin = new URL(url).origin
if (flags.includes('--demo')) {
  await page.goto(origin + '/sign-in')
  await page.click('#demo-btn')
  await page.waitForURL(origin + '/')
  await page.waitForSelector('.kpi-grid')
}
await page.goto(url)
await page.waitForLoadState('networkidle')
await page.waitForTimeout(300)
await page.screenshot({ path: out, fullPage: flags.includes('--full') })
await browser.close()
