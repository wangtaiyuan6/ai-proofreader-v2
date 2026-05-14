<template>
  <div class="rounded-2xl border border-gray-200 bg-white shadow-sm overflow-hidden">
    <!-- Header -->
    <div class="flex items-center gap-3 px-5 py-4 border-b border-gray-100 bg-slate-50">
      <div class="h-8 w-8 rounded-lg bg-blue-100 flex items-center justify-center flex-shrink-0">
        <svg class="h-4 w-4 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
        </svg>
      </div>
      <div class="flex-1 min-w-0">
        <p class="text-sm font-semibold text-gray-900">修改记录</p>
        <p class="text-xs text-gray-500">{{ changes && changes.length > 0 ? '详细的修改内容与理由' : '未发现需要修改的内容' }}</p>
      </div>
      <span v-if="changes && changes.length > 0" class="inline-flex items-center h-5 px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200">
        {{ changes.length }} 处修改
      </span>
    </div>

    <!-- Body -->
    <div class="p-5">
      <!-- Empty State -->
      <div v-if="!changes || changes.length === 0" class="flex items-center justify-center py-8">
        <div class="text-center">
          <svg class="h-10 w-10 text-gray-300 mx-auto mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <p class="text-sm text-gray-400">文档内容无需修改</p>
        </div>
      </div>
      <!-- Changes List -->
      <div v-else class="space-y-3 max-h-[400px] overflow-y-auto">
        <transition-group name="change-item" tag="div" class="space-y-3">
        <div
          v-for="(change, index) in visibleChanges"
          :key="index"
          class="group rounded-xl border border-gray-100 bg-slate-50/50 hover:bg-slate-50 p-4 transition-colors duration-150"
        >
          <div class="flex items-start gap-3 text-sm">
            <!-- Original -->
            <span class="inline-flex items-center gap-1 text-red-600 bg-red-50 rounded-md px-2 py-0.5 text-xs font-medium line-through">
              <svg class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M20 12H4" />
              </svg>
              {{ change.original }}
            </span>
            <!-- Arrow -->
            <svg class="h-3.5 w-3.5 text-gray-400 flex-shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
            </svg>
            <!-- Corrected -->
            <span class="inline-flex items-center gap-1 text-emerald-600 bg-emerald-50 rounded-md px-2 py-0.5 text-xs font-medium">
              <svg class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
              </svg>
              {{ change.corrected }}
            </span>
          </div>
          <!-- Reason -->
          <p class="text-xs text-gray-500 mt-2 leading-relaxed">{{ change.reason }}</p>
        </div>
        </transition-group>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ChangesList',
  props: {
    changes: {
      type: Array,
      default: () => []
    },
    streamingChanges: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      visibleCount: 0,
      timer: null
    }
  },
  computed: {
    visibleChanges() {
      // 优先使用流式接收的 changes
      if (this.streamingChanges && this.streamingChanges.length > 0) {
        return this.streamingChanges
      }
      return this.changes.slice(0, this.visibleCount)
    }
  },
  watch: {
    changes: {
      handler(val) {
        this.stopStream()
        if (val && val.length > 0) {
          this.visibleCount = 0
          this.startStream()
        }
      },
      immediate: true
    }
  },
  beforeUnmount() {
    this.stopStream()
  },
  methods: {
    startStream() {
      this.timer = setInterval(() => {
        if (this.visibleCount < this.changes.length) {
          this.visibleCount++
        } else {
          this.stopStream()
        }
      }, 300)
    },
    stopStream() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    }
  }
}
</script>

<style scoped>
.change-item-enter-active {
  transition: all 0.4s ease;
}
.change-item-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
</style>
