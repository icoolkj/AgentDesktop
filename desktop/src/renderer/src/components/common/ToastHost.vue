<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useToastStore } from '@renderer/stores/toastStore'

const toast = useToastStore()
const { message } = storeToRefs(toast)
</script>

<template>
  <Teleport to="body">
    <div v-if="message" class="toast-host" role="status" @click="toast.dismiss()">
      {{ message }}
    </div>
  </Teleport>
</template>

<style scoped>
.toast-host {
  position: fixed;
  left: 50%;
  bottom: 28px;
  z-index: 600;
  max-width: min(420px, calc(100vw - 32px));
  padding: 10px 16px;
  border: 1px solid color-mix(in srgb, var(--ide-error) 35%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--ide-error) 12%, var(--ide-surface-bg));
  color: var(--ide-error);
  font-size: 13px;
  line-height: 1.5;
  text-align: center;
  box-shadow: var(--ide-shadow);
  transform: translateX(-50%);
  cursor: pointer;
}
</style>
