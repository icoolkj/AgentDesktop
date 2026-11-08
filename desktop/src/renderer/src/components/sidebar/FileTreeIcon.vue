<script setup lang="ts">
import type { FileTreeIconKind } from '@renderer/utils/fileTreeIcons'
import type { FileIconThemeId } from '@renderer/types/appearance'

withDefaults(
  defineProps<{
    kind: FileTreeIconKind
    variant?: FileIconThemeId
  }>(),
  { variant: 'seti' },
)
</script>

<template>
  <span v-if="variant === 'minimal'" class="tree-icon" aria-hidden="true">
    <svg v-if="kind === 'folder' || kind === 'folder-open'" viewBox="0 0 16 16" class="tree-icon__svg tree-icon__svg--minimal">
      <path
        fill="currentColor"
        class="tree-icon__folder"
        d="M1.5 3.5A1 1 0 0 1 2.5 2.5h3.1l1.2 1.2h6.7A1 1 0 0 1 14.5 4.7v8.8a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1V3.5z"
      />
    </svg>
    <svg v-else viewBox="0 0 16 16" class="tree-icon__svg tree-icon__svg--minimal">
      <path
        fill="currentColor"
        d="M4 1.5h5.5L13 5v9.5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-12a1 1 0 0 1 1-1z"
      />
    </svg>
  </span>

  <span v-else class="tree-icon" aria-hidden="true">
    <!-- Classic folder (closed) -->
    <svg v-if="kind === 'folder'" viewBox="0 0 16 16" class="tree-icon__svg">
      <path
        fill="#C09553"
        d="M1.5 3.5A1 1 0 0 1 2.5 2.5h3.1l1.2 1.2h6.7A1 1 0 0 1 14.5 4.7v8.8a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1V3.5z"
      />
      <path fill="#D4A853" d="M2.5 3.5h4.3l1-1h6.2v1.5H2.5V3.5z" />
    </svg>

    <!-- Classic folder (open) -->
    <svg v-else-if="kind === 'folder-open'" viewBox="0 0 16 16" class="tree-icon__svg">
      <path fill="#C09553" d="M1 4.5A1 1 0 0 1 2 3.5h3.2l1.1 1.1H14a1 1 0 0 1 1 1v1.5H1V4.5z" />
      <path fill="#E8C878" d="M1 7h14l-1.2 5.5a1 1 0 0 1-1 .8H3.2a1 1 0 0 1-1-.8L1 7z" />
    </svg>

    <!-- Java -->
    <svg v-else-if="kind === 'java'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#3D2A1E" />
      <path
        fill="#E76F00"
        d="M8.2 11.2c2.1-.4 2.8-1.6 2.8-1.6s-.5.3-1.4.5c-.9.2-1.9.1-1.9.1s1.5 1.2 3.5.8c0 0-.3 1.4-2 2.2-1.7.8-3.8.2-3.8.2s2.5.6 4.8-.2zM6.5 8.5c-.1-1.8 1.6-2.7 1.6-2.7s-2.8 1.4-1.6 4.3c0 0 .2-1 .1-1.6z"
      />
      <text x="8" y="6.5" text-anchor="middle" fill="#fff" font-size="5" font-weight="700" font-family="sans-serif">J</text>
    </svg>

    <!-- XML / Maven -->
    <svg v-else-if="kind === 'xml' || kind === 'maven'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" :fill="kind === 'maven' ? '#3B2E5A' : '#5A3E2B'" />
      <text x="8" y="10.5" text-anchor="middle" fill="#fff" font-size="5.5" font-weight="700" font-family="monospace">
        {{ kind === 'maven' ? 'M' : '<>' }}
      </text>
    </svg>

    <!-- JSON -->
    <svg v-else-if="kind === 'json'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#5A4A2A" />
      <text x="8" y="10.5" text-anchor="middle" fill="#F0D060" font-size="5" font-weight="700" font-family="monospace">{ }</text>
    </svg>

    <!-- YAML -->
    <svg v-else-if="kind === 'yaml'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#3A3A5A" />
      <text x="8" y="10" text-anchor="middle" fill="#A8B4FF" font-size="4.5" font-weight="600" font-family="monospace">YML</text>
    </svg>

    <!-- Markdown -->
    <svg v-else-if="kind === 'markdown'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#2A4A6A" />
      <text x="8" y="10.5" text-anchor="middle" fill="#7EC8FF" font-size="5.5" font-weight="700" font-family="sans-serif">M</text>
    </svg>

    <!-- TypeScript -->
    <svg v-else-if="kind === 'typescript'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#2F5496" />
      <text x="8" y="10.5" text-anchor="middle" fill="#fff" font-size="5.5" font-weight="700" font-family="sans-serif">TS</text>
    </svg>

    <!-- JavaScript -->
    <svg v-else-if="kind === 'javascript'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#8B6914" />
      <text x="8" y="10.5" text-anchor="middle" fill="#fff" font-size="5.5" font-weight="700" font-family="sans-serif">JS</text>
    </svg>

    <!-- Vue -->
    <svg v-else-if="kind === 'vue'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#1E3A2F" />
      <path fill="#42B883" d="M8 4.5 4.5 11h2.2l1.3-2.5 1.3 2.5h2.2L8 4.5z" />
    </svg>

    <!-- HTML -->
    <svg v-else-if="kind === 'html'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#6A3A2A" />
      <text x="8" y="10" text-anchor="middle" fill="#FF8A65" font-size="4.5" font-weight="700" font-family="monospace">HTML</text>
    </svg>

    <!-- CSS -->
    <svg v-else-if="kind === 'css'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#2A4A8A" />
      <text x="8" y="10.5" text-anchor="middle" fill="#64B5F6" font-size="5" font-weight="700" font-family="sans-serif">CSS</text>
    </svg>

    <!-- Image -->
    <svg v-else-if="kind === 'image'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#2A3A4A" />
      <circle cx="5.5" cy="5.5" r="1.5" fill="#81C995" />
      <path fill="#5A9A6A" d="M2 12l3.5-4 2.5 2.5L11 6l3 6H2z" />
    </svg>

    <!-- Gradle -->
    <svg v-else-if="kind === 'gradle'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#1A3A2A" />
      <text x="8" y="10" text-anchor="middle" fill="#6FD4A0" font-size="4.5" font-weight="700" font-family="sans-serif">G</text>
    </svg>

    <!-- Docker -->
    <svg v-else-if="kind === 'docker'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#1A4A6A" />
      <rect x="4" y="7" width="2" height="2" fill="#4FC3F7" />
      <rect x="6.5" y="7" width="2" height="2" fill="#4FC3F7" />
      <rect x="9" y="7" width="2" height="2" fill="#4FC3F7" />
      <rect x="6.5" y="4.5" width="2" height="2" fill="#4FC3F7" />
    </svg>

    <!-- Git -->
    <svg v-else-if="kind === 'git'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#3A2A2A" />
      <circle cx="8" cy="8" r="2.5" fill="none" stroke="#F07178" stroke-width="1.2" />
      <path stroke="#F07178" stroke-width="1.2" d="M8 5.5V8l2 1" />
    </svg>

    <!-- SQL -->
    <svg v-else-if="kind === 'sql'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#2A3A5A" />
      <ellipse cx="8" cy="5.5" rx="4" ry="1.5" fill="#7EC8FF" />
      <path fill="#5A9AD0" d="M4 5.5v5c0 .8 1.8 1.5 4 1.5s4-.7 4-1.5v-5c0 .8-1.8 1.5-4 1.5S4 6.3 4 5.5z" />
    </svg>

    <!-- Python -->
    <svg v-else-if="kind === 'python'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#2A4A5A" />
      <text x="8" y="10.5" text-anchor="middle" fill="#FFD54F" font-size="5.5" font-weight="700" font-family="sans-serif">Py</text>
    </svg>

    <!-- Properties / config -->
    <svg v-else-if="kind === 'properties' || kind === 'config'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#3A3A3A" />
      <circle cx="8" cy="8" r="2.5" fill="none" stroke="#9AA0A6" stroke-width="1" />
      <circle cx="8" cy="8" r="0.8" fill="#9AA0A6" />
    </svg>

    <!-- Lock / package -->
    <svg v-else-if="kind === 'lock'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#2A4A3A" />
      <rect x="5.5" y="7" width="5" height="4" rx="0.5" fill="#81C995" />
      <path fill="none" stroke="#81C995" stroke-width="1.2" d="M6.5 7V5.5a1.5 1.5 0 0 1 3 0V7" />
    </svg>

    <!-- Shell -->
    <svg v-else-if="kind === 'shell'" viewBox="0 0 16 16" class="tree-icon__svg">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="#1A1A1A" />
      <text x="5" y="9.5" fill="#81C995" font-size="5" font-family="monospace">&gt;_</text>
    </svg>

    <!-- Generic file -->
    <svg v-else viewBox="0 0 16 16" class="tree-icon__svg">
      <path fill="#6B7280" d="M4 1.5h5.5L13 5v9.5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-12a1 1 0 0 1 1-1z" />
      <path fill="#9AA0A6" d="M9.5 1.5V5H13L9.5 1.5z" />
    </svg>
  </span>
</template>

<style scoped>
.tree-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.tree-icon__svg {
  width: 16px;
  height: 16px;
  display: block;
}

.tree-icon__svg--minimal {
  color: var(--ide-muted);
}

.tree-icon__svg--minimal .tree-icon__folder {
  color: #c09553;
}
</style>
