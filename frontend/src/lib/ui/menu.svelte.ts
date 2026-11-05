export interface MenuItem {
  label: string
  icon?: string
  danger?: boolean
  href?: string
  header?: boolean
  divider?: boolean
  onClick?: () => void
}

export const menuState = $state<{ anchor: HTMLElement | null; items: MenuItem[]; align: 'start' | 'end' }>({ anchor: null, items: [], align: 'end' })

/** Випадне меню біля кнопки (аналог CRM.menu). Повторний клік по тій самій кнопці закриває його. */
export function openMenu(anchor: HTMLElement, items: MenuItem[], align: 'start' | 'end' = 'end') {
  if (menuState.anchor === anchor) {
    closeMenu()
    return
  }
  menuState.anchor?.setAttribute('aria-expanded', 'false')
  menuState.anchor = anchor
  menuState.items = items
  menuState.align = align
  anchor.setAttribute('aria-expanded', 'true')
}

export function closeMenu(refocus = false) {
  const a = menuState.anchor
  a?.setAttribute('aria-expanded', 'false')
  menuState.anchor = null
  menuState.items = []
  if (refocus) a?.focus()
}
