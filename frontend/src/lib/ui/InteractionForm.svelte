<script lang="ts">
  import { onMount, untrack } from 'svelte'
  import { ApiError } from '../api/client'
  import { interactions as api } from '../api/crm'
  import type { Interaction, InteractionType } from '../api/types'
  import { todayIso } from '../format'
  import { linkValue, loadLinkOptions, parseLink, type LinkOptions } from '../links'
  import FieldError from './FieldError.svelte'
  import Icon from './Icon.svelte'
  import LinkSelect from './LinkSelect.svelte'
  import Modal from './Modal.svelte'
  import { toast } from './toast.svelte'

  interface Props {
    interaction?: Interaction | null
    ctx?: { clientId?: number | null; dealId?: number | null }
    onsaved?: (i: Interaction) => void
    onclose: () => void
  }
  let { interaction = null, ctx = {}, onsaved, onclose }: Props = $props()
  // Форма заповнюється один раз початковими значеннями пропсів.
  const init = untrack(() => ({ interaction, ctx }))

  const isEdit = !!init.interaction
  const TYPES: { id: InteractionType; label: string; icon: string }[] = [
    { id: 'call', label: 'Call', icon: 'phone-call' },
    { id: 'meeting', label: 'Meeting', icon: 'meeting' },
    { id: 'email', label: 'Email', icon: 'mail' },
    { id: 'note', label: 'Note', icon: 'note' },
  ]

  let type = $state<InteractionType>(init.interaction?.type ?? 'call')
  let date = $state(init.interaction?.date ?? todayIso())
  let description = $state(init.interaction?.description ?? '')
  let link = $state(init.interaction ? linkValue(init.interaction) : linkValue(init.ctx))
  let options = $state<LinkOptions | null>(null)
  let errors = $state<Record<string, string>>({})
  let saving = $state(false)
  let modal = $state<ReturnType<typeof Modal>>()

  onMount(async () => {
    try {
      options = await loadLinkOptions()
    } catch {
      options = { clients: [], deals: [] }
    }
  })

  function validate() {
    const e: Record<string, string> = {}
    if (!date) e.date = 'Choose a date'
    if (!link) e.link = 'Link it to a client or deal.'
    if (!description.trim()) e.description = 'Description is required'
    errors = e
    return Object.keys(e)
  }

  async function save() {
    const bad = validate()
    if (bad.length) {
      document.getElementById(bad[0] === 'description' ? 'if-desc' : bad[0] === 'link' ? 'if-link' : 'if-date')?.focus()
      return
    }
    saving = true
    try {
      const body = { type, date, description: description.trim(), ...parseLink(link) }
      const saved = isEdit ? await api.update(init.interaction!.id, body) : await api.create(body)
      onsaved?.(saved)
      toast({ type: 'success', text: isEdit ? 'Interaction updated' : 'Interaction logged' })
      modal?.close('save')
    } catch (e) {
      saving = false
      if (e instanceof ApiError && e.isValidation) errors = e.fieldErrors()
      else toast({ type: 'error', text: 'Couldn’t save changes. Check your connection and try again.' })
    }
  }
</script>

<Modal bind:this={modal} title={isEdit ? 'Edit interaction' : 'Log interaction'} {onclose}>
  <form class="form-stack" data-form="interaction" novalidate onsubmit={(e) => { e.preventDefault(); void save() }}>
    <fieldset class="field">
      <legend class="field-label">Type</legend>
      <div class="radio-cards is-compact" style="--cols:4">
        {#each TYPES as t}
          <label class="radio-card"
            ><input type="radio" name="itype" value={t.id} bind:group={type} /><span class="radio-card-body"><Icon name={t.icon} size={20} />{t.label}</span></label>
        {/each}
      </div>
    </fieldset>
    <div class="form-grid">
      <div class="field" class:is-invalid={!!errors.date}>
        <label for="if-date">Date<span class="field-required" aria-hidden="true">*</span></label>
        <input class="input" type="date" id="if-date" name="date" required bind:value={date} aria-invalid={!!errors.date || undefined} aria-describedby={errors.date ? 'if-date-error' : undefined} />
        <FieldError id="if-date-error" message={errors.date} />
      </div>
      <div class="field" class:is-invalid={!!errors.link}>
        <label for="if-link">Linked to<span class="field-required" aria-hidden="true">*</span></label>
        <LinkSelect id="if-link" bind:value={link} {options} invalid={!!errors.link} describedby={errors.link ? 'if-link-error' : undefined} />
        <FieldError id="if-link-error" message={errors.link} />
      </div>
    </div>
    <div class="field" data-field="description" class:is-invalid={!!errors.description}>
      <label for="if-desc">Description<span class="field-required" aria-hidden="true">*</span></label>
      <textarea
        class="textarea"
        id="if-desc"
        name="description"
        required
        placeholder="What was discussed?"
        bind:value={description}
        oninput={() => errors.description && validate()}
        aria-invalid={!!errors.description || undefined}
        aria-describedby={errors.description ? 'if-desc-error' : undefined}></textarea>
      <FieldError id="if-desc-error" message={errors.description} />
    </div>
  </form>
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="button" class="btn btn-primary" class:is-loading={saving} disabled={saving} onclick={save}>
      {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Log interaction'}
    </button>
  {/snippet}
</Modal>
