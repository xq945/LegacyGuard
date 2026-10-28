<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api'
import type { JobExecution, OutboxEvent } from '@/api/types'
import { fmtTime } from '@/utils/labels'

const loading = ref(false)
const summary = ref({ pending: 0, failed: 0 })
const jobs = ref<JobExecution[]>([])
const jobTotal = ref(0)
const jobPage = ref(1)

const failedEvents = ref<OutboxEvent[]>([])
const failedTotal = ref(0)
const failedPage = ref(1)

const publishing = ref(false)

const JOB_LABEL: Record<string, string> = {
  heartbeatDueScan: '心跳到期扫描',
  heartbeatRemindScan: '心跳提醒扫描',
  waitPeriodExpireScan: '等待期生效扫描',
  outboxPublishScan: '发件箱发布'
}

async function load() {
  loading.value = true
  try {
    const [s, j, f] = await Promise.all([
      adminApi.outboxSummary(),
      adminApi.jobExecutions(jobPage.value, 10),
      adminApi.failedEvents(failedPage.value, 10)
    ])
    summary.value = s
    jobs.value = j.records
    jobTotal.value = j.total
    failedEvents.value = f.records
    failedTotal.value = f.total
  } finally {
    loading.value = false
  }
}

async function retry(id: number) {
  await adminApi.retryEvent(id)
  ElMessage.success('已退回待发状态，发布器会在下一轮尝试投递')
  await load()
}

async function publishNow() {
  publishing.value = true
  try {
    const result = await adminApi.publishNow()
    ElMessage.success(`本轮发布 ${result.published} 条事件`)
    await load()
  } finally {
    publishing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">任务与消息</h2>
      <div>
        <el-button :loading="publishing" @click="publishNow">立即发布一轮</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <div class="metric">
            <div class="metric-value">{{ summary.pending }}</div>
            <div class="metric-label">待发布事件</div>
            <div class="muted">发件箱中状态为 NEW 的事件数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div class="metric">
            <div class="metric-value" :class="{ danger: summary.failed > 0 }">{{ summary.failed }}</div>
            <div class="metric-label">发送失败事件</div>
            <div class="muted">重试超限、需人工处理的事件数</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header><span>定时任务执行记录</span></template>
      <el-table v-loading="loading" :data="jobs" size="small" empty-text="暂无执行记录">
        <el-table-column label="任务" width="170">
          <template #default="{ row }">{{ JOB_LABEL[row.jobName] || row.jobName }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="processedCount" label="处理数" width="90" />
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ row.durationMs }} ms</template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column prop="instanceId" label="实例" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono muted">{{ row.instanceId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="danger-text">{{ row.errorMsg || '' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="jobPage"
        :page-size="10"
        :total="jobTotal"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="load"
      />
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>发送失败的事件</span></template>
      <el-table v-loading="loading" :data="failedEvents" size="small" empty-text="没有失败的事件">
        <el-table-column label="事件 ID" width="200">
          <template #default="{ row }">
            <span class="mono muted">{{ row.eventId.slice(0, 8) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="eventType" label="类型" width="180" />
        <el-table-column prop="retryCount" label="重试次数" width="100" />
        <el-table-column prop="lastError" label="最后错误" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="danger-text">{{ row.lastError || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="retry(row.id)">重投</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="failedPage"
        :page-size="10"
        :total="failedTotal"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<style scoped>
.metric {
  text-align: center;
  padding: 8px 0;
}

.metric-value {
  font-size: 34px;
  font-weight: 700;
}

.metric-value.danger {
  color: #f56c6c;
}

.metric-label {
  font-size: 14px;
  margin: 4px 0;
}

.danger-text {
  color: #f56c6c;
  font-size: 12px;
}
</style>
