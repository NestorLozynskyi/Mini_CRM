<script lang="ts">
  import { tagColor } from '../stores.svelte'
  import Icon from './Icon.svelte'

  let { name, onremove }: { name: string; onremove?: () => void } = $props()
  const color = $derived(tagColor(name))
</script>

<span class="tag"
  >{#if color}<span class="tag-dot" style="--tag-color:{color}"></span>{/if}{name}{#if onremove}<button
      type="button"
      class="tag-remove"
      aria-label="Remove tag {name}"
      onclick={onremove}><Icon name="x" size={12} /></button
    >{/if}</span
>
