const BACKEND_HTTP_BASE_KEY = 'agent-desktop:backend-http-base'

export function httpBaseFromWsUrl(wsUrl: string | null | undefined): string | null {
  if (!wsUrl) return null
  try {
    const url = new URL(wsUrl)
    url.protocol = url.protocol === 'wss:' ? 'https:' : 'http:'
    url.pathname = ''
    url.search = ''
    url.hash = ''
    return normalizeHttpBase(url.toString())
  } catch {
    return null
  }
}

export function normalizeHttpBase(input: string | null | undefined): string | null {
  if (!input?.trim()) return null
  let value = input.trim()
  if (!/^https?:\/\//i.test(value)) {
    value = `http://${value}`
  }
  try {
    const url = new URL(value)
    url.pathname = ''
    url.search = ''
    url.hash = ''
    return url.toString().replace(/\/$/, '')
  } catch {
    return null
  }
}

export function loadStoredBackendHttpBase(): string {
  return localStorage.getItem(BACKEND_HTTP_BASE_KEY) ?? ''
}

export function saveStoredBackendHttpBase(value: string): void {
  const normalized = normalizeHttpBase(value)
  if (normalized) {
    localStorage.setItem(BACKEND_HTTP_BASE_KEY, normalized)
  } else {
    localStorage.removeItem(BACKEND_HTTP_BASE_KEY)
  }
}

export function describeFetchFailure(error: unknown, httpBase: string): string {
  const message = error instanceof Error ? error.message : String(error)
  const lower = message.toLowerCase()

  if (error instanceof TypeError || lower.includes('failed to fetch') || lower.includes('network')) {
    return (
      `Cannot reach Agent backend at ${httpBase}. ` +
      'Start AgentServerApplication in IDEA, confirm the Backend URL port, then click Test connection.'
    )
  }

  return message
}
