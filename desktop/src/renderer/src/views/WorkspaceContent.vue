<script setup lang="ts">
import EditorPanel from '@renderer/components/editor/EditorPanel.vue'

defineOptions({ name: 'WorkspaceContent' })
</script>

<template>
  <div class="workspace-content">
    <EditorPanel />
  </div>
</template>

<style scoped>
.workspace-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
