<script lang="ts">
  import { onMount, untrack } from 'svelte'
  import { ApiError } from '../api/client'
  import { clients as clientsApi, deals as api } from '../api/crm'
  import type { ClientSummary, Deal, Stage } from '../api/types'
  import { currencySymbol } from '../format'
  import { session } from '../session.svelte'
  import { STAGES } from '../stages'
  import Avatar from './Avatar.svelte'
  import FieldError from './FieldError.svelte'
  import Icon from './Icon.svelte'
  import Modal from './Modal.svelte'
  import { toast } from './toast.svelte'

  interface Props {
    deal?: Deal | null
    /** Початковий клієнт для нової угоди (наприклад, з картки клієнта). */
    clientId?: number | null
    stage?: Stage
    onsaved?: (d: Deal, isNew: boolean) => void
    onclose: () => void
  }
  let { deal = null, clientId = null, stage: initialStage = 'lead', onsaved, onclose }: Props = $props()
  // Форма заповнюється один раз початковими значеннями пропсів.
  const init = untrack(() => ({ deal, clientId, initialStage }))

  const isEdit = !!init.deal
  let title = $state(init.deal?.title ?? '')
  let clientsList = $state<ClientSummary[]>([])
  let selected = $state<number | null>(init.deal?.clientId ?? init.clientId)
  let clientText = $state(init.deal?.clientName ?? '')
  let amount = $state<string>(init.deal ? String(init.deal.amount) : '')
  let stage = $state<Stage>(init.deal?.stage ?? init.initialStage)
  let expectedClose = $state(init.deal?.expectedClose ?? '')
  let lostReason = $state(init.deal?.lostReason ?? '')
  let errors = $state<Record<string, string>>({})
  let saving = $state(false)
  let open = $state(false)
  let active = $state(-1)
  let amountEl = $state<HTMLInputElement>()
  let modal = $state<ReturnType<typeof Modal>>()
  const currency = $derived(session.me?.currency ?? 'USD')

  onMount(async () => {
    try {
      clientsList = await clientsApi.list()
      if (selected && !clientText) clientText = clientsList.find((c) => c.id === selected)?.name ?? ''
    } catch {
      clientsList = []
    }
  })

  const matches = $derived.by(() => {
    let q = clientText.trim().toLowerCase()
    const sel = selected ? clientsList.find((c) => c.id === selected) : null
    if (sel && sel.name.toLowerCase() === q) q = ''
    return clientsList.filter((c) => !q || c.name.toLowerCase().includes(q))
  })

  function setErr(name: string, msg: string) {
    const next = { ...errors }
    if (msg) next[name] = msg
    else delete next[name]
    errors = next
    return !msg
  }
  const vTitle = () => setErr('title', title.trim() ? '' : 'Enter a deal title.')
  const vClient = () => setErr('clientId', !clientText.trim() ? 'Select a client.' : !selected ? 'Choose a client from the list or add a new client.' : '')
  function vAmount() {
    const bad = amountEl?.validity?.badInput
    const raw = String(amount ?? '').trim()
    return setErr('amount', bad ? 'Enter a number, e.g. 2500.' : raw === '' ? 'Enter an amount (use 0 if it’s not known yet).' : Number(raw) < 0 ? 'Amount can’t be negative.' : '')
  }

  function choose(c: ClientSummary) {
    selected = c.id
    clientText = c.name
    open = false
    setErr('clientId', '')
  }
  function onClientInput() {
    const v = clientText.trim().toLowerCase()
    selected = clientsList.find((c) => c.name.toLowerCase() === v)?.id ?? null
    active = v ? 0 : -1
    open = true
    if (selected && errors.clientId) setErr('clientId', '')
  }
  function onClientKey(e: KeyboardEvent) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      open = true
      if (!matches.length) return
      active = e.key === 'ArrowDown' ? (active + 1) % matches.length : active <= 0 ? matches.length - 1 : active - 1
      document.getElementById(`df-opt-${matches[active].id}`)?.scrollIntoView({ block: 'nearest' })
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (open && matches[active]) choose(matches[active])
    } else if (e.key === 'Escape' && open) {
      e.preventDefault()
      e.stopPropagation()
      open = false
    }
  }

  async function save() {
    const okT = vTitle()
    const okC = vClient()
    const okA = vAmount()
    if (!okT) return document.getElementById('df-title')?.focus()
    if (!okC) return document.getElementById('df-client')?.focus()
    if (!okA) return amountEl?.focus()
    saving = true
    try {
      const body = {
        title: title.trim(),
        clientId: selected,
        amount: Math.round(Number(amount) || 0),
        stage,
        expectedClose: expectedClose || null,
        lostReason: stage === 'lost' ? lostReason.trim() || null : null,
      }
      const saved = isEdit ? await api.update(init.deal!.id, body) : await api.create(body)
      onsaved?.(saved, !isEdit)
      toast({ type: 'success', text: isEdit ? 'Deal updated' : 'Deal created' })
      modal?.close('save')
    } catch (e) {
      saving = false
      if (e instanceof ApiError && e.isValidation) errors = e.fieldErrors()
      else if (e instanceof ApiError && e.status === 404) setErr('clientId', 'Choose a client from the list or add a new client.')
      else toast({ type: 'error', text: 'Couldn’t save changes. Check your connection and try again.' })
    }
  }
</script>

<Modal bind:this={modal} title={isEdit ? 'Edit deal' : 'New deal'} {onclose}>
  <form class="form-stack" data-form="deal" novalidate onsubmit={(e) => { e.preventDefault(); void save() }}>
    <div class="field" data-field="title" class:is-invalid={!!errors.title}>
      <label for="df-title">Title<span class="field-required" aria-hidden="true">*</span></label>
      <input
        class="input"
        id="df-title"
        name="title"
        required
        autocomplete="off"
        placeholder="e.g. Website redesign"
        bind:value={title}
        onblur={vTitle}
        oninput={() => errors.title && vTitle()}
        aria-invalid={!!errors.title || undefined}
        aria-describedby={errors.title ? 'df-title-error' : undefined} />
      <FieldError id="df-title-error" message={errors.title} />
    </div>
    <div class="field" data-field="client" class:is-invalid={!!errors.clientId}>
      <div class="df-label-row">
        <label for="df-client">Client<span class="field-required" aria-hidden="true">*</span></label>
        <a class="btn-link df-new-client" href="/clients?new=1">+ New client</a>
      </div>
      <div class="combo">
        <Icon name="search" size={18} />
        <input
          class="input"
          id="df-client"
          name="client"
          role="combobox"
          aria-autocomplete="list"
          aria-expanded={open}
          aria-controls="df-client-list"
          aria-activedescendant={open && matches[active] ? `df-opt-${matches[active].id}` : undefined}
          autocomplete="off"
          required
          placeholder="Search clients"
          bind:value={clientText}
          onfocus={() => (open = true)}
          onclick={() => (open = true)}
          oninput={onClientInput}
          onkeydown={onClientKey}
          onblur={() => { open = false; vClient() }}
          aria-invalid={!!errors.clientId || undefined}
          aria-describedby={errors.clientId ? 'df-client-error' : undefined} />
        <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
        <ul class="combo-list" id="df-client-list" role="listbox" aria-label="Clients" hidden={!open} onmousedown={(e) => e.preventDefault()}>
          {#if matches.length}
            {#each matches as c, i (c.id)}
              <!-- svelte-ignore a11y_click_events_have_key_events -->
              <li class="combo-option" class:is-active={i === active} role="option" id="df-opt-{c.id}" aria-selected={c.id === selected} onclick={() => choose(c)}>
                <Avatar name={c.name} size="sm" square={c.type === 'company'} />
                <span class="combo-option-text"><span class="combo-option-name">{c.name}</span><span class="combo-option-meta">{c.type === 'company' ? 'Company' : 'Person'}</span></span>
                {#if c.id === selected}<Icon name="check" size={16} />{/if}
              </li>
            {/each}
          {:else}
            <li class="combo-empty" role="presentation">No clients match “{clientText.trim()}”. Use “+ New client” to add one.</li>
          {/if}
        </ul>
      </div>
      <FieldError id="df-client-error" message={errors.clientId} />
    </div>
    <div class="form-grid">
      <div class="field" data-field="amount" class:is-invalid={!!errors.amount}>
        <label for="df-amount">Amount ({currency})<span class="field-required" aria-hidden="true">*</span></label>
        <div class="input-affix" style="--affix-ch:{currencySymbol().length}ch">
          <span class="input-affix-text" aria-hidden="true">{currencySymbol()}</span>
          <input
            class="input"
            id="df-amount"
            name="amount"
            type="number"
            inputmode="numeric"
            min="0"
            step="1"
            required
            placeholder="0"
            bind:this={amountEl}
            bind:value={amount}
            onblur={vAmount}
            oninput={() => errors.amount && vAmount()}
            aria-invalid={!!errors.amount || undefined}
            aria-describedby={errors.amount ? 'df-amount-error' : undefined} />
        </div>
        <FieldError id="df-amount-error" message={errors.amount} />
      </div>
      <div class="field">
        <label for="df-stage">Stage</label>
        <select class="select" id="df-stage" name="stage" bind:value={stage}>
          {#each STAGES as s}<option value={s.id}>{s.label}</option>{/each}
        </select>
      </div>
      <div class="field">
        <label for="df-close">Expected close date <span class="field-optional">(optional)</span></label>
        <input class="input" type="date" id="df-close" name="expectedClose" bind:value={expectedClose} />
      </div>
    </div>
    {#if stage === 'lost'}
      <div class="field">
        <label for="df-lost">Why was this deal lost? <span class="field-optional">(optional)</span></label>
        <textarea class="textarea" id="df-lost" placeholder="e.g. Went with a cheaper option" bind:value={lostReason}></textarea>
      </div>
    {/if}
  </form>
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="button" class="btn btn-primary" class:is-loading={saving} disabled={saving} onclick={save}>{saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create deal'}</button>
  {/snippet}
</Modal>
