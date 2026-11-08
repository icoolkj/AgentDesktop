export type TextLoadPolicy = 'normal' | 'large' | 'confirm-required' | 'blocked'

export const TEXT_MAX_LABEL = '100MB'
export const TEXT_CONFIRM_LABEL = '50MB'

export function textLoadPolicyMessage(policy: TextLoadPolicy, fileSizeLabel: string): string {
  switch (policy) {
    case 'blocked':
      return `This file (${fileSizeLabel}) exceeds the ${TEXT_MAX_LABEL} in-editor limit. Use an external app instead.`
    case 'confirm-required':
      return `This file (${fileSizeLabel}) is larger than ${TEXT_CONFIRM_LABEL}. Opening it may use significant memory and feel slow.`
    case 'large':
      return `Large file (${fileSizeLabel}). Syntax highlighting and some editor features are reduced for performance.`
    default:
      return ''
  }
}
