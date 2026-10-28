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
const objectType = ref('')
const objectId = ref<number | undefined>(undefined)

async function load() {
  loading.value = true
  try {
    const result = await auditApi.query({
      page: page.value,
      size: size.value,
      action: action.value || undefined,
      objectType: objectType.value || undefined,
      objectId: objectId.value
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

function reset() {
  action.value = ''
  objectType.value = ''
  objectId.value = undefined
  search()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">审计日志</h2>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="管理员可以查询全部记录"
      description="审计只增不改，不记录密码、令牌或资产内容。可按动作与业务对象定位一次完整操作链路。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form inline>
        <el-form-item label="动作">
          <el-input v-model="action" placeholder="如 CASE_APPROVE" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="objectType" placeholder="全部" clearable style="width: 150px">
            <el-option label="ASSET" value="ASSET" />
            <el-option label="CASE" value="CASE" />
            <el-option label="CONTACT" value="CONTACT" />
            <el-option label="HEARTBEAT" value="HEARTBEAT" />
            <el-option label="USER" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象 ID">
          <el-input-number v-model="objectId" :min="1" :controls="false" style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table v-loading="loading" :data="logs" border empty-text="暂无记录">
      <el-table-column label="动作" width="150">
        <template #default="{ row }">
          {{ AUDIT_ACTION_LABEL[row.action] || row.action }}
        </template>
      </el-table-column>
      <el-table-column label="操作人" width="120">
        <template #default="{ row }">
          <span class="mono">{{ row.actorId ?? '系统' }}</span>
          <div class="muted">{{ row.actorRole || '' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="对象" width="150">
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
      <el-table-column prop="detail" label="摘要" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="mono muted">{{ row.detail || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="请求 ID" width="140">
        <template #default="{ row }">
          <span class="mono muted">{{ row.requestId?.slice(0, 8) || '—' }}</span>
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
