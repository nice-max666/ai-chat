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