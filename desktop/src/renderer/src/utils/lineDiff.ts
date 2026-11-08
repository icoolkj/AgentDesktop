export interface LineChangeStats {
  added: number
  removed: number
}

/** Rough line diff stats for UI badges (Cursor-style +N -M). */
export function countLineChanges(before: string, after: string): LineChangeStats {
  const oldLines = before.split('\n')
  const newLines = after.split('\n')
  let i = 0
  let j = 0
  let added = 0
  let removed = 0
  while (i < oldLines.length && j < newLines.length) {
    if (oldLines[i] === newLines[j]) {
      i++
      j++
    } else {
      const nextOldInNew = newLines.indexOf(oldLines[i]!, j + 1)
      const nextNewInOld = oldLines.indexOf(newLines[j]!, i + 1)
      if (nextOldInNew >= 0 && (nextNewInOld < 0 || nextOldInNew - j <= nextNewInOld - i)) {
        added += nextOldInNew - j
        j = nextOldInNew
      } else if (nextNewInOld >= 0) {
        removed += nextNewInOld - i
        i = nextNewInOld
      } else {
        removed++
        added++
        i++
        j++
      }
    }
  }
  removed += oldLines.length - i
  added += newLines.length - j
  return { added, removed }
}

/** Compact unified diff for chat panel (truncated). */
export function buildUnifiedDiffPreview(
  before: string,
  after: string,
  maxLines = 48,
): string {
  const oldLines = before.split('\n')
  const newLines = after.split('\n')
  const out: string[] = []
  let i = 0
  let j = 0
  while ((i < oldLines.length || j < newLines.length) && out.length < maxLines) {
    if (i < oldLines.length && j < newLines.length && oldLines[i] === newLines[j]) {
      i++
      j++
      continue
    }
    if (j < newLines.length && (i >= oldLines.length || oldLines[i] !== newLines[j])) {
      out.push(`+ ${newLines[j]}`)
      j++
      continue
    }
    if (i < oldLines.length) {
      out.push(`- ${oldLines[i]}`)
      i++
    }
  }
  if (i < oldLines.length || j < newLines.length) {
    out.push('…')
  }
  return out.join('\n')
}

export function relativePathFromRoot(root: string | undefined, absolutePath: string): string {
  if (!root?.trim()) return absolutePath
  const normRoot = root.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
  const normPath = absolutePath.replace(/\\/g, '/').toLowerCase()
  if (normPath.startsWith(normRoot + '/')) {
    return absolutePath.slice(root.length).replace(/^[/\\]+/, '')
  }
  return absolutePath
}

export interface ChangeTreeDir {
  kind: 'dir'
  name: string
  path: string
  children: ChangeTreeNode[]
}

export interface ChangeTreeFile {
  kind: 'file'
  name: string
  path: string
  changeKind: 'modified' | 'created' | 'deleted'
  added: number
  removed: number
}

export type ChangeTreeNode = ChangeTreeDir | ChangeTreeFile

export interface SessionFileChangeInput {
  path: string
  relativePath: string
  changeKind: 'modified' | 'created' | 'deleted'
  previousContent?: string
  content: string
}

export function buildChangeTree(changes: SessionFileChangeInput[]): ChangeTreeNode[] {
  const root: ChangeTreeDir = { kind: 'dir', name: '', path: '', children: [] }

  for (const change of changes) {
    const parts = change.relativePath.replace(/\\/g, '/').split('/').filter(Boolean)
    let current = root
    let builtPath = ''
    for (let idx = 0; idx < parts.length; idx++) {
      const part = parts[idx]!
      builtPath = builtPath ? `${builtPath}/${part}` : part
      const isFile = idx === parts.length - 1
      if (isFile) {
        const stats =
          change.previousContent != null
            ? countLineChanges(change.previousContent, change.content)
            : { added: change.content.split('\n').length, removed: 0 }
        current.children.push({
          kind: 'file',
          name: part,
          path: change.path,
          changeKind: change.changeKind,
          added: stats.added,
          removed: stats.removed,
        })
      } else {
        let dir = current.children.find(
          (c): c is ChangeTreeDir => c.kind === 'dir' && c.name === part,
        )
        if (!dir) {
          dir = { kind: 'dir', name: part, path: builtPath, children: [] }
          current.children.push(dir)
        }
        current = dir
      }
    }
  }

  const sortNodes = (nodes: ChangeTreeNode[]): ChangeTreeNode[] =>
    [...nodes].sort((a, b) => {
      if (a.kind !== b.kind) return a.kind === 'dir' ? -1 : 1
      return a.name.localeCompare(b.name)
    })

  const sortTree = (node: ChangeTreeDir): ChangeTreeDir => ({
    ...node,
    children: sortNodes(node.children.map(c => (c.kind === 'dir' ? sortTree(c) : c))),
  })

  return sortTree(root).children
}
