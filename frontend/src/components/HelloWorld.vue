<!-- frontend/src/components/HelloWorld.vue -->
<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { getAssistantList, getChatHistory, sendChat, createAssistant, deleteAssistant } from '@/api/chat' // 引入 API 函数
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'

const assistants = ref([])
const currentAssistant = ref(null)
const messages = ref([])
const userInput = ref('')
const messageListRef = ref(null)

// 获取助手列表
onMounted(async () => {
  try {
     // TestController 未使用 Result 包装，拦截器会原样返回数组
     assistants.value = await getAssistantList()
  } catch (error) {
    console.error('获取助手列表失败', error)
  }
})

// 封装滚动到底部的方法
const scrollToBottom = async () => {
  await nextTick() // 等待 Vue 完成 DOM 更新
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}


// 选中助手并加载历史
const selectAssistant = async (assistant) => {
  currentAssistant.value = assistant
  try {
    const data = await getChatHistory(assistant.id)
    messages.value = Array.isArray(data) ? data : []
    scrollToBottom()
  } catch (error) {
    messages.value = []
  }
}

// 发送消息
const handleSend = async () => {
  if (!userInput.value.trim() || !currentAssistant.value) return

  messages.value.push({ role: 'user', message: userInput.value })
  const msgToSend = userInput.value
  userInput.value = ''



  try {
    // 使用 sendChat，params 会自动拼接为 ?assistantId=xxx
    const aiReply = await sendChat(currentAssistant.value.id, { message: msgToSend })
    messages.value.push({ role: 'assistant', message: aiReply })
  } catch (error) {
    console.error('发送消息失败', error)
  }
}
//新增助手
const handleAddAssistant = () => {
  ElMessageBox.prompt('请输入助手名称', '新增助手', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '名称不能为空'
  }).then(async ({ value }) => {
    // 调用后端接口，仅传 name，id与时间由后端生成
    const res = await createAssistant({ name: value })
    ElMessage.success('助手创建成功')
    assistants.value.push(res) // 将新助手追加到列表
  }).catch(() => {})
}
//删除助手
const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除该助手及所有聊天记录吗？', '警告', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
    await deleteAssistant(id)
    ElMessage.success('删除成功')
    // 从列表中移除该项，若删除的是当前选中助手，需清空右侧聊天区
    assistants.value = assistants.value.filter(item => item.id !== id)
    if (currentAssistant.value && currentAssistant.value.id === id) {
      currentAssistant.value = null
      messages.value = []
    }
  } catch (error) {}
}
</script>



<template>
  <el-container style="height: 100vh; border: 1px solid #eee">
    <!-- 左侧助手列表 -->
    <el-aside width="250px" style="background-color: rgb(238, 241, 246)">
      <h3 style="text-align: center; margin: 20px 0">AI 助手</h3>
      <div style="padding: 0 20px; margin-bottom: 10px;">
          <el-button type="primary" style="width: 100%;" @click="handleAddAssistant">
            + 添加助手
          </el-button>
        </div>
      <el-menu :default-openeds="['1']">
        <el-menu-item
          v-for="item in assistants"
          :key="item.id"
          :index="item.id"
          @click="selectAssistant(item)"
        >
          {{ item.name }}
          <el-icon class="delete-icon" @click.stop="handleDelete(item.id)"><Delete /></el-icon>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧聊天窗口 -->
    <el-main style="display: flex; flex-direction: column; padding: 0;">
      <div v-if="!currentAssistant" style="flex: 1; display: flex; align-items: center; justify-content: center; color: #999;">
        请选择一个助手开始聊天
      </div>
      <template v-else>
        <!-- 消息列表 -->
        <div ref="messageListRef" style="flex: 1; overflow-y: auto; padding: 20px;">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            :style="{ textAlign: msg.role === 'user' ? 'right' : 'left', marginBottom: '15px' }"
          >
            <div
              class="chat-bubble"
              :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'"
            >
              {{ msg.message }}
            </div>
          </div>
        </div>
        <!-- 输入框 -->
        <div style="padding: 20px; border-top: 1px solid #eee; display: flex;">
          <el-input
            v-model="userInput"
            placeholder="请输入消息..."
            @keyup.enter="handleSend"
            style="margin-right: 10px;"
          />
          <el-button type="primary" @click="handleSend">发送</el-button>
        </div>
      </template>
    </el-main>
  </el-container>
</template>

<style scoped>
.el-aside {
  color: #333;
}
/* 通用气泡样式 */
.chat-bubble {
  display: inline-block;
  max-width: 70%;       /* 防止气泡撑满全屏 */
  padding: 10px 15px;
  border-radius: 8px;
  word-wrap: break-word; /* 强制长单词换行 */
  white-space: pre-wrap; /* 核心：保留换行符并允许自动换行 */
  line-height: 1.5;
  text-align: left;      /* 无论气泡靠左靠右，文字均左对齐 */
}

/* 用户气泡样式 (替代原先的 primary tag) */
.bubble-user {
  background-color: #409eff;
  color: #fff;
}

/* AI气泡样式 (替代原先的 success tag) */
.bubble-ai {
  background-color: #e6e6e6;
  color: #333;
}
/* 删除图标样式 */
.delete-icon {
  display: none;
  margin-left: auto;
  cursor: pointer;
}
.el-menu-item:hover .delete-icon {
  display: inline-flex;
}
</style>
