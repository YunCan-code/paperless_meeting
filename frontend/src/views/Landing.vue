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

    <!-- Showcase Sections -->
    <div class="sections-container" id="showcase">
      
      <!-- Android Platform Section (Simplified) -->
      <section class="showcase-section android-focus">
        <div class="header-group">
            <div class="showcase-tag">移动端</div>
        </div>
        
        <div class="showcase-visual full-width">
          <div class="tablet-mockup tilt-idle">
            <div class="tablet-screen">
               <el-carousel trigger="click" height="100%" arrow="always" :interval="4000" autoplay style="width: 100%">
                 <el-carousel-item v-for="(img, key) in androidImages" :key="key" style="text-align: center; background: #000;">
                   <img :src="img" class="tablet-screenshot" alt="App Screenshot" />
                 </el-carousel-item>
               </el-carousel>
            </div>
          </div>
        </div>
      </section>

    </div>

    <!-- Footer -->
    <footer class="footer">
      <p>&copy; 2025 Smart Power Tech. All rights reserved.</p>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Lightning, ArrowRight, ArrowDown, Monitor, DataBoard, Files, Setting, Reading, Connection, EditPen } from '@element-plus/icons-vue'

const router = useRouter()

const enterSystem = () => {
  router.push('/admin/meetings')
}

const scrollToContent = () => {
  document.getElementById('showcase')?.scrollIntoView({ behavior: 'smooth' })
}

const androidImages = [
  '/landingPic/android_home.png',
  '/landingPic/android_meetings.png',
  '/landingPic/android_detail.png'
]
</script>

<style scoped>
/* ... */
/* Tablet Mockup Styling */

.tablet-mockup {
  width: 500px;
  height: 380px; /* Landscape */
  background: #0f172a;
  border: 10px solid #334155;
  border-radius: 24px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 0 2px rgba(255, 255, 255, 0.1);
}

.tablet-screen {
  padding: 0;
  height: 100%;
  background: black;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
}

.tablet-screenshot {
  width: 100%;
  height: 100%;
  object-fit: fill;
  display: block;
}

:deep(.el-carousel), :deep(.el-carousel__container) {
  height: 100%;
}

/* ... Existing Styles ... */

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

/* Showcase Sections */
.sections-container {
  position: relative;
  z-index: 2;
  background: linear-gradient(180deg, transparent 0%, rgba(15, 23, 42, 0.8) 100%);
}

.showcase-section {
  min-height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px 48px;
  gap: 80px;
  max-width: 1200px;
  margin: 0 auto;
}

.showcase-section.reverse {
  flex-direction: row-reverse;
}

.showcase-content {
  flex: 1;
  max-width: 500px;
}

.showcase-tag {
  display: inline-block;
  padding: 6px 12px;
  border-radius: 20px;
  background: rgba(34, 211, 238, 0.1);
  color: #22d3ee;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 24px;
  border: 1px solid rgba(34, 211, 238, 0.2);
}

.section-title {
  font-size: 40px;
  font-weight: 700;
  margin-bottom: 24px;
  line-height: 1.2;
}

.section-desc {
  font-size: 18px;
  color: #94a3b8;
  margin-bottom: 32px;
  line-height: 1.6;
}

.feature-list {
  list-style: none;
  padding: 0;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  font-size: 16px;
  color: #e2e8f0;
}

.feature-list li .el-icon {
  color: #818cf8;
  background: rgba(129, 140, 248, 0.1);
  padding: 8px;
  border-radius: 8px;
  box-sizing: content-box;
}

/* MOCKUPS */
.showcase-visual {
  flex: 1;
  display: flex;
  justify-content: center;
}

/* Browser Mockup */
.browser-mockup {
  width: 500px;
  height: 350px;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(10px);
  position: relative;
}

.showcase-visual.full-width {
    width: 100%;
}

.showcase-section.android-focus {
  flex-direction: column !important;
  padding-top: 40px;
  gap: 60px;
  min-height: 90vh;
}

.header-group {
    text-align: center;
    width: 100%;
}
.header-group .showcase-tag {
    font-size: 18px; 
    padding: 10px 32px;
    margin-bottom: 0;
}

/* Tablet Mockup Styling */
.tablet-mockup {
  width: 100%;
  max-width: 1200px; /* Allow wider */
  height: 800px; /* Fixed large height to accommodate original size */
  background: #0f172a;
  border: 12px solid #334155;
  border-radius: 24px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 50px 100px -20px rgba(0, 0, 0, 0.6), 0 0 0 2px rgba(255, 255, 255, 0.1);
  margin: 0 auto;
}

.tablet-screen {
  padding: 0;
  height: 100%;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
  display: flex;       /* Center the image if aspect ratio differs */
  align-items: center;
  justify-content: center;
}

.tablet-screenshot {
  width: 100%;
  height: 100%;
  object-fit: contain; /* Critical Fix: Maintain aspect ratio */
  display: block;
}

:deep(.el-carousel), :deep(.el-carousel__container) {
  height: 100%;
}

/* Tilts */
.tilt-idle {
  transform: perspective(2000px) rotateX(5deg);
  transition: transform 0.6s ease-out;
}
.showcase-visual:hover .tilt-idle {
  transform: perspective(2000px) rotateX(0) scale(1.02);
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
/* Mobile Adaptation */
@media (max-width: 768px) {
  .navbar {
    padding: 16px 24px;
  }
  
  .logo-text {
    font-size: 16px;
  }

  .hero-section {
    padding: 0 24px;
    min-height: 70vh;
  }
  
  .main-title {
    font-size: 36px;
    line-height: 1.2;
    margin-bottom: 24px;
  }
  
  .subtitle {
    font-size: 16px;
    margin-bottom: 32px;
  }
  
  .features-section {
    flex-direction: column;
    align-items: center;
    gap: 24px;
    padding-bottom: 48px;
  }
  
  .feature-card {
    width: 100%;
    max-width: 340px;
  }
  
  .enter-btn {
    padding: 12px 32px;
    font-size: 16px;
    width: 100%;
    max-width: 300px;
  }
  
  /* Showcase Responsive */
  .showcase-section,
  .showcase-section.reverse {
    flex-direction: column;
    padding: 64px 24px;
    gap: 48px;
    text-align: center;
  }
  
  .showcase-content {
    max-width: 100%;
  }
  
  .section-title {
    font-size: 28px;
  }
  
  .feature-list {
    display: inline-block;
    text-align: left;
  }

  .browser-mockup {
    width: 100%;
    height: 250px;
  }
  
  .tablet-mockup {
    width: 100%;
    height: auto;
    aspect-ratio: 16/10;
  }
}
</style>
