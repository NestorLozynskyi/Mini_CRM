// Доказ Drag & Drop на канбані угод: реальне перетягування картки мишею (HTML5 DnD),
// скриншоти до / під час / після, перезавантаження сторінки й перевірка, що етап збережено на сервері.
// Також: скидання в Lost → діалог причини (Cancel повертає, Confirm зберігає причину), Undo.
// Запуск: node deals-dnd.mjs [origin=http://127.0.0.1:5173] [outDir=/tmp/mcrm-deals/dnd]
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright-core'

const origin = process.argv[2] ?? process.env.ORIGIN ?? 'http://127.0.0.1:5173'
const out = process.argv[3] ?? '/tmp/mcrm-deals/dnd'
mkdirSync(out, { recursive: true })

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
page.on('pageerror', (e) => console.error('pageerror:', e.message))

let failures = 0
function check(ok, msg) {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${msg}`)
  if (!ok) failures++
}
const col = (stage) => `.board .kanban-col[data-stage="${stage}"]`
const card = (id) => `.board .deal-card[data-id="${id}"]`
const stageOfCard = (id) => page.$eval(card(id), (el) => el.closest('.kanban-col').dataset.stage).catch(() => null)
const apiDeal = (id) => page.evaluate(async (id) => (await fetch(`/api/deals/${id}`)).json(), id)
const count = (stage) => page.$eval(`${col(stage)} .kanban-count`, (el) => Number(el.textContent))

/** Реальний HTML5 drag мишею: натиснути на картці, повести до колонки, (скриншот), відпустити. */
async function dragCard(id, toStage, shot) {
  const from = await page.$(card(id) + ' .deal-card-client')
  const fb = await from.boundingBox()
  const tb = await (await page.$(`${col(toStage)} .kanban-col-body`)).boundingBox()
  await page.mouse.move(fb.x + fb.width / 2, fb.y + fb.height / 2)
  await page.mouse.down()
  const tx = tb.x + tb.width / 2
  const ty = tb.y + 30
  for (let i = 1; i <= 12; i++) await page.mouse.move(fb.x + (tx - fb.x) * (i / 12), fb.y + (ty - fb.y) * (i / 12))
  await page.waitForTimeout(100)
  // Користувач зависає над колонкою: браузер шле dragover постійно; синтетичному вводу потрібен ще один рух
  // після того, як з'явився drop slot, інакше Chromium скасовує drop.
  await page.mouse.move(tx + 2, ty + 2)
  await page.waitForTimeout(100)
  if (shot) await page.screenshot({ path: `${out}/${shot}` })
  const during = {
    target: await page.$eval(col(toStage), (el) => el.classList.contains('is-drop-target')),
    slot: await page.$eval(col(toStage), (el) => el.querySelector('.kanban-drop-slot')?.textContent ?? null),
    source: await page.$eval(card(id), (el) => el.classList.contains('is-drag-source')),
  }
  await page.mouse.up()
  return during
}

// 1. Новий демо-акаунт
await page.goto(origin + '/sign-in')
await page.click('#demo-btn')
await page.waitForURL(origin + '/')
await page.goto(origin + '/deals')
await page.waitForSelector('.board .deal-card')

// 2. Перша картка з Lead → Proposal
const id = await page.$eval(`${col('lead')} .deal-card`, (el) => el.dataset.id)
const title = await page.$eval(card(id) + ' .deal-card-title', (el) => el.textContent)
const [leadBefore, propBefore] = [await count('lead'), await count('proposal')]
console.log(`Deal #${id} “${title}”: lead=${leadBefore}, proposal=${propBefore}`)
await page.screenshot({ path: `${out}/1-before.png` })

const respP = page.waitForResponse((r) => r.url().endsWith(`/api/deals/${id}/stage`) && r.request().method() === 'PATCH')
const during = await dragCard(id, 'proposal', '2-during.png')
check(during.target, 'during drag: Proposal column highlighted as drop target')
check(during.slot === 'Drop to move to Proposal', `during drag: drop slot shown (“${during.slot}”)`)
check(during.source, 'during drag: source card dimmed (is-drag-source)')
const resp = await respP
check(resp.status() === 200, `PATCH /api/deals/${id}/stage → ${resp.status()} ${JSON.stringify(resp.request().postDataJSON())}`)
await page.waitForSelector('.toast >> text=Moved to Proposal')
check(true, 'toast “Moved to Proposal” with Undo shown')
check((await stageOfCard(id)) === 'proposal', 'after drop: card is in Proposal column')
check((await count('lead')) === leadBefore - 1 && (await count('proposal')) === propBefore + 1, 'after drop: column counts updated')
await page.screenshot({ path: `${out}/3-after-drop.png` })

await page.reload()
await page.waitForSelector('.board .deal-card')
check((await stageOfCard(id)) === 'proposal', 'after reload: card still in Proposal (persisted on server)')
check((await apiDeal(id)).stage === 'proposal', 'GET /api/deals/:id → stage=proposal')
await page.screenshot({ path: `${out}/4-after-reload.png` })

// 3. Undo: перетягнути в Negotiation, натиснути Undo → назад у Proposal (на сервері)
await dragCard(id, 'negotiation')
await page.waitForSelector('.toast >> text=Moved to Negotiation')
const undoP = page.waitForResponse((r) => r.url().endsWith(`/api/deals/${id}/stage`) && r.request().postDataJSON()?.stage === 'proposal')
await page.click('.toast:has-text("Moved to Negotiation") >> text=Undo')
await undoP
await page.waitForTimeout(200)
check((await apiDeal(id)).stage === 'proposal' && (await stageOfCard(id)) === 'proposal', 'Undo moves the deal back to Proposal on the server')

// 4. Lost: скидання відкриває діалог; Cancel — картка лишається
await dragCard(id, 'lost')
await page.waitForSelector('.modal-title >> text=Mark deal as lost')
check((await page.$$('.kanban-col.is-drop-target, .kanban-drop-slot')).length === 0, 'drop on Lost → “Mark deal as lost” dialog; drag highlight cleared')
await page.waitForTimeout(400)
await page.screenshot({ path: `${out}/5-lost-dialog.png` })
await page.click('.modal [data-close]:has-text("Cancel")')
await page.waitForSelector('.modal', { state: 'detached' })
check((await stageOfCard(id)) === 'proposal' && (await apiDeal(id)).stage === 'proposal', 'Lost dialog → Cancel: deal stays in Proposal')

// 5. Lost з причиною → зберігається; після reload причина на картці
await dragCard(id, 'lost')
await page.waitForSelector('.modal-title >> text=Mark deal as lost')
await page.fill('#lost-reason', 'Went with a cheaper option')
await page.click('.modal .btn-primary:has-text("Mark as lost")')
await page.waitForSelector('.toast >> text=Moved to Lost')
await page.reload()
await page.waitForSelector('.board .deal-card')
const lost = await apiDeal(id)
check((await stageOfCard(id)) === 'lost' && lost.stage === 'lost' && lost.lostReason === 'Went with a cheaper option', `after reload: Lost with reason (“${lost.lostReason}”)`)
check((await page.$eval(card(id) + ' .deal-card-note', (el) => el.textContent)) === 'Went with a cheaper option', 'lost reason shown on card')
await page.screenshot({ path: `${out}/6-lost-after-reload.png` })

await browser.close()
console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll checks passed')
process.exit(failures ? 1 : 0)
