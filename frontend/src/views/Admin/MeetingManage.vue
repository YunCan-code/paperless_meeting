<template>
  <div class="meeting-manage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <el-button 
          class="collapse-btn" 
          link 
          @click="toggleSidebar"
        >
          <el-icon size="24" color="#64748b">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        
        <div class="title-group">
          <h1 class="page-title">会议管理</h1>
          <p class="page-subtitle">管理会议日程与文件分发</p>
        </div>
      </div>
    </div>

    <!-- 顶部统计卡片 (Sessions Overview) -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="12" :sm="12" :md="6" :span="6" v-for="(stat, index) in statsData" :key="index">
        <el-card shadow="hover" class="stat-card" :class="{ 'clickable': stat.clickable }" @click="stat.onClick && stat.onClick()">
          <div class="stat-content">
            <div class="stat-icon" :class="stat.bgClass">
              <el-icon :class="stat.textClass" :size="24">
                <component :is="stat.icon" />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">{{ stat.title }}</div>
              <div class="stat-value">
                {{ stat.value }}
                <span class="stat-trend" :class="stat.trend >= 0 ? 'up' : 'down'" v-if="stat.trend !== undefined">
                  <el-icon><component :is="stat.trend >= 0 ? 'Top' : 'Bottom'" /></el-icon>
                  {{ Math.abs(stat.trend) }}%
                </span>
              </div>
              <!-- <div class="stat-desc">{{ stat.subtitle }}</div> -->
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主体区域: 日历 (2/3) + 今日会议 (1/3) -->
    <el-row :gutter="24" class="main-content-row">
      <el-col :xs="24" :sm="24" :md="16" :span="16" ref="calendarColRef">
        <SessionCalendar 
           :meetings="meetings" 
           @create="openCreate" 
           @select-date="(val) => currentSelectedDate = val"
        />
      </el-col>
      <el-col :xs="24" :sm="24" :md="8" :span="8" class="today-col">
        <TodayMeetings 
           class="today-component"
           :meetings="meetings" 
           :meeting-types="meetingTypes" 
           :date="currentSelectedDate"
           :style="calendarHeight ? { maxHeight: calendarHeight + 'px' } : {}"
           @create="openCreate" 
           @view="viewDetails" 
        />
      </el-col>
    </el-row>

    <!-- 会议历史列表 -->
    <MeetingHistory 
      :meetings="meetings" 
      :meeting-types="meetingTypes" 
      @view="viewDetails"
      @upload="handleUploadClick"
    />

    <!-- 文件列表抽屉 -->
    <el-drawer v-model="filesDrawerVisible" title="会议文件列表" size="500px">
      <div class="files-drawer-content">
        <div v-if="allFilesList.length === 0" class="empty-state" style="padding: 40px;">
          <el-icon :size="48" color="#e2e8f0"><FolderOpened /></el-icon>
          <p>暂无已上传的会议文件</p>
        </div>
        <div v-else class="files-list">
          <div v-for="file in allFilesList" :key="file.id" class="file-card">
            <div class="file-card-left">
              <div class="file-icon-box">
                <el-icon><Document /></el-icon>
              </div>
              <div class="file-info">
                <div class="file-name">{{ file.display_name }}</div>
                <div class="file-meeting">{{ file.meetingTitle }}</div>
                <div class="file-date">{{ new Date(file.meetingDate).toLocaleDateString() }} · {{ formatSize(file.file_size) }}</div>
              </div>
            </div>
            <el-button link type="primary" @click="downloadFile(file)">
              <el-icon><Download /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <div style="color: #94a3b8; font-size: 13px;">共 {{ allFilesList.length }} 个文件</div>
      </template>
    </el-drawer>

    <!-- 会议详情对话框 -->
    <el-dialog 
      v-model="detailDialogVisible" 
      title="会议详情" 
      width="1100px" 
      destroy-on-close 
      align-center
      class="meeting-dialog detail-mode"
    >
      <div class="dialog-layout" v-if="currentDetail">
        <div class="dialog-left">
          <!-- 标题区域 -->
          <div class="detail-header-section">
             <h3 class="detail-main-title">{{ currentDetail.title }}</h3>
          </div>

          <!-- 信息网格 -->
          <div class="detail-meta-list">
            <div class="meta-card">
              <div class="meta-icon bg-blue-50 text-blue-500"><el-icon><CollectionTag /></el-icon></div>
              <div class="meta-info">
                <div class="meta-label">会议类型</div>
                <div class="meta-value highlight">{{ getTypeName(currentDetail.meeting_type_id) }}</div>
              </div>
            </div>

            <div class="meta-card">
              <div class="meta-icon bg-green-50 text-green-500"><el-icon><Clock /></el-icon></div>
              <div class="meta-info">
                <div class="meta-label">会议时间</div>
                <div class="meta-value">{{ formatMeetingTimeRange(currentDetail.start_time, currentDetail.end_time) }}</div>
              </div>
            </div>

            <div class="meta-card" v-if="currentDetail.attendees && currentDetail.attendees.length > 0">
               <div class="meta-icon bg-orange-50 text-orange-500"><el-icon><User /></el-icon></div>
               <div class="meta-info">
                 <div class="meta-label">参会人员 ({{currentDetail.attendees.length}}人)</div>
                 <div class="meta-value" style="display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px;">
                    <el-tag 
                      v-for="(a, idx) in sortedDetailAttendees" 
                      :key="`${a.type || 'user'}-${a.user_id ?? a.name}-${idx}`" 
                      size="small" 
                       style="max-width: 100%; height: auto; white-space: normal; word-break: break-all; line-height: 1.6; padding: 2px 8px;"
                       :type="a.meeting_role === '主讲人' ? 'danger' : (a.meeting_role === '特邀嘉宾' ? 'warning' : 'info')"
                     >
                       {{ a.name }}
                      <span style="opacity: 0.7; font-size: 11px; margin-left: 2px;">
                        [{{ a.meeting_role }}{{ a.type === 'manual' ? ' · 手填' : '' }}]
                      </span>
                    </el-tag>
                 </div>
               </div>
            </div>

            <div class="meta-card meta-card-agenda" v-if="detailAgendaItems.length > 0">
              <div class="meta-icon bg-yellow-50 text-yellow-500"><el-icon><List /></el-icon></div>
              <div class="meta-info">
                <div class="meta-label">主要内容及议程</div>
                <div class="agenda-list" style="margin-top: 6px;">
                  <div v-for="(item, idx) in detailAgendaItems" :key="idx" class="agenda-item">
                    <span class="agenda-index">{{ idx + 1 }}</span>
                    <span class="agenda-content">{{ item.content }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="meta-card" v-if="detailContacts.length > 0">
              <div class="meta-icon bg-blue-50 text-blue-500"><el-icon><Phone /></el-icon></div>
              <div class="meta-info">
                <div class="meta-label">会议联系人 ({{ detailContacts.length }}位)</div>
                <div class="contact-list">
                  <div v-for="(contact, idx) in detailContacts" :key="`${contact.name}-${idx}`" class="contact-item">
                    <div class="contact-name">{{ contact.name }}</div>
                    <div class="contact-lines">
                      <span v-if="contact.short_phone">短号：{{ contact.short_phone }}</span>
                      <span v-if="contact.phone">长号：{{ contact.phone }}</span>
                      <span v-if="contact.email">邮箱：{{ contact.email }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 会议地点（最后显示） -->
            <div class="meta-card">
              <div class="meta-icon bg-purple-50 text-purple-500"><el-icon><LocationInformation /></el-icon></div>
              <div class="meta-info">
                <div class="meta-label">会议地点</div>
                <div class="meta-value">{{ currentDetail.location || '线上会议 / 未指定' }}</div>
              </div>
            </div>
          </div>
        </div>
        
        <div class="dialog-right">
          <div class="section-header">
            <h4 class="section-title">会议资料 ({{ currentDetail.attachments?.length || 0 }})</h4>
          </div>
          <!-- 安卓端媒体入口状态（只读） -->
          <div class="media-link-toggle" :class="{ active: currentDetail.show_media_link }">
            <div class="media-link-toggle-left">
              <div class="media-link-dot"></div>
              <span class="media-link-label">安卓端提示点击媒体页</span>
            </div>
            <el-tag :type="currentDetail.show_media_link ? 'success' : 'info'" size="small" round>
              {{ currentDetail.show_media_link ? '已启用' : '未启用' }}
            </el-tag>
          </div>
          <div class="file-list-container">
             <div v-if="!currentDetail.attachments || currentDetail.attachments.length === 0" class="empty-state">
              <el-icon size="40" color="#e2e8f0"><FolderOpened /></el-icon>
              <p style="font-size: 13px;">暂无相关资料</p>
            </div>
            <div v-else class="file-list">
              <div v-for="file in currentDetail.attachments" :key="file.id" class="file-item read-only">
                <el-icon class="file-icon"><Document /></el-icon>
                <div class="file-content">
                  <span class="file-name">{{ file.display_name }}</span>
                  <span class="file-meta">{{ formatSize(file.file_size) }}</span>
                </div>
                <!-- 预留下载按钮 -->
                <el-button link type="primary" size="small" @click="downloadFile(file)"><el-icon><Download /></el-icon></el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <template #footer>
        <div class="dialog-footer detail-footer">
           <div class="footer-left"></div> <!-- Spacer -->
           <div class="footer-actions">
              <el-button @click="handleDeleteMeeting" type="danger" plain>删除会议</el-button>
              <el-button @click="openEdit" type="primary">编辑会议</el-button>
           </div>
        </div>
      </template>
    </el-dialog>

    <!-- 创建/编辑会议对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="isEditMode ? '编辑会议' : '发起新会议'" 
      width="1100px" 
      destroy-on-close 
      align-center
      class="meeting-dialog edit-mode"
    >
      <div class="dialog-layout">
        <!-- 左侧：会议信息 -->
        <div class="dialog-left modern-form-container">
          <el-form :model="form" label-position="top" size="large" class="meeting-form modern-meeting-form">
            <!-- 基础设置 -->
            <!-- 基础设置 -->
            <div class="form-section">
              <el-form-item class="modern-form-item is-required">
                <template #label>
                  <div class="form-item-label-with-icon">
                    <el-icon><Document /></el-icon> 会议主题
                  </div>
                </template>
                <el-input 
                   v-model="form.title" 
                   placeholder="请输入会议主题" 
                   type="textarea"
                   :autosize="{ minRows: 1, maxRows: 3 }"
                />
              </el-form-item>

              <el-form-item class="modern-form-item is-required">
                <template #label>
                  <div class="form-item-label-with-icon">
                    <el-icon><CollectionTag /></el-icon> 会议类型
                  </div>
                </template>
                <el-select v-model="form.meeting_type_id" placeholder="请选择会议类型" style="width: 100%" effect="light">
                  <el-option v-for="item in meetingTypes" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
            </div>

            <el-divider class="form-divider" />

            <!-- 时间 -->
            <div class="form-section">
              <el-form-item class="modern-form-item is-required">
                <template #label>
                  <div class="form-item-label-with-icon">
                    <el-icon><Clock /></el-icon> 会议时间
                  </div>
                </template>
                <button
                  v-if="meetingTimePreview && !timeEditorVisible"
                  type="button"
                  class="meeting-time-preview"
                  @click.stop="timeEditorVisible = true"
                >
                  <div class="meeting-time-preview-main">
                    <el-icon><Calendar /></el-icon>
                    <span>{{ meetingTimePreview.fullLabel }}</span>
                  </div>
                  <div class="meeting-time-preview-action">
                    <el-icon><EditPen /></el-icon>
                    <span>修改</span>
                  </div>
                </button>
                <div v-else class="time-grid-modern-wrapper">
                  <div class="time-grid-modern">
                    <el-date-picker 
                      v-model="form.start_time" 
                      type="datetime" 
                      placeholder="开始时间" 
                      style="width: 100%" 
                      format="YYYY-MM-DD HH:mm"
                    />
                    <div class="time-separator"><el-icon><Right /></el-icon></div>
                    <el-date-picker
                      v-model="form.end_time"
                      type="datetime"
                      placeholder="结束时间"
                      style="width: 100%"
                      format="YYYY-MM-DD HH:mm"
                    />
                  </div>
                  <div v-if="meetingTimePreview" class="time-editor-actions" style="display: flex; justify-content: flex-end; margin-top: 8px;">
                    <el-button text type="primary" @click.stop="timeEditorVisible = false" size="small">
                      完成时间设置
                    </el-button>
                  </div>
                </div>
              </el-form-item>
            </div>

            <el-divider class="form-divider" />

            <!-- 详细内容与地点 -->
            <div class="form-section">
              <el-form-item class="modern-form-item">
                <template #label>
                  <div class="form-item-label-with-icon">
                    <el-icon><User /></el-icon> 参会人员
                  </div>
                </template>
                <div class="dynamic-list">
                  <div v-for="(attendee, index) in form.attendee_entries" :key="index" class="dynamic-list-item align-center">
                    <el-popover
                       placement="bottom-start"
                       :width="280"
                       trigger="click"
                       popper-class="attendee-popover"
                       :visible="attendee.showPopover"
                    >
                       <template #reference>
                          <el-input 
                             v-model="attendee.user_name"
                             type="textarea"
                             :autosize="{ minRows: 1, maxRows: 4 }"
                             placeholder="输入姓名或在此搜索"
                             class="flex-1"
                             @input="handleAttendeeInput(attendee)"
                             @focus="attendee.showPopover = true"
                             @click="attendee.showPopover = true"
                             @blur="hidePopoverDelay(attendee)"
                          />
                       </template>
                       <div class="attendee-suggestions">
                          <div v-if="getSuggestions(attendee.user_name).length === 0" class="no-data">无相关系统人员，直接点击外部即可作为自定义人员</div>
                          <div 
                             v-for="u in getSuggestions(attendee.user_name)" 
                             :key="u.id" 
                             class="suggestion-item"
                             @mousedown.prevent
                             @click="handleUserSelect(attendee, u)"
                          >
                             <span class="sugg-name">{{ u.name }}</span>
                             <span v-if="u.department" class="sugg-dept">{{ u.department }}</span>
                          </div>
                       </div>
                    </el-popover>
                    <el-select
                       v-model="attendee.meeting_role"
                       placeholder="角色"
                       style="width: 110px; flex-shrink: 0;"
                       effect="light"
                    >
                       <el-option label="参会人员" value="参会人员"/>
                       <el-option label="主讲人" value="主讲人"/>
                       <el-option label="特邀嘉宾" value="特邀嘉宾"/>
                    </el-select>
                    <el-button type="danger" link class="delete-btn-subtle" @click.stop="removeAttendee(index)">
                       <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                  <el-button type="primary" link class="add-btn-subtle" @click.stop="addAttendee">
                     <el-icon><Plus /></el-icon> 添加参会人员
                  </el-button>
                </div>
              </el-form-item>

              <el-form-item class="modern-form-item">
                <template #label>
                  <div class="form-item-label-with-icon">
                    <el-icon><List /></el-icon> 主要内容及议程
                  </div>
                </template>
                <div class="dynamic-list">
                  <div v-for="(item, index) in form.agendaItems" :key="index" class="dynamic-list-item align-center">
                    <span class="list-index">{{ index + 1 }}</span>
                    <el-input 
                       v-model="item.content" 
                       placeholder="输入议程内容" 
                       class="flex-1" 
                       type="textarea" 
                       :autosize="{ minRows: 1, maxRows: 4 }" 
                    />
                    <el-button type="danger" link class="delete-btn-subtle" @click.stop="removeAgendaItem(index)">
                       <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                  <el-button type="primary" link class="add-btn-subtle" @click.stop="addAgendaItem">
                     <el-icon><Plus /></el-icon> 添加议程
                  </el-button>
                </div>
              </el-form-item>

              <el-form-item class="modern-form-item">
                <template #label>
                  <div class="form-item-label-with-icon">
                    <el-icon><Phone /></el-icon> 会议联系人
                  </div>
                </template>
                <div class="dynamic-list">
                  <div v-for="(contact, index) in form.meeting_contacts" :key="index" class="dynamic-list-item contact-item-modern">
                    <span class="list-index" style="align-self: flex-start; margin-top: 10px;">{{ index + 1 }}</span>
                    <div class="contact-inputs flex-1" style="display: flex; flex-direction: column; gap: 8px;">
                      <!-- 第一行：姓名 -->
                      <el-input 
                        v-model="contact.name" placeholder="请输入姓名" 
                        type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" 
                      />
                      <!-- 第二行：电话 -->
                      <div class="contact-row" style="display: flex; gap: 8px;">
                        <el-input 
                          v-model="contact.phone" placeholder="长号" style="flex: 1;"
                          type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" 
                        />
                        <el-input 
                          v-model="contact.short_phone" placeholder="短号" style="flex: 1;"
                          type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" 
                        />
                      </div>
                      <!-- 第三行：邮箱 -->
                      <el-input 
                        v-model="contact.email" placeholder="请输入邮箱地址" 
                        type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" 
                      />
                    </div>
                    <el-button type="danger" link class="delete-btn-subtle contact-delete" @click.stop="removeContact(index)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                  <el-button type="primary" link class="add-btn-subtle" @click.stop="addContact">
                    <el-icon><Plus /></el-icon> 添加联系人
                  </el-button>
                </div>
              </el-form-item>

              <el-form-item class="modern-form-item">
                <template #label>
                  <div class="form-item-label-with-icon">
                    <el-icon><LocationInformation /></el-icon> 会议地点
                  </div>
                </template>
                <el-input 
                   v-model="form.location" 
                   placeholder="例如：1号会议室 / 线上会议" 
                   type="textarea" 
                   :autosize="{ minRows: 1, maxRows: 4 }" 
                />
              </el-form-item>
            </div>
          </el-form>
        </div>
      
        <!-- 右侧：文件管理 -->
        <div class="dialog-right">
          <div class="section-header">
            <div>
              <h4 class="section-title">会议资料</h4>
              <div class="section-tip">仅支持上传 PDF 格式文件（安卓端仅支持 PDF 预览）</div>
            </div>
            <el-upload
              action=""
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleFileSelect"
              accept="application/pdf"
              multiple
            >
              <el-button type="primary" link size="small"><el-icon class="el-icon--left"><Plus/></el-icon>添加文件</el-button>
            </el-upload>
          </div>

          <!-- 媒体入口开关 -->
          <div class="media-link-toggle" :class="{ active: form.show_media_link }">
            <div class="media-link-toggle-left">
              <div class="media-link-dot"></div>
              <span class="media-link-label">安卓端提示点击媒体页</span>
            </div>
            <el-switch v-model="form.show_media_link" size="small" />
          </div>
          <div class="file-list-container">
            <div v-if="attachmentList.length === 0" class="empty-state">
              <el-icon :size="48" color="#e2e8f0"><UploadFilled /></el-icon>
              <p>暂无文件，点击上方按钮添加</p>
            </div>
            
            <div v-else class="file-list">
              <div v-for="(file, index) in attachmentList" :key="file.id" class="file-item">
                <div class="file-icon">
                  <el-icon><Document /></el-icon>
                </div>
                <div class="file-content">
                  <div style="display: flex; align-items: center; gap: 8px;">
                     <el-input v-model="file.name" size="small" class="name-input" placeholder="文件名" />
                     <el-tag v-if="file.type === 'new'" size="small" type="danger" effect="plain" round>NEW</el-tag>
                  </div>
                  <span class="file-meta">{{ formatSize(file.size) }}</span>
                </div>
                <div class="file-actions">
                  <el-tooltip content="上移" placement="top" :show-after="500">
                    <el-button circle size="small" @click="moveFile(index, -1)" :disabled="index === 0">
                      <el-icon><Top /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="下移" placement="top" :show-after="500">
                    <el-button circle size="small" @click="moveFile(index, 1)" :disabled="index === attachmentList.length - 1">
                      <el-icon><Bottom /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-button circle size="small" type="danger" plain @click="removeFile(index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <div class="footer-tip">
            <span v-if="attachmentList.length > 0">共 {{ attachmentList.length }} 个文件</span>
          </div>
          <div class="footer-btns">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSubmit" :loading="submitting">
              {{ isEditMode ? '保存修改' : '确认发起' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>


  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch, reactive } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Calendar, Timer as Clock, User, CircleCheck, Fold, Expand, 
  Document, UploadFilled, Top, Bottom, Delete, Edit, Plus,
  CollectionTag, LocationInformation, FolderOpened, Download, DataAnalysis, List, Phone, Right, VideoPlay
} from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'
import SessionCalendar from './components/SessionCalendar.vue'

import TodayMeetings from './components/TodayMeetings.vue'
import MeetingHistory from './components/MeetingHistory.vue'

const { isCollapse, toggleSidebar } = useSidebar()

const meetings = ref([])
const meetingTypes = ref([])
const loading = ref(false)

// Dialogs
const dialogVisible = ref(false)
const detailDialogVisible = ref(false)
const currentDetail = ref(null)

// Files Drawer
const filesDrawerVisible = ref(false)
const allFilesList = computed(() => {
    const files = []
    meetings.value.forEach(m => {
        if (m.attachments && m.attachments.length > 0) {
            m.attachments.forEach(a => {
                files.push({
                    ...a,
                    meetingTitle: m.title,
                    meetingDate: m.start_time
                })
            })
        }
    })
    // Sort by date descending
    files.sort((a, b) => new Date(b.meetingDate) - new Date(a.meetingDate))
    return files
})

// Forms & States
const submitting = ref(false)
const isEditMode = ref(false)
const editingId = ref(null)

const form = ref({ 
  title: '', 
  meeting_type_id: null, 
  start_time: null, 
  end_time: null,
  location: '',
  attendee_entries: [],
  agendaItems: [],
  meeting_contacts: []
})
const timeEditorVisible = ref(true)


const currentSelectedDate = ref(new Date())

// 监听日历列高度，用于限制今日会议卡片最大高度
const calendarColRef = ref(null)
const calendarHeight = ref(0)
let resizeObserver = null

const updateCalendarHeight = () => {
  const el = calendarColRef.value?.$el || calendarColRef.value
  if (el) {
    calendarHeight.value = el.offsetHeight
  }
}

onMounted(() => {
  nextTick(() => {
    const el = calendarColRef.value?.$el || calendarColRef.value
    if (el) {
      updateCalendarHeight()
      resizeObserver = new ResizeObserver(() => updateCalendarHeight())
      resizeObserver.observe(el)
    }
  })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})

// Stats State
const stats = ref({
  yearly_count: 0,
  yearly_growth: 0,
  monthly_count: 0,
  monthly_growth: 0,
  weekly_count: 0,
  weekly_growth: 0,
  total_storage_bytes: 0,
  storage_growth: 0
})

const fetchStats = async () => {
  try {
    const res = await request.get('/meetings/stats')
    if (res) {
      stats.value = res
    }
  } catch(e) { 
    console.error('Failed to fetch stats', e)
  }
}

onMounted(async () => {
  await fetchMeetingTypes()
  await fetchMeetings()
  fetchStats()
})



const statsData = computed(() => {
  if (!stats.value) return []
  
  const formatBytesSimple = (bytes) => {
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      if (bytes === 0) return '0 B'
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
  }
  const storageStr = formatBytesSimple(stats.value.total_storage_bytes || 0)

  return [
    { 
      title: '本年会议数', 
      value: stats.value.yearly_count, 
      trend: stats.value.yearly_growth || 0,
      icon: 'DataAnalysis', 
      bgClass: 'bg-purple-50', 
      textClass: 'text-purple-500' 
    },
    { 
      title: '本月会议数', 
      value: stats.value.monthly_count, 
      trend: stats.value.monthly_growth || 0,
      icon: 'Calendar', 
      bgClass: 'bg-blue-50', 
      textClass: 'text-blue-500' 
    },
    { 
      title: '本周会议数', 
      value: stats.value.weekly_count, 
      trend: stats.value.weekly_growth || 0,
      icon: 'CollectionTag', 
      bgClass: 'bg-orange-50', 
      textClass: 'text-orange-500' 
    },
    { 
      title: '文件存储', 
      value: storageStr, 
      trend: stats.value.storage_growth || 0,
      icon: 'FolderOpened', 
      bgClass: 'bg-green-50', 
      textClass: 'text-green-500',
      clickable: true,
      onClick: () => { filesDrawerVisible.value = true }
    }
  ]
})


const getTypeName = (id) => meetingTypes.value.find(t => t.id === id)?.name || id
const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

const meetingRoleOrder = {
  '主讲人': 0,
  '特邀嘉宾': 1,
  '参会人员': 2
}

const sortAttendees = (list = []) => [...list].sort((a, b) => {
  const orderDiff = (meetingRoleOrder[a.meeting_role] ?? 3) - (meetingRoleOrder[b.meeting_role] ?? 3)
  if (orderDiff !== 0) return orderDiff
  return String(a.name || '').localeCompare(String(b.name || ''), 'zh-CN')
})

const sortedDetailAttendees = computed(() => sortAttendees(currentDetail.value?.attendees || []))

const parseAgendaItems = (agendaItems = [], agendaJson = '') => {
  if (Array.isArray(agendaItems) && agendaItems.length > 0) {
    return agendaItems
      .map(item => ({ content: String(item.content || '').trim() }))
      .filter(item => item.content)
  }

  try {
    const parsed = JSON.parse(agendaJson || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed
      .map(item => ({ content: String(item?.content || '').trim() }))
      .filter(item => item.content)
  } catch (e) {
    return []
  }
}

const detailAgendaItems = computed(() => parseAgendaItems(currentDetail.value?.agenda_items, currentDetail.value?.agenda))
const detailContacts = computed(() => Array.isArray(currentDetail.value?.meeting_contacts) ? currentDetail.value.meeting_contacts : [])

const weekdayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const weekdayShortNames = ['日', '一', '二', '三', '四', '五', '六']
const formatTwoDigits = (value) => String(value).padStart(2, '0')
const DAY_IN_MS = 24 * 60 * 60 * 1000
const USER_OPTION_PREFIX = 'user:'

const getDayPeriod = (date) => {
  const hour = date.getHours()
  if (hour < 6) return '凌晨'
  if (hour < 12) return '上午'
  if (hour < 14) return '中午'
  if (hour < 18) return '下午'
  return '晚上'
}

const normalizeDate = (value) => {
  if (!value) return null
  const date = value instanceof Date ? new Date(value.getTime()) : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

const startOfWeek = (date) => {
  const result = new Date(date)
  result.setHours(0, 0, 0, 0)
  const dayOffset = (result.getDay() + 6) % 7
  result.setDate(result.getDate() - dayOffset)
  return result
}

const getWeekdayContext = (date) => {
  const currentWeekStart = startOfWeek(new Date())
  const targetWeekStart = startOfWeek(date)
  const diffWeeks = Math.round((targetWeekStart.getTime() - currentWeekStart.getTime()) / (7 * DAY_IN_MS))
  const weekday = weekdayShortNames[date.getDay()]

  if (diffWeeks === 0) return `本周${weekday}`
  if (diffWeeks === 1) return `下周${weekday}`
  if (diffWeeks === -1) return `上周${weekday}`
  return weekdayNames[date.getDay()]
}

const formatClockTime = (date) => `${formatTwoDigits(date.getHours())}:${formatTwoDigits(date.getMinutes())}`

const formatPreviewDateLabel = (date) => `${date.getMonth() + 1}月${date.getDate()}日（${getWeekdayContext(date)}）`

const formatMeetingTimePreviewData = (start, end) => {
  const startDate = normalizeDate(start)
  if (!startDate) return null

  const dateLabel = formatPreviewDateLabel(startDate)
  const startTime = formatClockTime(startDate)
  const endDate = normalizeDate(end)

  if (!endDate) {
    return {
      dateLabel,
      timeLabel: `${getDayPeriod(startDate)} ${startTime}`,
      fullLabel: `${dateLabel} ${getDayPeriod(startDate)} ${startTime}`
    }
  }

  const endTime = formatClockTime(endDate)
  const isSameDay =
    startDate.getFullYear() === endDate.getFullYear() &&
    startDate.getMonth() === endDate.getMonth() &&
    startDate.getDate() === endDate.getDate()

  if (isSameDay) {
    const startPeriod = getDayPeriod(startDate)
    const endPeriod = getDayPeriod(endDate)
    if (startPeriod === endPeriod) {
      return {
        dateLabel,
        timeLabel: `${startPeriod} ${startTime}-${endTime}`,
        fullLabel: `${dateLabel} ${startPeriod} ${startTime}-${endTime}`
      }
    }
    return {
      dateLabel,
      timeLabel: `${startPeriod} ${startTime} - ${endPeriod} ${endTime}`,
      fullLabel: `${dateLabel} ${startPeriod} ${startTime} - ${endPeriod} ${endTime}`
    }
  }

  return {
    dateLabel,
    timeLabel: `${getDayPeriod(startDate)} ${startTime} - ${formatPreviewDateLabel(endDate)} ${getDayPeriod(endDate)} ${endTime}`,
    fullLabel: `${dateLabel} ${getDayPeriod(startDate)} ${startTime} - ${formatPreviewDateLabel(endDate)} ${getDayPeriod(endDate)} ${endTime}`
  }
}

const formatMeetingTimeRange = (start, end) => {
  const startDate = normalizeDate(start)
  if (!startDate) return start ? String(start) : '未设置'

  const startLabel = `${startDate.getMonth() + 1}月${startDate.getDate()}日 ${weekdayNames[startDate.getDay()]}`
  const startTime = formatClockTime(startDate)

  if (!end) {
    return `${startLabel} ${getDayPeriod(startDate)} ${startTime}`
  }

  const endDate = normalizeDate(end)
  if (!endDate) {
    return `${startLabel} ${getDayPeriod(startDate)} ${startTime}`
  }

  const endTime = formatClockTime(endDate)
  const isSameDay =
    startDate.getFullYear() === endDate.getFullYear() &&
    startDate.getMonth() === endDate.getMonth() &&
    startDate.getDate() === endDate.getDate()

  if (isSameDay) {
    return `${startLabel} ${getDayPeriod(startDate)} ${startTime}-${endTime}`
  }

  const endLabel = `${endDate.getMonth() + 1}月${endDate.getDate()}日 ${weekdayNames[endDate.getDay()]}`
  return `${startLabel} ${getDayPeriod(startDate)} ${startTime} 至 ${endLabel} ${getDayPeriod(endDate)} ${endTime}`
}

const resetActiveCards = () => {}
const meetingTimePreview = computed(() => formatMeetingTimePreviewData(form.value.start_time, form.value.end_time))
watch(
  () => [form.value.start_time, form.value.end_time],
  ([start, end], [prevStart, prevEnd] = []) => {
    const hasCompleteRange = Boolean(normalizeDate(start) && normalizeDate(end))
    const hadCompleteRange = Boolean(normalizeDate(prevStart) && normalizeDate(prevEnd))

    if (!hasCompleteRange) {
      timeEditorVisible.value = true
      return
    }

    if (!hadCompleteRange) {
      timeEditorVisible.value = false
    }
  },
  { immediate: true }
)

const createUserOptionValue = (userId) => `${USER_OPTION_PREFIX}${userId}`

const handleAttendeeInput = (attendee) => {
  attendee.user_id = null
  attendee.showPopover = true
}

const hidePopoverDelay = (attendee) => {
  setTimeout(() => {
    attendee.showPopover = false
  }, 200)
}

const getSuggestions = (queryString) => {
  if (!userOptions.value || userOptions.value.length === 0) return []
  return queryString
    ? userOptions.value.filter(u => u.name && u.name.toLowerCase().includes(queryString.toLowerCase()))
    : userOptions.value
}

const handleUserSelect = (attendee, item) => {
  attendee.user_name = item.name
  attendee.user_id = item.id
  attendee.showPopover = false
}

const createEmptyAttendeeEntry = () => ({
  user_name: '',
  user_id: null,
  meeting_role: '参会人员',
  showPopover: false
})

const createEmptyAgendaItem = () => ({
  content: ''
})

const createEmptyContact = () => ({
  name: '',
  short_phone: '',
  phone: '',
  email: ''
})

// Fetch
const fetchMeetings = async () => {
  loading.value = true
  try { meetings.value = await request.get('/meetings/', { params: { force_show_all: true } }) } finally { loading.value = false }
}
const fetchMeetingTypes = async () => {
  try { meetingTypes.value = await request.get('/meeting_types/') } catch (e) {}
}
const handleUploadClick = (meeting) => {}
const handleUploadSuccess = () => { fetchMeetings() }

const handleDeleteMeeting = async () => {
  if (!currentDetail.value) return
  try {
    await ElMessageBox.confirm('确定删除该会议吗？删除后不可恢复。', '警告', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await request.delete(`/meetings/${currentDetail.value.id}`)
    ElMessage.success('会议已删除')
    detailDialogVisible.value = false
    fetchMeetings()
  } catch (e) { /* 用户取消或删除失败 */ }
}



// Details
const viewDetails = async (row) => {
  try {
    await refreshMeetingDetail(row.id)
    detailDialogVisible.value = true
  } catch (e) { ElMessage.error('获取详情失败') }
}

// Unified File List
 // Structure: { id, name, size, type: 'existing'|'new', raw?: File, existingId?: int }
const attachmentList = ref([])

const setAttachmentListFromMeeting = (meeting) => {
  const sorted = [...(meeting?.attachments || [])].sort((a, b) => (a.sort_order || 0) - (b.sort_order || 0))
  attachmentList.value = sorted.map(a => ({
    id: `${a.id}-${a.sort_order || 0}`,
    existingId: a.id,
    name: a.display_name,
    size: a.file_size,
    type: 'existing'
  }))
}

const refreshMeetingDetail = async (meetingId) => {
  const res = await request.get(`/meetings/${meetingId}`)
  if (res.attachments) {
    res.attachments.sort((a, b) => (a.sort_order || 0) - (b.sort_order || 0))
  }
  currentDetail.value = res
  return res
}

// File Actions
const handleFileSelect = (uploadFile) => {
  const raw = uploadFile.raw
  // 校验文件类型
  if (!raw.name.toLowerCase().endsWith('.pdf')) {
    ElMessage.warning('仅支持上传 PDF 格式文件')
    return
  }
  // 校验文件大小（200MB）
  const maxSize = 200 * 1024 * 1024
  if (raw.size > maxSize) {
    ElMessage.warning('文件大小不能超过 200MB')
    return
  }
  attachmentList.value.push({
      id: Date.now() + Math.random(),
      name: raw.name,
      size: raw.size,
      type: 'new',
      raw: raw
  })
}

const removeFile = async (index) => {
  const item = attachmentList.value[index]
  if (item.type === 'existing') {
     try {
        await ElMessageBox.confirm('确定删除该附件吗？', '提示')
        await request.delete(`/meetings/attachments/${item.existingId}`)
        attachmentList.value.splice(index, 1)
        ElMessage.success('附件已删除')
     } catch (e) {}
  } else {
     attachmentList.value.splice(index, 1)
  }
}

const moveFile = (index, delta) => {
  const targetIndex = index + delta
  if (targetIndex < 0 || targetIndex >= attachmentList.value.length) return
  const list = [...attachmentList.value]
  const [item] = list.splice(index, 1)
  list.splice(targetIndex, 0, item)
  attachmentList.value = list
}



// Users/Attendees
const userOptions = ref([])
const fetchUsers = async () => {
   try {
      const res = await request.get('/users/', { params: { page: 1, page_size: 500 } })
      const users = res.items || []
      userOptions.value = users.map(u => ({
          value: u.name,
          name: u.name,
          id: u.id,
          department: u.department || ''
      }))
   } catch(e) {}
}

const addAttendee = () => {
    form.value.attendee_entries.push(createEmptyAttendeeEntry())
}
const removeAttendee = (index) => {
    form.value.attendee_entries.splice(index, 1)
}

// Agenda
const addAgendaItem = () => {
    form.value.agendaItems.push(createEmptyAgendaItem())
}
const removeAgendaItem = (index) => {
    form.value.agendaItems.splice(index, 1)
}

const addContact = () => {
  form.value.meeting_contacts.push(createEmptyContact())
}

const removeContact = (index) => {
  form.value.meeting_contacts.splice(index, 1)
}

// Actions
const openCreate = () => {
  if(userOptions.value.length === 0) fetchUsers()
  isEditMode.value = false
  editingId.value = null
  const defaultLoc = localStorage.getItem('defaultMeetingLocation') || ''
  resetActiveCards()

  form.value = {
    title: '',
    meeting_type_id: null,
    start_time: null,
    end_time: null,
    location: defaultLoc,
    attendee_entries: [],
    agendaItems: [],
    meeting_contacts: [],
    show_media_link: false
  }
  attachmentList.value = []
  dialogVisible.value = true
}

const openEdit = () => {
  if (!currentDetail.value) return
  const m = currentDetail.value

  if(userOptions.value.length === 0) fetchUsers()
  resetActiveCards()

  form.value = { 
      title: m.title, 
      meeting_type_id: m.meeting_type_id, 
      start_time: m.start_time, 
      end_time: m.end_time || null,
      location: m.location,
      attendee_entries: (m.attendees || []).map(a => ({
        user_name: a.name || '',
        user_id: a.user_id || null,
        meeting_role: a.meeting_role || '参会人员',
        showPopover: false
      })),
      agendaItems: parseAgendaItems(m.agenda_items, m.agenda),
      meeting_contacts: Array.isArray(m.meeting_contacts)
        ? m.meeting_contacts.map(contact => ({
            name: contact.name || '',
            short_phone: contact.short_phone || '',
            phone: contact.phone || '',
            email: contact.email || ''
          }))
        : [],
      show_media_link: m.show_media_link || false
  }
  editingId.value = m.id
  isEditMode.value = true
  
  setAttachmentListFromMeeting(m)
  
  detailDialogVisible.value = false
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!form.value.title || !form.value.start_time || !form.value.end_time || !form.value.meeting_type_id) {
    return ElMessage.warning('请填写完整')
  }

  if (new Date(form.value.end_time) < new Date(form.value.start_time)) {
    return ElMessage.warning('结束时间不能早于开始时间')
  }

  const attendeeEntries = (form.value.attendee_entries || [])
    .map(item => {
      const nameStr = String(item.user_name || '').trim()
      if (!nameStr) return null
      return {
        type: item.user_id ? 'user' : 'manual',
        user_id: item.user_id || null,
        name: item.user_id ? null : nameStr,
        meeting_role: item.meeting_role || '参会人员'
      }
    })
    .filter(Boolean)

  const agendaItems = (form.value.agendaItems || [])
    .map(item => ({ content: String(item.content || '').trim() }))
    .filter(item => item.content)

  const meetingContacts = (form.value.meeting_contacts || [])
    .map(contact => ({
      name: String(contact.name || '').trim(),
      short_phone: String(contact.short_phone || '').trim(),
      phone: String(contact.phone || '').trim(),
      email: String(contact.email || '').trim()
    }))
    .filter(contact => contact.name)

  const payload = {
    title: form.value.title,
    meeting_type_id: form.value.meeting_type_id,
    start_time: form.value.start_time,
    end_time: form.value.end_time,
    location: form.value.location,
    attendee_entries: attendeeEntries,
    agenda_items: agendaItems,
    meeting_contacts: meetingContacts,
    show_media_link: form.value.show_media_link || false
  }

  try {
    submitting.value = true
    let targetId = editingId.value
    if (isEditMode.value) {
       await request.put(`/meetings/${targetId}`, payload)
       ElMessage.success('更新成功')
    } else {
       const res = await request.post('/meetings/', payload)
       if (!res || !res.id) {
           throw new Error('创建会议失败，未获取到 ID')
       }
       targetId = res.id
       editingId.value = targetId
    }

    // Process new files...
    const newFiles = attachmentList.value.filter(f => f.type === 'new')
    if (newFiles.length > 0) {
        if (!targetId) {
            ElMessage.error('无法上传附件：会议 ID 无效')
            return
        }
        for (const f of newFiles) {
            try {
                const formData = new FormData()
                formData.append('file', f.raw)
                await request.post(`/meetings/${targetId}/upload`, formData)
            } catch(e) {
                console.error('Upload failed for file', f.name, e)
            }
        }
        ElMessage.success('附件上传完成')
    }
    await fetchMeetings()

    if (targetId) {
      await refreshMeetingDetail(targetId)
      detailDialogVisible.value = true
    }

    dialogVisible.value = false
} catch (e) {
    console.error('Save meeting error:', e)
    ElMessage.error(e.message || '保存失败')
} finally {
    submitting.value = false
  }
}
const downloadFile = (file) => {
  if (!file || !file.filename) return
  const downloadUrl = `/static/${file.filename}`
  window.open(downloadUrl, '_blank')
}

</script>

<style scoped>
.meeting-manage {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 详情样式优化 */
.detail-header-section { margin-bottom: 24px; }
.detail-main-title { margin: 0 0 8px 0; font-size: 22px; font-weight: 700; color: #0f172a; line-height: 1.3; }
.detail-meta-list { display: flex; flex-direction: column; gap: 12px; }
.meta-card { display: flex; align-items: center; padding: 12px 16px; background-color: #ffffff; border: 1px solid #f1f5f9; border-radius: 12px; transition: all 0.2s; }
.meta-card:hover { border-color: #e2e8f0; background-color: #f8fafc; }
.meta-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 20px; margin-right: 16px; flex-shrink: 0; }
.meta-info { flex: 1; }
.meta-label { font-size: 13px; color: #94a3b8; margin-bottom: 2px; }
.meta-value { font-size: 15px; color: #334155; font-weight: 600; }
.detail-footer { display: flex; justify-content: flex-end; gap: 12px; width: 100%; }
.contact-list { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }
.contact-item { padding: 10px 12px; border-radius: 10px; background: var(--bg-main); }
.contact-name { font-size: 14px; font-weight: 700; color: var(--text-main); }
.contact-lines { display: flex; flex-direction: column; gap: 4px; margin-top: 6px; font-size: 13px; color: var(--text-secondary); }

/* Colors Utility */
.bg-blue-50 { background-color: #eff6ff; } .text-blue-500 { color: #3b82f6; }
.bg-green-50 { background-color: #f0fdf4; } .text-green-500 { color: #22c55e; }
.bg-purple-50 { background-color: #faf5ff; } .text-purple-500 { color: #a855f7; }
.bg-orange-50 { background-color: #fff7ed; } .text-orange-500 { color: #f97316; }

/* Read Only File List */
.read-only { cursor: default; border-style: dashed; }

/* 头部样式调整 */
.page-header { display: flex; justify-content: space-between; align-items: flex-end; padding: 0 4px; }
.header-left { display: flex; align-items: center; gap: 12px; }

/* 主体区域布局 */
.stats-row { row-gap: 20px; }
.main-content-row { display: flex; align-items: flex-start; margin-bottom: 24px; row-gap: 24px; }
.main-content-row > .el-col { display: flex; flex-direction: column; }

@media screen and (max-width: 991px) {
  .today-component { max-height: 500px !important; }
}
.today-component { display: flex; flex-direction: column; overflow: hidden; }

.collapse-btn { padding: 8px; border-radius: 8px; transition: background-color 0.2s; height: auto; }
.collapse-btn:hover { background-color: var(--bg-main); }
.header-divider { height: 24px; border-color: var(--border-color); margin: 0 4px; }
.title-group { display: flex; flex-direction: column; }
.page-title { margin: 0; font-size: 24px; font-weight: 600; color: var(--text-main); line-height: 1.2; }
.page-subtitle { margin: 4px 0 0; color: var(--text-secondary); font-size: 14px; line-height: 1.4; }

/* Stats Row */
.stat-card { border: none; background: var(--card-bg); border-radius: 12px; box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06); transition: all 0.2s; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06); }
.stat-content { display: flex; align-items: flex-start; }
.stat-icon { padding: 12px; border-radius: 12px; margin-right: 16px; display: flex; align-items: center; justify-content: center; }
.stat-info { flex: 1; }
.stat-label { font-size: 14px; color: var(--text-secondary); font-weight: 500; }
.stat-value { 
    font-size: 24px; font-weight: 700; color: var(--text-main); margin: 4px 0; 
    display: flex; align-items: flex-end; gap: 8px; /* Added flex for trend alignment */
    white-space: nowrap;
}
.stat-trend { font-size: 13px; font-weight: 600; display: flex; align-items: center; margin-bottom: 3px; }
.stat-trend.up { color: #10b981; } .stat-trend.down { color: #ef4444; }
.stat-desc { font-size: 12px; color: var(--text-secondary); }

/* Dialog Styles */
.meeting-dialog :deep(.el-dialog__body) { padding: 0; }
.dialog-layout { display: flex; height: 500px; }
.detail-mode :deep(.el-dialog),
.edit-mode :deep(.el-dialog) { width: min(1100px, 92vw) !important; }
.detail-mode .dialog-layout,
.edit-mode .dialog-layout { height: min(680px, 76vh); }
.detail-mode .dialog-right,
.edit-mode .dialog-right { width: 440px; }
.dialog-left { flex: 1; padding: 24px; border-right: 1px solid var(--border-color); overflow-y: auto; }
.dialog-right { width: 400px; background-color: var(--bg-main); display: flex; flex-direction: column; }
@media screen and (max-width: 991px) {
  .detail-mode :deep(.el-dialog),
  .edit-mode :deep(.el-dialog) { width: min(1100px, 96vw) !important; }
  .detail-mode .dialog-layout,
  .edit-mode .dialog-layout {
    height: auto;
    min-height: 560px;
    max-height: 82vh;
    flex-direction: column;
  }
  .detail-mode .dialog-left,
  .edit-mode .dialog-left {
    border-right: none;
    border-bottom: 1px solid var(--border-color);
  }
  .detail-mode .dialog-right,
  .edit-mode .dialog-right {
    width: 100%;
    min-height: 260px;
  }
}
/* Modern Form Redesign */
.modern-form-container {
  padding: 32px 40px !important;
  background-color: #ffffff;
}
.modern-meeting-form {
  display: flex !important;
  flex-direction: column !important;
  gap: 0 !important;
}
.form-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.form-section-title {
  font-size: 14px;
  font-weight: 700;
  color: #94a3b8;
  margin: 0 0 4px 0;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.form-divider {
  margin: 28px 0;
  border-top-color: transparent;
  background: linear-gradient(to right, transparent, rgba(148, 163, 184, 0.25), transparent);
  height: 1px;
}
.modern-form-item {
  margin-bottom: 0;
  display: flex;
  flex-direction: column;
}
.modern-form-item :deep(.el-form-item__label) {
  padding-bottom: 6px;
  line-height: 1.2;
  font-size: 14px;
  font-weight: 600;
  color: #475569;
}
.modern-form-item.is-required :deep(.el-form-item__label)::before {
  content: '*';
  color: #ef4444;
  margin-right: 4px;
}

/* Base input overrides */
.modern-meeting-form :deep(.el-input),
.modern-meeting-form :deep(.el-select),
.modern-meeting-form :deep(.el-textarea),
.modern-meeting-form :deep(.el-date-editor) {
  width: 100%;
}
.modern-meeting-form :deep(.el-input__wrapper),
.modern-meeting-form :deep(.el-select__wrapper),
.modern-meeting-form :deep(.el-textarea__inner) {
  background-color: #f8fafc !important;
  box-shadow: none !important;
  border-radius: 8px;
  padding: 8px 12px;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  font-family: inherit;
}
.modern-meeting-form :deep(.el-input__inner),
.modern-meeting-form :deep(.el-select__placeholder),
.modern-meeting-form :deep(.el-textarea__inner) {
  font-size: 15px;
  color: #1e293b;
}
.modern-meeting-form :deep(.el-textarea__inner) {
  word-break: break-all;
  white-space: pre-wrap;
}
.modern-meeting-form :deep(.el-input__inner::placeholder),
.modern-meeting-form :deep(.el-textarea__inner::placeholder) {
  color: #94a3b8;
}
.modern-meeting-form :deep(.el-input__wrapper.is-focus),
.modern-meeting-form :deep(.el-select__wrapper.is-focused),
.modern-meeting-form :deep(.el-input__wrapper:hover),
.modern-meeting-form :deep(.el-select__wrapper:hover),
.modern-meeting-form :deep(.el-textarea__inner:focus),
.modern-meeting-form :deep(.el-textarea__inner:hover) {
  background-color: #ffffff !important;
  border: 1px solid #bfdbfe;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
}

html.dark .modern-form-container {
  background-color: transparent !important;
}
html.dark .modern-meeting-form :deep(.el-input__inner),
html.dark .modern-meeting-form :deep(.el-select__inner),
html.dark .modern-meeting-form :deep(.el-select__placeholder),
html.dark .modern-meeting-form :deep(.el-textarea__inner) {
  color: #f8fafc;
}
html.dark .modern-meeting-form :deep(.el-input__wrapper),
html.dark .modern-meeting-form :deep(.el-select__wrapper),
html.dark .modern-meeting-form :deep(.el-textarea__inner) {
  background-color: #0f172a !important;
  border-color: #1e293b !important;
}
html.dark .modern-meeting-form :deep(.el-input__wrapper.is-focus),
html.dark .modern-meeting-form :deep(.el-select__wrapper.is-focused),
html.dark .modern-meeting-form :deep(.el-input__wrapper:hover),
html.dark .modern-meeting-form :deep(.el-select__wrapper:hover),
html.dark .modern-meeting-form :deep(.el-textarea__inner:focus),
html.dark .modern-meeting-form :deep(.el-textarea__inner:hover) {
  background-color: #1e293b !important;
  border-color: #3b82f6 !important;
}

.attendee-popover {
  padding: 8px !important;
  border-radius: 8px !important;
  border: 1px solid #e2e8f0 !important;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1) !important;
}
html.dark .attendee-popover {
  background-color: #1e293b !important;
  border-color: #334155 !important;
}
.attendee-suggestions {
  max-height: 200px;
  overflow-y: auto;
}
.suggestion-item {
  padding: 8px 12px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-radius: 6px;
  transition: all 0.2s ease;
}
.suggestion-item:hover {
  background-color: #f1f5f9;
}
html.dark .suggestion-item:hover {
  background-color: #334155;
}
.sugg-name {
  font-weight: 500;
  color: #1e293b;
}
html.dark .sugg-name {
  color: #f8fafc;
}
.sugg-dept {
  color: #94a3b8;
  font-size: 13px;
}
.no-data {
  padding: 12px;
  color: #94a3b8;
  text-align: center;
  font-size: 13px;
}

.section-header { padding: 16px 20px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; background: var(--card-bg); }
.section-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--text-main); }
.section-tip { font-size: 12px; color: #f59e0b; margin-top: 4px; }

/* Time preview */
.meeting-time-preview {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding: 12px 16px;
  background-color: #f8fafc;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  color: #1e293b;
  font-size: 15px;
}
.meeting-time-preview:hover {
  background-color: #f1f5f9;
  border-color: #bfdbfe;
}
.meeting-time-preview-main {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}
.meeting-time-preview-action {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #3b82f6;
  font-weight: 600;
}

/* Time Grid */
.time-grid-modern {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.time-separator {
  color: #94a3b8;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Dynamic Lists */
.dynamic-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.dynamic-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: 8px;
  background-color: transparent;
  transition: background-color 0.2s;
}
.dynamic-list-item:hover {
  background-color: #f1f5f9;
}
.list-index {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: #e2e8f0;
  color: #64748b;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 0;
}
.delete-btn-subtle {
  opacity: 0;
  margin-top: 0;
  transition: all 0.2s;
  height: 32px;
}
.dynamic-list-item:hover .delete-btn-subtle {
  opacity: 1;
}
.add-btn-subtle {
  margin-top: 4px;
  justify-content: flex-start;
  padding-left: 8px;
  font-weight: 600;
}
.flex-1 {
  flex: 1;
  min-width: 0;
}

/* Contact List specific */
.contact-item-modern {
  flex-direction: row;
  align-items: flex-start;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 8px;
}
html.dark .contact-item-modern {
  background-color: #1e293b;
  border-color: #334155;
}
.modern-meeting-form :deep(.contact-item-modern .el-textarea__inner),
.modern-meeting-form :deep(.contact-item-modern .el-input__wrapper) {
  background-color: #ffffff !important;
}
html.dark .modern-meeting-form :deep(.contact-item-modern .el-textarea__inner),
html.dark .modern-meeting-form :deep(.contact-item-modern .el-input__wrapper) {
  background-color: #0f172a !important;
}
.contact-inputs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.contact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.contact-half-input {
  flex: 1;
}
.contact-delete {
  margin-top: 4px;
}

/* Media Link Toggle */
.media-link-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px;
  border-bottom: 1px solid var(--border-color);
  gap: 10px;
  background: transparent;
  transition: background 0.2s;
}
.media-link-toggle.active {
  background: rgba(139, 92, 246, 0.06);
}
.media-link-toggle-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.media-link-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #8b5cf6;
  flex-shrink: 0;
  opacity: 0.5;
  transition: opacity 0.2s;
}
.media-link-toggle.active .media-link-dot {
  opacity: 1;
  box-shadow: 0 0 4px #8b5cf6;
}
.media-link-label {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.media-link-toggle.active .media-link-label {
  color: #8b5cf6;
  font-weight: 500;
}
.file-list-container { flex: 1; padding: 16px; overflow-y: auto; }
.empty-state { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-secondary); gap: 12px; }
.file-item { background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; margin-bottom: 12px; display: flex; align-items: center; gap: 12px; transition: all 0.2s; }
.file-item:hover { border-color: var(--color-slate-400); box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); }
.file-icon { color: var(--text-secondary); font-size: 20px; flex-shrink: 0; }
.file-content { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.name-input :deep(.el-input__wrapper) { box-shadow: none; padding: 0; background: transparent; }
.name-input :deep(.el-input__inner) { font-weight: 500; color: var(--text-main); height: 24px; line-height: 24px; }
.file-meta { font-size: 12px; color: var(--text-secondary); }
.file-actions { display: flex; gap: 4px; opacity: 0; transition: opacity 0.2s; }
.file-item:hover .file-actions { opacity: 1; }
.dialog-footer { display: flex; justify-content: space-between; align-items: center; padding-top: 8px; }
.footer-tip { font-size: 13px; color: var(--text-secondary); }

/* Agenda Styles */
.agenda-list { display: flex; flex-direction: column; gap: 8px; }
.agenda-item { display: flex; align-items: flex-start; gap: 12px; font-size: 14px; }
.agenda-index { width: 24px; height: 24px; border-radius: 999px; background: var(--bg-main); color: var(--color-primary); display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0; margin-top: 1px; }
.agenda-content { color: var(--text-main); line-height: 1.5; }

/* Clickable Stat Card */
.stat-card.clickable { cursor: pointer; }

/* Files Drawer */
.files-drawer-content { height: 100%; overflow-y: auto; }
.files-list { display: flex; flex-direction: column; gap: 12px; }
.file-card {
    display: flex; justify-content: space-between; align-items: center;
    padding: 16px; background: var(--card-bg);
    border: 1px solid var(--border-color); border-radius: 12px;
    transition: all 0.2s;
}
.file-card:hover { border-color: var(--color-slate-400); box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.file-card-left { display: flex; align-items: center; gap: 14px; flex: 1; min-width: 0; }
.file-icon-box {
    width: 44px; height: 44px; border-radius: 10px;
    background: #f0fdf4; color: #22c55e;
    display: flex; align-items: center; justify-content: center;
    font-size: 20px; flex-shrink: 0;
}
.file-info { flex: 1; min-width: 0; }
.file-name { font-weight: 600; color: var(--text-main); font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.file-meeting { font-size: 13px; color: var(--text-secondary); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.file-date { font-size: 12px; color: #94a3b8; margin-top: 4px; }

/* Dark Mode Overrides for Meeting Detail */
html.dark .meta-card {
    background-color: #2d3748;
    border-color: #4a5568;
}
html.dark .meta-card:hover {
    background-color: #374151;
}
html.dark .detail-main-title {
    color: #f1f5f9;
}
html.dark .meta-label {
    color: #a0aec0;
}
html.dark .meta-value {
    color: #e2e8f0;
}
html.dark .contact-item {
    background-color: #1f2937;
}
/* Modern Form Dark Mode */
html.dark .meeting-time-preview { background-color: #0f172a; color: #f8fafc; }
html.dark .meeting-time-preview:hover { background-color: #1e293b; border-color: #3b82f6; }
html.dark .modern-form-container {
  background-color: #1e293b;
}
html.dark .modern-form-item :deep(.el-form-item__label) {
  color: #e2e8f0;
}
html.dark .form-section-title {
  color: #64748b;
}
html.dark .modern-meeting-form :deep(.el-input__wrapper),
html.dark .modern-meeting-form :deep(.el-select__wrapper),
html.dark .modern-meeting-form :deep(.el-textarea__wrapper) {
  background-color: #0f172a !important;
  border-color: transparent !important;
}
html.dark .modern-meeting-form :deep(.el-input__inner),
html.dark .modern-meeting-form :deep(.el-select__placeholder),
html.dark .modern-meeting-form :deep(.el-select__selected-item) {
  color: #f8fafc !important;
}
html.dark .modern-meeting-form :deep(.el-input__wrapper.is-focus),
html.dark .modern-meeting-form :deep(.el-select__wrapper.is-focused),
html.dark .modern-meeting-form :deep(.el-input__wrapper:hover),
html.dark .modern-meeting-form :deep(.el-select__wrapper:hover) {
  background-color: #1e293b !important;
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2) !important;
}
html.dark .dynamic-list-item:hover {
  background-color: #0f172a;
}
html.dark .list-index {
  background-color: #334155;
  color: #94a3b8;
}
html.dark .form-divider {
  border-top-color: #334155;
}

@media (max-width: 768px) {
  .time-grid-modern {
    flex-direction: column;
    align-items: flex-start;
  }
  .time-separator {
    display: none;
  }
  .contact-phones {
    grid-template-columns: 1fr;
  }
  .dynamic-list-item {
    flex-wrap: wrap;
  }
}
.form-item-label-with-icon {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  vertical-align: middle;
}
.form-item-label-with-icon .el-icon {
  font-size: 16px;
  color: #3b82f6;
}
html.dark .form-item-label-with-icon .el-icon {
  color: #60a5fa;
}
</style>
