/** Disable bracket-rainbow / match-bracket boxes while reviewing agent diffs. */
export const DIFF_VIEW_EDITOR_OPTIONS = {
  bracketPairColorization: { enabled: false },
  matchBrackets: 'never',
  occurrencesHighlight: 'off',
  selectionHighlight: false,
  guides: {
    bracketPairs: false,
    bracketPairsHorizontal: false,
    indentation: true,
  },
} as const
