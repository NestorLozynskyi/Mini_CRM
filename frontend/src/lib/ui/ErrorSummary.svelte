<script lang="ts">
  import Icon from './Icon.svelte'

  /** Зведення помилок форми з посиланнями на поля (sign-in / sign-up). */
  let { errors }: { errors: { id: string; msg: string }[] } = $props()

  function focus(e: MouseEvent, id: string) {
    e.preventDefault()
    document.getElementById(id)?.focus()
  }
</script>

<div class="alert alert-error error-summary" role="alert" tabindex="-1">
  <Icon name="alert-circle" size={20} />
  <div class="alert-body">
    <p class="alert-title">{errors.length === 1 ? 'Please fix 1 error' : `Please fix ${errors.length} errors`}</p>
    <ul>
      {#each errors as e}<li><a href="#{e.id}" onclick={(ev) => focus(ev, e.id)}>{e.msg}</a></li>{/each}
    </ul>
  </div>
</div>
