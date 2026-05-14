<template>
  <div class="flex flex-col h-screen bg-slate-50">
    <!-- Header -->
    <AppHeader />

    <!-- Main Content -->
    <div class="flex-1 flex overflow-hidden">
      <!-- Left Panel: Input Area -->
      <div class="w-[480px] border-r border-gray-200 flex flex-col bg-white flex-shrink-0">
        <!-- Section Header -->
        <div class="flex items-center gap-2.5 px-5 py-4 border-b border-gray-100">
          <svg class="h-4 w-4 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
          </svg>
          <span class="text-sm font-semibold text-gray-900">输入区</span>
        </div>

        <!-- Content -->
        <div class="flex-1 flex flex-col p-5 gap-4 overflow-y-auto">
          <!-- File Upload -->
          <FileUpload
            :disabled="loading"
            @file-select="handleFileSelect"
            @error="handleUploadError"
          />

          <!-- Divider -->
          <div class="flex items-center gap-3">
            <div class="flex-1 h-px bg-gray-200"></div>
            <span class="text-xs text-gray-400">或</span>
            <div class="flex-1 h-px bg-gray-200"></div>
          </div>

          <!-- Text Input -->
          <TextInput
            :value="inputText"
            :disabled="loading"
            :max-length="MAX_LENGTH"
            @change="inputText = $event"
          />

          <!-- Error Display -->
          <div v-if="error" class="rounded-xl border border-red-200 bg-red-50 px-4 py-3">
            <p class="text-sm text-red-600">{{ error }}</p>
          </div>
        </div>

        <!-- Bottom Bar -->
        <div class="border-t border-gray-100 p-4">
          <button
            @click="handleProofread"
            :disabled="!canProofread"
            :aria-label="loading ? 'AI 正在校对中' : '开始校对'"
            :class="[
              'w-full h-11 rounded-xl font-medium text-white transition-all duration-200 flex items-center justify-center gap-2',
              loading
                ? 'bg-blue-400 cursor-not-allowed'
                : 'bg-blue-600 hover:bg-blue-700 shadow-sm shadow-blue-600/20 hover:shadow-md hover:shadow-blue-600/25 hover:-translate-y-px active:translate-y-0',
              !canProofread && !loading ? 'bg-gray-300 text-gray-500 shadow-none translate-y-0 cursor-not-allowed' : ''
            ]"
          >
            <template v-if="loading">
              <span>AI 正在校对中...</span>
            </template>
            <template v-else>
              <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
              </svg>
              <span>开始校对</span>
            </template>
          </button>
        </div>
      </div>

      <!-- Right Panel: Results Area -->
      <div class="flex-1 flex flex-col px-8 pt-4 pb-8 overflow-y-auto bg-slate-50">
        <!-- Section Header -->
        <div class="flex items-center gap-2.5 mb-6">
          <svg class="h-4 w-4 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
          </svg>
          <span class="text-sm font-semibold text-gray-900">校对结果</span>
        </div>

        <!-- Empty State -->
        <div v-if="!hasResults && !loading" class="flex-1 flex flex-col items-center justify-center text-center">
          <div class="h-16 w-16 rounded-2xl bg-blue-50 flex items-center justify-center mb-4">
            <svg class="h-8 w-8 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
            </svg>
          </div>
          <p class="text-sm text-gray-400">在左侧输入文本或上传文件</p>
          <p class="text-sm text-gray-400 mt-1">点击「开始校对」查看 AI 分析结果</p>
        </div>

        <!-- Loading State -->
        <div v-else-if="loading && !hasResults" class="flex-1 flex flex-col items-center justify-center text-center">
          <svg class="h-8 w-8 text-blue-500 animate-spin mb-4" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          <p class="text-sm text-gray-500">AI 正在分析文档...</p>
        </div>

        <!-- Results -->
        <div v-else class="space-y-4">
          <ThinkingPanel
            v-if="thinking"
            :thinking="thinking"
            :is-done="currentPhase !== 'thinking'"
          />
          <DiffView
            v-if="correction"
            :original="originalText"
            :correction="correction"
          />
          <ChangesList
            v-if="correction && currentPhase === 'done'"
            :changes="changes"
            :streaming-changes="streamingChanges"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import AppHeader from './components/AppHeader.vue'
import FileUpload from './components/FileUpload.vue'
import TextInput from './components/TextInput.vue'
import ThinkingPanel from './components/ThinkingPanel.vue'
import DiffView from './components/DiffView.vue'
import ChangesList from './components/ChangesList.vue'
import { parseFile, proofread } from './api'
import { parseSSEStream } from './utils/stream'

export default {
  name: 'App',
  components: {
    AppHeader,
    FileUpload,
    TextInput,
    ThinkingPanel,
    DiffView,
    ChangesList
  },
  data() {
    return {
      inputText: '',
      originalText: '',
      loading: false,
      error: null,
      thinking: '',
      correction: '',
      changes: [],
      streamingChanges: [],
      currentPhase: 'idle',
      abortController: null,
      MAX_LENGTH: 10000
    }
  },
  computed: {
    hasResults() {
      return this.currentPhase !== 'idle'
    },
    canProofread() {
      return this.inputText.trim().length > 0 && this.inputText.length <= this.MAX_LENGTH && !this.loading
    }
  },
  methods: {
    async handleFileSelect(file) {
      this.error = null
      try {
        const data = await parseFile(file)
        this.inputText = data.text
      } catch (err) {
        this.error = err.response?.data?.error || err.message || '文件解析失败'
      }
    },

    handleUploadError(message) {
      this.error = message
    },

    async handleProofread() {
      if (!this.canProofread) return

      // Abort previous request
      if (this.abortController) {
        this.abortController.abort()
      }

      this.abortController = new AbortController()
      this.loading = true
      this.error = null
      this.originalText = this.inputText
      this.thinking = ''
      this.correction = ''
      this.changes = []
      this.streamingChanges = []
      this.currentPhase = 'thinking'

      try {
        const response = await proofread(this.inputText, this.abortController.signal)

        await parseSSEStream(response, {
          onThinking: (data) => {
            this.thinking = data.text
            if (data.done) {
              this.currentPhase = 'correction'
            }
          },
          onCorrection: (data) => {
            this.correction = data.text
            if (data.done) {
              this.currentPhase = 'changes'
            }
          },
          onChange: (data) => {
            // 流式接收单条 change
            if (data.change) {
              this.streamingChanges.push(data.change)
            }
          },
          onChanges: (data) => {
            // 接收完整的 changes 数组（流式结束时）
            if (data.changes) {
              this.changes = data.changes
            }
            if (data.done) {
              this.currentPhase = 'done'
            }
          },
          onDone: () => {
            this.currentPhase = 'done'
          },
          onError: (errorMsg) => {
            this.error = errorMsg
          }
        })
      } catch (err) {
        if (err.name !== 'AbortError') {
          this.error = err.message || '校对过程中发生错误'
        }
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
