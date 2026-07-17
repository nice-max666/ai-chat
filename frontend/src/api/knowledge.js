import request from '@/utils/request'

export function uploadDoc(file, assistantId) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/knowledge/upload', formData, {
    params: { assistantId },
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listDocs(assistantId) {
  return request.get('/knowledge/list', { params: { assistantId } })
}

export function deleteDoc(docId) {
  return request.delete(`/knowledge/${docId}`)
}
