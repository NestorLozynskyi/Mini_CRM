<script lang="ts">
  import type { Task } from '../api/types'
  import { date, dateShort, diffDays, relDate } from '../format'
  import { taskStatus } from '../tasks'
  import Icon from './Icon.svelte'

  let { task }: { task: Task } = $props()
  const st = $derived(taskStatus(task))
</script>

{#if st === 'done'}
  <span class="due-chip" title="Due {date(task.due)}"><Icon name="calendar" size={14} />{dateShort(task.due)}</span>
{:else if st === 'overdue'}
  <span class="due-chip" title="Was due {date(task.due)}"><Icon name="alert-circle" size={14} /><strong>Overdue</strong> · {relDate(task.due)}</span>
{:else if st === 'today'}
  <span class="due-chip" title="Due {date(task.due)}"><Icon name="clock" size={14} />Today</span>
{:else}
  <span class="due-chip" title="Due {date(task.due)}"><Icon name="calendar" size={14} />{diffDays(task.due) <= 7 ? relDate(task.due) : dateShort(task.due)}</span>
{/if}
