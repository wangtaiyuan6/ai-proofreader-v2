<template>
  <div class="flex flex-col flex-1">
    <textarea
      :value="value"
      @input="$emit('change', $event.target.value)"
      :disabled="disabled"
      :maxlength="maxLength"
      aria-label="需要校对的文本"
      placeholder="在此粘贴或输入需要校对的文本..."
      :class="[
        'min-h-[200px] resize-none flex-1 w-full rounded-xl border bg-white shadow-sm text-sm leading-relaxed placeholder:text-gray-400 text-gray-800 px-4 py-3 outline-none transition-all duration-200 hover:shadow-md focus:ring-2 disabled:cursor-not-allowed disabled:opacity-50',
        isOverLimit
          ? 'border-red-300 hover:border-red-400 focus:border-red-500 focus:ring-red-500/30'
          : 'border-gray-200 hover:border-gray-300 focus:border-blue-500 focus:ring-blue-500/30'
      ]"
    ></textarea>
    <div class="flex justify-end mt-1.5 px-1">
      <span :class="['text-xs', isOverLimit ? 'text-red-500 font-medium' : 'text-gray-400']">
        {{ value.length }} / {{ maxLength }}
      </span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TextInput',
  props: {
    value: {
      type: String,
      default: ''
    },
    disabled: {
      type: Boolean,
      default: false
    },
    maxLength: {
      type: Number,
      default: 10000
    }
  },
  computed: {
    isOverLimit() {
      return this.value.length > this.maxLength
    }
  }
}
</script>
