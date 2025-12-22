<template>
  <div class="mobile-login-container">
    <!-- Left Panel: Image & Slogan -->
    <div class="left-panel">
      <div class="brand-overlay">
        <div class="brand-text">
          <h1>Paperless Meeting</h1>
          <p>绿色 · 低碳 · 高效 · 智能</p>
        </div>
      </div>
    </div>

    <!-- Right Panel: Login Form -->
    <div class="right-panel">
      <div class="login-box">
        <div class="welcome-text">
          <h2>欢迎使用</h2>
          <p>无纸化会议系统</p>
        </div>

        <el-form class="login-form">
          <el-form-item>
            <el-input 
              v-model="query" 
              placeholder="请输入 姓名 或 手机号" 
              size="large" 
              :prefix-icon="User"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-button 
            type="primary" 
            size="large" 
            class="login-btn" 
            :loading="loading" 
            @click="handleLogin"
          >
            进入会议
            <el-icon class="el-icon--right"><Right /></el-icon>
          </el-button>
        </el-form>

        <div class="footer-tips">
          <p>如果是首次登录或遇到重名提示，请尝试使用手机号登录。</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Right } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const query = ref('')
const loading = ref(false)

const handleLogin = async () => {
  if (!query.value.trim()) return ElMessage.warning('请输入姓名或手机号')

  loading.value = true
  try {
    const res = await request.post('/auth/login', { query: query.value })
    
    // Success
    ElMessage.success(`欢迎回来，${res.name}`)
    
    // Store user info (simple implementation)
    localStorage.setItem('user_id', res.user_id)
    localStorage.setItem('user_name', res.name)
    localStorage.setItem('token', res.token)

    // Navigate to Home
    router.push('/mobile/home')

  } catch (error) {
    // Error handling
    const msg = error.response?.data?.detail || '登录失败，请检查网络'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.mobile-login-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: #fff;
  overflow: hidden;
}

/* Left Panel */
.left-panel {
  flex: 1;
  background-image: url('https://images.unsplash.com/photo-1497366216548-37526070297c?q=80&w=2069&auto=format&fit=crop');
  background-size: cover;
  background-position: center;
  position: relative;
  display: flex;
  align-items: flex-end;
}

.brand-overlay {
  background: linear-gradient(to top, rgba(0,0,0,0.8), transparent);
  width: 100%;
  padding: 60px 40px;
  color: white;
}

.brand-text h1 {
  font-size: 32px;
  margin: 0;
  font-weight: 700;
  letter-spacing: 1px;
}
.brand-text p {
  margin: 8px 0 0;
  font-size: 16px;
  opacity: 0.9;
  font-weight: 300;
  letter-spacing: 2px;
}

/* Right Panel */
.right-panel {
  width: 480px; /* Fixed width for login side on tablet */
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #ffffff;
  padding: 40px;
}

.login-box {
  width: 100%;
  max-width: 320px;
}

.welcome-text { margin-bottom: 40px; }
.welcome-text h2 {
  font-size: 28px;
  color: #1e293b;
  margin: 0 0 8px 0;
}
.welcome-text p {
  color: #64748b;
  margin: 0;
  font-size: 16px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.login-btn {
  width: 100%;
  font-weight: 600;
  letter-spacing: 1px;
  height: 48px;
  font-size: 16px;
}

.footer-tips {
  margin-top: 32px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
  text-align: center;
}

/* Responsive: For smaller phones, stack them or hide image */
@media (max-width: 768px) {
  .left-panel { display: none; }
  .right-panel { width: 100%; }
}
</style>
