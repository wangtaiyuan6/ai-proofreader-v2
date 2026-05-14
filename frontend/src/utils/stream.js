/* eslint-disable no-constant-condition */
/**
 * Parse SSE stream from a fetch Response and dispatch events.
 * Compatible with both "event:name" and "event: name" formats
 * (Spring SseEmitter uses no space, browsers may use space).
 */
export async function parseSSEStream(response, handlers) {
  if (!response.body) {
    throw new Error('Response body is null')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      let eventType = ''
      let data = ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          data = line.substring(5).trim()
        } else if (line === '' && eventType && data) {
          dispatchEvent(eventType, data, handlers)
          eventType = ''
          data = ''
        }
      }

      if (eventType && data) {
        if (buffer === '' || buffer.startsWith('event:') || buffer.startsWith('data:')) {
          dispatchEvent(eventType, data, handlers)
          eventType = ''
          data = ''
        }
      }
    }

    // Process any remaining buffer
    if (buffer.trim()) {
      const lines = buffer.split('\n')
      let eventType = ''
      let data = ''
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          data = line.substring(5).trim()
        } else if (line === '' && eventType && data) {
          dispatchEvent(eventType, data, handlers)
          eventType = ''
          data = ''
        }
      }
      if (eventType && data) {
        dispatchEvent(eventType, data, handlers)
      }
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      throw error
    }
  }
}

function dispatchEvent(eventType, dataStr, handlers) {
  try {
    let data = JSON.parse(dataStr)

    // Spring SseEmitter may double-serialize: JSON string inside JSON string
    if (typeof data === 'string') {
      data = JSON.parse(data)
    }

    switch (eventType) {
      case 'start':
        if (handlers.onStart) handlers.onStart()
        break
      case 'thinking':
        if (handlers.onThinking) handlers.onThinking(data)
        break
      case 'correction':
        if (handlers.onCorrection) handlers.onCorrection(data)
        break
      case 'change':
        if (handlers.onChange) handlers.onChange(data)
        break
      case 'changes':
        if (handlers.onChanges) handlers.onChanges(data)
        break
      case 'done':
        if (handlers.onDone) handlers.onDone()
        break
      case 'error':
        if (handlers.onError) handlers.onError(data.error || '未知错误')
        break
    }
  } catch (e) {
    console.warn('[SSE] parse error:', e)
  }
}
