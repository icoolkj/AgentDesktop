export interface GrepMatchDto {
  file: string
  line: number
  column: number
  text: string
}

export interface GrepRequestDto {
  rootPath: string
  pattern: string
  caseInsensitive?: boolean
  wholeWord?: boolean
  glob?: string
  headLimit?: number
}

export interface GrepResultDto {
  matches: GrepMatchDto[]
  truncated: boolean
  error?: string
  engine?: 'ripgrep' | 'fallback'
}
