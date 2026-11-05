<script lang="ts">
  import type { Snippet } from 'svelte'
  import { tasks as api } from '../api/crm'
  import type { Task } from '../api/types'
  import { refreshBadges } from '../session.svelte'
  import { sortTasks, taskStatus } from '../tasks'
  import { confirm } from './confirm.svelte'
  import DueChip from './DueChip.svelte'
  import EmptyState from './EmptyState.svelte'
  import EntityChip from './EntityChip.svelte'
  import Icon from './Icon.svelte'
  import { openMenu } from './menu.svelte'
  import TaskForm from './TaskForm.svelte'
  import { errorToast, toast } from './toast.svelte'

  interface Props {
    tasks: Task[]
    showLink?: boolean
    sort?: boolean
    padded?: boolean
    emptyTitle?: string
    emptyText?: string
    emptyAction?: Snippet
    /** Після будь-якої зміни (виконання, редагування, видалення) — батько перезавантажує дані. */
    onchange?: () => void
  }
  let { tasks, showLink = false, sort = true, padded = false, emptyTitle = 'No tasks', emptyText = 'Add a task with a deadline so nothing slips.', emptyAction, onchange }: Props = $props()

  const list = $derived(sort ? sortTasks(tasks) : tasks)
  let editing = $state<Task | null>(null)
  /** Оптимістичний стан галочок до відповіді сервера. */
  let pending = $state<Record<number, boolean>>({})

  async function setDone(t: Task, done: boolean, withToast = true) {
    pending[t.id] = done
    try {
      await api.setDone(t.id, done)
      void refreshBadges()
      if (withToast) {
        if (done) toast({ type: 'success', text: 'Task completed', action: { label: 'Undo', onClick: () => void setDone(t, false, false) } })
        else toast({ type: 'success', text: 'Task marked as active' })
      }
      onchange?.()
    } catch (e) {
      errorToast(e)
    } finally {
      delete pending[t.id]
    }
  }

  function menu(e: MouseEvent, t: Task) {
    openMenu(e.currentTarget as HTMLElement, [
      { label: 'Edit task', icon: 'edit', onClick: () => (editing = t) },
      {
        label: 'Delete task', icon: 'trash', danger: true,
        onClick: async () => {
          const ok = await confirm({ title: 'Delete task?', text: `“${t.title}” will be permanently deleted.`, confirmLabel: 'Delete task' })
          if (!ok) return
          try {
            await api.remove(t.id)
            void refreshBadges()
            toast({ type: 'success', text: 'Task deleted' })
            onchange?.()
          } catch (err) {
            errorToast(err)
          }
        },
      },
    ])
  }
</script>

{#if !list.length}
  <EmptyState icon="check-square" compact title={emptyTitle} text={emptyText} actions={emptyAction} />
{:else}
  <ul class="task-list" class:is-padded={padded} data-show-link={showLink ? '1' : ''}>
    {#each list as t (t.id)}
      {@const done = pending[t.id] ?? t.done}
      {@const st = taskStatus({ ...t, done })}
      <li class="task-item is-{st}" class:is-done={done} data-id={t.id}>
        <label class="checkbox task-check"
          ><input
            type="checkbox"
            checked={done}
            aria-label="{done ? 'Mark as not done: ' : 'Mark as complete: '}{t.title}"
            onchange={(e) => void setDone(t, (e.currentTarget as HTMLInputElement).checked)} /></label>
        <div class="task-body">
          <span class="task-title">{t.title}</span>
          <div class="task-meta">
            <DueChip task={{ ...t, done }} />
            {#if showLink}
              {#if t.dealId}<EntityChip kind="deal" id={t.dealId} label={t.dealTitle ?? ''} />
              {:else}<EntityChip kind="client" id={t.clientId} label={t.clientName} clientType={t.clientType} />{/if}
            {/if}
          </div>
        </div>
        <button type="button" class="btn btn-ghost btn-icon btn-sm task-more" aria-label="Actions for {t.title}" aria-haspopup="menu" aria-expanded="false" onclick={(e) => menu(e, t)}>
          <Icon name="more" size={18} />
        </button>
      </li>
    {/each}
  </ul>
{/if}

{#if editing}
  <TaskForm task={editing} onsaved={() => onchange?.()} onclose={() => (editing = null)} />
{/if}
