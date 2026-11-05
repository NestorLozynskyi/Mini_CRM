export interface ConfirmOptions {
  title: string
  text: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
  icon?: string
}

interface Pending extends ConfirmOptions {
  resolve: (ok: boolean) => void
}

export const confirmState = $state<{ current: Pending | null }>({ current: null })

/** Діалог підтвердження (аналог CRM.confirm макета). */
export function confirm(o: ConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    confirmState.current?.resolve(false)
    confirmState.current = { ...o, resolve }
  })
}
