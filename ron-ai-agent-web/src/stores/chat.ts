import {
    computed,
    ref
} from 'vue'

import {
    defineStore
} from 'pinia'

import type {
    ChatMessage,
    Conversation
} from '../types/chat'


const STORAGE_KEY =
    'ron-ai-conversations'


export const useChatStore =
    defineStore(
        'chat',
        () => {

            const conversations =
                ref<Conversation[]>([])


            const currentConversationId =
                ref<string>('')


            /*
             * 当前会话
             */
            const currentConversation =
                computed(() => {

                    return conversations
                        .value
                        .find(
                            item =>
                                item.id ===
                                currentConversationId.value
                        )

                })


            /*
             * 从 localStorage 恢复
             */
            function load() {

                const raw =
                    localStorage.getItem(
                        STORAGE_KEY
                    )


                if (raw) {

                    try {

                        conversations.value =
                            JSON.parse(raw)

                    } catch {

                        conversations.value = []
                    }
                }


                if (
                    conversations.value.length
                    === 0
                ) {

                    createConversation()

                } else {

                    currentConversationId.value =
                        conversations
                            .value[0]
                            .id
                }
            }


            /*
             * 保存
             */
            function persist() {

                localStorage.setItem(
                    STORAGE_KEY,
                    JSON.stringify(
                        conversations.value
                    )
                )
            }


            /*
             * 新建会话
             */
            function createConversation() {

                const now =
                    Date.now()


                const conversation:
                    Conversation = {

                    id: crypto.randomUUID(),

                    title: '新对话',

                    messages: [],

                    createdAt: now,

                    updatedAt: now
                }


                conversations.value.unshift(
                    conversation
                )


                currentConversationId.value =
                    conversation.id


                persist()


                return conversation
            }


            /*
             * 切换会话
             */
            function selectConversation(
                id: string
            ) {

                currentConversationId.value =
                    id
            }


            /*
             * 删除会话
             */
            function deleteConversation(
                id: string
            ) {

                conversations.value =
                    conversations
                        .value
                        .filter(
                            item =>
                                item.id !== id
                        )


                if (
                    conversations.value.length
                    === 0
                ) {

                    createConversation()

                    return
                }


                if (
                    currentConversationId.value
                    === id
                ) {

                    currentConversationId.value =
                        conversations
                            .value[0]
                            .id
                }


                persist()
            }


            /*
             * 添加消息
             */
            function addMessage(
                conversationId: string,
                message: ChatMessage
            ) {

                const conversation =
                    conversations
                        .value
                        .find(
                            item =>
                                item.id ===
                                conversationId
                        )


                if (!conversation) {
                    return
                }


                conversation.messages.push(
                    message
                )


                conversation.updatedAt =
                    Date.now()


                /*
                 * 第一条用户消息作为标题
                 */
                if (
                    conversation.title
                    === '新对话'
                    &&
                    message.role
                    === 'user'
                ) {

                    conversation.title =
                        createTitle(
                            message.content
                        )
                }


                persist()
            }


            /*
             * 更新消息
             */
            function updateMessage(
                conversationId: string,
                messageId: string,
                content: string
            ) {

                const conversation =
                    conversations
                        .value
                        .find(
                            item =>
                                item.id ===
                                conversationId
                        )


                if (!conversation) {
                    return
                }


                const message =
                    conversation.messages
                        .find(
                            item =>
                                item.id ===
                                messageId
                        )


                if (!message) {
                    return
                }


                message.content =
                    content


                conversation.updatedAt =
                    Date.now()


                persist()
            }


            /*
             * 保存后端 conversationId
             */
            function setBackendId(
                localId: string,
                backendId: string
            ) {

                const conversation =
                    conversations
                        .value
                        .find(
                            item =>
                                item.id ===
                                localId
                        )


                if (!conversation) {
                    return
                }


                conversation.backendId =
                    backendId


                persist()
            }


            function createTitle(
                message: string
            ) {

                const text =
                    message.trim()


                if (
                    text.length <= 20
                ) {
                    return text
                }


                return (
                    text.substring(
                        0,
                        20
                    )
                    + '...'
                )
            }


            return {

                conversations,

                currentConversationId,

                currentConversation,

                load,

                persist,

                createConversation,

                selectConversation,

                deleteConversation,

                addMessage,

                updateMessage,

                setBackendId
            }
        }
    )