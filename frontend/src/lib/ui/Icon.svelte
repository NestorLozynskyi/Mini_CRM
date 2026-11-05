<script lang="ts">
  import { ICONS } from '../icons'

  let { name, size = 20, class: cls = '' }: { name: string; size?: number; class?: string } = $props()
</script>

<svg
  class="icon icon-{name} {cls}"
  width={size}
  height={size}
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="1.5"
  stroke-linecap="round"
  stroke-linejoin="round"
  aria-hidden="true"
  focusable="false">{@html ICONS[name] ?? ICONS['info']}</svg
>
