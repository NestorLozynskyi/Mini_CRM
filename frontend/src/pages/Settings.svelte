<script lang="ts">
  import { onMount } from 'svelte'
  import { account, deals as dealsApi } from '../lib/api/crm'
  import type { Deal } from '../lib/api/types'
  import { currencyActions, currencyGroups, currencyName } from '../lib/currencies'
  import { currencySymbol, money, moneyShort } from '../lib/format'
  import { session, setAccountCurrency } from '../lib/session.svelte'
  import EmptyState from '../lib/ui/EmptyState.svelte'
  import Icon from '../lib/ui/Icon.svelte'
  import Modal from '../lib/ui/Modal.svelte'
  import Shell from '../lib/ui/Shell.svelte'
  import { dismissToast, toast } from '../lib/ui/toast.svelte'

  const demo = $derived(!!session.me?.demo)
  const email = $derived(session.me?.email ?? '')

  let saved = $state(session.me?.currency ?? 'USD')
  let selected = $state(session.me?.currency ?? 'USD')
  let query = $state('')
  let deals = $state<Deal[] | null>(null)
  let confirming = $state(false)
  let saving = $state(false)
  let failed = $state(false)
  let errorToastId: number | null = null
  let searchEl = $state<HTMLInputElement>()
  let confirmModal = $state<ReturnType<typeof Modal>>()

  onMount(async () => {
    try {
      deals = await dealsApi.list({ scope: 'all' })
    } catch {
      deals = []
    }
  })

  // Дані для превʼю й підтвердження — з реальних угод акаунта (як у макеті: найбільша активна угода).
  const active = $derived((deals ?? []).filter((d) => !d.archived && d.stage !== 'won' && d.stage !== 'lost'))
  const pipeline = $derived(active.reduce((s, d) => s + d.amount, 0))
  const sampleAmount = $derived(active.length ? Math.max(...active.map((d) => d.amount)) : 12400)
  const allDeals = $derived(deals?.length ?? 0)
  const archivedDeals = $derived((deals ?? []).filter((d) => d.archived).length)

  const groups = $derived(currencyGroups(query))
  const matchCount = $derived(groups.reduce((n, g) => n + g.codes.length, 0))
  const actions = $derived(currencyActions(selected, saved, saving))

  function select(code: string) {
    selected = code
    failed = false
  }
  function reset() {
    selected = saved
    failed = false
    if (errorToastId) dismissToast(errorToastId)
  }
  function clearSearch() {
    query = ''
    searchEl?.focus()
  }

  async function save() {
    if (saving) return
    const to = selected
    saving = true
    try {
      const me = await account.changeCurrency(to)
      setAccountCurrency(me.currency)
      saved = me.currency
      selected = me.currency
      failed = false
      if (errorToastId) dismissToast(errorToastId)
      confirmModal?.close('save')
      toast({ type: 'success', text: `Currency changed to ${saved} — ${currencyName(saved)}. Amounts now show in ${saved}.` })
    } catch {
      failed = true
      saving = false
      confirmModal?.close('error')
      errorToastId = toast({ type: 'error', text: 'Couldn’t save the currency. Try again.' })
    }
  }
</script>

<div class="screen" data-screen="settings">
  <Shell active="settings" title="Settings" subtitle="Account preferences">
    <div class="settings">
      <section class="card" aria-labelledby="acc-title">
        <div class="card-header"><div><h2 class="card-title" id="acc-title">Account</h2></div></div>
        <div class="card-body">
          <dl class="dl">
            <div class="dl-row">
              <dt><Icon name="mail" size={16} />{demo ? 'Account' : 'Email'}</dt>
              <dd>
                {#if demo}
                  <div class="account-row">
                    <span class="badge badge-accent"><Icon name="info" size={14} />Demo account</span>
                    <span class="account-hint">Temporary demo — deleted 24 hours after start.</span>
                  </div>
                {:else}
                  <div class="account-row"><span class="account-value">{email}</span></div>
                {/if}
              </dd>
            </div>
          </dl>
        </div>
      </section>

      <section class="card" aria-labelledby="cur-title">
        <div class="card-header">
          <div>
            <h2 class="card-title" id="cur-title">Currency</h2>
            <p class="settings-section-desc">One currency for all deal amounts in your account.</p>
          </div>
        </div>
        <div class="card-body">
          {#if failed}
            <div class="settings-error" id="cur-error">
              <div class="alert alert-error" role="alert">
                <Icon name="alert-circle" size={20} />
                <div class="alert-body">
                  <div class="alert-title">Couldn’t save the currency</div>
                  <div>Your selection is kept. Check your connection and try again.</div>
                </div>
                <button type="button" class="btn btn-secondary btn-sm" id="cur-retry" onclick={() => (confirming = true)}>Retry</button>
              </div>
            </div>
          {/if}
          <div class="settings-grid">
            <div class="option-list">
              <div class="currency-current" id="cur-current">
                {#if actions.dirty}
                  <p class="currency-change">Changing from <strong>{saved}</strong> to <strong>{selected}</strong> — {currencyName(selected)}. Not saved yet.</p>
                {:else}
                  <p class="currency-change">Account currency: <strong>{saved}</strong> — {currencyName(saved)}</p>
                {/if}
              </div>
              <label class="sr-only" for="cur-search">Search currencies</label>
              <div class="search">
                <Icon name="search" size={18} />
                <input class="input" id="cur-search" type="search" autocomplete="off" placeholder="Search by code or name" aria-controls="cur-list" bind:this={searchEl} bind:value={query} />
              </div>
              <div class="option-list-count" id="cur-count" aria-live="polite">
                {#if !query.trim()}{matchCount} currencies{:else if matchCount}{matchCount === 1 ? '1 currency' : `${matchCount} currencies`} match “{query.trim()}”{/if}
              </div>
              <div class="option-list-scroll" id="cur-list" role="radiogroup" aria-labelledby="cur-title">
                {#if groups.length}
                  {#each groups as g (g.label)}
                    <div class="option-group" role="group" aria-label={g.label}>
                      {#if g.label !== 'Results'}<div class="option-group-label" aria-hidden="true">{g.label}</div>{/if}
                      {#each g.codes as code (code)}
                        <label class="option-row">
                          <input type="radio" name="currency" value={code} checked={code === selected} onchange={() => select(code)} />
                          <span class="option-code">{code}</span>
                          <span class="option-name">{currencyName(code)}</span>
                          {#if code === saved}<span class="badge">Current</span>{:else}<span class="option-meta" aria-hidden="true">{currencySymbol(code)}</span>{/if}
                        </label>
                      {/each}
                    </div>
                  {/each}
                {:else}
                  <div class="option-list-empty">
                    <EmptyState compact icon="search" title="No currencies match “{query.trim()}”" text="Search by a 3-letter ISO code like EUR or by name like Euro.">
                      {#snippet actions()}<button type="button" class="btn btn-secondary btn-sm" id="cur-clear" onclick={clearSearch}>Clear search</button>{/snippet}
                    </EmptyState>
                  </div>
                {/if}
              </div>
            </div>
            <aside class="settings-preview" aria-label="Preview">
              <div class="amount-preview" id="cur-preview">
                <div class="amount-preview-title">Preview in {selected}</div>
                <dl class="amount-preview-rows">
                  <div class="amount-preview-row"><dt>Deal amount</dt><dd class="is-large">{money(sampleAmount, selected)}</dd></div>
                  <div class="amount-preview-row"><dt>Active pipeline</dt><dd>{money(pipeline, selected)}</dd></div>
                  <div class="amount-preview-row"><dt>Board column total</dt><dd>{moneyShort(sampleAmount, selected)}</dd></div>
                </dl>
                <p class="amount-preview-note"><Icon name="info" size={18} /><span>Amounts aren’t converted — only the currency sign changes.</span></p>
              </div>
            </aside>
          </div>
        </div>
        <div class="card-footer">
          <button type="button" class="btn btn-secondary" id="cur-reset" disabled={!actions.canCancel} onclick={reset}>Cancel</button>
          <button type="button" class="btn btn-primary" id="cur-save" disabled={!actions.canSave} onclick={() => (confirming = true)}>Save</button>
        </div>
      </section>
    </div>
  </Shell>

  {#if confirming}
    {@const from = saved}
    {@const to = selected}
    <Modal
      bind:this={confirmModal}
      title="Change currency to {to}?"
      size="sm"
      icon="alert-triangle"
      className="confirm"
      role="alertdialog"
      closeButton={false}
      dismissible={!saving}
      initialFocus="[data-close]"
      onclose={() => { confirming = false; saving = false }}>
      <p class="confirm-text">
        Amounts won’t be converted. {allDeals === 1 ? 'Your 1 deal' : `All ${allDeals} deals`}{archivedDeals ? ', including archived ones,' : ''} will be shown in {to}.
      </p>
      <p class="confirm-text">
        For example, <span class="od-nowrap">{money(sampleAmount, from)}</span> becomes <span class="od-nowrap"><strong>{money(sampleAmount, to)}</strong></span>.
      </p>
      {#snippet footer()}
        <button type="button" class="btn btn-secondary" data-close disabled={saving}>Cancel</button>
        <button type="button" class="btn btn-primary" class:is-loading={saving} aria-busy={saving || undefined} onclick={() => void save()}>{saving ? 'Saving…' : `Change to ${to}`}</button>
      {/snippet}
    </Modal>
  {/if}
</div>
