<script lang="ts">
  import EmptyState from '../lib/ui/EmptyState.svelte'
  import Shell from '../lib/ui/Shell.svelte'
</script>

<div class="screen" data-screen="not-found">
  <Shell active="" title="Page not found">
    <div class="card">
      <EmptyState icon="alert-circle" title="This page doesn’t exist" text="The link may be broken or the record was deleted.">
        {#snippet actions()}<a class="btn btn-primary" href="/">Go to dashboard</a>{/snippet}
      </EmptyState>
    </div>
  </Shell>
</div>
