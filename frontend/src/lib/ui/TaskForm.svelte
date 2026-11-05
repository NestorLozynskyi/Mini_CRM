<script lang="ts">
  import { onMount, untrack } from 'svelte'
  import { ApiError } from '../api/client'
  import { tasks as api } from '../api/crm'
  import type { Task } from '../api/types'
  import { addDaysIso, todayIso } from '../format'
  import { linkValue, loadLinkOptions, parseLink, type LinkOptions } from '../links'
  import { refreshBadges } from '../session.svelte'
  import FieldError from './FieldError.svelte'
  import LinkSelect from './LinkSelect.svelte'
  import Modal from './Modal.svelte'
  import { toast } from './toast.svelte'

  interface Props {
    task?: Task | null
    ctx?: { clientId?: number | null; dealId?: number | null }
    defaultDue?: string
    onsaved?: (t: Task) => void
    onclose: () => void
  }
  let { task = null, ctx = {}, defaultDue, onsaved, onclose }: Props = $props()
  // Форма заповнюється один раз початковими значеннями пропсів.
  const init = untrack(() => ({ task, ctx, defaultDue }))

  const isEdit = !!init.task
  let title = $state(init.task?.title ?? '')
  let due = $state(init.task?.due ?? init.defaultDue ?? addDaysIso(todayIso(), 1))
  let link = $state(init.task ? linkValue(init.task) : linkValue(init.ctx))
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
    if (!title.trim()) e.title = 'Enter a task title.'
    if (!due) e.due = 'Choose a due date.'
    if (!link) e.link = 'Link the task to a client or deal.'
    errors = e
    return Object.keys(e)
  }

  async function save() {
    const bad = validate()
    if (bad.length) {
      document.getElementById({ title: 'tf-title', due: 'tf-due', link: 'tf-link' }[bad[0]] ?? 'tf-title')?.focus()
      return
    }
    saving = true
    try {
      const body = { title: title.trim(), due, ...parseLink(link) }
      const saved = isEdit ? await api.update(init.task!.id, body) : await api.create(body)
      onsaved?.(saved)
      void refreshBadges()
      toast({ type: 'success', text: isEdit ? 'Task updated' : 'Task created' })
      modal?.close('save')
    } catch (e) {
      saving = false
      if (e instanceof ApiError && e.isValidation) errors = e.fieldErrors()
      else toast({ type: 'error', text: 'Couldn’t save changes. Check your connection and try again.' })
    }
  }
</script>

<Modal bind:this={modal} title={isEdit ? 'Edit task' : 'New task'} {onclose}>
  <form class="form-stack" data-form="task" novalidate onsubmit={(e) => { e.preventDefault(); void save() }}>
    <div class="field" data-field="title" class:is-invalid={!!errors.title}>
      <label for="tf-title">Title<span class="field-required" aria-hidden="true">*</span></label>
      <input
        class="input"
        id="tf-title"
        name="title"
        required
        placeholder="e.g. Send revised proposal"
        bind:value={title}
        oninput={() => errors.title && validate()}
        aria-invalid={!!errors.title || undefined}
        aria-describedby={errors.title ? 'tf-title-error' : undefined} />
      <FieldError id="tf-title-error" message={errors.title} />
    </div>
    <div class="form-grid">
      <div class="field" class:is-invalid={!!errors.due}>
        <label for="tf-due">Due date<span class="field-required" aria-hidden="true">*</span></label>
        <input class="input" type="date" id="tf-due" name="due" required bind:value={due} aria-invalid={!!errors.due || undefined} aria-describedby={errors.due ? 'tf-due-error' : undefined} />
        <FieldError id="tf-due-error" message={errors.due} />
      </div>
      <div class="field" data-field="link" class:is-invalid={!!errors.link}>
        <label for="tf-link">Linked to<span class="field-required" aria-hidden="true">*</span></label>
        <LinkSelect id="tf-link" bind:value={link} {options} invalid={!!errors.link} describedby={errors.link ? 'tf-link-error' : undefined} />
        <FieldError id="tf-link-error" message={errors.link} />
      </div>
    </div>
  </form>
  {#snippet footer()}
    <button type="button" class="btn btn-secondary" data-close>Cancel</button>
    <button type="button" class="btn btn-primary" class:is-loading={saving} disabled={saving} onclick={save}>
      {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create task'}
    </button>
  {/snippet}
</Modal>
