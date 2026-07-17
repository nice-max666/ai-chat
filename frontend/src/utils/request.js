// frontend/src/utils/request.js
import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 600000
})

// 请求拦截器：自动附加 JWT Token
service.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  response => {
    const res = response.data
    // 401 未认证：清除 token 并跳转登录页
    if (res.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      window.location.href = '/login'
      return Promise.reject(new Error(res.message || '请先登录'))
    }
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
    // HTTP 401/403：清除 token 并跳转登录页
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    ElMessage.error('网络异常')
    return Promise.reject(error)
  }
)

export default service
