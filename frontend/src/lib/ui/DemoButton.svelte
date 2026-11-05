<script lang="ts">
  import { ApiError } from '../api/client'
  import { auth } from '../api/crm'
  import { navigate } from '../router.svelte'
  import { setMe } from '../session.svelte'
  import Icon from './Icon.svelte'

  /** «Try demo» / «Start a new demo»: створює новий ізольований демо-акаунт і відкриває CRM. */
  let { label = 'Try demo', cls = 'btn btn-secondary btn-block', onerror }: { label?: string; cls?: string; onerror?: (message: string) => void } = $props()
  let busy = $state(false)

  async function start(e: MouseEvent) {
    e.preventDefault()
    if (busy) return
    busy = true
    try {
      setMe(await auth.demo())
      navigate('/', { replace: true })
    } catch (err) {
      busy = false
      onerror?.(err instanceof ApiError && err.status !== 0 && err.status < 500 ? err.message : 'Couldn’t start the demo. Check your connection and try again.')
    }
  }
</script>

<a class={cls} class:is-loading={busy} aria-busy={busy || undefined} href="/" id="demo-btn" onclick={start}><Icon name="play" size={18} /><span>{label}</span></a>
