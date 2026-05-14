<template>
  <div v-if="correction" class="rounded-2xl border border-gray-200 bg-white shadow-sm overflow-hidden">
    <div class="flex items-center gap-3 px-5 py-4 border-b border-gray-100 bg-slate-50">
      <div class="h-8 w-8 rounded-lg bg-emerald-100 flex items-center justify-center flex-shrink-0">
        <svg class="h-4 w-4 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </div>
      <div class="flex-1 min-w-0">
        <p class="text-sm font-semibold text-gray-900">校对结果对比</p>
        <p class="text-xs text-gray-500">{{ hasChanges ? '已标记修改内容' : '未发现需要修改的内容' }}</p>
      </div>
      <div v-if="hasChanges" class="flex items-center gap-3 flex-shrink-0">
        <span class="flex items-center gap-1 text-xs text-red-500">
          <svg class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M20 12H4" />
          </svg>
          删除
        </span>
        <span class="flex items-center gap-1 text-xs text-emerald-600">
          <svg class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          新增
        </span>
      </div>
    </div>

    <div class="p-5">
      <div class="text-sm leading-[1.8] whitespace-pre-wrap rounded-xl bg-slate-50 border border-gray-100 p-5 max-h-[400px] overflow-y-auto text-gray-800">
        <template v-for="(part, index) in diffParts">
          <span
            v-if="part.removed"
            :key="'rem-' + index"
            class="bg-red-50 text-red-600 line-through decoration-red-300 rounded-sm px-0.5"
          >{{ part.value }}</span>
          <span
            v-else-if="part.added"
            :key="'add-' + index"
            class="bg-emerald-50 text-emerald-700 font-medium rounded-sm px-0.5"
          >{{ part.value }}</span>
          <span 
            v-else 
            :key="'norm-' + index"
          >{{ part.value }}</span>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import { diffChars } from 'diff'

export default {
  name: 'DiffView',
  props: {
    original: {
      type: String,
      default: ''
    },
    correction: {
      type: String,
      default: ''
    }
  },
  computed: {
    cleanCorrection() {
      if (!this.correction) return ''
      // 去除可能残留的完整或不完整 XML 标签（流式传输可能导致标签截断，如 </cor）
      return this.correction
        .replace(/<\/?(?:t(?:h(?:i(?:n(?:k(?:ing)?)?)?)?)?|c(?:o(?:r(?:r(?:e(?:c(?:tion)?)?)?)?))|c(?:h(?:a(?:n(?:g(?:es?)?)?)?)?))/gi, '')
        .trim()
    },
    diffParts() {
      if (!this.cleanCorrection) return []
      return diffChars(this.original, this.cleanCorrection)
    },
    hasChanges() {
      return this.diffParts.some(p => p.added || p.removed)
    }
  }
}
</script>