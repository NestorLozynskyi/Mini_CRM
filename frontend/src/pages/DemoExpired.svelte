<script lang="ts">
  import { BRAND_MARK } from '../lib/icons'
  import DemoButton from '../lib/ui/DemoButton.svelte'
  import Icon from '../lib/ui/Icon.svelte'

  let error = $state('')
  $effect(() => {
    document.title = 'Demo session ended · Mini CRM'
  })
</script>

<div class="screen" data-screen="demo-expired">
  <main class="expired" id="main">
    <a class="expired-brand" href="/sign-in" aria-label="Mini CRM, go to sign in">{@html BRAND_MARK}<span>Mini CRM</span></a>
    <section class="expired-card" aria-labelledby="expired-title">
      <span class="expired-icon" aria-hidden="true"><Icon name="clock" size={28} /></span>
      <div class="expired-head">
        <h1 class="expired-title" id="expired-title">Your demo session has ended</h1>
        <p class="expired-text">Demo sessions last 24 hours. Your temporary demo account and the sample data you worked with have been deleted.</p>
      </div>
      {#if error}
        <div class="alert alert-error" role="alert"><Icon name="alert-circle" size={20} /><div class="alert-body"><p>{error}</p></div></div>
      {/if}
      <div class="expired-actions">
        <DemoButton label="Start a new demo" cls="btn btn-primary btn-block" onerror={(m) => (error = m)} />
        <div class="btn-pair">
          <a class="btn btn-secondary btn-block" href="/sign-up"><Icon name="user-plus" size={18} /><span>Create account</span></a>
          <a class="btn btn-secondary btn-block" href="/sign-in"><Icon name="log-in" size={18} /><span>Sign in</span></a>
        </div>
      </div>
      <p class="expired-note"><Icon name="info" size={18} /><span>A new demo starts with a fresh copy of the sample data. To keep your clients, deals and tasks, create an account.</span></p>
    </section>
  </main>
</div>
