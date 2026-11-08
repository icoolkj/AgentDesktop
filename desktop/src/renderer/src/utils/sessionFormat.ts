export function formatSessionTime(iso?: string): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (sameDay) {
    return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function workspaceBasename(path?: string): string {
  if (!path?.trim()) return ''
  const normalized = path.replace(/\\/g, '/').replace(/\/+$/, '')
  const parts = normalized.split('/')
  return parts[parts.length - 1] || path
}

export function formatSessionLabel(title?: string, id?: string, updatedAt?: string, workspace?: string): string {
  const name = title?.trim() || (id ? id.slice(0, 8) : 'Session')
  const time = formatSessionTime(updatedAt)
  const project = workspaceBasename(workspace)
  const bits = [name]
  if (project) bits.push(`[${project}]`)
  if (time) bits.push(time)
  return bits.join(' · ')
}

export function formatMessageTime(iso?: string): string {
  return formatSessionTime(iso)
}
