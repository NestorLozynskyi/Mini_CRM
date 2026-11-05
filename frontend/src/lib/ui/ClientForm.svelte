<script lang="ts">
  import { untrack } from 'svelte'
  import { ApiError } from '../api/client'
  import { clients as api } from '../api/crm'
  import type { Client, ClientType } from '../api/types'
  import { refreshTags, tagStore } from '../stores.svelte'
  import FieldError from './FieldError.svelte'
  import Icon from './Icon.svelte'
  import Modal from './Modal.svelte'
  import TagChip from './TagChip.svelte'
  import { dismissToast, toast } from './toast.svelte'

  interface Props {
    client?: Client | null
    onsaved?: (c: Client, isNew: boolean) => void
    onclose: () => void
  }
  let { client = null, onsaved, onclose }: Props = $props()
  // Форма заповнюється один раз початковими значеннями пропсів.
  const init = untrack(() => ({ client }))

  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
  const URL_RE = /^(https?:\/\/)?([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,}(:\d+)?(\/\S*)?$/i
  const RULES: Record<string, (v: string) => string> = {
    name: (v) => (v.trim() ? '' : 'Name is required'),
    email: (v) => (!v.trim() || EMAIL_RE.test(v.trim()) ? '' : 'Enter a valid email address'),
    website: (v) => (!v.trim() || URL_RE.test(v.trim()) ? '' : 'Enter a valid website URL'),
  }

  const isEdit = !!init.client
  let v = $state({
    type: (init.client?.type ?? 'company') as ClientType,
    name: init.client?.name ?? '',
    phone: init.client?.phone ?? '',
    email: init.client?.email ?? '',
    address: init.client?.address ?? '',
    website: init.client?.website ?? '',
    notes: init.client?.notes ?? '',
    tags: [...(init.client?.tags ?? [])],
  })
  let errors = $state<Record<string, string>>({})
  let touched: Record<string, boolean> = {}
  let alert = $state<'invalid' | 'save' | null>(null)
  let saving = $state(false)
  let tagQuery = $state('')
  let tagInput = $state<HTMLInputElement>()
  let modal = $state<ReturnType<typeof Modal>>()
  let errorToastId: number | null = null
  const isCo = $derived(v.type === 'company')

  function check(name: string) {
    const msg = RULES[name]((v as Record<string, unknown>)[name] as string)
    errors = { ...errors, [name]: msg }
    if (!msg) delete errors[name]
    return msg
  }
  function validateAll() {
    const bad = ['name', 'email', 'website'].filter((n) => !!check(n))
    alert = bad.length ? 'invalid' : null
    return bad
  }

  // ---- tags (chip input)
  const allTagNames = $derived(tagStore.list.map((t) => t.name))
  const suggestions = $derived.by(() => {
    const q = tagQuery.trim().toLowerCase()
    return allTagNames.filter((n) => !v.tags.includes(n) && (!q || n.toLowerCase().includes(q)))
  })
  const exact = $derived(allTagNames.concat(v.tags).some((n) => n.toLowerCase() === tagQuery.trim().toLowerCase()))

  function addTag(name: string) {
    name = name.trim().replace(/,+$/, '').trim()
    if (!name) return
    const canon = allTagNames.find((n) => n.toLowerCase() === name.toLowerCase()) ?? name
    if (!v.tags.some((t) => t.toLowerCase() === canon.toLowerCase())) v.tags.push(canon)
    tagQuery = ''
    tagInput?.focus()
  }
  function onTagKey(e: KeyboardEvent) {
    if ((e.key === 'Enter' || e.key === ',') && tagQuery.trim()) {
      e.preventDefault()
      addTag(tagQuery)
    } else if (e.key === 'Enter') {
      e.preventDefault()
    } else if (e.key === 'Backspace' && !tagQuery && v.tags.length) {
      v.tags.pop()
    }
  }

  async function submit(e?: Event) {
    e?.preventDefault()
    const bad = validateAll()
    if (bad.length) {
      document.getElementById('cf-' + bad[0])?.focus()
      return
    }
    saving = true
    const body = {
      type: v.type,
      name: v.name.trim(),
      phone: v.phone.trim(),
      email: v.email.trim(),
      address: v.address.trim(),
      website: v.website.trim().replace(/^https?:\/\//i, '').replace(/\/$/, ''),
      notes: v.notes.trim(),
      tags: v.tags,
    }
    try {
      const saved = isEdit ? await api.update(init.client!.id, body) : await api.create(body)
      if (errorToastId) dismissToast(errorToastId)
      void refreshTags()
      onsaved?.(saved, !isEdit)
      modal?.close('save')
    } catch (err) {
      saving = false
      if (err instanceof ApiError && err.isValidation) {
        errors = err.fieldErrors()
        alert = 'invalid'
      } else {
        alert = 'save'
        errorToastId = toast({ type: 'error', text: 'Couldn’t save changes. Check your connection and try again.', duration: 0, action: { label: 'Retry', onClick: () => void submit() } })
      }
    }
  }

  const invalidCount = $derived(Object.keys(errors).length)
</script>

<Modal bind:this={modal} title={isEdit ? 'Edit client' : 'New client'} subtitle={isEdit ? client!.name : 'Add a person or a company you work with.'} onclose={() => { if (errorToastId) dismissToast(errorToastId); onclose() }}>
  <form id="client-form" class="form-stack" novalidate onsubmit={submit}>
    {#if alert === 'invalid' && invalidCount}
      <div>
        <div class="alert alert-error" role="alert">
          <Icon name="alert-circle" size={20} />
          <div class="alert-body">
            <span class="alert-title">Check the highlighted fields</span><span>{invalidCount === 1 ? '1 field needs' : invalidCount + ' fields need'} your attention before the client can be saved.</span>
          </div>
        </div>
      </div>
    {:else if alert === 'save'}
      <div>
        <div class="alert alert-error" role="alert">
          <Icon name="alert-circle" size={20} />
          <div class="alert-body">
            <span class="alert-title">Couldn’t save changes</span><span>Couldn’t save changes. Check your connection and try again. Your edits are kept.</span>
            <div class="alert-actions"><button type="button" class="btn btn-secondary btn-sm" onclick={() => void submit()}><Icon name="refresh" size={16} />Retry</button></div>
          </div>
        </div>
      </div>
    {/if}
    <fieldset class="field">
      <legend class="field-label">Type<span class="field-required" aria-hidden="true">*</span></legend>
      <div class="radio-cards">
        <label class="radio-card"
          ><input type="radio" name="ctype" value="person" bind:group={v.type} /><span class="radio-card-body"
            ><Icon name="user" size={20} /><span><span class="radio-card-title">Person</span><span class="radio-card-text">Individual client</span></span></span
          ></label>
        <label class="radio-card"
          ><input type="radio" name="ctype" value="company" bind:group={v.type} /><span class="radio-card-body"
            ><Icon name="building" size={20} /><span><span class="radio-card-title">Company</span><span class="radio-card-text">Business with contact persons</span></span></span
          ></label>
      </div>
    </fieldset>
    <div class="field" data-field="name" class:is-invalid={!!errors.name}>
      <label for="cf-name"><span>{isCo ? 'Company name' : 'Full name'}</span><span class="field-required" aria-hidden="true">*</span></label>
      <input
        class="input"
        id="cf-name"
        name="name"
        autocomplete="off"
        required
        placeholder={isCo ? 'e.g. Northwind Studio' : 'e.g. Maya Chen'}
        bind:value={v.name}
        oninput={() => { touched.name = true; if (errors.name) check('name') }}
        onblur={() => { if (v.name.trim() || touched.name) check('name') }}
        aria-invalid={!!errors.name || undefined}
        aria-describedby={errors.name ? 'cf-name-error' : undefined} />
      <FieldError id="cf-name-error" message={errors.name} />
    </div>
    {#if isCo}
      <div class="alert alert-info company-hint">
        <Icon name="info" size={20} />
        <div class="alert-body">
          <span
            >{isEdit
              ? 'Contact persons are managed in the Contact persons section of the client card.'
              : 'Contact persons (name, phone, email) are added in the client card after you create the company.'}</span>
        </div>
      </div>
    {/if}
    <div class="form-grid">
      <div class="field" data-field="phone">
        <label for="cf-phone">Phone <span class="field-optional">(optional)</span></label>
        <input class="input" type="tel" id="cf-phone" name="phone" autocomplete="tel" placeholder="+1 (555) 555-0100" bind:value={v.phone} />
      </div>
      <div class="field" data-field="email" class:is-invalid={!!errors.email}>
        <label for="cf-email">Email <span class="field-optional">(optional)</span></label>
        <input
          class="input"
          type="email"
          id="cf-email"
          name="email"
          autocomplete="email"
          placeholder="name@company.com"
          bind:value={v.email}
          oninput={() => errors.email && check('email')}
          onblur={() => v.email.trim() && check('email')}
          aria-invalid={!!errors.email || undefined}
          aria-describedby={errors.email ? 'cf-email-error' : undefined} />
        <FieldError id="cf-email-error" message={errors.email} />
      </div>
      <div class="field span-2" data-field="address">
        <label for="cf-address">Address <span class="field-optional">(optional)</span></label>
        <input class="input" id="cf-address" name="address" autocomplete="street-address" placeholder="Street, city, state, ZIP" bind:value={v.address} />
      </div>
      <div class="field span-2" data-field="website" class:is-invalid={!!errors.website}>
        <label for="cf-website">Website <span class="field-optional">(optional)</span></label>
        <input
          class="input"
          type="url"
          inputmode="url"
          id="cf-website"
          name="website"
          placeholder="e.g. northwindstudio.co"
          bind:value={v.website}
          oninput={() => errors.website && check('website')}
          onblur={() => v.website.trim() && check('website')}
          aria-invalid={!!errors.website || undefined}
          aria-describedby={errors.website ? 'cf-website-error' : undefined} />
        <FieldError id="cf-website-error" message={errors.website} />
      </div>
      <div class="field span-2" data-field="tags">
        <label for="cf-tag-input">Tags <span class="field-optional">(optional)</span></label>
        <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
        <div class="tag-input" onclick={() => tagInput?.focus()}>
          <span class="tag-input-chips">
            {#each v.tags as t, i (t)}<TagChip name={t} onremove={() => { v.tags.splice(i, 1); tagInput?.focus() }} />{/each}
          </span>
          <input
            class="tag-input-field"
            id="cf-tag-input"
            autocomplete="off"
            placeholder="Add a tag…"
            aria-describedby="cf-tag-hint"
            bind:this={tagInput}
            bind:value={tagQuery}
            onkeydown={onTagKey} />
        </div>
        <div class="tag-suggest">
          {#if suggestions.length || (tagQuery.trim() && !exact)}
            <span class="tag-suggest-label">{tagQuery.trim() ? 'Matches:' : 'Existing tags:'}</span>
            {#each suggestions as n}
              {@const color = tagStore.list.find((t) => t.name === n)?.color}
              <button type="button" class="tag-suggest-item" onclick={() => addTag(n)}
                >{#if color}<span class="tag-dot" style="--tag-color:{color}"></span>{/if}{n}</button>
            {/each}
            {#if tagQuery.trim() && !exact}
              <button type="button" class="tag-suggest-item is-create" onclick={() => addTag(tagQuery)}><Icon name="plus" size={14} />Create tag “{tagQuery.trim()}”</button>
            {/if}
          {/if}
        </div>
        <p class="field-hint" id="cf-tag-hint">Pick an existing tag or type a new one and press Enter.</p>
      </div>
      <div class="field span-2" data-field="notes">
        <label for="cf-notes">Notes <span class="field-optional">(optional)</span></label>
        <textarea class="textarea" id="cf-notes" name="notes" placeholder="Anything worth remembering about this client" bind:value={v.notes}></textarea>
      </div>
    </div>
  </form>
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="submit" form="client-form" class="btn btn-primary" class:is-loading={saving} aria-busy={saving || undefined} disabled={saving}>
      {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create client'}
    </button>
  {/snippet}
</Modal>
