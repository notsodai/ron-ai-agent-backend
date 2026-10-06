export type AgentEventType =
    | 'STATUS'
    | 'TOOL_START'
    | 'TOOL_RESULT'
    | 'ANSWER'
    | 'ERROR'
    | 'DONE'


export interface AgentEvent {

    type: AgentEventType

    step?: number

    agent?: string

    tool?: string | null

    content?: string

    status?: string
}


export interface ChatRequest {

    conversationId?: string

    message: string
}


export interface ChatMessage {

    id: string

    role: 'user' | 'assistant'

    content: string

    createdAt: number
}


export interface Conversation {

    /**
     * 前端本地 ID
     */
    id: string

    /**
     * 后端 conversationId
     */
    backendId?: string

    title: string

    messages: ChatMessage[]

    createdAt: number

    updatedAt: number
}