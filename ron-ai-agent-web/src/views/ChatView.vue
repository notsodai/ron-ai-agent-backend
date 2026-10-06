<script setup lang="ts">

import {
  computed,
  nextTick,
  onMounted,
  ref
} from 'vue'


import ChatSidebar
  from '../components/ChatSidebar.vue'

import ChatMessage
  from '../components/ChatMessage.vue'

import ChatInput
  from '../components/ChatInput.vue'


import {
  useChatStore
} from '../stores/chat'


import {
  streamChat
} from '../api/chat'


import type {
  AgentEvent,
  ChatMessage as ChatMessageType
} from '../types/chat'


const chatStore =
    useChatStore()


const loading =
    ref(false)


const statusText =
    ref('')


const toolText =
    ref('')


const messageContainer =
    ref<HTMLDivElement>()


let abortController:
    AbortController | null = null


const currentConversation =
    computed(() =>
        chatStore.currentConversation
    )


onMounted(() => {

  chatStore.load()

  scrollToBottom()
})


async function sendMessage(
    text: string
) {

  if (loading.value) {
    return
  }


  let conversation =
      currentConversation.value


  if (!conversation) {

    conversation =
        chatStore.createConversation()
  }


  const localConversationId =
      conversation.id


  /*
   * 用户消息
   */
  const userMessage:
      ChatMessageType = {

    id: crypto.randomUUID(),

    role: 'user',

    content: text,

    createdAt: Date.now()
  }


  chatStore.addMessage(
      localConversationId,
      userMessage
  )


  /*
   * 先创建空 AI 消息。
   *
   * ANSWER 到来后再更新内容。
   */
  const assistantMessage:
      ChatMessageType  = {

    id: crypto.randomUUID(),

    role: 'assistant',

    content: '',

    createdAt: Date.now()
  }


  chatStore.addMessage(
      localConversationId,
      assistantMessage
  )


  loading.value = true

  statusText.value =
      'Ron AI 正在思考...'

  toolText.value = ''


  abortController =
      new AbortController()


  await scrollToBottom()


  try {

    await streamChat({

      request: {

        conversationId:
            conversation.backendId ?? '',

        message: text
      },


      signal:
      abortController.signal,


      onConversationId(
          backendId
      ) {

        chatStore.setBackendId(
            localConversationId,
            backendId
        )
      },


      onEvent(event) {

        handleAgentEvent(
            event,

            localConversationId,

            assistantMessage.id
        )
      }
    })


  } catch (error) {

    if (
        error instanceof DOMException
        &&
        error.name === 'AbortError'
    ) {

      statusText.value =
          '已停止生成'

      return
    }


    console.error(
        error
    )


    const errorMessage =

        error instanceof Error
            ? error.message
            : '请求失败'


    chatStore.updateMessage(
        localConversationId,
        assistantMessage.id,
        `请求失败：${errorMessage}`
    )


    statusText.value =
        '发生错误'


  } finally {

    loading.value = false

    abortController = null

    await scrollToBottom()
  }
}


function handleAgentEvent(
    event: AgentEvent,
    conversationId: string,
    assistantMessageId: string
) {

  console.log(
      '[AgentEvent]',
      event
  )


  switch (
      event.type
      ) {

    case 'STATUS':

      statusText.value =
          event.content
          || '正在处理...'

      break


    case 'TOOL_START':

      toolText.value =
          event.tool
              ? `正在调用工具：${event.tool}`
              : '正在调用工具...'

      statusText.value =
          event.content
          || '正在执行工具'

      break


    case 'TOOL_RESULT':

      toolText.value =
          event.tool
              ? `工具执行完成：${event.tool}`
              : '工具执行完成'

      statusText.value =
          event.content
          || '工具执行完成'

      break


    case 'ANSWER':

      if (
          event.content
      ) {

        /*
         * 当前后端一次发送完整 ANSWER。
         *
         * 所以这里直接赋值。
         *
         * 后续升级 Token Streaming 时，
         * 再改成 append。
         */
        chatStore.updateMessage(
            conversationId,
            assistantMessageId,
            event.content
        )
      }

      statusText.value = ''

      break


    case 'ERROR':

      chatStore.updateMessage(
          conversationId,
          assistantMessageId,

          event.content
          || 'Agent 执行失败'
      )

      statusText.value =
          '执行失败'

      break


    case 'DONE':

      statusText.value = ''

      toolText.value = ''

      loading.value = false

      break
  }


  scrollToBottom()
}


function stopGeneration() {

  abortController?.abort()

  abortController = null

  loading.value = false

  statusText.value =
      '已停止生成'
}


async function scrollToBottom() {

  await nextTick()


  const el =
      messageContainer.value


  if (!el) {
    return
  }


  el.scrollTop =
      el.scrollHeight
}

</script>


<template>

  <div class="app-layout">

    <ChatSidebar />


    <main class="chat-main">

      <header class="chat-header">

        <div>

          <h2>
            {{
              currentConversation
                  ?.title
              || 'Ron AI Assistant'
            }}
          </h2>

          <p>
            RonManus · ReAct Agent
          </p>

        </div>


        <div
            class="online-status"
        >
          <span
              class="online-dot"
          />

          Online

        </div>

      </header>


      <div
          ref="messageContainer"
          class="messages-container"
      >

        <div
            v-if="
            !currentConversation
            ||
            currentConversation
              .messages.length === 0
          "
            class="welcome"
        >

          <div
              class="welcome-logo"
          >
            R
          </div>


          <h1>
            Ron AI Assistant
          </h1>


          <p>
            基于 Spring AI 的
            ReAct 智能助手
          </p>


          <div
              class="suggestions"
          >

            <div>
              帮我解释一下 Spring AI
            </div>

            <div>
              帮我分析一段 Java 代码
            </div>

            <div>
              搜索最新的 AI 新闻
            </div>

          </div>

        </div>


        <template
            v-else
        >

          <ChatMessage

              v-for="
              message
              in currentConversation.messages
            "

              :key="
              message.id
            "

              :message="
              message
            "

          />

        </template>


        <div
            v-if="
            loading
            || statusText
            || toolText
          "
            class="agent-status"
        >

          <div
              v-if="loading"
              class="thinking-dot"
          >
            <span />
            <span />
            <span />
          </div>


          <span>
            {{ statusText }}
          </span>


          <span
              v-if="toolText"
              class="tool-status"
          >
            {{ toolText }}
          </span>

        </div>

      </div>


      <ChatInput

          :loading="loading"

          @send="
          sendMessage
        "

          @stop="
          stopGeneration
        "

      />

    </main>

  </div>

</template>