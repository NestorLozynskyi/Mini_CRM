<script lang="ts" module>
  let openCount = 0
  let seq = 0
</script>

<script lang="ts">
  import { onMount, type Snippet } from 'svelte'
  import Icon from './Icon.svelte'

  interface Props {
    title: string
    subtitle?: string
    size?: 'sm' | 'md'
    icon?: string
    role?: 'dialog' | 'alertdialog'
    className?: string
    closeButton?: boolean
    dismissible?: boolean
    /** CSS-селектор елемента, що отримує фокус при відкритті. */
    initialFocus?: string
    onclose: (reason: string) => void
    children: Snippet
    footer?: Snippet
  }

  let {
    title, subtitle, size = 'md', icon, role = 'dialog', className = '', closeButton = true, dismissible = true,
    initialFocus, onclose, children, footer,
  }: Props = $props()

  const id = `modal-${++seq}`
  const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  let el: HTMLDivElement
  let leaving = $state(false)
  let downOnBackdrop = false
  const prev = document.activeElement as HTMLElement | null

  /** Закриття з короткою анімацією (як у макетах, 120 мс). */
  export function close(reason = 'button') {
    if (leaving) return
    leaving = true
    setTimeout(() => onclose(reason), 120)
  }

  onMount(() => {
    openCount++
    document.documentElement.style.overflow = 'hidden'
    const target =
      (initialFocus && el.querySelector<HTMLElement>(initialFocus)) ||
      el.querySelector<HTMLElement>('[autofocus]') ||
      el.querySelector<HTMLElement>('.modal-body ' + FOCUSABLE.split(', ').join(', .modal-body ')) ||
      el.querySelector<HTMLElement>('.modal-footer .btn-primary, .modal-footer .btn-danger') ||
      el
    setTimeout(() => target.focus({ preventScroll: true }), 10)
    return () => {
      openCount--
      if (openCount <= 0) document.documentElement.style.overflow = ''
      if (prev && document.contains(prev)) prev.focus()
    }
  })

  function onkeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      e.stopPropagation()
      e.preventDefault()
      if (dismissible) close('escape')
      return
    }
    if (e.key === 'Tab') {
      const f = Array.from(el.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((n) => n.offsetParent !== null || n === document.activeElement)
      if (!f.length) {
        e.preventDefault()
        el.focus()
        return
      }
      const first = f[0]
      const last = f[f.length - 1]
      if (e.shiftKey && (document.activeElement === first || document.activeElement === el)) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }
  }

  function onclick(e: MouseEvent) {
    const target = e.target as HTMLElement
    if (target.classList.contains('modal-backdrop') && downOnBackdrop && dismissible) close('backdrop')
    const c = target.closest?.('[data-close]')
    if (c && el.contains(c)) {
      e.preventDefault()
      close('button')
    }
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions, a11y_click_events_have_key_events -->
<div
  class="modal-backdrop"
  class:is-leaving={leaving}
  {onkeydown}
  {onclick}
  onmousedown={(e) => (downOnBackdrop = (e.target as HTMLElement).classList.contains('modal-backdrop'))}>
  <div
    bind:this={el}
    class="modal {size === 'sm' ? 'modal-sm' : ''} {className}"
    {role}
    aria-modal="true"
    aria-labelledby="{id}-title"
    aria-describedby="{id}-body"
    tabindex="-1">
    <div class="modal-handle" aria-hidden="true"></div>
    <div class="modal-header">
      {#if icon}<span class="confirm-icon"><Icon name={icon} size={22} /></span>{/if}
      <div>
        <h2 class="modal-title" id="{id}-title">{title}</h2>
        {#if subtitle}<p class="modal-subtitle">{subtitle}</p>{/if}
      </div>
      {#if closeButton}
        <button type="button" class="btn btn-ghost btn-icon modal-close" data-close aria-label="Close"><Icon name="x" size={20} /></button>
      {/if}
    </div>
    <div class="modal-body" id="{id}-body">{@render children()}</div>
    {#if footer}
      <!-- Натискання кнопок футера не забирає фокус із поля: blur-валідація не зсуває розмітку до кліку. -->
      <!-- svelte-ignore a11y_no_static_element_interactions -->
      <div class="modal-footer" onmousedown={(e) => { if ((e.target as Element).closest('button')) e.preventDefault() }}>{@render footer()}</div>
    {/if}
  </div>
</div>
