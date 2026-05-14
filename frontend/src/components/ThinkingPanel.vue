<template>
  <div class="rounded-2xl border border-amber-200/60 bg-white shadow-sm overflow-hidden">
    <!-- Header (clickable to toggle) -->
    <button
      @click="open = !open"
      class="w-full flex items-center gap-3 px-5 py-4 bg-amber-50/50 transition-colors text-left"
    >
      <div class="h-8 w-8 rounded-lg bg-amber-100 flex items-center justify-center flex-shrink-0">
        <svg class="h-4 w-4 text-amber-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
        </svg>
      </div>
      <div class="flex-1 min-w-0">
        <p class="text-sm font-semibold text-gray-900">AI 思考过程</p>
        <p class="text-xs text-gray-500">文档分析与校验规则推演</p>
      </div>
      <!-- Loading spinner when not done -->
      <svg v-if="!isDone" class="h-4 w-4 text-amber-500 animate-spin flex-shrink-0" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
      <!-- Chevron -->
      <svg
        :class="['h-4 w-4 text-gray-400 transition-transform duration-200 flex-shrink-0', open ? 'rotate-180' : '']"
        fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"
      >
        <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
      </svg>
    </button>

    <!-- Content -->
    <transition name="collapse">
      <div v-show="open" class="px-5 pb-5 pt-1">
        <div class="rounded-xl bg-slate-50 border border-gray-100 p-4 space-y-4">
          <!-- 文档体裁分析 -->
          <div v-if="parsed.genre">
            <p class="text-xs font-semibold text-amber-700 mb-1">文档体裁分析</p>
            <p class="text-sm leading-relaxed text-gray-700">{{ parsed.genre }}</p>
          </div>
          <!-- 目标受众 -->
          <div v-if="parsed.audience">
            <p class="text-xs font-semibold text-amber-700 mb-1">目标受众</p>
            <p class="text-sm leading-relaxed text-gray-700">{{ parsed.audience }}</p>
          </div>
          <!-- 正式程度 -->
          <div v-if="parsed.formality">
            <p class="text-xs font-semibold text-amber-700 mb-1">正式程度</p>
            <p class="text-sm leading-relaxed text-gray-700">{{ parsed.formality }}</p>
          </div>
          <!-- 校验规则 -->
          <div v-if="parsed.rules.length > 0">
            <p class="text-xs font-semibold text-amber-700 mb-2">校验规则</p>
            <ol class="space-y-2">
              <li v-for="(rule, i) in parsed.rules" :key="i" class="text-sm leading-relaxed text-gray-700 flex gap-2">
                <span class="text-amber-500 font-medium flex-shrink-0">{{ i + 1 }}.</span>
                <span>{{ rule }}</span>
              </li>
            </ol>
          </div>
          <!-- 原始文本兜底（如果解析失败） -->
          <div v-if="!parsed.genre && !parsed.audience && !parsed.formality && parsed.rules.length === 0">
            <p class="text-sm leading-relaxed whitespace-pre-wrap text-gray-700">{{ thinking }}</p>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
export default {
  name: 'ThinkingPanel',
  props: {
    thinking: {
      type: String,
      default: ''
    },
    isDone: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      open: true
    }
  },
  watch: {
    isDone(val) {
      if (val) this.open = false
    }
  },
  computed: {
    parsed() {
      if (!this.thinking) return { genre: '', audience: '', formality: '', rules: [] }

      const text = this.thinking
      const result = { genre: '', audience: '', formality: '', rules: [] }

      // 提取文档体裁分析
      const genreMatch = text.match(/文档体裁分析[：:]\s*([\s\S]*?)(?=目标受众|正式程度|校验规则|$)/)
      if (genreMatch) result.genre = genreMatch[1].trim()

      // 提取目标受众
      const audienceMatch = text.match(/目标受众[：:]\s*([\s\S]*?)(?=正式程度|校验规则|$)/)
      if (audienceMatch) result.audience = audienceMatch[1].trim()

      // 提取正式程度
      const formalityMatch = text.match(/正式程度[：:]\s*([\s\S]*?)(?=校验规则|$)/)
      if (formalityMatch) result.formality = formalityMatch[1].trim()

      // 提取校验规则
      const rulesMatch = text.match(/校验规则[：:]\s*([\s\S]*)/)
      if (rulesMatch) {
        const rulesText = rulesMatch[1].trim()
        // 匹配 "1. xxx" 或 "1、xxx" 格式的规则
        const ruleRegex = /(?:^|\n)\s*\d+[.、]\s*(.+?)(?=\n\s*\d+[.、]|$)/gs
        let match
        while ((match = ruleRegex.exec(rulesText)) !== null) {
          const rule = match[1].trim()
          if (rule) result.rules.push(rule)
        }
      }

      return result
    }
  }
}
</script>

<style scoped>
.collapse-enter-active {
  transition: opacity 0.2s ease;
}
.collapse-leave-active {
  transition: opacity 0.15s ease;
}
.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
}
</style>
