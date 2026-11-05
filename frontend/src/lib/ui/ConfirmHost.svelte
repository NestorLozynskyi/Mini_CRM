<script lang="ts">
  import Modal from './Modal.svelte'
  import { confirmState } from './confirm.svelte'

  let result = false
  let modal = $state<ReturnType<typeof Modal>>()

  function finish() {
    const c = confirmState.current
    confirmState.current = null
    c?.resolve(result)
    result = false
  }
</script>

{#if confirmState.current}
  {@const c = confirmState.current}
  {@const danger = c.danger !== false}
  <Modal
    bind:this={modal}
    title={c.title}
    size="sm"
    role="alertdialog"
    className="confirm{danger ? '' : ' is-neutral'}"
    icon={c.icon ?? (danger ? 'alert-triangle' : 'info')}
    closeButton={false}
    initialFocus="[data-close]"
    onclose={finish}>
    <p class="confirm-text">{c.text}</p>
    {#snippet footer()}
      <button type="button" class="btn btn-secondary" data-close>{c.cancelLabel ?? 'Cancel'}</button>
      <button
        type="button"
        class="btn {danger ? 'btn-danger' : 'btn-primary'}"
        onclick={() => { result = true; modal?.close('confirm') }}>{c.confirmLabel ?? (danger ? 'Delete' : 'Confirm')}</button>
    {/snippet}
  </Modal>
{/if}
