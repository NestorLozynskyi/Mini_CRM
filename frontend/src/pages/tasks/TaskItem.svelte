<script lang="ts">
  import type { Task } from '../../lib/api/types'
  import { date, dateShort, diffDays, relDate } from '../../lib/format'
  import { taskStatus } from '../../lib/tasks'
  import EntityChip from '../../lib/ui/EntityChip.svelte'
  import Icon from '../../lib/ui/Icon.svelte'
  import { completedDay, shortWeekday } from './groups'

  interface Props {
    task: Task
    today: string
    isNew?: boolean
    ontoggle: (t: Task, done: boolean) => void
    onmenu: (e: MouseEvent, t: Task) => void
  }
  let { task: t, today, isNew = false, ontoggle, onmenu }: Props = $props()

  const st = $derived(taskStatus(t, today))
  const doneDay = $derived(completedDay(t))
</script>

{#snippet abs()}<span class="due-sep" aria-hidden="true">·</span><time class="due-abs" datetime={t.due}
    ><span class="due-abs-long">{shortWeekday(t.due)}{', '}</span>{dateShort(t.due)}</time>{/snippet}

<li class="task-item is-{st}" class:is-done={t.done} class:is-new={isNew} data-id={t.id}>
  <label class="checkbox task-check"
    ><input
      type="checkbox"
      checked={t.done}
      aria-label="{t.done ? 'Mark as not done: ' : 'Mark as complete: '}{t.title}"
      onchange={(e) => ontoggle(t, (e.currentTarget as HTMLInputElement).checked)} /></label>
  <div class="task-body">
    <span class="task-title">{t.title}</span>
    <div class="task-meta">
      {#if st === 'done'}
        {#if doneDay}
          <span class="due-chip done-chip" title="Completed {date(doneDay)}"
            ><Icon name="check-circle" size={14} />Completed {doneDay === today ? 'today' : dateShort(doneDay)}</span>
        {/if}
        <span class="due-chip" title="Was due {date(t.due)}"><Icon name="calendar" size={14} />Due {dateShort(t.due)}</span>
      {:else if st === 'overdue'}
        <span class="due-chip" title="Was due {date(t.due)}"
          ><Icon name="alert-circle" size={14} /><strong>Overdue</strong><span class="due-sep" aria-hidden="true">·</span>{relDate(t.due, today)}{@render abs()}</span>
      {:else if st === 'today'}
        <span class="due-chip" title="Due {date(t.due)}"><Icon name="clock" size={14} />Due today{@render abs()}</span>
      {:else}
        {@const d = diffDays(t.due, today)}
        <span class="due-chip" title="Due {date(t.due)}"><Icon name="calendar" size={14} />{d === 1 ? 'Tomorrow' : `In ${d} days`}{@render abs()}</span>
      {/if}
      {#if t.dealId}<EntityChip kind="deal" id={t.dealId} label={t.dealTitle ?? ''} />
      {:else}<EntityChip kind="client" id={t.clientId} label={t.clientName} clientType={t.clientType} />{/if}
    </div>
  </div>
  <button type="button" class="btn btn-ghost btn-icon btn-sm task-more" aria-label="Actions for {t.title}" aria-haspopup="menu" aria-expanded="false" onclick={(e) => onmenu(e, t)}>
    <Icon name="more" size={18} />
  </button>
</li>
