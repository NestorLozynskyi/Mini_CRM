<script lang="ts">
  import Icon from './Icon.svelte'
  import { dismissToast, toasts, type ToastItem } from './toast.svelte'

  const timers = new Map<number, ReturnType<typeof setTimeout>>()

  function arm(t: ToastItem) {
    clearTimeout(timers.get(t.id))
    if (t.duration === 0) return
    timers.set(t.id, setTimeout(() => dismissToast(t.id), t.duration ?? (t.action ? 6000 : 4000)))
  }
  function pause(t: ToastItem) {
    clearTimeout(timers.get(t.id))
  }

  $effect(() => {
    for (const t of toasts) if (!timers.has(t.id)) arm(t)
  })
</script>

<div class="toast-region" aria-live="polite">
  {#each toasts as t (t.id)}
    <div
      class="toast toast-{t.type}"
      class:is-leaving={t.leaving}
      role={t.type === 'error' ? 'alert' : undefined}
      onmouseenter={() => pause(t)}
      onmouseleave={() => arm(t)}
      onfocusin={() => pause(t)}>
      <Icon name={t.type === 'error' ? 'alert-circle' : 'check-circle'} size={20} />
      <span class="toast-text">{t.text}</span>
      {#if t.action}
        <button type="button" class="toast-action" onclick={() => { t.action?.onClick(); dismissToast(t.id) }}>{t.action.label}</button>
      {/if}
      <button type="button" class="btn btn-ghost btn-icon btn-sm toast-close" aria-label="Dismiss" onclick={() => dismissToast(t.id)}>
        <Icon name="x" size={16} />
      </button>
    </div>
  {/each}
</div>
