<template>
  <div class="landing-page">
    <!-- Navbar -->
    <nav class="navbar">
      <div class="logo">
        <el-icon class="logo-icon"><Lightning /></el-icon>
        <span class="logo-text">无纸化会议系统</span>
      </div>

    </nav>

    <!-- Hero Section -->
    <main class="hero-section">
      <div class="hero-content">
        <h1 class="main-title">
          <!-- <span class="gradient-text">智慧电力</span> -->
          <br />
          无纸化会议系统
        </h1>
        <p class="subtitle">
          安全可靠 · 高效协同<br/>
          <!-- 专为电力行业打造的新一代智能会议解决方案 -->
        </p>
        
        <div class="cta-group">
          <el-button type="primary" size="large" class="enter-btn" @click="enterSystem">
            立即进入系统 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
      
      <!-- Visual Element (Abstract Power Grid) -->
      <div class="hero-visual">
         <div class="glowing-orb"></div>
         <div class="grid-lines"></div>
      </div>
    </main>

    <!-- Features Section -->
    <!-- <section class="features-section">
      <div class="feature-card">
        <div class="icon-box"><el-icon><Monitor /></el-icon></div>
        <h3>即时同步</h3>
        <p>多终端实时同屏，会议资料秒级分发，提升决策效率。</p>
      </div>
      <div class="feature-card">
        <div class="icon-box"><el-icon><Lock /></el-icon></div>
        <h3>安全合规</h3>
        <p>国密算法加密传输，细粒度权限管控，保障数据安全。</p>
      </div>
      <div class="feature-card">
        <div class="icon-box"><el-icon><Leaf /></el-icon></div>
        <h3>绿色低碳</h3>
        <p>全流程无纸化，每年节约纸张数万张，助力双碳目标。</p>
      </div>
    </section> -->

    <!-- Footer -->
    <footer class="footer">
      <p>&copy; 2025 Smart Power Tech. All rights reserved.</p>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Lightning, ArrowRight, Monitor, Lock, SwitchButton as Leaf } from '@element-plus/icons-vue'

const router = useRouter()

const enterSystem = () => {
  router.push('/admin/meetings')
}
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
  background-color: #0f172a;
  background-image: 
    radial-gradient(at 0% 0%, hsla(253,16%,7%,1) 0, transparent 50%), 
    radial-gradient(at 50% 0%, hsla(225,39%,30%,1) 0, transparent 50%), 
    radial-gradient(at 100% 0%, hsla(339,49%,30%,1) 0, transparent 50%);
  color: white;
  font-family: 'Inter', sans-serif;
  overflow: hidden;
  position: relative;
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
  right: 0;
  z-index: 10;
}
.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 700;
  color: white;
}
.logo-icon {
  color: #22d3ee;
  font-size: 28px;
}
.nav-links {
  display: flex;
  gap: 24px;
  align-items: center;
}
.nav-btn {
  color: #94a3b8;
  font-size: 15px;
}
.nav-btn:hover {
  color: white;
}
.login-btn {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: white;
}
.login-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* Hero Section */
.hero-section {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 85vh;
  padding: 0 48px;
  position: relative;
  text-align: center;
}
.hero-content {
  z-index: 2;
  max-width: 800px;
}
.main-title {
  font-size: 64px;
  font-weight: 800;
  line-height: 1.1;
  margin-bottom: 24px;
  letter-spacing: -1px;
}
.gradient-text {
  background: linear-gradient(135deg, #22d3ee 0%, #818cf8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.subtitle {
  font-size: 20px;
  color: #94a3b8;
  margin-bottom: 48px;
  line-height: 1.6;
}
.enter-btn {
  background: linear-gradient(135deg, #0ea5e9 0%, #6366f1 100%);
  border: none;
  border-radius: 30px;
  padding: 16px 48px;
  font-size: 18px;
  font-weight: 600;
  height: auto;
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.4);
  transition: transform 0.2s;
}
.enter-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(99, 102, 241, 0.5);
}

/* Visuals */
.hero-visual {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  pointer-events: none;
}
.glowing-orb {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(99,102,241,0.15) 0%, rgba(0,0,0,0) 70%);
  filter: blur(60px);
}
.grid-lines {
  position: absolute;
  width: 100%;
  height: 100%;
  background-image: 
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 50px 50px;
  mask-image: radial-gradient(circle at center, black 40%, transparent 80%);
}

/* Features */
.features-section {
  display: flex;
  justify-content: center;
  gap: 32px;
  padding: 0 48px 64px;
  z-index: 2;
  position: relative;
}
.feature-card {
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.05);
  padding: 32px;
  border-radius: 16px;
  width: 300px;
  text-align: left;
  transition: all 0.3s;
}
.feature-card:hover {
  background: rgba(255, 255, 255, 0.06);
  transform: translateY(-5px);
  border-color: rgba(255, 255, 255, 0.1);
}
.icon-box {
  background: rgba(34, 211, 238, 0.1);
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #22d3ee;
  font-size: 24px;
  margin-bottom: 16px;
}
.feature-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}
.feature-card p {
  margin: 0;
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.5;
}

/* Footer */
.footer {
  text-align: center;
  padding: 24px;
  color: #475569;
  font-size: 12px;
  position: absolute;
  bottom: 0;
  width: 100%;
}
</style>
