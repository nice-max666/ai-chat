// frontend/src/api/chat.js
import request from '@/utils/request'

export function getAssistantList() {
  // 直接请求 /assistant/list，由 vite 代理转发
  return request.get('/assistant/list')
}

export function getAssistant(id) {
  return request.get(`/assistant/${id}`)
}

export function sendChat(assistantId, data) {
  return request.post('/chat/completions', data, { params: { assistantId } })
}

export function getChatHistory(assistantId) {
  return request.get(`/chat/history/${assistantId}`)
}

export function createAssistant(data) {
  return request.post('/assistant', data)
}

export function deleteAssistant(id) {
  return request.delete(`/assistant/${id}`)
}

// 流式对话:通过 SSE 逐段接收 AI 回复,onChunk 每收到一段回调一次
export async function sendChatStream(assistantId, data, onChunk) {
  const resp = await fetch(
    `http://localhost:8080/api/chat/completions/stream?assistantId=${assistantId}`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: JSON.stringify(data)
    }
  )
  if (!resp.ok || !resp.body) {
    throw new Error('流式请求失败: ' + resp.status)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // SSE 以空行分隔每一帧,最后一段可能不完整,留到下次
    const frames = buffer.split('\n\n')
    buffer = frames.pop()
    for (const frame of frames) {
      const dataLine = frame.split('\n').find(l => l.startsWith('data:'))
      if (!dataLine) continue
      const payload = dataLine.slice(5).trim()
      if (!payload) continue
      try {
        const obj = JSON.parse(payload)
        if (obj.c) onChunk(obj.c)
      } catch (e) {
        // 单帧解析失败忽略,不中断整体
      }
    }
  }
}