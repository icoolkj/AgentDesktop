export interface WorkspacePermissionsFile {
  permissions: {
    allow: string[]
    ask: string[]
    deny: string[]
  }
}

export const EMPTY_PERMISSIONS: WorkspacePermissionsFile = {
  permissions: {
    allow: [],
    ask: [],
    deny: [],
  },
}

export function permissionsFilePath(workspaceRoot: string): string {
  const sep = workspaceRoot.includes('\\') ? '\\' : '/'
  return `${workspaceRoot}${workspaceRoot.endsWith(sep) ? '' : sep}.agent-desktop${sep}permissions.json`
}

export function agentDesktopDir(workspaceRoot: string): string {
  const sep = workspaceRoot.includes('\\') ? '\\' : '/'
  return `${workspaceRoot}${workspaceRoot.endsWith(sep) ? '' : sep}.agent-desktop`
}

function normalizeRules(values: string[] | undefined): string[] {
  if (!values?.length) return []
  return values.map(v => v.trim()).filter(Boolean)
}

export function parsePermissionsJson(raw: string | null): WorkspacePermissionsFile {
  if (!raw?.trim()) return structuredClone(EMPTY_PERMISSIONS)
  try {
    const parsed = JSON.parse(raw) as Partial<WorkspacePermissionsFile> & {
      allow?: string[]
      ask?: string[]
      deny?: string[]
    }
    const root = parsed.permissions ?? parsed
    return {
      permissions: {
        allow: normalizeRules((root as WorkspacePermissionsFile['permissions']).allow ?? parsed.allow),
        ask: normalizeRules((root as WorkspacePermissionsFile['permissions']).ask ?? parsed.ask),
        deny: normalizeRules((root as WorkspacePermissionsFile['permissions']).deny ?? parsed.deny),
      },
    }
  } catch {
    return structuredClone(EMPTY_PERMISSIONS)
  }
}

export function serializePermissions(file: WorkspacePermissionsFile): string {
  return `${JSON.stringify(
    {
      permissions: {
        allow: normalizeRules(file.permissions.allow),
        ask: normalizeRules(file.permissions.ask),
        deny: normalizeRules(file.permissions.deny),
      },
    },
    null,
    2,
  )}\n`
}

export async function loadWorkspacePermissions(workspaceRoot: string): Promise<WorkspacePermissionsFile> {
  const path = permissionsFilePath(workspaceRoot)
  const raw = await window.workspaceApi.readFile(path, { optional: true })
  return parsePermissionsJson(raw)
}

export async function saveWorkspacePermissions(
  workspaceRoot: string,
  file: WorkspacePermissionsFile,
): Promise<void> {
  await window.workspaceApi.createDirectory(agentDesktopDir(workspaceRoot))
  await window.workspaceApi.writeFile(permissionsFilePath(workspaceRoot), serializePermissions(file))
}
