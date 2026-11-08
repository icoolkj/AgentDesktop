export type FileTreeIconKind =
  | 'folder'
  | 'folder-open'
  | 'file'
  | 'java'
  | 'xml'
  | 'json'
  | 'yaml'
  | 'markdown'
  | 'typescript'
  | 'javascript'
  | 'vue'
  | 'html'
  | 'css'
  | 'image'
  | 'gradle'
  | 'maven'
  | 'docker'
  | 'git'
  | 'sql'
  | 'python'
  | 'properties'
  | 'lock'
  | 'shell'
  | 'config'

export function iconKindForEntry(
  name: string,
  kind: 'file' | 'directory',
  expanded?: boolean,
): FileTreeIconKind {
  if (kind === 'directory') {
    return expanded ? 'folder-open' : 'folder'
  }

  const lower = name.toLowerCase()
  if (lower === 'pom.xml') return 'maven'
  if (lower === 'build.gradle' || lower === 'build.gradle.kts' || lower.endsWith('.gradle')) return 'gradle'
  if (lower === 'dockerfile' || lower.endsWith('.dockerfile')) return 'docker'
  if (lower === 'docker-compose.yml' || lower === 'docker-compose.yaml') return 'docker'
  if (lower === '.gitignore' || lower === '.gitattributes') return 'git'
  if (lower.startsWith('readme')) return 'markdown'
  if (lower === 'package.json' || lower === 'package-lock.json' || lower.endsWith('.lock')) return 'lock'
  if (lower.endsWith('.properties') || lower.endsWith('.env') || lower.endsWith('.config')) return 'properties'

  const dot = lower.lastIndexOf('.')
  const ext = dot >= 0 ? lower.slice(dot + 1) : ''

  switch (ext) {
    case 'java':
      return 'java'
    case 'xml':
      return 'xml'
    case 'json':
    case 'jsonc':
      return 'json'
    case 'yml':
    case 'yaml':
      return 'yaml'
    case 'md':
    case 'markdown':
      return 'markdown'
    case 'ts':
    case 'tsx':
      return 'typescript'
    case 'js':
    case 'jsx':
    case 'mjs':
    case 'cjs':
      return 'javascript'
    case 'vue':
      return 'vue'
    case 'html':
    case 'htm':
      return 'html'
    case 'css':
    case 'scss':
    case 'less':
      return 'css'
    case 'png':
    case 'jpg':
    case 'jpeg':
    case 'gif':
    case 'webp':
    case 'svg':
    case 'ico':
      return 'image'
    case 'sql':
      return 'sql'
    case 'py':
      return 'python'
    case 'sh':
    case 'bash':
    case 'ps1':
      return 'shell'
    default:
      return 'file'
  }
}
