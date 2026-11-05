<script lang="ts">
  import { untrack } from 'svelte'
  import { ApiError } from '../../lib/api/client'
  import { clients as api } from '../../lib/api/crm'
  import type { Contact } from '../../lib/api/types'
  import FieldError from '../../lib/ui/FieldError.svelte'
  import Modal from '../../lib/ui/Modal.svelte'
  import { toast } from '../../lib/ui/toast.svelte'

  /** Додавання/редагування контактної особи компанії (макет client-detail.html, openContactForm). */
  interface Props {
    clientId: number
    clientName: string
    contact?: Contact | null
    onsaved: (c: Contact, isNew: boolean) => void
    onclose: () => void
  }
  let { clientId, clientName, contact = null, onsaved, onclose }: Props = $props()
  // Форма заповнюється один раз початковими значеннями пропсів.
  const init = untrack(() => ({ contact, clientId }))
  const isEdit = !!init.contact

  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
  let name = $state(init.contact?.name ?? '')
  let phone = $state(init.contact?.phone ?? '')
  let email = $state(init.contact?.email ?? '')
  let errors = $state<Record<string, string>>({})
  let touched = false
  let saving = $state(false)
  let modal = $state<ReturnType<typeof Modal>>()

  function setError(field: string, msg: string) {
    const next = { ...errors }
    if (msg) next[field] = msg
    else delete next[field]
    errors = next
    return msg
  }
  const vName = () => setError('name', name.trim() ? '' : 'Name is required')
  const vEmail = () => setError('email', !email.trim() || EMAIL_RE.test(email.trim()) ? '' : 'Enter a valid email address')

  async function submit(e: Event) {
    e.preventDefault()
    const a = vName()
    const b = vEmail()
    if (a) return document.getElementById('ct-name')?.focus()
    if (b) return document.getElementById('ct-email')?.focus()
    saving = true
    const body = { name: name.trim(), phone: phone.trim() || null, email: email.trim() || null }
    try {
      const saved = isEdit ? await api.updateContact(init.contact!.id, body) : await api.addContact(init.clientId, body)
      onsaved(saved, !isEdit)
      toast({ type: 'success', text: isEdit ? 'Contact person updated' : `${saved.name} added to contact persons` })
      modal?.close('save')
    } catch (err) {
      saving = false
      if (err instanceof ApiError && err.isValidation) errors = err.fieldErrors()
      else toast({ type: 'error', text: 'Couldn’t save changes. Check your connection and try again.' })
    }
  }
</script>

<Modal bind:this={modal} title={isEdit ? 'Edit contact person' : 'Add contact person'} subtitle={clientName} size="sm" {onclose}>
  <form id="contact-form" class="form-stack" novalidate onsubmit={submit}>
    <div class="field" data-field="cname" class:is-invalid={!!errors.name}>
      <label for="ct-name">Name<span class="field-required" aria-hidden="true">*</span></label>
      <input
        class="input"
        id="ct-name"
        autocomplete="off"
        placeholder="e.g. Olivia Hart"
        bind:value={name}
        oninput={() => { touched = true; if (errors.name) vName() }}
        onblur={() => touched && vName()}
        aria-invalid={!!errors.name || undefined}
        aria-describedby={errors.name ? 'ct-name-error' : undefined} />
      <FieldError id="ct-name-error" message={errors.name} />
    </div>
    <div class="field" data-field="cphone" class:is-invalid={!!errors.phone}>
      <label for="ct-phone">Phone <span class="field-optional">(optional)</span></label>
      <input class="input" type="tel" id="ct-phone" placeholder="+1 (555) 555-0100" bind:value={phone} aria-invalid={!!errors.phone || undefined} aria-describedby={errors.phone ? 'ct-phone-error' : undefined} />
      <FieldError id="ct-phone-error" message={errors.phone} />
    </div>
    <div class="field" data-field="cemail" class:is-invalid={!!errors.email}>
      <label for="ct-email">Email <span class="field-optional">(optional)</span></label>
      <input
        class="input"
        type="email"
        id="ct-email"
        placeholder="name@company.com"
        bind:value={email}
        oninput={() => errors.email && vEmail()}
        onblur={() => email.trim() && vEmail()}
        aria-invalid={!!errors.email || undefined}
        aria-describedby={errors.email ? 'ct-email-error' : undefined} />
      <FieldError id="ct-email-error" message={errors.email} />
    </div>
  </form>
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="submit" form="contact-form" class="btn btn-primary" class:is-loading={saving} aria-busy={saving || undefined} disabled={saving}>
      {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Add contact'}
    </button>
  {/snippet}
</Modal>
