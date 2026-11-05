<script lang="ts">
  import { ApiError } from '../lib/api/client'
  import { auth } from '../lib/api/crm'
  import { BRAND_MARK } from '../lib/icons'
  import { navigate, params } from '../lib/router.svelte'
  import { setMe } from '../lib/session.svelte'
  import AuthAside from '../lib/ui/AuthAside.svelte'
  import DemoButton from '../lib/ui/DemoButton.svelte'
  import ErrorSummary from '../lib/ui/ErrorSummary.svelte'
  import FieldError from '../lib/ui/FieldError.svelte'
  import Icon from '../lib/ui/Icon.svelte'

  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
  let email = $state(params().get('email') ?? '')
  let password = $state('')
  let showPassword = $state(false)
  let errors = $state<Record<string, string>>({})
  let touched: Record<string, boolean> = {}
  let alert = $state<null | { kind: 'summary'; list: { id: string; msg: string }[] } | { kind: 'invalid' | 'server'; title: string; text: string }>(null)
  let loading = $state(false)
  let passwordEl = $state<HTMLInputElement>()

  /** Фокус іде на кнопку відправлення: не показуємо помилку на blur, інакше зсув розмітки «зриває» натискання (особливо на мобільному). */
  const toSubmit = (e: FocusEvent) => (e.relatedTarget as HTMLButtonElement | null)?.type === 'submit'

  function rule(name: 'email' | 'password') {
    if (name === 'email') {
      const v = email.trim()
      if (!v) return 'Enter your email address'
      if (!EMAIL_RE.test(v)) return 'Enter a valid email address'
      return ''
    }
    return password ? '' : 'Enter your password'
  }
  function validate(name: 'email' | 'password') {
    const msg = rule(name)
    const next = { ...errors }
    if (msg) next[name] = msg
    else delete next[name]
    errors = next
    return msg
  }

  async function submit(e: SubmitEvent) {
    e.preventDefault()
    if (loading) return
    touched = { email: true, password: true }
    const list = (['email', 'password'] as const).map((n) => ({ id: n, msg: validate(n) })).filter((x) => x.msg)
    if (list.length) {
      alert = { kind: 'summary', list }
      document.getElementById(list[0].id)?.focus()
      return
    }
    alert = null
    loading = true
    try {
      setMe(await auth.signIn(email.trim(), password))
      navigate('/', { replace: true })
    } catch (err) {
      loading = false
      if (err instanceof ApiError && err.code === 'invalid_credentials') {
        password = ''
        alert = { kind: 'invalid', title: 'Incorrect email or password.', text: 'Check your details and try again.' }
        passwordEl?.focus()
      } else if (err instanceof ApiError && err.code === 'too_many_attempts') {
        alert = { kind: 'server', title: 'Too many sign-in attempts.', text: 'Please wait a few minutes and try again.' }
      } else {
        alert = { kind: 'server', title: 'Couldn’t sign in.', text: 'Check your connection and try again.' }
      }
    }
  }

  function demoError(message: string) {
    alert = { kind: 'server', title: 'Couldn’t start the demo.', text: message }
  }
</script>

<div class="screen" data-screen="sign-in">
  <div class="auth">
    <AuthAside />
    <main class="auth-main" id="main">
      <a class="auth-brand" href="/sign-in" aria-label="Mini CRM">{@html BRAND_MARK}<span>Mini CRM</span></a>
      <section class="auth-card" aria-labelledby="auth-title">
        <div class="auth-head">
          <h1 class="auth-title" id="auth-title">Sign in to Mini CRM</h1>
          <p class="auth-sub">Welcome back. Enter your email and password.</p>
        </div>

        {#if alert?.kind === 'summary'}
          <ErrorSummary errors={alert.list} />
        {:else if alert}
          <div class="alert alert-error" role="alert" tabindex="-1">
            <Icon name="alert-circle" size={20} />
            <div class="alert-body"><p class="alert-title">{alert.title}</p><p>{alert.text}</p></div>
          </div>
        {/if}

        <form class="auth-form" novalidate onsubmit={submit} aria-busy={loading}>
          <fieldset disabled={loading}>
            <div class="field" data-field="email" class:is-invalid={!!errors.email}>
              <label for="email">Email</label>
              <input
                class="input"
                type="email"
                id="email"
                name="email"
                autocomplete="email"
                inputmode="email"
                spellcheck="false"
                placeholder="you@example.com"
                required
                bind:value={email}
                oninput={() => { touched.email = true; if (errors.email) validate('email') }}
                onblur={(e) => { if (toSubmit(e)) return; if (touched.email || email) { touched.email = true; validate('email') } }}
                aria-invalid={!!errors.email || undefined}
                aria-describedby={errors.email ? 'email-error' : undefined} />
              <FieldError id="email-error" message={errors.email} />
            </div>
            <div class="field" data-field="password" class:is-invalid={!!errors.password}>
              <label for="password">Password</label>
              <div class="input-wrap">
                <input
                  class="input"
                  type={showPassword ? 'text' : 'password'}
                  id="password"
                  name="password"
                  autocomplete="current-password"
                  required
                  bind:this={passwordEl}
                  bind:value={password}
                  oninput={() => { touched.password = true; if (errors.password) validate('password') }}
                  onblur={(e) => { if (toSubmit(e)) return; if (touched.password || password) { touched.password = true; validate('password') } }}
                  aria-invalid={!!errors.password || undefined}
                  aria-describedby={errors.password ? 'password-error' : undefined} />
                <button
                  type="button"
                  class="btn btn-ghost btn-icon input-action"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  aria-pressed={showPassword}
                  aria-controls="password"
                  onclick={() => (showPassword = !showPassword)}><Icon name={showPassword ? 'eye-off' : 'eye'} size={20} /></button>
              </div>
              <FieldError id="password-error" message={errors.password} />
            </div>
          </fieldset>
          <button type="submit" class="btn btn-primary btn-block" class:is-loading={loading} aria-busy={loading}><span class="btn-text">{loading ? 'Signing in…' : 'Sign in'}</span></button>
        </form>

        <div class="divider-text" role="separator">or</div>

        <div class="demo-cta">
          <DemoButton onerror={demoError} />
          <p class="demo-cta-text">Explore with sample data — no sign-up. Your demo is private and is deleted after 24 hours.</p>
        </div>

        <p class="auth-foot">Don’t have an account? <a href="/sign-up">Create account</a></p>
      </section>
    </main>
  </div>
</div>
