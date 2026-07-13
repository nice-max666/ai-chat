// frontend/src/utils/request.js
import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 600000
})

service.interceptors.response.use(
  response => {
    const res = response.data
    // TestController.listAssistants() 直接返回数组，未包装 Result
//    if (response.config.url.includes('/assistant/list')) {
//      return res // 直接返回数组
//    }
    // 其他接口返回 Result 包装
    if (res.code !== 200 && res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res.data
  },
  error => {
    ElMessage.error('网络异常')
    return Promise.reject(error)
  }
)

export default service
