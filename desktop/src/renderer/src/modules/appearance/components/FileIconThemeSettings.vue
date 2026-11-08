<script setup lang="ts">
import FileIconThemePicker from '@renderer/modules/appearance/components/FileIconThemePicker.vue'
import '@renderer/modules/settings/settings.css'
</script>

<template>
  <section class="settings-block">
    <h2>文件图标主题</h2>
    <p class="settings-block__hint">资源管理器中的文件/文件夹图标样式。也可通过菜单栏「文件图标」快速切换。</p>
    <FileIconThemePicker />
  </section>
</template>
