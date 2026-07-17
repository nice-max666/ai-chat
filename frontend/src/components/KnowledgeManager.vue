<template>
  <div class="km-root">
    <div class="km-upload">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="10"
        accept=".txt,.docx"
        :on-change="handleFileChange"
        :show-file-list="false"
      >
        <el-button type="primary" size="small" :icon="Upload">上传文档</el-button>
      </el-upload>
      <span class="km-hint">支持 TXT、DOCX</span>
    </div>

    <div v-if="docs.length === 0" class="km-empty">暂无文档，上传知识让 AI 更懂你</div>

    <div v-for="doc in docs" :key="doc.id" class="km-item">
      <div class="km-doc-info">
        <span class="km-doc-icon">{{ doc.fileType === 'docx' ? '📄' : '📃' }}</span>
        <div>
          <div class="km-doc-name">{{ doc.fileName }}</div>
          <div class="km-doc-meta">{{ doc.chunkCount }} 个片段 · {{ formatDate(doc.createdAt) }}</div>
        </div>
      </div>
      <el-button type="danger" size="small" circle :icon="Delete" @click="handleDelete(doc.id)" />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Delete } from '@element-plus/icons-vue'
import { uploadDoc, listDocs, deleteDoc } from '@/api/knowledge'

const props = defineProps({ assistantId: String })
const docs = ref([])

const loadDocs = async () => {
  if (!props.assistantId) return
  try { docs.value = await listDocs(props.assistantId) } catch (e) { docs.value = [] }
}

watch(() => props.assistantId, loadDocs, { immediate: true })

const handleFileChange = async (file) => {
  try {
    await uploadDoc(file.raw, props.assistantId)
    ElMessage.success('文档上传成功')
    loadDocs()
  } catch (e) {
    ElMessage.error('上传失败: ' + (e.message || '未知错误'))
  }
}

const handleDelete = async (docId) => {
  try {
    await ElMessageBox.confirm('确定删除该文档？知识库将移除其内容', '提示', { type: 'warning' })
    await deleteDoc(docId)
    ElMessage.success('已删除')
    loadDocs()
  } catch (e) {}
}

const formatDate = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.km-root { padding: 4px 0; }
.km-upload { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.km-hint { font-size: 12px; color: #9ca3af; }
.km-empty { text-align: center; color: #9ca3af; font-size: 13px; padding: 24px 0; }
.km-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px; border: 1px solid #f0f0f0; border-radius: 10px; margin-bottom: 8px;
  transition: background .15s;
}
.km-item:hover { background: #fafafa; }
.km-doc-info { display: flex; align-items: center; gap: 10px; }
.km-doc-icon { font-size: 22px; }
.km-doc-name { font-size: 13px; font-weight: 500; color: #374151; }
.km-doc-meta { font-size: 11px; color: #9ca3af; margin-top: 2px; }
</style>
