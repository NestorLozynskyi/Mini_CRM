<script lang="ts">
  import { untrack } from 'svelte'
  import { ApiError } from '../../lib/api/client'
  import { interactions as api } from '../../lib/api/crm'
  import type { Deal, InteractionType } from '../../lib/api/types'
  import { todayIso } from '../../lib/format'
  import { stageLabel } from '../../lib/stages'
  import FieldError from '../../lib/ui/FieldError.svelte'
  import Icon from '../../lib/ui/Icon.svelte'
  import Modal from '../../lib/ui/Modal.svelte'
  import { toast } from '../../lib/ui/toast.svelte'

  /** «Log interaction» картки клієнта (client-detail.html): прив'язка — цей клієнт або одна з його угод. */
  let { client, deals, onsaved, onclose }: { client: { id: number; name: string }; deals: Deal[]; onsaved: () => void; onclose: () => void } = $props()
  const c = untrack(() => client)
  const own = untrack(() => deals.filter((d) => !d.archived))

  const TYPES: [InteractionType, string, string][] = [
    ['call', 'Call', 'phone-call'],
    ['meeting', 'Meeting', 'meeting'],
    ['email', 'Email', 'mail'],
    ['note', 'Note', 'note'],
  ]
  const today = todayIso()
  let type = $state<InteractionType>('call')
  let date = $state(today)
  let link = $state('client')
  let description = $state('')
  let errors = $state<Record<string, string>>({})
  let descTouched = false
  let saving = $state(false)
  let modal = $state<ReturnType<typeof Modal>>()

  const vDate = () => set('date', date ? '' : 'Choose a date')
  const vDesc = () => set('description', description.trim() ? '' : 'Description is required')
  function set(name: string, msg: string) {
    const next = { ...errors }
    if (msg) next[name] = msg
    else delete next[name]
    errors = next
    return msg
  }

  async function submit(e: SubmitEvent) {
    e.preventDefault()
    const a = vDate()
    const b = vDesc()
    if (a) return document.getElementById('ix-date')?.focus()
    if (b) return document.getElementById('ix-desc')?.focus()
    saving = true
    try {
      const dealId = link.startsWith('deal:') ? Number(link.slice(5)) : null
      await api.create({ type, date, description: description.trim(), clientId: dealId ? null : c.id, dealId })
      onsaved()
      toast({ type: 'success', text: 'Interaction logged' })
      modal?.close('save')
    } catch (err) {
      saving = false
      if (err instanceof ApiError && err.isValidation) errors = err.fieldErrors()
      else toast({ type: 'error', text: 'Couldn’t save changes. Check your connection and try again.' })
    }
  }
</script>

<Modal bind:this={modal} title="Log interaction" subtitle={c.name} {onclose}>
  <form id="ix-form" class="form-stack" novalidate onsubmit={submit}>
    <fieldset class="field">
      <legend class="field-label">Type</legend>
      <div class="segmented seg-radio" role="radiogroup" aria-label="Interaction type">
        {#each TYPES as t}
          <label class="segmented-item"><input type="radio" name="ix-type" value={t[0]} bind:group={type} /><Icon name={t[2]} size={18} /><span>{t[1]}</span></label>
        {/each}
      </div>
    </fieldset>
    <div class="form-grid">
      <div class="field" data-field="ixdate" class:is-invalid={!!errors.date}>
        <label for="ix-date">Date<span class="field-required" aria-hidden="true">*</span></label>
        <input class="input" type="date" id="ix-date" max={today} bind:value={date} onblur={vDate} aria-invalid={!!errors.date || undefined} aria-describedby={errors.date ? 'ix-date-error' : undefined} />
        <FieldError id="ix-date-error" message={errors.date} />
      </div>
      <div class="field">
        <label for="ix-link">Related to</label>
        <select class="select" id="ix-link" bind:value={link}>
          <option value="client">This client — {c.name}</option>
          {#if own.length}
            <optgroup label="Deals of {c.name}">
              {#each own as d}<option value="deal:{d.id}">{d.title} · {stageLabel(d.stage)}</option>{/each}
            </optgroup>
          {/if}
        </select>
      </div>
      <p class="field-hint span-2">
        {own.length ? 'Interactions logged on a deal also appear in this client’s history.' : 'This client has no deals yet, so the interaction is linked to the client.'}
      </p>
      <div class="field span-2" data-field="ixdesc" class:is-invalid={!!errors.description}>
        <label for="ix-desc">Description<span class="field-required" aria-hidden="true">*</span></label>
        <textarea
          class="textarea"
          id="ix-desc"
          placeholder="What was discussed? Any next steps?"
          bind:value={description}
          oninput={() => { descTouched = true; if (errors.description) vDesc() }}
          onblur={() => descTouched && vDesc()}
          aria-invalid={!!errors.description || undefined}
          aria-describedby={errors.description ? 'ix-desc-error' : undefined}></textarea>
        <FieldError id="ix-desc-error" message={errors.description} />
      </div>
    </div>
  </form>
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="submit" form="ix-form" class="btn btn-primary" class:is-loading={saving} disabled={saving}>{saving ? 'Saving…' : 'Log interaction'}</button>
  {/snippet}
</Modal>
