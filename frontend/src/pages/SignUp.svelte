<script lang="ts">
  import { ApiError } from '../lib/api/client'
  import { auth } from '../lib/api/crm'
  import { BRAND_MARK } from '../lib/icons'
  import { navigate } from '../lib/router.svelte'
  import { setMe } from '../lib/session.svelte'
  import AuthAside from '../lib/ui/AuthAside.svelte'
  import DemoButton from '../lib/ui/DemoButton.svelte'
  import ErrorSummary from '../lib/ui/ErrorSummary.svelte'
  import FieldError from '../lib/ui/FieldError.svelte'
  import Icon from '../lib/ui/Icon.svelte'

  type Name = 'email' | 'password' | 'confirm'
  const NAMES: Name[] = ['email', 'password', 'confirm']
  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
  let v = $state({ email: '', password: '', confirm: '' })
  let show = $state({ password: false, confirm: false })
  let errors = $state<Partial<Record<Name, string>>>({})
  let touched: Partial<Record<Name, boolean>> = {}
  let alert = $state<null | { kind: 'summary'; list: { id: string; msg: string }[] } | { kind: 'taken'; email: string } | { kind: 'server'; title: string; text: string }>(null)
  let loading = $state(false)
  const hintMet = $derived(v.password.length >= 8)

  function rule(name: Name) {
    const val = v[name]
    if (name === 'email') {
      if (!val.trim()) return 'Enter your email address'
      if (!EMAIL_RE.test(val.trim())) return 'Enter a valid email address'
    } else if (name === 'password') {
      if (!val) return 'Create a password'
      if (val.length < 8) return 'Password must be at least 8 characters'
      if (val.length > 64) return 'Password must be at most 64 characters'
    } else {
      if (!val) return 'Confirm your password'
      if (val !== v.password) return 'Passwords don’t match'
    }
    return ''
  }
  function validate(name: Name) {
    const msg = rule(name)
    const next = { ...errors }
    if (msg) next[name] = msg
    else delete next[name]
    errors = next
    return msg
  }
  function onInput(name: Name) {
    touched[name] = true
    if (name === 'email' && alert?.kind === 'taken') alert = null
    if (errors[name]) validate(name)
    if (name === 'password' && touched.confirm && v.confirm) validate('confirm')
  }
  function onBlur(name: Name, e: FocusEvent) {
    // Фокус іде на кнопку відправлення: валідуємо при submit, щоб зсув розмітки не «зривав» натискання.
    if ((e.relatedTarget as HTMLButtonElement | null)?.type === 'submit') return
    if (touched[name] || v[name]) {
      touched[name] = true
      validate(name)
    }
  }
  function describedBy(name: Name) {
    const ids: string[] = []
    if (name === 'email' && alert?.kind === 'taken') ids.push('taken-msg')
    if (errors[name]) ids.push(`${name}-error`)
    if (name === 'password') ids.push('password-hint')
    return ids.join(' ') || undefined
  }

  async function submit(e: SubmitEvent) {
    e.preventDefault()
    if (loading) return
    NAMES.forEach((n) => (touched[n] = true))
    const list = NAMES.map((n) => ({ id: n, msg: validate(n) })).filter((x) => x.msg)
    if (list.length) {
      alert = { kind: 'summary', list }
      document.getElementById(list[0].id)?.focus()
      return
    }
    alert = null
    loading = true
    try {
      setMe(await auth.signUp(v.email.trim(), v.password))
      navigate('/', { replace: true })
    } catch (err) {
      loading = false
      if (err instanceof ApiError && err.code === 'email_taken') {
        alert = { kind: 'taken', email: v.email.trim() }
        document.getElementById('email')?.focus()
      } else if (err instanceof ApiError && err.isValidation) {
        const fe = err.fieldErrors()
        errors = fe as typeof errors
        alert = { kind: 'summary', list: Object.entries(fe).map(([id, msg]) => ({ id, msg })) }
      } else {
        alert = { kind: 'server', title: 'Couldn’t create your account.', text: 'Check your connection and try again.' }
      }
    }
  }
</script>

<div class="screen" data-screen="sign-up">
  <div class="auth">
    <AuthAside />
    <main class="auth-main" id="main">
      <a class="auth-brand" href="/sign-in" aria-label="Mini CRM">{@html BRAND_MARK}<span>Mini CRM</span></a>
      <section class="auth-card" aria-labelledby="auth-title">
        <div class="auth-head">
          <h1 class="auth-title" id="auth-title">Create your account</h1>
          <p class="auth-sub">Sign up with your email. Your data stays private to your account.</p>
        </div>

        {#if alert?.kind === 'summary'}
          <ErrorSummary errors={alert.list} />
        {:else if alert?.kind === 'taken'}
          <div class="alert alert-error" role="alert" tabindex="-1">
            <Icon name="alert-circle" size={20} />
            <div class="alert-body">
              <p class="alert-title" id="taken-msg">An account with this email already exists.</p>
              <p>Sign in instead, or use a different email address.</p>
              <div class="alert-actions">
                <a class="btn btn-secondary btn-sm" href="/sign-in?email={encodeURIComponent(alert.email)}"><Icon name="log-in" size={16} /><span>Sign in</span></a>
              </div>
            </div>
          </div>
        {:else if alert}
          <div class="alert alert-error" role="alert" tabindex="-1">
            <Icon name="alert-circle" size={20} />
            <div class="alert-body"><p class="alert-title">{alert.title}</p><p>{alert.text}</p></div>
          </div>
        {/if}

        <form class="auth-form" novalidate onsubmit={submit} aria-busy={loading}>
          <fieldset disabled={loading}>
            <div class="field" data-field="email" class:is-invalid={!!errors.email || alert?.kind === 'taken'}>
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
                bind:value={v.email}
                oninput={() => onInput('email')}
                onblur={(e) => onBlur('email', e)}
                aria-invalid={!!errors.email || alert?.kind === 'taken' || undefined}
                aria-describedby={describedBy('email')} />
              <FieldError id="email-error" message={errors.email} />
            </div>
            {#each [{ n: 'password', label: 'Password' }, { n: 'confirm', label: 'Confirm password' }] as f}
              {@const name = f.n as 'password' | 'confirm'}
              <div class="field" data-field={name} class:is-invalid={!!errors[name]}>
                <label for={name}>{f.label}</label>
                <div class="input-wrap">
                  <input
                    class="input"
                    type={show[name] ? 'text' : 'password'}
                    id={name}
                    {name}
                    autocomplete="new-password"
                    required
                    bind:value={v[name]}
                    oninput={() => onInput(name)}
                    onblur={(e) => onBlur(name, e)}
                    aria-invalid={!!errors[name] || undefined}
                    aria-describedby={describedBy(name)} />
                  <button
                    type="button"
                    class="btn btn-ghost btn-icon input-action"
                    aria-label={show[name] ? 'Hide password' : 'Show password'}
                    aria-pressed={show[name]}
                    aria-controls={name}
                    onclick={() => (show[name] = !show[name])}><Icon name={show[name] ? 'eye-off' : 'eye'} size={20} /></button>
                </div>
                {#if name === 'password'}
                  <p class="field-hint" class:is-met={hintMet} id="password-hint">{#if hintMet}<Icon name="check" size={14} /> {/if}At least 8 characters.</p>
                {/if}
                <FieldError id="{name}-error" message={errors[name]} />
              </div>
            {/each}
          </fieldset>
          <button type="submit" class="btn btn-primary btn-block" class:is-loading={loading} aria-busy={loading}><span class="btn-text">{loading ? 'Creating account…' : 'Create account'}</span></button>
        </form>

        <div class="divider-text" role="separator">or</div>

        <div class="demo-cta">
          <DemoButton onerror={(m) => (alert = { kind: 'server', title: 'Couldn’t start the demo.', text: m })} />
          <p class="demo-cta-text">Explore with sample data — no sign-up. Your demo is private and is deleted after 24 hours.</p>
        </div>

        <p class="auth-foot">Already have an account? <a href="/sign-in">Sign in</a></p>
      </section>
    </main>
  </div>
</div>
