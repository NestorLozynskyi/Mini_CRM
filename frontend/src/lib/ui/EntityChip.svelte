<script lang="ts">
  import Icon from './Icon.svelte'

  let { kind, id, label, clientType = 'company' }: { kind: 'client' | 'deal'; id: number; label: string; clientType?: string } = $props()
</script>

{#if kind === 'deal'}
  <a class="entity-chip" href="/deals/{id}" title="Deal: {label}"><Icon name="briefcase" size={14} /><span>{label}</span></a>
{:else}
  <a class="entity-chip" href="/clients/{id}" title="Client: {label}"><Icon name={clientType === 'company' ? 'building' : 'user'} size={14} /><span>{label}</span></a>
{/if}
