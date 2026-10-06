<script setup lang="ts">

import {
  computed
} from 'vue'

import MarkdownIt
  from 'markdown-it'

import hljs
  from 'highlight.js'

import type {
  ChatMessage as ChatMessageType
} from '../types/chat'


const props =
    defineProps<{
      message: ChatMessageType
    }>()


/**
 * HTML 转义。
 *
 * 不再在 MarkdownIt 初始化过程中
 * 使用 md.utils.escapeHtml，
 * 避免 TypeScript 循环类型推断。
 */
function escapeHtml(
    text: string
): string {

  return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;')
}


/**
 * Markdown 渲染器
 */
const md: MarkdownIt =
    new MarkdownIt({

      html: false,

      linkify: true,

      breaks: true,

      highlight(
          str: string,
          lang: string
      ): string {

        /*
         * 指定了合法语言时，
         * 使用 highlight.js。
         */
        if (
            lang &&
            hljs.getLanguage(lang)
        ) {

          try {

            const highlighted =
                hljs.highlight(
                    str,
                    {
                      language: lang
                    }
                ).value


            return (
                '<pre class="hljs"><code>'
                +
                highlighted
                +
                '</code></pre>'
            )

          } catch (error) {

            console.warn(
                'Code highlight failed:',
                error
            )
          }
        }


        /*
         * 无法识别语言时，
         * 使用普通代码块。
         */
        return (
            '<pre class="hljs"><code>'
            +
            escapeHtml(str)
            +
            '</code></pre>'
        )
      }
    })


/**
 * 将消息转换成 HTML
 */
const htmlContent =
    computed<string>(() => {

      /*
       * 用户消息暂时不解析 Markdown，
       * 只做安全转义。
       */
      if (
          props.message.role
          === 'user'
      ) {

        return escapeHtml(
            props.message.content
        )
      }


      /*
       * AI 消息支持 Markdown。
       */
      return md.render(
          props.message.content
      )
    })


/**
 * 复制 AI 回答
 */
async function copyMessage() {

  try {

    await navigator.clipboard
        .writeText(
            props.message.content
        )

  } catch (error) {

    console.error(
        'Copy failed:',
        error
    )
  }
}

</script>


<template>

  <div
      class="message-row"

      :class="
      message.role
    "
  >

    <div
        v-if="
        message.role === 'assistant'
      "
        class="avatar assistant-avatar"
    >
      R
    </div>


    <div class="message-body">

      <div class="message-header">

        {{
          message.role === 'user'
              ? '你'
              : 'Ron AI'
        }}

      </div>


      <div
          class="message-content"
          v-html="htmlContent"
      />


      <button
          v-if="
          message.role === 'assistant'
          && message.content
        "
          class="copy-button"
          @click="copyMessage"
      >
        复制
      </button>

    </div>


    <div
        v-if="
        message.role === 'user'
      "
        class="avatar user-avatar"
    >
      你
    </div>

  </div>

</template>