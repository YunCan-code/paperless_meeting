<template>
  <div class="settings-page">
    <el-card shadow="never" class="settings-card">
      <template #header>
        <div class="card-header">
          <h3>系统设置</h3>
        </div>
      </template>
      
      <el-form class="settings-form" label-position="top">
        <el-form-item label="默认会议地点">
          <el-input 
            v-model="defaultLocation" 
            placeholder="请输入默认会议室地址，例如：第一会议室" 
            clearable
            @change="saveSettings"
          >
           <template #append>
             <el-button @click="saveSettings">保存</el-button>
           </template>
          </el-input>
          <div class="form-help">设置后，发起新会议时将自动填充此地点。</div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const defaultLocation = ref('')

onMounted(() => {
  const saved = localStorage.getItem('defaultMeetingLocation')
  if (saved) {
    defaultLocation.value = saved
  }
})

const saveSettings = () => {
  localStorage.setItem('defaultMeetingLocation', defaultLocation.value)
  ElMessage.success('设置已保存')
}
</script>

<style scoped>
.settings-page {
  max-width: 800px;
  margin: 0 auto;
}
.settings-card {
  border-radius: 12px;
  border: 1px solid var(--color-slate-200);
}
.card-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-slate-800);
}
.form-help {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 8px;
}
</style>
