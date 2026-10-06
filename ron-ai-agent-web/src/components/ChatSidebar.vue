<script setup lang="ts">

import {
  useChatStore
} from '../stores/chat'


const chatStore =
    useChatStore()


function createChat() {

  chatStore
      .createConversation()
}


function selectChat(
    id: string
) {

  chatStore
      .selectConversation(id)
}


function deleteChat(
    id: string
) {

  const confirmed =
      window.confirm(
          '确定删除这个会话吗？'
      )


  if (!confirmed) {
    return
  }


  chatStore
      .deleteConversation(id)
}

</script>


<template>

  <aside class="sidebar">

    <div class="sidebar-header">

      <div class="brand">

        <div class="brand-icon">
          R
        </div>

        <div>

          <div class="brand-name">
            Ron AI
          </div>

          <div class="brand-subtitle">
            Agent Assistant
          </div>

        </div>

      </div>


      <button
          class="new-chat-button"
          @click="createChat"
      >
        ＋ 新建对话
      </button>

    </div>


    <div class="conversation-list">

      <div
          v-for="conversation
          in chatStore.conversations"

          :key="conversation.id"

          class="conversation-item"

          :class="{
          active:
            conversation.id ===
            chatStore.currentConversationId
        }"

          @click="
          selectChat(
            conversation.id
          )
        "
      >

        <div
            class="conversation-title"
        >
          {{ conversation.title }}
        </div>


        <button
            class="delete-button"

            @click.stop="
            deleteChat(
              conversation.id
            )
          "
        >
          ×
        </button>

      </div>

    </div>


    <div class="sidebar-footer">

      <div>
        Ron AI Assistant
      </div>

      <small>
        Powered by Spring AI
      </small>

    </div>

  </aside>

</template>