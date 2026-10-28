<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { heartbeatApi } from '@/api'
import type { HeartbeatRecord, HeartbeatStatus } from '@/api/types'
import { heartbeatLabel, heartbeatType, fmtTime } from '@/utils/labels'

const loading = ref(false)
const status = ref<HeartbeatStatus | null>(null)
const records = ref<HeartbeatRecord[]>([])
const recordTotal = ref(0)
const page = ref(1)
const size = ref(10)

const configVisible = ref(false)
const submitting = ref(false)
const config = reactive({ cycleDays: 90, reminderRounds: 3, reminderIntervalHours: 72 })

async function load() {
  loading.value = true
  try {
    const [s, r] = await Promise.all([
      heartbeatApi.status(),
      heartbeatApi.records(page.value, size.value)
    ])
    status.value = s
    records.value = r.records
    recordTotal.value = r.total
    config.cycleDays = s.cycleDays
    config.reminderRounds = s.reminderRounds
  } finally {
    loading.value = false
  }
}

async function confirmNow() {
  await ElMessageBox.confirm(
    '确认你当前一切正常吗？确认后计时会重置，同时你名下所有"未生效"的授权案件会被自动取消。',
    '状态确认',
    { type: 'info', confirmButtonText: '我一切正常' }
  )
  const result = await heartbeatApi.confirm()
  status.value = result
  ElMessage.success('确认成功，新的周期已开始')
  await load()
}

function openConfig() {
  configVisible.value = true
}

async function saveConfig() {
  submitting.value = true
  try {
    const result = await heartbeatApi.updateConfig({
      cycleDays: config.cycleDays,
      reminderRounds: config.reminderRounds,
      reminderIntervalHours: config.reminderIntervalHours
    })
    status.value = result
    configVisible.value = false
    ElMessage.success('配置已更新')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header">
      <h2 class="page-title">状态确认</h2>
      <div>
        <el-button @click="openConfig">确认设置</el-button>
        <el-button type="primary" @click="confirmNow">立即确认</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>当前状态</span>
              <el-tag v-if="status" :type="heartbeatType(status.status)">
                {{ heartbeatLabel(status.status) }}
              </el-tag>
            </div>
          </template>

          <el-descriptions v-if="status" :column="1" border size="small">
            <el-descriptions-item label="周期">
              第 {{ status.cycleNo }} 期 · {{ status.cycleDays }} 天
            </el-descriptions-item>
            <el-descriptions-item label="最近确认">{{ fmtTime(status.lastConfirmAt) }}</el-descriptions-item>
            <el-descriptions-item label="下次截止">{{ fmtTime(status.nextDeadlineAt) }}</el-descriptions-item>
            <el-descriptions-item label="剩余">
              <span :class="{ danger: status.overdue }">
                {{ status.overdue ? `已逾期 ${Math.abs(status.daysRemaining)} 天` : `${status.daysRemaining} 天` }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="提醒进度">
              {{ status.reminderCount }} / {{ status.reminderRounds }} 轮
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span>这个机制如何运作</span></template>
          <el-timeline>
            <el-timeline-item type="success" :hollow="true">按时完成确认 —— 计时重置，状态保持正常</el-timeline-item>
            <el-timeline-item type="warning" :hollow="true">
              逾期未确认 —— 系统开始多轮提醒（默认 3 轮）
            </el-timeline-item>
            <el-timeline-item type="danger" :hollow="true">
              提醒耗尽 —— 进入人工核验队列，由审核员核实
            </el-timeline-item>
            <el-timeline-item type="primary" :hollow="true">
              核验通过 —— 可信联系人才可以发起授权申请
            </el-timeline-item>
          </el-timeline>
          <el-alert
            type="success"
            :closable="false"
            show-icon
            title="任何环节超时都不会直接授权"
            description="授权必须由可信联系人主动申请，经过审核、等待期与到期复核三道关。你随时可以提出异议或撤销。"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>确认历史</span></template>
      <el-table :data="records" size="small" empty-text="暂无确认记录">
        <el-table-column prop="cycleNo" label="周期" width="90">
          <template #default="{ row }">第 {{ row.cycleNo }} 期</template>
        </el-table-column>
        <el-table-column label="确认时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.confirmedAt) }}</template>
        </el-table-column>
        <el-table-column label="原截止时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.prevDeadlineAt) }}</template>
        </el-table-column>
        <el-table-column label="新截止时间">
          <template #default="{ row }">{{ fmtTime(row.nextDeadlineAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="recordTotal"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="configVisible" title="确认设置" width="460px">
      <el-form label-width="110px">
        <el-form-item label="确认周期">
          <el-input-number v-model="config.cycleDays" :min="7" :max="365" />
          <span class="muted" style="margin-left: 8px">天</span>
        </el-form-item>
        <el-form-item label="提醒轮数">
          <el-input-number v-model="config.reminderRounds" :min="1" :max="10" />
          <span class="muted" style="margin-left: 8px">轮</span>
        </el-form-item>
        <el-form-item label="提醒间隔">
          <el-input-number v-model="config.reminderIntervalHours" :min="1" :max="720" />
          <span class="muted" style="margin-left: 8px">小时</span>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="周期不建议设置过短"
        description="确认周期越短，越容易因出差或忘记确认而误触发失联流程。"
      />
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.danger {
  color: #f56c6c;
  font-weight: 600;
}
</style>
