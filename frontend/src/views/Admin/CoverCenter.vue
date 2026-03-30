<template>
  <div class="cover-center-page">
    <div class="page-header">
      <div class="header-left">
        <el-button class="collapse-btn" link @click="goBackToToolbox">
          <el-icon size="24" color="#64748b">
            <ArrowLeft />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        <div class="title-group">
          <h1 class="page-title">封面中心</h1>
          <p class="page-subtitle">统一管理默认封面、随机封面池、类型封面策略和 Android 登录海报</p>
        </div>
      </div>

      <div class="header-right">
        <el-button plain @click="router.push('/admin/meetings')">会议管理</el-button>
        <el-button type="primary" plain @click="router.push('/admin/media')">媒体库</el-button>
      </div>
    </div>

    <div class="summary-grid">
      <el-card shadow="hover" class="summary-card">
        <div class="summary-label">会议类型</div>
        <div class="summary-value">{{ meetingTypes.length }}</div>
        <div class="summary-desc">在这里统一配置类型封面策略</div>
      </el-card>
      <el-card shadow="hover" class="summary-card">
        <div class="summary-label">固定封面类型</div>
        <div class="summary-value">{{ fixedTypeCount }}</div>
        <div class="summary-desc">其余类型会走随机池或系统默认图</div>
      </el-card>
      <el-card shadow="hover" class="summary-card">
        <div class="summary-label">公共随机池</div>
        <div class="summary-value">{{ commonPool.length }}</div>
        <div class="summary-desc">作为类型随机池缺失时的全局兜底</div>
      </el-card>
      <el-card shadow="hover" class="summary-card">
        <div class="summary-label">登录海报</div>
        <div class="summary-value">{{ hasPoster ? '已配置' : '未配置' }}</div>
        <div class="summary-desc">Android 平板登录页会优先使用这里的海报</div>
      </el-card>
    </div>

    <el-alert
      class="page-alert"
      type="info"
      :closable="false"
      show-icon
      title="管理原则"
      description="单场会议封面优先级最高，类型固定封面次之；若类型使用随机策略，则先取该类型随机池，再回退到公共随机池，最后使用系统默认封面。"
    />

    <div class="content-grid">
      <div class="main-column">
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-header">
              <div>
                <h3>系统默认封面</h3>
                <p>当前系统兜底封面来自后端内置素材，暂时只读展示</p>
              </div>
            </div>
          </template>

          <div v-if="defaultImages.length" class="image-grid">
            <div v-for="item in defaultImages" :key="item.name" class="image-card readonly">
              <div class="image-preview">
                <img :src="item.url" :alt="item.name" />
              </div>
              <div class="image-meta">
                <div class="image-name">{{ item.name }}</div>
                <div class="image-subtext">系统内置默认图</div>
              </div>
            </div>
          </div>
          <el-empty v-else description="当前没有可展示的系统默认封面" :image-size="84" />
        </el-card>

        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-header">
              <div>
                <h3>公共随机封面池</h3>
                <p>类型没有独立随机池时，会从这里稳定分配封面</p>
              </div>
              <el-upload
                action="/api/cover_center/common/upload"
                :show-file-list="false"
                :before-upload="beforeImageUpload"
                :on-success="handleCommonPoolUploadSuccess"
                :on-error="handleCommonPoolUploadError"
              >
                <el-button type="primary">上传到公共池</el-button>
              </el-upload>
            </div>
          </template>

          <div v-if="commonPool.length" class="image-grid">
            <div v-for="item in commonPool" :key="item.name" class="image-card">
              <div class="image-preview">
                <img :src="item.url" :alt="item.name" />
              </div>
              <div class="image-meta">
                <div class="image-name">{{ item.name }}</div>
                <div class="image-subtext">{{ formatUpdatedAt(item.updated_at) }}</div>
              </div>
              <div class="image-actions">
                <el-button text type="danger" @click="deleteCommonPoolItem(item)">删除</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="公共随机池还是空的，建议先准备几张通用封面" :image-size="84" />
        </el-card>

        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-header">
              <div>
                <h3>会议类型封面策略</h3>
                <p>在一个地方统一维护固定封面与类型随机池</p>
              </div>
            </div>
          </template>

          <div class="type-list">
            <div v-for="item in meetingTypes" :key="item.id" class="type-card">
              <div class="type-card-top">
                <div>
                  <div class="type-name-row">
                    <h4>{{ item.name }}</h4>
                    <el-tag size="small" :type="item.is_fixed_image ? 'warning' : 'success'" round>
                      {{ item.is_fixed_image ? '固定封面' : '随机封面' }}
                    </el-tag>
                    <el-tag size="small" type="info" round>随机池 {{ item.random_pool_count }} 张</el-tag>
                  </div>
                  <p class="type-desc">{{ item.description || '暂无说明' }}</p>
                </div>

                <div class="strategy-switch">
                  <el-button
                    :type="item.is_fixed_image ? 'default' : 'primary'"
                    plain
                    @click="switchTypeStrategy(item, false)"
                  >
                    随机封面
                  </el-button>
                  <el-button
                    :type="item.is_fixed_image ? 'primary' : 'default'"
                    plain
                    @click="switchTypeStrategy(item, true)"
                  >
                    固定封面
                  </el-button>
                </div>
              </div>

              <div v-if="item.is_fixed_image" class="fixed-cover-panel">
                <div class="fixed-cover-preview" :class="{ empty: !item.cover_image }">
                  <img v-if="item.cover_image" :src="item.cover_image" :alt="`${item.name} 固定封面`" />
                  <div v-else class="empty-cover-text">当前未设置固定封面，会议会回退到随机池或默认图</div>
                </div>
                <div class="fixed-cover-actions">
                  <el-upload
                    action="/api/meeting_types/upload_cover"
                    :show-file-list="false"
                    :before-upload="beforeImageUpload"
                    :on-success="(res) => handleTypeFixedCoverSuccess(item, res)"
                    :on-error="handleTypeFixedCoverError"
                  >
                    <el-button type="primary">上传固定封面</el-button>
                  </el-upload>
                  <el-button v-if="item.cover_image" plain @click="clearTypeFixedCover(item)">清空固定封面</el-button>
                </div>
              </div>

              <div v-else class="type-random-panel">
                <div class="type-random-header">
                  <span class="type-random-title">类型随机池</span>
                  <el-upload
                    :action="`/api/cover_center/type/${item.id}/upload`"
                    :show-file-list="false"
                    :before-upload="beforeImageUpload"
                    :on-success="() => handleTypePoolUploadSuccess(item)"
                    :on-error="handleTypePoolUploadError"
                  >
                    <el-button type="primary" plain>上传到该类型随机池</el-button>
                  </el-upload>
                </div>

                <div v-if="item.random_pool?.length" class="mini-grid">
                  <div v-for="cover in item.random_pool" :key="cover.name" class="mini-image-card">
                    <img :src="cover.url" :alt="cover.name" />
                    <button class="mini-delete-btn" @click="deleteTypePoolItem(item, cover)">删除</button>
                  </div>
                </div>
                <div v-else class="inline-empty">
                  该类型暂时没有独立随机池，实际展示时会回退到公共随机池。
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <div class="side-column">
        <el-card shadow="hover" class="section-card side-card">
          <template #header>
            <div class="section-header">
              <div>
                <h3>Android 登录海报</h3>
                <p>统一在这里替换平板登录页海报</p>
              </div>
            </div>
          </template>

          <div class="poster-preview" :class="{ empty: !posterPreviewUrl }">
            <img v-if="posterPreviewUrl" :src="posterPreviewUrl" alt="Android 登录海报" />
            <div v-else class="poster-empty-text">当前未设置登录海报，安卓端会回退到本地默认图</div>
          </div>

          <div class="poster-actions">
            <el-upload
              action="/api/settings/upload_login_poster"
              :show-file-list="false"
              :before-upload="beforeImageUpload"
              :on-success="handlePosterUploadSuccess"
              :on-error="handlePosterUploadError"
            >
              <el-button type="primary">{{ posterPreviewUrl ? '替换海报' : '上传海报' }}</el-button>
            </el-upload>
            <el-button v-if="posterPreviewUrl" plain @click="clearPoster">清空海报</el-button>
          </div>

          <div class="side-hint-list">
            <div>推荐尺寸：1920 x 1080</div>
            <div>推荐比例：16:9</div>
            <div>支持格式：JPG / PNG / WebP</div>
            <div>文件大小：不超过 5MB</div>
          </div>
        </el-card>

        <el-card shadow="hover" class="section-card side-card">
          <template #header>
            <div class="section-header">
              <div>
                <h3>会议专属封面</h3>
                <p>单场会议如果需要独立封面，仍在会议编辑中设置</p>
              </div>
            </div>
          </template>

          <div class="exclusive-card">
            <p>这里负责统一的全局规则与类型规则；单场会议封面属于临时特例，继续在会议管理里操作更合适。</p>
            <el-button type="primary" plain @click="router.push('/admin/meetings')">前往会议管理</el-button>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()

const overview = ref({
  settings: {},
  default_images: [],
  common_pool: [],
  meeting_types: []
})

const loading = ref(false)

const defaultImages = computed(() => overview.value.default_images || [])
const commonPool = computed(() => overview.value.common_pool || [])
const meetingTypes = computed(() => overview.value.meeting_types || [])
const settings = computed(() => overview.value.settings || {})
const fixedTypeCount = computed(() => meetingTypes.value.filter(item => item.is_fixed_image).length)
const hasPoster = computed(() => Boolean(settings.value.android_login_poster_url))
const posterPreviewUrl = computed(() => {
  const url = settings.value.android_login_poster_url
  const version = settings.value.android_login_poster_version
  if (!url) return ''
  if (!version) return url
  return `${url}${url.includes('?') ? '&' : '?'}v=${encodeURIComponent(version)}`
})

const fetchOverview = async () => {
  loading.value = true
  try {
    overview.value = await request.get('/cover_center/overview')
  } catch (error) {
    ElMessage.error('加载封面中心失败')
  } finally {
    loading.value = false
  }
}

const beforeImageUpload = (rawFile) => {
  const isSupported = ['image/jpeg', 'image/png', 'image/webp'].includes(rawFile.type)
  if (!isSupported) {
    ElMessage.error('仅支持 JPG、PNG、WebP 格式')
    return false
  }
  if (rawFile.size / 1024 / 1024 > 5) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

const formatUpdatedAt = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const handleCommonPoolUploadSuccess = async () => {
  ElMessage.success('已加入公共随机池')
  await fetchOverview()
}

const handleCommonPoolUploadError = () => {
  ElMessage.error('上传到公共随机池失败')
}

const deleteCommonPoolItem = async (item) => {
  try {
    await ElMessageBox.confirm(`确定删除公共随机封面「${item.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await request.delete(`/cover_center/common/${encodeURIComponent(item.name)}`)
    ElMessage.success('已从公共随机池删除')
    await fetchOverview()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

const updateType = async (item, patch = {}) => {
  await request.put(`/meeting_types/${item.id}`, {
    name: item.name,
    description: item.description,
    is_fixed_image: item.is_fixed_image,
    cover_image: item.cover_image || null,
    ...patch
  })
}

const switchTypeStrategy = async (item, isFixedImage) => {
  if (item.is_fixed_image === isFixedImage) return

  try {
    await updateType(item, {
      is_fixed_image: isFixedImage,
      cover_image: isFixedImage ? (item.cover_image || null) : null
    })
    ElMessage.success(isFixedImage ? '已切换为固定封面模式' : '已切换为随机封面模式')
    await fetchOverview()
  } catch (error) {
    ElMessage.error('更新类型封面策略失败')
  }
}

const handleTypeFixedCoverSuccess = async (item, res) => {
  try {
    await updateType(item, {
      is_fixed_image: true,
      cover_image: res?.url || null
    })
    ElMessage.success(`已更新「${item.name}」固定封面`)
    await fetchOverview()
  } catch (error) {
    ElMessage.error('保存固定封面失败')
  }
}

const handleTypeFixedCoverError = () => {
  ElMessage.error('上传固定封面失败')
}

const clearTypeFixedCover = async (item) => {
  try {
    await updateType(item, { cover_image: null })
    ElMessage.success(`已清空「${item.name}」固定封面`)
    await fetchOverview()
  } catch (error) {
    ElMessage.error('清空固定封面失败')
  }
}

const handleTypePoolUploadSuccess = async (item) => {
  ElMessage.success(`已加入「${item.name}」随机池`)
  await fetchOverview()
}

const handleTypePoolUploadError = () => {
  ElMessage.error('上传到类型随机池失败')
}

const deleteTypePoolItem = async (item, cover) => {
  try {
    await ElMessageBox.confirm(`确定删除「${item.name}」随机池中的封面「${cover.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await request.delete(`/cover_center/type/${item.id}/${encodeURIComponent(cover.name)}`)
    ElMessage.success(`已从「${item.name}」随机池删除`)
    await fetchOverview()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

const handlePosterUploadSuccess = async () => {
  ElMessage.success('登录海报上传成功')
  await fetchOverview()
}

const handlePosterUploadError = () => {
  ElMessage.error('登录海报上传失败')
}

const clearPoster = async () => {
  try {
    await request.post('/settings/', {
      default_meeting_location: settings.value.default_meeting_location || '',
      meeting_visibility_hide_after_hours: settings.value.meeting_visibility_hide_after_hours || '0',
      android_login_poster_url: '',
      android_login_poster_version: ''
    })
    ElMessage.success('登录海报已清空')
    await fetchOverview()
  } catch (error) {
    ElMessage.error('清空登录海报失败')
  }
}

const goBackToToolbox = () => {
  router.push('/admin/toolbox')
}

onMounted(fetchOverview)
</script>

<style scoped>
.cover-center-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
  overflow-x: hidden;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  padding: 0 4px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.collapse-btn {
  padding: 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
  height: auto;
}

.collapse-btn:hover {
  background-color: var(--bg-main, #f8fafc);
}

.header-divider {
  height: 24px;
  border-color: var(--border-color, #e2e8f0);
  margin: 0 4px;
}

.title-group {
  display: flex;
  flex-direction: column;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}

.page-subtitle {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.5;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  border-radius: 16px;
}

.summary-label {
  color: var(--text-secondary);
  font-size: 13px;
}

.summary-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-main);
}

.summary-desc {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #94a3b8;
}

.page-alert {
  border-radius: 14px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, 0.9fr);
  gap: 20px;
  align-items: start;
  min-width: 0;
}

.main-column,
.side-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.section-card {
  border-radius: 18px;
  min-width: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text-main);
}

.section-header p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
}

.image-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--card-bg);
}

.image-card.readonly {
  background: linear-gradient(180deg, rgba(59, 130, 246, 0.06), transparent);
}

.image-preview {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 12px;
  overflow: hidden;
  background: #f8fafc;
}

.image-preview img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.image-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.image-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
  word-break: break-all;
}

.image-subtext {
  font-size: 12px;
  color: var(--text-secondary);
}

.image-actions {
  display: flex;
  justify-content: flex-end;
}

.type-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.type-card {
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--border-color);
  background: var(--card-bg);
}

.type-card-top {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.type-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.type-name-row h4 {
  margin: 0;
  font-size: 17px;
  color: var(--text-main);
}

.type-desc {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.strategy-switch {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.fixed-cover-panel,
.type-random-panel {
  margin-top: 16px;
}

.fixed-cover-preview,
.poster-preview {
  width: 100%;
  box-sizing: border-box;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid #dbeafe;
  background: linear-gradient(135deg, #eff6ff, #e0f2fe);
}

.fixed-cover-preview {
  aspect-ratio: 16 / 9;
}

.poster-preview {
  min-height: 220px;
  max-height: 360px;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fixed-cover-preview img,
.poster-preview img {
  object-fit: contain;
  object-position: center;
  background: #f8fafc;
}

.fixed-cover-preview img {
  width: 100%;
  height: 100%;
  display: block;
}

.poster-preview img {
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 328px;
  display: block;
}

.fixed-cover-preview.empty,
.poster-preview.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.empty-cover-text,
.poster-empty-text {
  text-align: center;
  color: #64748b;
  line-height: 1.7;
  font-size: 13px;
}

.fixed-cover-actions,
.poster-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.type-random-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.type-random-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.mini-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(116px, 1fr));
  gap: 12px;
}

.mini-image-card {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  background: #f8fafc;
  aspect-ratio: 16 / 9;
}

.mini-image-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.mini-delete-btn {
  position: absolute;
  right: 8px;
  bottom: 8px;
  border: none;
  border-radius: 999px;
  padding: 4px 8px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  cursor: pointer;
  font-size: 12px;
}

.inline-empty {
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.side-card {
  position: static;
}

.side-hint-list {
  margin-top: 14px;
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: #64748b;
}

.exclusive-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.exclusive-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .side-card {
    position: static;
  }
}

@media (max-width: 768px) {
  .page-header,
  .section-header,
  .type-card-top,
  .type-random-header {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .image-grid,
  .mini-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
