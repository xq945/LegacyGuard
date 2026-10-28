<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { assetApi, caseApi, contactApi, heartbeatApi, notificationApi } from '@/api'
import type { CaseSummary, HeartbeatStatus } from '@/api/types'
import { caseLabel, caseType, heartbeatLabel, heartbeatType } from '@/utils/labels'

const router = useRouter()

const loading = ref(true)
const heartbeat = ref<HeartbeatStatus | null>(null)
const assetCount = ref(0)
const contactCount = ref(0)
const unread = ref(0)
const activeCases = ref<CaseSummary[]>([])

async function load() {
  loading.value = true
  try {
    const [hb, assets, contacts, unreadResult, cases] = await Promise.all([
      heartbeatApi.status(),
      assetApi.list(1, 1),
      contactApi.list(),
      notificationApi.unreadCount(),
      caseApi.list(1, 5)
    ])
    heartbeat.value = hb
    assetCount.value = assets.total
    contactCount.value = contacts.filter((c) => c.status === 'ACTIVE').length
    unread.value = unreadResult.count
    activeCases.value = cases.records
  } finally {
    loading.value = false
  }
}

async function handleConfirm() {
  await heartbeatApi.confirm()
  ElMessage.success('状态确认完成，计时已重置')
  await load()
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="重要提醒"
      description="平台托管的是加密后的说明性资料，请不要在描述字段中填写任何密码或可直接使用的凭据。"
      style="margin-bottom: 20px"
    />

    <el-row :gutter="16">
      <!-- 状态确认卡片：最重要的信息放最前面 -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>状态确认</span>
              <el-tag
                v-if="heartbeat"
                :type="heartbeatType(heartbeat.status)"
                size="small"
              >
                {{ heartbeatLabel(heartbeat.status) }}
              </el-tag>
            </div>
          </template>

          <div v-if="heartbeat" class="heartbeat-summary">
            <div class="stat">
              <div class="stat-value" :class="{ danger: heartbeat.overdue }">
                {{ heartbeat.daysRemaining }}
              </div>
              <div class="stat-label">{{ heartbeat.overdue ? '已逾期天数' : '距离截止（天）' }}</div>
            </div>
            <div class="heartbeat-meta">
              <p class="muted">确认周期：{{ heartbeat.cycleDays }} 天 · 第 {{ heartbeat.cycleNo }} 期</p>
              <p class="muted">下次截止：{{ heartbeat.nextDeadlineAt?.replace('T', ' ').slice(0, 16) }}</p>
            </div>
          </div>

          <el-button type="primary" @click="handleConfirm">立即确认</el-button>
          <el-button text @click="router.push('/heartbeat')">查看详情</el-button>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>概览</span></template>
          <el-row :gutter="12">
            <el-col :span="8">
              <div class="mini-stat" @click="router.push('/assets')">
                <div class="mini-value">{{ assetCount }}</div>
                <div class="mini-label">托管资产</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="mini-stat" @click="router.push('/contacts')">
                <div class="mini-value">{{ contactCount }}</div>
                <div class="mini-label">生效联系人</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="mini-stat" @click="router.push('/notifications')">
                <div class="mini-value">{{ unread }}</div>
                <div class="mini-label">未读通知</div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>最近的授权案件</span>
          <el-link type="primary" @click="router.push('/cases')">全部案件</el-link>
        </div>
      </template>

      <el-empty v-if="!activeCases.length" description="暂无授权案件" :image-size="80" />

      <el-table v-else :data="activeCases" size="small">
        <el-table-column prop="caseNo" label="案件号" width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push(`/cases/${row.id}`)">
              <span class="mono">{{ row.caseNo }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="身份" width="100">
          <template #default="{ row }">
            {{ row.iAmOwner ? '资产所有者' : '申请人' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="caseType(row.status)" size="small">
              {{ caseLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间">
          <template #default="{ row }">
            {{ row.createdAt?.replace('T', ' ').slice(0, 16) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.heartbeat-summary {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 16px;
}

.stat-value {
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
  color: #409eff;
}

.stat-value.danger {
  color: #f56c6c;
}

.stat-label {
  font-size: 12px;
  color: var(--lg-text-muted);
  margin-top: 6px;
}

.heartbeat-meta p {
  margin: 4px 0;
}

.mini-stat {
  text-align: center;
  padding: 12px 0;
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.2s;
}

.mini-stat:hover {
  background: #f5f7fa;
}

.mini-value {
  font-size: 28px;
  font-weight: 600;
}

.mini-label {
  font-size: 12px;
  color: var(--lg-text-muted);
  margin-top: 4px;
}
</style>
