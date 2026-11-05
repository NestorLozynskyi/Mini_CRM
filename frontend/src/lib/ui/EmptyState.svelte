<script lang="ts">
  import type { Snippet } from 'svelte'
  import Icon from './Icon.svelte'

  let { icon = 'inbox', title = 'Nothing here yet', text, compact = false, actions }:
    { icon?: string; title?: string; text?: string; compact?: boolean; actions?: Snippet } = $props()
</script>

<div class="empty-state" class:is-compact={compact}>
  <div class="empty-state-icon"><Icon name={icon} size={24} /></div>
  <h3 class="empty-state-title">{title}</h3>
  {#if text}<p class="empty-state-text">{text}</p>{/if}
  {#if actions}<div class="empty-state-actions">{@render actions()}</div>{/if}
</div>
