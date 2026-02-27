<template>
  <div class="landing-page">
    <!-- Navbar (Logo only) -->
    <nav class="navbar">
      <div class="logo">
        <el-icon class="logo-icon"><Lightning /></el-icon>
        <span class="logo-text">无纸化会议系统</span>
      </div>

      <div class="nav-links">
        <a href="https://komari.coso.top" target="_blank" class="probe-link">
          <el-icon class="link-icon"><Connection /></el-icon>
          VPS 探针
        </a>
        <a href="https://github.com/YunCan-code/paperless_meeting" target="_blank" class="probe-link">
          <el-icon class="link-icon"><Link /></el-icon>
          GitHub
        </a>
      </div>
    </nav>

    <!-- Hero Section -->
    <main class="hero-section">
      <div class="hero-content">
        <div class="hero-badge">高效协同 · 绿色办公</div>
        <h1 class="main-title">无纸化会议系统</h1>
        
        <div class="cta-group">
          <!-- Enter System Button -->
          <el-button type="primary" size="large" class="enter-btn" @click="enterSystem">
            立即进入系统 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>

          <!-- Download Android Button (with Popover) -->
          <el-popover
            trigger="click"
            title="扫码下载安卓端"
            :width="200"
            placement="bottom"
          >
            <template #reference>
              <el-button size="large" class="download-btn">
                <el-icon class="el-icon--left"><Cellphone /></el-icon>
                下载安卓端
              </el-button>
            </template>
            
            <div class="qr-container">
              <qrcode-vue v-if="downloadUrl" :value="fullDownloadUrl" :size="160" level="H" />
              <div v-else class="loading-text">获取链接中...</div>
              
              <div class="version-info" v-if="latestVersion">
                v{{ latestVersion.version_name }}
              </div>
              
              <a v-if="downloadUrl" :href="fullDownloadUrl" target="_blank" class="direct-link">
                点击直接下载
              </a>
            </div>
          </el-popover>
        </div>
      </div>
      
      <!-- Decorative Elements -->
      <div class="hero-decoration">
        <div class="decoration-circle circle-1"></div>
        <div class="decoration-circle circle-2"></div>
        <div class="decoration-circle circle-3"></div>
      </div>
    </main>

    <!-- No Footer -->
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Lightning, ArrowRight, Connection, Cellphone, Link } from '@element-plus/icons-vue'
import QrcodeVue from 'qrcode.vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const downloadUrl = ref('')
const latestVersion = ref(null)

// Compute full URL for QR code (linking to backend static file or external URL)
const fullDownloadUrl = computed(() => {
  if (!downloadUrl.value) return ''
  if (downloadUrl.value.startsWith('http')) {
    return downloadUrl.value
  }

  // 使用当前页面的 origin 作为基础 URL（适配内网部署）
  const baseUrl = window.location.origin
  return `${baseUrl}${downloadUrl.value.startsWith('/') ? '' : '/'}${downloadUrl.value}`
})

const enterSystem = () => {
  router.push('/admin/meetings')
}

const fetchLatestApk = async () => {
  try {
    // Assuming axios is configured globally with base URL, or use direct path
    // Let's use relative path /api/updates/latest if proxy is set up, 
    // or rely on the axios instance created in utils/request.js if available.
    // Here we'll try to use the standard fetch for simplicity in this file scope
    // or import the request utility.
    
    // Using global axios default or relative path
    // Note: Project likely has a configured axios instance.
    // Let's assume /api prefix via vite proxy or direct.
    
    // Quick fix: user project structure check showed axios use.
    // Let's try to import request from utils if possible, otherwise use axios directly.
    
    const res = await axios.get('/api/updates/latest') // Assuming /api proxy
    if (res.data) {
      latestVersion.value = res.data
      downloadUrl.value = res.data.download_url
    }
  } catch (error) {
    console.error('Failed to fetch latest APK:', error)
    // ElMessage.warning('无法获取最新安卓版本')
  }
}

onMounted(() => {
  fetchLatestApk()
})
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 50%, #f1f5f9 100%);
  color: #0f172a;
  font-family: 'Inter', sans-serif;
  position: relative;
  overflow: hidden;
}

/* Navbar */
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 48px;
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 10;
  box-sizing: border-box;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.logo-icon {
  color: #3b82f6;
  font-size: 28px;
}

.probe-link {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  text-decoration: none;
  font-size: 14px;
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
  background: white;
}

.probe-link:hover {
  color: #3b82f6;
  border-color: #3b82f6;
  background: #eff6ff;
}

.nav-links {
  display: flex;
  gap: 12px;
  align-items: center;
}

.link-icon {
  font-size: 16px;
}

/* Hero Section */
.hero-section {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  width: 100%;
  padding: 0 48px;
  position: relative;
  text-align: center;
  box-sizing: border-box;
}

.hero-content {
  z-index: 2;
  max-width: 800px;
  display: flex;
  flex-direction: column;
  align-items: center;
  /* Visual adjustment: Shift UP */
  margin-bottom: 25vh; 
}

.hero-badge {
  display: inline-block;
  padding: 8px 20px;
  border-radius: 24px;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  color: #3b82f6;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 32px;
  border: 1px solid #bfdbfe;
  letter-spacing: 1px;
}

.main-title {
  font-size: 64px;
  font-weight: 800;
  line-height: 1.1;
  margin-bottom: 48px;
  letter-spacing: -1.5px;
  color: #0f172a;
  background: linear-gradient(120deg, #0f172a 0%, #334155 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.cta-group {
  display: flex;
  gap: 24px;
  align-items: center;
}

.enter-btn {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border: none;
  border-radius: 12px;
  padding: 20px 40px;
  font-size: 17px;
  font-weight: 600;
  height: auto;
  box-shadow: 0 10px 25px -5px rgba(59, 130, 246, 0.4);
  transition: all 0.2s;
}

.enter-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 15px 30px -10px rgba(59, 130, 246, 0.5);
}

.download-btn {
  background: white;
  border: 2px solid #e2e8f0;
  color: #475569;
  border-radius: 12px;
  padding: 20px 40px;
  font-size: 17px;
  font-weight: 600;
  height: auto;
  transition: all 0.2s;
}

.download-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
  background: #f8fafc;
}

/* QR Code Popover Content */
.qr-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  text-align: center;
}

.loading-text {
  padding: 20px;
  color: #94a3b8;
}

.version-info {
  margin-top: 12px;
  font-size: 14px;
  color: #64748b;
  font-weight: 500;
}

.direct-link {
  margin-top: 8px;
  font-size: 12px;
  color: #3b82f6;
  text-decoration: none;
}
.direct-link:hover {
  text-decoration: underline;
}

/* Decorative Elements */
.hero-decoration {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  pointer-events: none;
  overflow: hidden;
}

.decoration-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.6;
}

.circle-1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.1) 0%, transparent 70%);
  top: -100px;
  right: -100px;
}

.circle-2 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(34, 197, 94, 0.08) 0%, transparent 70%);
  bottom: 50px;
  left: -50px;
}

.circle-3 {
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.08) 0%, transparent 70%);
  top: 40%;
  right: 10%;
}
</style>
