<template>
  <div>
    <!-- File selected state -->
    <div v-if="selectedFile" class="flex items-center gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3.5 shadow-sm">
      <div class="h-9 w-9 rounded-lg bg-blue-50 flex items-center justify-center">
        <svg class="h-5 w-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      </div>
      <div class="flex-1 min-w-0">
        <p class="text-sm font-medium text-gray-900 truncate">{{ selectedFile.name }}</p>
        <p class="text-xs text-gray-500">{{ formatFileSize(selectedFile.size) }}</p>
      </div>
      <button
        @click="clearFile"
        class="h-7 w-7 rounded-md flex items-center justify-center text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
      >
        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>

    <!-- Drop zone state -->
    <div
      v-else
      @dragenter.prevent="handleDragEnter"
      @dragover.prevent
      @dragleave.prevent="handleDragLeave"
      @drop.prevent="handleDrop"
      @click="triggerFileInput"
      :class="[
        'relative flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed p-8 cursor-pointer transition-all duration-200',
        dragActive
          ? 'border-blue-400 bg-blue-50 shadow-sm'
          : 'border-gray-200 bg-slate-50 hover:border-blue-300 hover:bg-blue-50/50',
        disabled ? 'opacity-50 cursor-not-allowed' : ''
      ]"
    >
      <div :class="[
        'h-12 w-12 rounded-xl flex items-center justify-center transition-colors',
        dragActive ? 'bg-blue-100 text-blue-600' : 'bg-blue-50 text-blue-500'
      ]">
        <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
        </svg>
      </div>
      <div class="text-center">
        <p class="text-sm text-gray-600">
          拖拽文件到此处，或
          <span class="text-blue-600 font-medium">点击选择</span>
        </p>
        <p class="text-xs text-gray-400 mt-1">支持 .txt、.docx、.pdf 格式，最大 20MB</p>
      </div>
      <input
        ref="fileInput"
        type="file"
        accept=".txt,.docx,.pdf"
        @change="handleChange"
        :disabled="disabled"
        aria-label="选择文件上传"
        class="hidden"
      />
    </div>
  </div>
</template>

<script>
const MAX_FILE_SIZE = 20 * 1024 * 1024

export default {
  name: 'FileUpload',
  props: {
    disabled: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      dragActive: false,
      selectedFile: null
    }
  },
  methods: {
    formatFileSize(bytes) {
      if (bytes === 0) return '0 B'
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
    },

    validateAndSelect(file) {
      if (file.size > MAX_FILE_SIZE) {
        this.$emit('error', `文件过大（${this.formatFileSize(file.size)}），最大支持 20MB`)
        return
      }
      this.selectedFile = file
      this.$emit('file-select', file)
    },

    handleDragEnter() {
      this.dragActive = true
    },

    handleDragLeave(e) {
      // Only set false if we're leaving the label element
      if (!e.currentTarget.contains(e.relatedTarget)) {
        this.dragActive = false
      }
    },

    handleDrop(e) {
      this.dragActive = false
      const file = e.dataTransfer.files[0]
      if (file) {
        this.validateAndSelect(file)
      }
    },

    handleChange(e) {
      const file = e.target.files[0]
      if (file) {
        this.validateAndSelect(file)
      }
      // Reset input so same file can be selected again
      e.target.value = ''
    },

    clearFile() {
      this.selectedFile = null
    },

    triggerFileInput() {
      if (!this.disabled) {
        this.$refs.fileInput.click()
      }
    }
  }
}
</script>
