<template>
  <div class="auth-container">
    <!-- 装饰背景 -->
    <div class="bg-blob blob-1"></div>
    <div class="bg-blob blob-2"></div>
    <div class="bg-blob blob-3"></div>

    <!-- 卡片 -->
    <div class="auth-card">
      <div class="card-logo">
        <svg viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="14" fill="url(#g)"/><defs><linearGradient id="g" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#6366f1"/><stop offset="1" stop-color="#a855f7"/></linearGradient></defs><text x="24" y="33" text-anchor="middle" fill="#fff" font-size="26" font-weight="700">AI</text></svg>
      </div>
      <h1 class="auth-title">创建账号</h1>
      <p class="auth-subtitle">注册你的 AI 助手账号</p>

      <el-form ref="formRef" :model="form" :rules="rules">
        <el-form-item prop="username">
          <el-input
            v-model="form.username" placeholder="3-20个字符" size="large"
            :prefix-icon="User" class="custom-input" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password" type="password" placeholder="6-20个字符"
            show-password size="large" :prefix-icon="Lock" class="custom-input" />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword" type="password" placeholder="再次输入密码"
            show-password size="large" :prefix-icon="Lock" class="custom-input"
            @keyup.enter="handleRegister" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="auth-btn" size="large"
            @click="handleRegister" :loading="loading" round>注 册</el-button>
        </el-form-item>
      </el-form>

      <p class="auth-link">已有账号？<router-link to="/login">去登录</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { register } from '@/api/auth'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({ username: '', password: '', confirmPassword: '' })

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次密码输入不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度需在3-20个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需在6-20个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

const handleRegister = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await register(form.username, form.password)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    ElMessage.error(e.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-container {
  position: relative; display: flex; align-items: center; justify-content: center;
  min-height: 100vh; background: linear-gradient(135deg, #0f0a2e 0%, #1e1b4b 40%, #312e81 100%);
  overflow: hidden;
}
.bg-blob {
  position: absolute; border-radius: 50%; filter: blur(80px); opacity: .25;
  animation: float 20s ease-in-out infinite;
}
.blob-1 {
  width: 400px; height: 400px; background: #6366f1;
  top: -100px; right: -80px; animation-delay: 0s;
}
.blob-2 {
  width: 300px; height: 300px; background: #a855f7;
  bottom: -80px; left: -60px; animation-delay: -7s;
}
.blob-3 {
  width: 200px; height: 200px; background: #38bdf8;
  top: 50%; left: 50%; animation-delay: -14s;
}
@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -40px) scale(1.08); }
  66% { transform: translate(-20px, 20px) scale(.95); }
}
.auth-card {
  position: relative; z-index: 1; width: 420px; padding: 44px 40px;
  background: rgba(255,255,255,.95); backdrop-filter: blur(20px);
  border-radius: 24px; box-shadow: 0 24px 80px rgba(0,0,0,.35);
  animation: cardIn .6s ease-out;
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}
.card-logo { display: flex; justify-content: center; margin-bottom: 16px; }
.card-logo svg { width: 56px; height: 56px; }
.auth-title { text-align: center; font-size: 26px; font-weight: 700; color: #1e1b4b; margin: 0 0 6px; }
.auth-subtitle { text-align: center; color: #9ca3af; margin: 0 0 28px; font-size: 14px; }
.custom-input :deep(.el-input__wrapper) {
  border-radius: 12px; box-shadow: none; border: 1.5px solid #e5e7eb;
  padding: 0 14px; transition: border-color .2s;
}
.custom-input :deep(.el-input__wrapper:hover) { border-color: #a5b4fc; }
.custom-input :deep(.el-input__wrapper.is-focus) { border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,.1); }
.auth-btn {
  width: 100%; height: 48px; margin-top: 4px; border: none; font-size: 16px; font-weight: 600;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  transition: transform .2s, box-shadow .2s;
}
.auth-btn:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(99,102,241,.4); color: #fff; }
.auth-link { text-align: center; color: #9ca3af; font-size: 13px; margin-top: 16px; }
.auth-link a { color: #6366f1; text-decoration: none; font-weight: 600; }
.auth-link a:hover { text-decoration: underline; }
</style>
