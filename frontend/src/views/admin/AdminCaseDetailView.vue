<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'
import type { CaseDetail } from '@/api/types'
import { APPROVAL_ACTION_LABEL, caseLabel, caseType, fmtTime } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const caseId = computed(() => Number(route.params.id))

const loading = ref(false)
const acting = ref(false)
const detail = ref<CaseDetail | null>(null)

async function load() {
  loading.value = true
  try {
    detail.value = await adminApi.caseDetail(caseId.value)
  } finally {
    loading.value = false
  }
}

async function accept() {
  acting.value = true
  try {
    await adminApi.acceptCase(caseId.value)
    ElMessage.success('已受理')
    await load()
  } finally {
    acting.value = false
  }
}

async function approve() {
  const { value } = await ElMessageBox.prompt(
    '批准后案件进入等待期，等待期内的异议与到期复核依然可能让流程终止。请填写审批意见。',
    '批准案件',
    { inputPlaceholder: '例如：已核实联系人身份，资产范围与申请理由一致', type: 'info' }
  )
  if (!value) {
    ElMessage.warning('审批意见不能为空')
    return
  }
  acting.value = true
  try {
    await adminApi.approveCase(caseId.value, value)
    ElMessage.success('已批准，进入等待期')
    await load()
  } finally {
    acting.value = false
  }
}

async function reject() {
  const { value } = await ElMessageBox.prompt('请填写驳回理由，该理由会通知申请人与所有者。', '驳回案件', {
    inputPlaceholder: '例如：材料不足以证明失联事实',
    type: 'warning'
  })
  if (!value) {
    ElMessage.warning('驳回理由不能为空')
    return
  }
  acting.value = true
  try {
    await adminApi.rejectCase(caseId.value, value)
    ElMessage.success('已驳回')
    await load()
  } finally {
    acting.value = false
  }
}

async function resolveDispute(result: 'UPHELD' | 'OVERTURNED') {
  const label = result === 'UPHELD' ? '异议不成立' : '异议成立'
  const hint =
    result === 'UPHELD'
      ? '案件将回到等待期，且等待期重新计时。'
      : '案件将直接驳回。'
  const { value } = await ElMessageBox.prompt(`${label}：${hint}请填写处理说明。`, '处理异议', {
    inputPlaceholder: '核查过程与依据',
    type: 'warning'
  })
  if (!value) {
    ElMessage.warning('处理说明不能为空')
    return
  }
  acting.value = true
  try {
    await adminApi.resolveDispute(caseId.value, result, value)
    ElMessage.success('异议已处理')
    await load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header">
      <h2 class="page-title">
        <el-button text @click="router.push('/admin/cases')">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        案件审核
      </h2>
    </div>

    <template v-if="detail">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="审核边界"
        description="你可以推进流程，但无法读取任何资产内容。资产明文只有所有者本人与生效授权后的联系人可以看到。"
        style="margin-bottom: 16px"
      />

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="mono">{{ detail.caseNo }}</span>
            <el-tag :type="caseType(detail.status)">
              {{ caseLabel(detail.status) }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="资产所有者 ID">{{ detail.ownerId }}</el-descriptions-item>
          <el-descriptions-item label="申请人关系 ID">{{ detail.contactId }}</el-descriptions-item>
          <el-descriptions-item label="申请理由" :span="2">
            {{ detail.reason || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="覆盖资产数">{{ detail.assetIds.length }}</el-descriptions-item>
          <el-descriptions-item label="资产名称">
            {{ detail.assets.map((item) => item.name).join('、') || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="资产 ID 列表" :span="2">
            <span class="mono">{{ detail.assetIds.join(', ') || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.waitPeriodEndAt" label="等待期截止">
            {{ fmtTime(detail.waitPeriodEndAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <div class="actions">
          <el-button
            v-if="detail.status === 'PENDING_REVIEW'"
            type="primary"
            :loading="acting"
            @click="accept"
          >
            受理
          </el-button>
          <el-button
            v-if="detail.status === 'REVIEWING'"
            type="success"
            :loading="acting"
            @click="approve"
          >
            批准
          </el-button>
          <el-button
            v-if="detail.status === 'PENDING_REVIEW' || detail.status === 'REVIEWING'"
            type="danger"
            :loading="acting"
            @click="reject"
          >
            驳回
          </el-button>

          <template v-if="detail.status === 'DISPUTED'">
            <el-button type="warning" :loading="acting" @click="resolveDispute('UPHELD')">
              异议不成立
            </el-button>
            <el-button type="danger" :loading="acting" @click="resolveDispute('OVERTURNED')">
              异议成立
            </el-button>
          </template>
        </div>
      </el-card>

      <el-card shadow="never" style="margin-top: 16px">
        <template #header><span>审批链</span></template>
        <el-empty v-if="!detail.approvals.length" description="暂无审批记录" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="(item, index) in detail.approvals"
            :key="index"
            :timestamp="fmtTime(item.createdAt)"
            placement="top"
          >
            <div>
              <strong>{{ APPROVAL_ACTION_LABEL[item.action] || item.action }}</strong>
              <span class="muted">
                （{{ item.fromStatus }} → {{ item.toStatus }}）
              </span>
            </div>
            <div v-if="item.comment" class="muted">{{ item.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>
