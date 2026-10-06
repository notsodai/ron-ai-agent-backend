import type {
    AgentEvent,
    ChatRequest
} from '../types/chat'


const CHAT_URL =
    '/api/ai/ronmanus/chat'


export interface StreamChatOptions {

    request: ChatRequest

    signal?: AbortSignal

    onConversationId?: (
        conversationId: string
    ) => void

    onEvent: (
        event: AgentEvent
    ) => void
}


export async function streamChat(
    options: StreamChatOptions
): Promise<void> {

    const {
        request,
        signal,
        onConversationId,
        onEvent
    } = options


    const response = await fetch(
        CHAT_URL,
        {
            method: 'POST',

            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream'
            },

            body: JSON.stringify(request),

            signal
        }
    )


    if (!response.ok) {

        const text =
            await response.text()

        throw new Error(
            text ||
            `HTTP ${response.status}`
        )
    }


    /*
     * 后端 Controller 会返回：
     *
     * X-Conversation-ID
     */
    const conversationId =
        response.headers.get(
            'X-Conversation-ID'
        )


    if (conversationId) {

        onConversationId?.(
            conversationId
        )
    }


    if (!response.body) {

        throw new Error(
            'Response body is empty'
        )
    }


    const reader =
        response.body.getReader()


    const decoder =
        new TextDecoder(
            'utf-8'
        )


    let buffer = ''


    while (true) {

        const {
            value,
            done
        } = await reader.read()


        if (done) {
            break
        }


        buffer += decoder.decode(
            value,
            {
                stream: true
            }
        )


        /*
         * Windows / Spring 有可能返回 \r\n。
         *
         * 统一转换成 \n。
         */
        buffer =
            buffer.replace(
                /\r\n/g,
                '\n'
            )


        /*
         * SSE 每一个事件之间以空行分隔。
         */
        const blocks =
            buffer.split('\n\n')


        /*
         * 最后一段可能还是半截，
         * 留给下一次读取继续拼。
         */
        buffer =
            blocks.pop() ?? ''


        for (const block of blocks) {

            parseSseBlock(
                block,
                onEvent
            )
        }
    }


    /*
     * 处理最后残留数据。
     */
    if (buffer.trim()) {

        parseSseBlock(
            buffer,
            onEvent
        )
    }
}


function parseSseBlock(
    block: string,
    onEvent: (
        event: AgentEvent
    ) => void
) {

    if (!block.trim()) {
        return
    }


    let eventName = ''

    const dataLines: string[] = []


    const lines =
        block.split('\n')


    for (const line of lines) {

        if (
            line.startsWith('event:')
        ) {

            eventName =
                line
                    .substring(6)
                    .trim()

        } else if (
            line.startsWith('data:')
        ) {

            dataLines.push(
                line
                    .substring(5)
                    .trimStart()
            )
        }
    }


    if (dataLines.length === 0) {
        return
    }


    const data =
        dataLines.join('\n')


    /*
     * 兼容后端旧版：
     *
     * data:[DONE]
     */
    if (data === '[DONE]') {

        onEvent({
            type: 'DONE',
            content: '',
            status: 'SUCCESS'
        })

        return
    }


    try {

        const parsed =
            JSON.parse(
                data
            ) as AgentEvent


        /*
         * 如果 JSON 自带 type，
         * 直接使用。
         */
        if (parsed.type) {

            onEvent(parsed)

            return
        }


    } catch {

        /*
         * 某些旧接口错误信息
         * 可能仍然是纯文本。
         */
    }


    /*
     * 兼容纯文本 SSE。
     */
    if (
        eventName.toLowerCase()
        === 'error'
    ) {

        onEvent({
            type: 'ERROR',
            content: data,
            status: 'ERROR'
        })

        return
    }


    onEvent({
        type: 'STATUS',
        content: data,
        status: 'RUNNING'
    })
}