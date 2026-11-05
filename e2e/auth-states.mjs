// Стани екранів входу/реєстрації через реальні дії: node auth-states.mjs <origin> <outDir> [width] [height]
import { chromium } from 'playwright-core'
const [origin, out, w = '1440', h = '900'] = process.argv.slice(2)
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: +w, height: +h } })
const page = await ctx.newPage()
const shot = (name) => page.screenshot({ path: `${out}/${name}-${w}.png`, fullPage: true })

// sign-up: помилки валідації
await page.goto(origin + '/sign-up')
await page.fill('#email', 'jordan@')
await page.fill('#password', 'pine42')
await page.fill('#confirm', 'pine4')
await page.click('button[type=submit]')
await page.waitForSelector('.error-summary')
await shot('sign-up_errors')

// sign-up: зайнятий email (спершу реєструємо акаунт в окремому контексті)
const taken = `taken-${Date.now()}@example.com`
const other = await (await browser.newContext()).newPage()
await other.goto(origin + '/sign-up')
await other.fill('#email', taken); await other.fill('#password', 'harbor-lights-7'); await other.fill('#confirm', 'harbor-lights-7')
await other.click('button[type=submit]'); await other.waitForURL(origin + '/')
await page.goto(origin + '/sign-up')
await page.fill('#email', taken); await page.fill('#password', 'harbor-lights-7'); await page.fill('#confirm', 'harbor-lights-7')
await page.click('button[type=submit]')
await page.waitForSelector('#taken-msg')
await shot('sign-up_taken')

// sign-in: помилки валідації та невірні облікові дані
await page.goto(origin + '/sign-in')
await page.fill('#email', 'alex.morgan@example')
await page.click('button[type=submit]')
await page.waitForSelector('.error-summary')
await shot('sign-in_errors')
await page.fill('#email', taken)
await page.fill('#password', 'wrong-password')
await page.click('button[type=submit]')
await page.waitForSelector('text=Incorrect email or password.')
await shot('sign-in_invalid')
// успішний вхід → дашборд нового (порожнього) акаунта
await page.fill('#password', 'harbor-lights-7')
await page.click('button[type=submit]')
await page.waitForURL(origin + '/')
await page.waitForSelector('.welcome')
await shot('dashboard_empty')
// перезавантаження — сесія зберігається
await page.reload()
await page.waitForSelector('.welcome')
console.log('session survives reload:', page.url())
await browser.close()
