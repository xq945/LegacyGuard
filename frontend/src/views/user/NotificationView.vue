<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { notificationApi } from '@/api'
import type { NotificationItem } from '@/api/types'
import { fmtTime } from '@/utils/labels'

const loading = ref(false)
const items = ref<NotificationItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const unreadOnly = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await notificationApi.list(page.value, size.value, unreadOnly.value)
    items.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function markRead(item: NotificationItem) {
  if (item.read) return
  await notificationApi.markRead(item.id)
  item.read = true
}

async function markAllRead() {
  const result = await notificationApi.markAllRead()
  ElMessage.success(`已标记 ${result.updated} 条为已读`)
  await load()
}

function toggleFilter() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">通知中心</h2>
      <div>
        <el-switch
          v-model="unreadOnly"
          active-text="只看未读"
          style="margin-right: 12px"
          @change="toggleFilter"
        />
        <el-button @click="markAllRead">全部已读</el-button>
      </div>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!items.length" description="暂无通知" />

      <el-card
        v-for="item in items"
        :key="item.id"
        shadow="never"
        class="notice"
        :class="{ unread: !item.read }"
        @click="markRead(item)"
      >
        <div class="notice-head">
          <div>
            <el-badge is-dot :hidden="item.read" />
            <strong>{{ item.title }}</strong>
          </div>
          <span class="muted">{{ fmtTime(item.createdAt) }}</span>
        </div>
        <p class="notice-body">{{ item.content }}</p>
      </el-card>
    </div>

    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.notice {
  margin-bottom: 10px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.notice:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.notice.unread {
  border-left: 3px solid #409eff;
}

.notice-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.notice-body {
  margin: 8px 0 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
</style>
