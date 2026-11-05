<script lang="ts">
  import Icon from './Icon.svelte'
  let { id, message }: { id: string; message?: string | null } = $props()
</script>

{#if message}<p class="field-error" {id}><Icon name="alert-circle" size={16} /><span>{message}</span></p>{/if}
