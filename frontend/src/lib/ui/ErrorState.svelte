<script lang="ts">
  import Icon from './Icon.svelte'

  let { title = 'Couldn’t load data', text = 'Something went wrong while loading. Check your connection and try again.', retryLabel = 'Try again', onretry }:
    { title?: string; text?: string; retryLabel?: string; onretry?: () => void } = $props()
  let busy = $state(false)
</script>

<div class="empty-state is-error" role="alert">
  <div class="empty-state-icon"><Icon name="alert-triangle" size={24} /></div>
  <h3 class="empty-state-title">{title}</h3>
  <p class="empty-state-text">{text}</p>
  {#if onretry}
    <div class="empty-state-actions">
      <button type="button" class="btn btn-secondary" class:is-loading={busy} aria-busy={busy} onclick={() => { busy = true; onretry?.() }}>
        <Icon name="refresh" size={18} /><span>{retryLabel}</span>
      </button>
    </div>
  {/if}
</div>
