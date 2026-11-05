<script lang="ts">
  import { BRAND_MARK } from '../icons'
  import { STAGES } from '../stages'
  import Icon from './Icon.svelte'
  import StageBadge from './StageBadge.svelte'
</script>

<aside class="auth-aside" aria-label="About Mini CRM">
  <span class="aside-brand">{@html BRAND_MARK}<span>Mini CRM</span></span>
  <div>
    <h2 class="aside-title">A simple CRM for freelancers and small businesses</h2>
    <p class="aside-lead">Keep clients, deals and follow-ups in one calm place — without the setup of a big sales tool.</p>
  </div>
  <ul class="aside-list">
    <li><span class="aside-ico"><Icon name="users" size={20} /></span><span><strong>Clients and contacts</strong>People and companies with phone, email, notes, tags and contact persons.</span></li>
    <li><span class="aside-ico"><Icon name="kanban" size={20} /></span><span><strong>A clear sales pipeline</strong>Move deals across fixed stages on a board and see totals per stage.</span></li>
    <li><span class="aside-ico"><Icon name="check-square" size={20} /></span><span><strong>Tasks that don’t slip</strong>Today, Upcoming and Overdue lists keep follow-ups on track.</span></li>
    <li><span class="aside-ico"><Icon name="note" size={20} /></span><span><strong>Interaction history</strong>Log calls, meetings, emails and notes for every client and deal.</span></li>
  </ul>
  <div class="aside-stages" aria-label="Pipeline stages">
    {#each STAGES as s, i}<StageBadge stage={s.id} />{#if i < 3}<Icon name="chevron-right" size={14} />{/if}{/each}
  </div>
</aside>
