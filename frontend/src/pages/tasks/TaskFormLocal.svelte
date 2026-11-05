<script lang="ts">
  import { onMount, untrack } from 'svelte'
  import { ApiError } from '../../lib/api/client'
  import { clients as clientsApi, deals as dealsApi, tasks as api } from '../../lib/api/crm'
  import type { ClientSummary, Deal, Task } from '../../lib/api/types'
  import { refreshBadges } from '../../lib/session.svelte'
  import FieldError from '../../lib/ui/FieldError.svelte'
  import Icon from '../../lib/ui/Icon.svelte'
  import Modal from '../../lib/ui/Modal.svelte'
  import { toast } from '../../lib/ui/toast.svelte'

  /** Форма завдання екрана tasks.html: Title, Due date з підказкою, Related to (Client / Deal + пошуковий список). */
  interface Props {
    task?: Task | null
    onsaved?: (t: Task) => void
    onclose: () => void
  }
  let { task = null, onsaved, onclose }: Props = $props()
  const init = untrack(() => task)

  type Kind = 'client' | 'deal'
  interface Opt { value: string; label: string; sub: string; icon: string }

  const isEdit = !!init
  let title = $state(init?.title ?? '')
  let due = $state(init?.due ?? '')
  let kind = $state<Kind>(init?.dealId ? 'deal' : 'client')
  let relValue = $state(init ? (init.dealId ? `deal:${init.dealId}` : `client:${init.clientId}`) : '')
  let relText = $state(init ? (init.dealId ? init.dealTitle ?? '' : init.clientName) : '')
  let clients = $state<ClientSummary[]>([])
  let deals = $state<Deal[]>([])
  let open = $state(false)
  let active = $state(-1)
  let errors = $state<Record<string, string>>({})
  let touched: Record<string, boolean> = {}
  let saving = $state(false)
  let relInput = $state<HTMLInputElement>()
  let modal = $state<ReturnType<typeof Modal>>()

  onMount(async () => {
    try {
      ;[clients, deals] = await Promise.all([clientsApi.list(), dealsApi.list({ scope: 'active' })])
    } catch {
      // список лишається порожнім; збереження покаже помилку валідації
    }
  })

  const opts = $derived<Opt[]>(
    kind === 'deal'
      ? deals.map((d) => ({ value: `deal:${d.id}`, label: d.title, sub: d.clientName, icon: 'briefcase' }))
      : clients.map((c) => ({ value: `client:${c.id}`, label: c.name, sub: c.type === 'company' ? 'Company' : 'Person', icon: c.type === 'company' ? 'building' : 'user' })),
  )
  const selected = $derived(opts.find((o) => o.value === relValue) ?? null)
  const shown = $derived.by(() => {
    let q = relText.trim().toLowerCase()
    if (selected && relText === selected.label) q = ''
    return opts.filter((o) => !q || `${o.label} ${o.sub}`.toLowerCase().includes(q))
  })

  const checks: Record<string, () => string> = {
    title: () => (title.trim() ? '' : 'Enter a task title.'),
    due: () => (due ? '' : 'Choose a due date.'),
    rel: () => (relValue ? '' : kind === 'deal' ? 'Choose a deal for this task.' : 'Choose a client for this task.'),
  }
  function validate(name: string) {
    const msg = checks[name]()
    const next = { ...errors }
    if (msg) next[name] = msg
    else delete next[name]
    errors = next
    return !msg
  }

  function choose(o: Opt) {
    relValue = o.value
    relText = o.label
    open = false
    active = -1
    if (touched.rel) validate('rel')
  }
  function setKind(k: Kind) {
    if (k === kind) return
    kind = k
    relValue = ''
    relText = ''
    if (touched.rel) validate('rel')
    relInput?.focus()
  }
  function onRelKey(e: KeyboardEvent) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      open = true
      active = Math.min(active + 1, shown.length - 1)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      active = Math.max(active - 1, 0)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (open && shown[active]) choose(shown[active])
    } else if (e.key === 'Escape' && open) {
      e.preventDefault()
      e.stopPropagation()
      open = false
      active = -1
    }
  }

  async function save() {
    touched = { title: true, due: true, rel: true }
    const a = validate('title')
    const b = validate('due')
    const c = validate('rel')
    if (!a) return document.getElementById('nt-title')?.focus()
    if (!b) return document.getElementById('nt-due')?.focus()
    if (!c) return relInput?.focus()
    saving = true
    const [k, id] = relValue.split(':')
    const body = { title: title.trim(), due, clientId: k === 'client' ? Number(id) : null, dealId: k === 'deal' ? Number(id) : null }
    try {
      const saved = isEdit ? await api.update(init!.id, body) : await api.create(body)
      void refreshBadges()
      onsaved?.(saved)
      toast({ type: 'success', text: isEdit ? 'Task updated' : 'Task created' })
      modal?.close('save')
    } catch (e) {
      saving = false
      if (e instanceof ApiError && e.isValidation) {
        const fe = e.fieldErrors()
        errors = { ...(fe.title ? { title: fe.title } : {}), ...(fe.due ? { due: fe.due } : {}), ...(fe.link ? { rel: fe.link } : {}) }
      } else toast({ type: 'error', text: 'Couldn’t save changes. Check your connection and try again.' })
    }
  }
</script>

<Modal bind:this={modal} title={isEdit ? 'Edit task' : 'New task'} subtitle={isEdit ? undefined : 'Reminders are shown in-app only — in these lists and the sidebar counter.'} initialFocus="#nt-title" {onclose}>
  <form class="form-stack" data-form="task-local" novalidate onsubmit={(e) => { e.preventDefault(); void save() }}>
    <div class="field" data-field="title" class:is-invalid={!!errors.title}>
      <label for="nt-title">Title<span class="field-required" aria-hidden="true">*</span></label>
      <input
        class="input"
        id="nt-title"
        name="title"
        required
        autocomplete="off"
        placeholder="e.g. Send revised proposal"
        bind:value={title}
        onblur={() => { touched.title = true; validate('title') }}
        oninput={() => touched.title && validate('title')}
        aria-invalid={!!errors.title || undefined}
        aria-describedby={errors.title ? 'nt-title-error' : undefined} />
      <FieldError id="nt-title-error" message={errors.title} />
    </div>
    <div class="field" data-field="due" class:is-invalid={!!errors.due}>
      <label for="nt-due">Due date<span class="field-required" aria-hidden="true">*</span></label>
      <input
        class="input"
        type="date"
        id="nt-due"
        name="due"
        required
        bind:value={due}
        onblur={() => { touched.due = true; validate('due') }}
        onchange={() => touched.due && validate('due')}
        aria-invalid={!!errors.due || undefined}
        aria-describedby={errors.due ? 'nt-due-error' : 'nt-due-hint'} />
      <p class="field-hint" id="nt-due-hint">The task appears in Today, Upcoming or Overdue based on this date.</p>
      <FieldError id="nt-due-error" message={errors.due} />
    </div>
    <fieldset class="field" data-field="rel" style="border:0;padding:0;margin:0" class:is-invalid={!!errors.rel}>
      <legend class="field-label" style="margin-bottom:6px">Related to<span class="field-required" aria-hidden="true">*</span></legend>
      <div class="segmented rel-switch" role="group" aria-label="Link type">
        <button type="button" class="segmented-item" aria-pressed={kind === 'client'} onclick={() => setKind('client')}><Icon name="users" size={18} />Client</button>
        <button type="button" class="segmented-item" aria-pressed={kind === 'deal'} onclick={() => setKind('deal')}><Icon name="briefcase" size={18} />Deal</button>
      </div>
      <div class="combo">
        <label class="sr-only" for="nt-rel">Search clients or deals</label>
        <span class="search" style="display:block"
          ><Icon name="search" size={18} /><input
            class="input"
            id="nt-rel"
            role="combobox"
            aria-autocomplete="list"
            aria-expanded={open}
            aria-controls="nt-rel-list"
            aria-activedescendant={open && active > -1 && shown.length ? `nt-opt-${active}` : undefined}
            autocomplete="off"
            placeholder={kind === 'deal' ? 'Search deals…' : 'Search clients…'}
            bind:this={relInput}
            bind:value={relText}
            onfocus={() => (open = true)}
            onclick={() => (open = true)}
            oninput={() => { relValue = ''; active = 0; open = true }}
            onkeydown={onRelKey}
            onblur={() => { open = false; active = -1; touched.rel = true; validate('rel') }}
            aria-invalid={!!errors.rel || undefined}
            aria-describedby={errors.rel ? 'nt-rel-error' : undefined} /></span>
        <Icon name="chevron-down" size={18} class="combo-caret" />
        <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
        <ul class="combo-list" id="nt-rel-list" role="listbox" hidden={!open} onmousedown={(e) => e.preventDefault()}>
          {#if shown.length}
            {#each shown as o, i (o.value)}
              <!-- svelte-ignore a11y_click_events_have_key_events -->
              <li class="combo-option" class:is-active={i === active} role="option" id="nt-opt-{i}" aria-selected={o.value === relValue} onclick={() => choose(o)}>
                <Icon name={o.icon} size={16} />
                <span class="combo-option-main"><span class="truncate">{o.label}</span>{#if o.sub}<span class="combo-option-sub truncate">{o.sub}</span>{/if}</span>
                {#if o.value === relValue}<Icon name="check" size={16} class="icon-check" />{/if}
              </li>
            {/each}
          {:else}
            <li class="combo-empty" role="presentation">No {kind === 'deal' ? 'deals' : 'clients'} match “{relText}”</li>
          {/if}
        </ul>
      </div>
      <FieldError id="nt-rel-error" message={errors.rel} />
    </fieldset>
  </form>
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="button" class="btn btn-primary" class:is-loading={saving} disabled={saving} onclick={save}>{saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create task'}</button>
  {/snippet}
</Modal>
