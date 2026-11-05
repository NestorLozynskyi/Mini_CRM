<script lang="ts">
  let { name, size, square = false }: { name: string; size?: 'sm' | 'lg' | 'xl'; square?: boolean } = $props()

  function initials(n: string) {
    const words = String(n || '?').replace(/@.*$/, '').split(/[\s._\-&]+/).filter(Boolean)
    if (!words.length) return '?'
    return ((words[0][0] || '') + (words.length > 1 ? words[1][0] : '')).toUpperCase()
  }
  function tone(n: string) {
    let h = 0
    const s = String(n || '')
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
    return (h % 6) + 1
  }
</script>

<span class="avatar{size ? ' avatar-' + size : ''}{square ? ' avatar-square' : ''}" data-tone={tone(name)} aria-hidden="true">{initials(name)}</span>
