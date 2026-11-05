<script lang="ts">
  import type { LinkOptions } from '../links'

  let { id, value = $bindable(''), options, invalid = false, describedby }:
    { id: string; value?: string; options: LinkOptions | null; invalid?: boolean; describedby?: string } = $props()
</script>

<select class="select" {id} name="link" bind:value aria-invalid={invalid || undefined} aria-describedby={describedby}>
  <option value="">{options ? 'Choose a client or deal' : 'Loading…'}</option>
  {#if options}
    <optgroup label="Clients">
      {#each options.clients as c}<option value="client:{c.id}">{c.name}</option>{/each}
    </optgroup>
    <optgroup label="Deals">
      {#each options.deals as d}<option value="deal:{d.id}">{d.title} — {d.clientName}</option>{/each}
    </optgroup>
  {/if}
</select>
