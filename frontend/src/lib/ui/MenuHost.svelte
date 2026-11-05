<script lang="ts">
  import { tick } from 'svelte'
  import Icon from './Icon.svelte'
  import { closeMenu, menuState } from './menu.svelte'

  let el = $state<HTMLDivElement>()
  let pos = $state({ left: 0, top: 0 })

  $effect(() => {
    const anchor = menuState.anchor
    if (!anchor) return
    void tick().then(() => {
      if (!el) return
      const r = anchor.getBoundingClientRect()
      const mw = el.offsetWidth
      const mh = el.offsetHeight
      let left = menuState.align === 'start' ? r.left : r.right - mw
      if (left < 8) left = Math.min(r.left, window.innerWidth - mw - 8)
      if (left + mw > window.innerWidth - 8) left = window.innerWidth - mw - 8
      let top = r.bottom + 4
      if (top + mh > window.innerHeight - 8 && r.top - mh - 4 > 8) top = r.top - mh - 4
      pos = { left: Math.max(8, left), top: Math.max(8, top) }
      el.querySelector<HTMLElement>('.menu-item')?.focus()
    })
    const onDown = (e: PointerEvent) => {
      if (!el?.contains(e.target as Node) && !anchor.contains(e.target as Node)) closeMenu()
    }
    const onScroll = (e: Event) => {
      if (!el?.contains(e.target as Node)) closeMenu()
    }
    const onResize = () => closeMenu()
    setTimeout(() => document.addEventListener('pointerdown', onDown, true), 0)
    window.addEventListener('scroll', onScroll, true)
    window.addEventListener('resize', onResize)
    return () => {
      document.removeEventListener('pointerdown', onDown, true)
      window.removeEventListener('scroll', onScroll, true)
      window.removeEventListener('resize', onResize)
    }
  })

  function onkeydown(e: KeyboardEvent) {
    const items = Array.from(el?.querySelectorAll<HTMLElement>('.menu-item') ?? [])
    const i = items.indexOf(document.activeElement as HTMLElement)
    if (e.key === 'Escape') { e.preventDefault(); closeMenu(true) }
    else if (e.key === 'ArrowDown') { e.preventDefault(); items[(i + 1) % items.length]?.focus() }
    else if (e.key === 'ArrowUp') { e.preventDefault(); items[(i - 1 + items.length) % items.length]?.focus() }
    else if (e.key === 'Home') { e.preventDefault(); items[0]?.focus() }
    else if (e.key === 'End') { e.preventDefault(); items[items.length - 1]?.focus() }
    else if (e.key === 'Tab') closeMenu()
  }
</script>

{#if menuState.anchor}
  <!-- svelte-ignore a11y_interactive_supports_focus -->
  <div class="menu" role="menu" bind:this={el} style="left:{pos.left}px;top:{pos.top}px" {onkeydown}>
    {#each menuState.items as it}
      {#if it.divider}
        <div class="menu-divider" role="separator"></div>
      {:else if it.header}
        <div class="menu-label">{it.label}</div>
      {:else if it.href}
        <a class="menu-item" class:is-danger={it.danger} role="menuitem" tabindex="-1" href={it.href} onclick={() => closeMenu()}>
          {#if it.icon}<Icon name={it.icon} size={18} />{/if}<span>{it.label}</span>
        </a>
      {:else}
        <button type="button" class="menu-item" class:is-danger={it.danger} role="menuitem" tabindex="-1" onclick={() => { closeMenu(); it.onClick?.() }}>
          {#if it.icon}<Icon name={it.icon} size={18} />{/if}<span>{it.label}</span>
        </button>
      {/if}
    {/each}
  </div>
{/if}
