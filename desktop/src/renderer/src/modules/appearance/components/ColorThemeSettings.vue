<script setup lang="ts">
import ColorThemePicker from '@renderer/modules/appearance/components/ColorThemePicker.vue'
import '@renderer/modules/settings/settings.css'
</script>

<template>
  <section class="settings-block">
    <h2>颜色主题</h2>
    <p class="settings-block__hint">切换 IDE 整体配色（亮色 / 暗色）。也可通过菜单栏「颜色主题」快速切换。</p>
    <ColorThemePicker compact />
  </section>
</template>
