<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { systemHelpSections } from '@renderer/content/systemHelp'
import {
  PRODUCT_COPYRIGHT,
  PRODUCT_WEBSITE,
  productContactLine,
} from '@renderer/constants/productInfo'

const router = useRouter()
const desktopApi = window.desktopApi
const activeId = ref(systemHelpSections[0]?.id ?? 'overview')

function scrollTo(id: string) {
  activeId.value = id
  document.getElementById(`help-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function backToWorkspace() {
  void router.push('/workspace')
}
</script>

<template>
  <div class="help-page">
    <header class="help-page__header">
      <div>
        <h1>系统帮助文档</h1>
        <p class="help-page__subtitle">Agent Desktop 使用说明</p>
      </div>
      <button type="button" class="help-page__back" @click="backToWorkspace">返回工作区</button>
    </header>

    <div class="help-page__layout">
      <nav class="help-page__nav" aria-label="帮助目录">
        <button
          v-for="section in systemHelpSections"
          :key="section.id"
          type="button"
          class="help-page__nav-item"
          :class="{ active: activeId === section.id }"
          @click="scrollTo(section.id)"
        >
          {{ section.title }}
        </button>
      </nav>

      <article class="help-page__content">
        <section
          v-for="section in systemHelpSections"
          :id="`help-${section.id}`"
          :key="section.id"
          class="help-section"
        >
          <h2>{{ section.title }}</h2>
          <p v-for="(para, idx) in section.paragraphs" :key="idx" class="help-section__para">
            {{ para }}
          </p>
          <ul v-if="section.items?.length" class="help-section__list">
            <li v-for="(item, idx) in section.items" :key="idx">{{ item }}</li>
          </ul>
          <div v-for="sub in section.subsections" :key="sub.title" class="help-subsection">
            <h3>{{ sub.title }}</h3>
            <ul class="help-section__list">
              <li v-for="(line, idx) in sub.body" :key="idx">{{ line }}</li>
            </ul>
          </div>
        </section>

        <footer class="help-page__footer">
          <a
            class="help-page__link"
            href="#"
            @click.prevent="void desktopApi.openExternal(PRODUCT_WEBSITE)"
          >
            {{ PRODUCT_WEBSITE }}
          </a>
          <p class="help-page__meta">{{ productContactLine }}</p>
          <p class="help-page__meta">{{ PRODUCT_COPYRIGHT }}</p>
        </footer>
      </article>
    </div>
  </div>
</template>

<style scoped>
.help-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--ide-editor-bg);
  color: var(--ide-fg);
}

.help-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px 12px;
  border-bottom: 1px solid var(--ide-border);
  flex-shrink: 0;
}

.help-page__header h1 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.help-page__subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--ide-muted);
}

.help-page__back {
  padding: 6px 12px;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--ide-fg);
  font-size: 12px;
  cursor: pointer;
}

.help-page__back:hover {
  background: rgba(255, 255, 255, 0.08);
}

.help-page__layout {
  display: flex;
  flex: 1;
  min-height: 0;
}

.help-page__nav {
  width: 180px;
  flex-shrink: 0;
  padding: 12px;
  border-right: 1px solid var(--ide-border);
  overflow: auto;
}

.help-page__nav-item {
  display: block;
  width: 100%;
  padding: 8px 10px;
  margin-bottom: 2px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-muted);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.help-page__nav-item:hover,
.help-page__nav-item.active {
  background: rgba(255, 255, 255, 0.06);
  color: var(--ide-fg);
}

.help-page__content {
  flex: 1;
  min-width: 0;
  overflow: auto;
  padding: 16px 28px 32px;
}

.help-section {
  margin-bottom: 32px;
  scroll-margin-top: 12px;
}

.help-section h2 {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--ide-accent);
}

.help-section__para {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--ide-fg);
}

.help-subsection {
  margin-top: 16px;
}

.help-subsection h3 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ide-fg);
}

.help-section__list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--ide-fg);
}

.help-section__list li + li {
  margin-top: 4px;
}

.help-page__footer {
  margin-top: 40px;
  padding-top: 16px;
  border-top: 1px solid var(--ide-border);
}

.help-page__link {
  color: var(--ide-accent);
  font-size: 13px;
  text-decoration: none;
}

.help-page__link:hover {
  text-decoration: underline;
}

.help-page__meta {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--ide-muted);
}
</style>
