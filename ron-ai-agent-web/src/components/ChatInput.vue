<script setup lang="ts">

import {
  ref,
  nextTick
} from 'vue'


defineProps<{

  loading: boolean

}>()


const emit =
    defineEmits<{

      send: [message: string]

      stop: []

    }>()


const text =
    ref('')


const textarea =
    ref<HTMLTextAreaElement>()


function send() {

  const content =
      text.value.trim()


  if (!content) {
    return
  }


  emit(
      'send',
      content
  )


  text.value = ''


  nextTick(() => {
    resize()
  })
}


function handleKeydown(
    event: KeyboardEvent
) {

  if (
      event.key === 'Enter'
      &&
      !event.shiftKey
  ) {

    event.preventDefault()

    send()
  }
}


function resize() {

  const el =
      textarea.value


  if (!el) {
    return
  }


  el.style.height =
      'auto'


  el.style.height =
      Math.min(
          el.scrollHeight,
          180
      )
      + 'px'
}

</script>


<template>

  <div class="input-area">

    <div class="input-box">

      <textarea
          ref="textarea"

          v-model="text"

          placeholder="
          给 Ron AI 发送消息...
        "

          rows="1"

          :disabled="loading"

          @input="resize"

          @keydown="handleKeydown"
      />


      <button
          v-if="!loading"

          class="send-button"

          :disabled="
          !text.trim()
        "

          @click="send"
      >
        ↑
      </button>


      <button
          v-else

          class="stop-button"

          @click="
          emit('stop')
        "
      >
        ■
      </button>

    </div>


    <div class="input-tip">

      Enter 发送 · Shift + Enter 换行

    </div>

  </div>

</template>