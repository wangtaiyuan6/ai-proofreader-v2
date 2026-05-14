<template>
  <header class="sticky top-0 z-50 border-b border-gray-200 bg-white h-14 flex items-center px-6">
    <div class="flex items-center gap-3">
      <div class="h-8 w-8 rounded-lg bg-blue-600 flex items-center justify-center">
        <svg class="h-4 w-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
        </svg>
      </div>
      <span class="text-base font-semibold text-gray-900">AI 文档校对</span>
    </div>
  </header>
</template>

<script>
export default {
  name: 'AppHeader'
}
</script>
