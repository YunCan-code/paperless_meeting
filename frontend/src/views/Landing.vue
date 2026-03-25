<template>
  <div class="landing-page">
    <div class="landing-shell">
      <header class="landing-header">
        <div class="brand-block">
          <div class="brand-mark">
            <el-icon><Monitor /></el-icon>
          </div>
          <div class="brand-copy">
            <span class="brand-title">无纸化会议系统</span>
            <span class="brand-subtitle">实时协同、投票抽签、文档同步</span>
          </div>
        </div>

        <div class="header-actions">
          <button type="button" class="theme-toggle" @click="toggleDark">
            <el-icon>
              <Sunny v-if="isDark" />
              <MoonNight v-else />
            </el-icon>
            <span>{{ isDark ? '浅色模式' : '夜间模式' }}</span>
          </button>

          <a
            href="https://komari.coso.top"
            target="_blank"
            rel="noreferrer"
            class="header-link"
          >
            <el-icon><Connection /></el-icon>
            <span>VPS 探针</span>
          </a>

          <a
            href="https://github.com/YunCan-code/paperless_meeting"
            target="_blank"
            rel="noreferrer"
            class="header-link"
          >
            <el-icon><Link /></el-icon>
            <span>GitHub</span>
          </a>
        </div>
      </header>

      <main class="hero-panel">
        <section class="hero-content">
          <div class="hero-badge">
            <span class="badge-dot"></span>
            会务入口
          </div>

          <h1 class="hero-title">让会议组织、展示与协同更顺畅</h1>
          <p class="hero-description">
            从会议资料同步到投票、抽签和终端协作，都可以在同一套系统里完成。首页保持轻量，
            进入后台即可继续处理日常会务工作。
          </p>

          <div class="hero-highlights">
            <span class="highlight-pill">实时投票</span>
            <span class="highlight-pill">抽签互动</span>
            <span class="highlight-pill">文档同步</span>
          </div>

          <div class="hero-actions">
            <el-button type="primary" size="large" class="primary-action" @click="enterSystem">
              进入系统
              <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </section>

        <aside class="download-card">
          <div class="card-topline">安卓客户端</div>
          <div class="card-header">
            <div>
              <h2 class="card-title">扫码安装到平板或手机</h2>
              <p class="card-subtitle">{{ downloadHint }}</p>
            </div>
            <div class="card-version">{{ versionText }}</div>
          </div>

          <div class="card-body">
            <div class="qr-panel" :class="{ 'qr-panel-empty': !hasDownload }">
              <qrcode-vue
                v-if="hasDownload"
                :value="fullDownloadUrl"
                :size="180"
                level="H"
                render-as="svg"
              />
              <div v-else class="empty-state">
                <el-icon class="empty-icon"><Download /></el-icon>
                <span>{{ downloadEmptyText }}</span>
              </div>
            </div>

            <div class="card-notes">
              <div class="note-item">
                <span class="note-label">安装方式</span>
                <span class="note-value">扫码下载或复制链接到浏览器</span>
              </div>
            </div>
          </div>

          <div class="card-footer">
            <a
              v-if="hasDownload"
              :href="fullDownloadUrl"
              target="_blank"
              rel="noreferrer"
              class="direct-download-link"
            >
              下载最新安装包
            </a>
            <span v-else class="direct-download-link disabled">{{ downloadEmptyText }}</span>
          </div>
        </aside>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Connection,
  Download,
  Link,
  Monitor,
  MoonNight,
  Sunny
} from '@element-plus/icons-vue'
import QrcodeVue from 'qrcode.vue'
import axios from 'axios'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const { isDark, toggleDark } = useTheme()

const downloadUrl = ref('')
const latestVersion = ref(null)
const apkStatus = ref('loading')

const fullDownloadUrl = computed(() => {
  if (!downloadUrl.value) return ''
  if (downloadUrl.value.startsWith('http')) {
    return downloadUrl.value
  }

  const baseUrl = window.location.origin
  return `${baseUrl}${downloadUrl.value.startsWith('/') ? '' : '/'}${downloadUrl.value}`
})

const hasDownload = computed(() => Boolean(fullDownloadUrl.value))

const versionText = computed(() => {
  if (latestVersion.value?.version_name) {
    return `v${latestVersion.value.version_name}`
  }
  if (apkStatus.value === 'loading') {
    return '版本同步中'
  }
  return '暂无版本信息'
})

const downloadHint = computed(() => {
  if (hasDownload.value) {
    return '保持与后台同一入口，扫码即可快速安装最新版。'
  }
  if (apkStatus.value === 'loading') {
    return '正在读取最新发布记录，请稍候。'
  }
  return '当前未获取到可用安装包，可稍后重试。'
})

const downloadEmptyText = computed(() => {
  if (apkStatus.value === 'loading') {
    return '正在同步安装包信息'
  }
  return '暂未提供安卓安装包'
})

const enterSystem = () => {
  router.push('/admin/meetings')
}

const fetchLatestApk = async () => {
  apkStatus.value = 'loading'

  try {
    const res = await axios.get('/api/updates/latest')
    if (res.data) {
      latestVersion.value = res.data
      downloadUrl.value = res.data.download_url || ''
      apkStatus.value = res.data.download_url ? 'ready' : 'empty'
      return
    }

    apkStatus.value = 'empty'
  } catch (error) {
    console.error('Failed to fetch latest APK:', error)
    apkStatus.value = 'error'
  }
}

onMounted(() => {
  fetchLatestApk()
})
</script>

<style scoped>
.landing-page {
  --landing-hero-bg:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.18), transparent 34%),
    radial-gradient(circle at 85% 18%, rgba(14, 165, 233, 0.14), transparent 24%),
    linear-gradient(160deg, var(--bg-color) 0%, var(--bg-main) 100%);
  --landing-glow: rgba(59, 130, 246, 0.16);
  --landing-grid-line: rgba(148, 163, 184, 0.18);
  --landing-card-shadow: 0 24px 60px -32px rgba(15, 23, 42, 0.28);
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background: var(--landing-hero-bg);
  color: var(--text-main);
}

.landing-page::before,
.landing-page::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.landing-page::before {
  background-image:
    linear-gradient(var(--landing-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--landing-grid-line) 1px, transparent 1px);
  background-size: 72px 72px;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.42), transparent 78%);
}

.landing-page::after {
  inset: auto -12% -24% auto;
  width: 520px;
  height: 520px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--landing-glow) 0%, transparent 68%);
  filter: blur(12px);
}

.landing-shell {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  padding: 28px 32px 32px;
  box-sizing: border-box;
}

.landing-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary), #06b6d4);
  color: #fff;
  box-shadow: 0 16px 30px -18px rgba(37, 99, 235, 0.8);
  font-size: 22px;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.brand-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1;
}

.brand-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.theme-toggle,
.header-link {
  height: 42px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: color-mix(in srgb, var(--card-bg) 82%, transparent);
  color: var(--text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    background-color 0.2s ease;
  box-shadow: 0 10px 24px -22px rgba(15, 23, 42, 0.55);
}

.theme-toggle:hover,
.header-link:hover {
  transform: translateY(-1px);
  color: var(--text-main);
  border-color: color-mix(in srgb, var(--color-primary) 34%, var(--border-color));
  background: color-mix(in srgb, var(--card-bg) 94%, var(--bg-main));
}

.hero-panel {
  min-height: calc(100vh - 128px);
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1.2fr);
  gap: 32px;
  align-items: center;
  grid-template-areas: 'side main';
}

.hero-content,
.download-card {
  position: relative;
  border: 1px solid color-mix(in srgb, var(--border-color) 88%, transparent);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--card-bg) 96%, transparent), color-mix(in srgb, var(--card-bg) 88%, transparent));
  box-shadow: var(--landing-card-shadow);
  backdrop-filter: blur(18px);
}

.hero-content {
  grid-area: main;
  padding: 48px 52px;
  border-radius: 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
}

.download-card {
  grid-area: side;
  padding: 28px;
  border-radius: 28px;
}

.hero-badge {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--color-primary) 24%, var(--border-color));
  background: color-mix(in srgb, var(--color-primary) 8%, var(--card-bg));
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.badge-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 6px color-mix(in srgb, currentColor 18%, transparent);
}

.hero-title {
  margin: 20px 0 18px;
  font-size: clamp(40px, 6vw, 66px);
  line-height: 1.04;
  letter-spacing: -0.04em;
  color: var(--text-main);
}

.hero-description {
  max-width: none;
  margin: 0;
  font-size: 16px;
  line-height: 1.9;
  color: var(--text-secondary);
}

.hero-highlights {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 28px 0 34px;
}

.highlight-pill {
  padding: 9px 14px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg-main) 72%, var(--card-bg));
  border: 1px solid var(--border-color);
  color: var(--text-main);
  font-size: 14px;
  font-weight: 500;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.primary-action {
  min-width: 196px;
  height: 52px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 16px 30px -18px rgba(37, 99, 235, 0.7);
}

.card-topline {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg-main) 70%, var(--card-bg));
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
}

.card-title {
  margin: 0;
  font-size: 24px;
  color: var(--text-main);
}

.card-subtitle {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.card-version {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-primary) 10%, var(--card-bg));
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.card-body {
  margin-top: 26px;
  display: grid;
  gap: 22px;
}

.qr-panel {
  min-height: 228px;
  border-radius: 24px;
  border: 1px solid var(--border-color);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--card-bg) 100%, transparent), color-mix(in srgb, var(--bg-main) 45%, var(--card-bg)));
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
}

.qr-panel-empty {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--bg-main) 60%, var(--card-bg)), color-mix(in srgb, var(--bg-main) 85%, var(--card-bg)));
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  text-align: center;
  color: var(--text-secondary);
}

.empty-state {
  min-height: 148px;
  font-size: 14px;
}

.empty-icon {
  font-size: 22px;
  color: var(--color-primary);
}

.card-notes {
  display: grid;
  gap: 12px;
}

.note-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--border-color);
  background: color-mix(in srgb, var(--bg-main) 52%, var(--card-bg));
}

.note-label {
  color: var(--text-secondary);
  font-size: 13px;
}

.note-value {
  color: var(--text-main);
  font-size: 13px;
  font-weight: 500;
  text-align: right;
}

.card-footer {
  margin-top: 20px;
}

.direct-download-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 48px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--color-primary) 10%, var(--card-bg));
  color: var(--color-primary);
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.direct-download-link:hover {
  transform: translateY(-1px);
  background: color-mix(in srgb, var(--color-primary) 16%, var(--card-bg));
}

.direct-download-link.disabled {
  color: var(--text-secondary);
  background: color-mix(in srgb, var(--bg-main) 70%, var(--card-bg));
  cursor: default;
}

html.dark .landing-page {
  --landing-hero-bg:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.22), transparent 30%),
    radial-gradient(circle at 80% 12%, rgba(14, 165, 233, 0.16), transparent 24%),
    linear-gradient(160deg, var(--bg-color) 0%, var(--bg-main) 100%);
  --landing-glow: rgba(56, 189, 248, 0.18);
  --landing-grid-line: rgba(51, 65, 85, 0.4);
  --landing-card-shadow: 0 26px 64px -36px rgba(2, 6, 23, 0.85);
}

html.dark .theme-toggle,
html.dark .header-link,
html.dark .hero-content,
html.dark .download-card {
  background:
    linear-gradient(180deg, rgba(30, 41, 59, 0.92), rgba(15, 23, 42, 0.88));
}

@media (max-width: 1080px) {
  .landing-shell {
    padding: 22px 20px 24px;
  }

  .hero-panel {
    grid-template-columns: 1fr;
    grid-template-areas:
      'main'
      'side';
    gap: 22px;
    padding-top: 28px;
  }

  .hero-content,
  .download-card {
    padding: 28px 24px;
  }
}

@media (max-width: 768px) {
  .landing-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    justify-content: stretch;
  }

  .theme-toggle,
  .header-link {
    flex: 1 1 calc(50% - 6px);
    justify-content: center;
  }

  .hero-panel {
    min-height: auto;
  }

  .hero-content {
    padding: 24px 20px;
    border-radius: 24px;
  }

  .download-card {
    padding: 24px 20px;
    border-radius: 24px;
  }

  .hero-title {
    font-size: clamp(34px, 12vw, 48px);
  }

  .hero-description {
    font-size: 15px;
  }

  .hero-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .primary-action {
    width: 100%;
    max-width: 320px;
  }

  .card-header,
  .note-item {
    flex-direction: column;
  }

  .note-value {
    text-align: left;
  }
}

@media (max-width: 520px) {
  .theme-toggle,
  .header-link {
    flex-basis: 100%;
  }

  .hero-highlights {
    gap: 10px;
  }

  .highlight-pill {
    width: 100%;
    box-sizing: border-box;
    text-align: center;
  }
}
</style>
