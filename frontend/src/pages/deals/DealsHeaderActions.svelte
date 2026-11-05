<script lang="ts">
  import Icon from '../../lib/ui/Icon.svelte'
  import { openMenu, type MenuItem } from '../../lib/ui/menu.svelte'

  /** Кнопки заголовка Deals: перемикач Board/List, меню «More actions», New deal (усі видимі й на мобільному). */
  let { view, overflow, onnew }: { view: 'board' | 'list'; overflow: () => MenuItem[]; onnew: () => void } = $props()
</script>

<div class="segmented" data-keep role="group" aria-label="Deals view">
  <a class="segmented-item" href="/deals" aria-current={view === 'board' ? 'page' : undefined}><Icon name="kanban" size={18} /><span>Board</span></a>
  <a class="segmented-item" href="/deals/list" aria-current={view === 'list' ? 'page' : undefined}><Icon name="list" size={18} /><span>List</span></a>
</div>
<button
  type="button"
  class="btn btn-secondary btn-icon"
  data-keep
  aria-label="More actions"
  aria-haspopup="menu"
  aria-expanded="false"
  onclick={(e) => openMenu(e.currentTarget, overflow())}><Icon name="more" size={20} /></button>
<button type="button" class="btn btn-primary btn-collapse" onclick={onnew}><Icon name="plus" size={18} /><span class="btn-label">New deal</span></button>
