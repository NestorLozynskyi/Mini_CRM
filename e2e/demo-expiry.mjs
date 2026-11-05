// Демо-життєвий цикл у браузері: старт → зміна даних → перезавантаження (дані на місці) →
// «перемотка» 24 год (expires_at у БД) → екран demo-expired → новий демо з чистою копією.
// node demo-expiry.mjs <origin> <outDir> <psqlCmd...>   (psqlCmd виконує SQL з stdin, напр. docker compose exec -T postgres psql -U minicrm -d minicrm)
import { chromium } from 'playwright-core'
import { execFileSync } from 'node:child_process'
const [origin, out, ...psql] = process.argv.slice(2)
const sql = (q) => execFileSync(psql[0], psql.slice(1), { input: q, encoding: 'utf8' })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()

await page.goto(origin + '/sign-in')
await page.click('#demo-btn')
await page.waitForSelector('.demo-bar')
await page.screenshot({ path: `${out}/demo-1-started.png` })
const id = sql("SELECT max(id) FROM app_user WHERE demo;").match(/\n\s*(\d+)\s*\n/)[1]
const before = sql(`SELECT count(*) FROM client WHERE owner_id = ${id};`).match(/\n\s*(\d+)\s*\n/)[1]

// зміна даних через UI: виконати перше завдання на дашборді
await page.locator('#today-tasks input[type=checkbox]').first().check()
await page.waitForSelector('.toast')
await page.reload()
await page.waitForSelector('#kpi-tasks')
const doneAfterReload = await page.locator('#today-tasks .task-item.is-done').count()
await page.screenshot({ path: `${out}/demo-2-after-reload.png` })

sql(`UPDATE app_user SET expires_at = now() - interval '1 second' WHERE id = ${id};`)
await page.reload()
await page.waitForURL(origin + '/demo-expired')
await page.waitForSelector('.expired-card')
await page.screenshot({ path: `${out}/demo-3-expired.png` })
const left = sql(`SELECT (SELECT count(*) FROM app_user WHERE id = ${id}) || '/' || (SELECT count(*) FROM client WHERE owner_id = ${id});`).match(/\n\s*([\d/]+)\s*\n/)[1]

await page.click('#demo-btn')
await page.waitForURL(origin + '/')
await page.waitForSelector('#kpi-tasks')
const id2 = sql("SELECT max(id) FROM app_user WHERE demo;").match(/\n\s*(\d+)\s*\n/)[1]
const fresh = sql(`SELECT count(*) FROM client WHERE owner_id = ${id2};`).match(/\n\s*(\d+)\s*\n/)[1]
const doneFresh = await page.locator('#today-tasks .task-item.is-done').count()
await page.screenshot({ path: `${out}/demo-4-new-demo.png` })
console.log(JSON.stringify({ demoUser: id, clientsBefore: +before, completedTodayAfterReload: doneAfterReload, afterExpiry_userRows_clientRows: left, newDemoUser: id2, newDemoClients: +fresh, newDemoCompletedToday: doneFresh }))
await browser.close()
