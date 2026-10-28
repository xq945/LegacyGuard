<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { auditApi } from '@/api'
import type { AuditLog } from '@/api/types'
import { AUDIT_ACTION_LABEL, fmtTime } from '@/utils/labels'

const loading = ref(false)
const logs = ref<AuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const action = ref('')

async function load() {
  loading.value = true
  try {
    const result = await auditApi.query({
      page: page.value,
      size: size.value,
      action: action.value || undefined
    })
    logs.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">安全审计</h2>
      <el-input
        v-model="action"
        placeholder="按动作筛选，如 ASSET_VIEW"
        clearable
        style="width: 260px"
        @keyup.enter="search"
        @clear="search"
      >
        <template #append>
          <el-button @click="search">查询</el-button>
        </template>
      </el-input>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="这里只能看到你自己的操作记录"
      description="审计记录只增不改，且不包含密码、令牌或资产内容。管理员可查询全部记录。"
      style="margin-bottom: 16px"
    />

    <el-table v-loading="loading" :data="logs" border empty-text="暂无记录">
      <el-table-column label="动作" width="150">
        <template #default="{ row }">
          {{ AUDIT_ACTION_LABEL[row.action] || row.action }}
        </template>
      </el-table-column>
      <el-table-column label="对象" width="160">
        <template #default="{ row }">
          <span class="mono">{{ row.objectType }}#{{ row.objectId ?? '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="请求 ID" width="180">
        <template #default="{ row }">
          <span class="mono muted">{{ row.requestId?.slice(0, 8) || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="IP" width="140">
        <template #default="{ row }">
          <span class="mono muted">{{ row.clientIp || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

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
