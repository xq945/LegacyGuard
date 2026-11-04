<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assetApi, caseApi } from '@/api'
import type { CaseAssetItem, CaseDetail } from '@/api/types'
import { APPROVAL_ACTION_LABEL, caseLabel, caseType, fmtTime } from '@/utils/labels'

const route = useRoute()
const router = useRouter()

const caseId = computed(() => Number(route.params.id))
const loading = ref(false)
const detail = ref<CaseDetail | null>(null)
/** 资产元数据由案件详情直接返回：申请人不是所有者，资产列表接口拿不到这份清单 */
const assets = computed(() => detail.value?.assets ?? [])
const contentDialog = ref(false)
const contentTitle = ref('')
const contentText = ref('')
const contentLoading = ref(false)

async function load() {
  loading.value = true
  try {
    detail.value = await caseApi.detail(caseId.value)
  } finally {
    loading.value = false
  }
}

const canSubmit = computed(
  () => detail.value?.status === 'DRAFT' && detail.value.iAmApplicant
)
const canCancel = computed(
  () =>
    !!detail.value &&
    (detail.value.iAmApplicant || detail.value.iAmOwner) &&
    ['DRAFT', 'PENDING_REVIEW', 'REVIEWING', 'WAITING_PERIOD'].includes(detail.value.status)
)
const canDispute = computed(
  () =>
    !!detail.value &&
    (detail.value.iAmApplicant || detail.value.iAmOwner) &&
    detail.value.status === 'WAITING_PERIOD'
)
const canRevoke = computed(
  () => !!detail.value && detail.value.iAmOwner && detail.value.status === 'AUTHORIZED'
)

async function submit() {
  await ElMessageBox.confirm('提交后将进入管理员审核流程，期间无法直接修改资产范围。', '提交审核', {
    type: 'info'
  })
  await caseApi.submit(caseId.value)
  ElMessage.success('已提交')
  await load()
}

async function cancel() {
  const { value } = await ElMessageBox.prompt('请填写取消原因（可选）', '取消案件', {
    inputPlaceholder: '例如：情况已变化，不再需要',
    confirmButtonText: '确认取消',
    type: 'warning'
  })
  await caseApi.cancel(caseId.value, value || undefined)
  ElMessage.success('案件已取消')
  await load()
}

async function dispute() {
  const { value } = await ElMessageBox.prompt(
    '异议会让流程暂停并转交管理员处理。请说明理由。',
    '提出异议',
    { inputPlaceholder: '例如：我本人健在，此申请存在冒名情形', type: 'warning' }
  )
  if (!value) {
    ElMessage.warning('异议理由不能为空')
    return
  }
  await caseApi.dispute(caseId.value, value)
  ElMessage.success('异议已提交，流程已暂停')
  await load()
}

async function revoke() {
  await ElMessageBox.confirm(
    '撤销后，申请人将立即无法访问相关资产。此操作不可恢复。',
    '撤销授权',
    { type: 'warning', confirmButtonText: '确认撤销' }
  )
  await caseApi.revoke(caseId.value)
  ElMessage.success('授权已撤销')
  await load()
}

/**
 * 读取资产明文。
 *
 * 这个按钮只在该案件"已授权"且当前用户是申请人时才有意义；
 * 但即便界面上不显示，后端依然会独立校验权限 —— 前端判断只是体验层。
 */
async function viewAsset(asset: CaseAssetItem) {
  if (asset.deleted) {
    ElMessage.warning('该资产已被所有者删除，无法读取')
    return
  }
  contentLoading.value = true
  contentDialog.value = true
  contentTitle.value = asset.name
  contentText.value = ''
  try {
    const result = await assetApi.content(asset.id)
    contentText.value = result.content
  } catch {
    contentDialog.value = false
  } finally {
    contentLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="page-header">
      <h2 class="page-title">
        <el-button text @click="router.push('/cases')">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        案件详情
      </h2>
    </div>

    <template v-if="detail">
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
          <el-descriptions-item label="我的身份">
            {{ detail.iAmOwner ? '资产所有者' : '申请人' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="申请理由" :span="2">
            {{ detail.reason || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="2">
            {{ detail.reviewComment || '—' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.waitPeriodEndAt" label="等待期截止">
            {{ fmtTime(detail.waitPeriodEndAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.authorizedAt" label="生效时间">
            {{ fmtTime(detail.authorizedAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.revokedAt" label="撤销时间">
            {{ fmtTime(detail.revokedAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="actions">
          <el-button v-if="canSubmit" type="primary" @click="submit">提交审核</el-button>
          <el-button v-if="canDispute" type="warning" @click="dispute">提出异议</el-button>
          <el-button v-if="canRevoke" type="danger" @click="revoke">撤销授权</el-button>
          <el-button v-if="canCancel" @click="cancel">取消案件</el-button>
        </div>

        <el-alert
          v-if="detail.status === 'WAITING_PERIOD'"
          type="warning"
          :closable="false"
          show-icon
          title="当前处于等待期"
          description="审核已通过但尚未生效。等待期结束时会重新校验全部条件，任何一条不满足都会让流程终止。"
          style="margin-top: 12px"
        />
        <el-alert
          v-if="detail.status === 'DISPUTED'"
          type="danger"
          :closable="false"
          show-icon
          title="流程因异议暂停"
          description="管理员会核实异议内容。异议不成立则回到等待期并重新计时；异议成立则案件被驳回。"
          style="margin-top: 12px"
        />
      </el-card>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span>涉及的资产</span></template>
            <el-empty v-if="!assets.length" description="无" :image-size="60" />
            <el-table v-else :data="assets" size="small">
              <el-table-column label="名称">
                <template #default="{ row }">
                  {{ row.name }}
                  <el-tag v-if="row.deleted" type="info" size="small" style="margin-left: 6px">
                    已删除
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="110">
                <template #default="{ row }">
                  <el-button
                    v-if="detail.status === 'AUTHORIZED' && !row.deleted"
                    text
                    type="primary"
                    size="small"
                    @click="viewAsset(row)"
                  >
                    查看内容
                  </el-button>
                  <span v-else class="muted">{{ row.deleted ? '已删除' : '未授权' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card shadow="never">
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
                    （{{ caseLabel(item.fromStatus) || item.fromStatus }}
                    →
                    {{ caseLabel(item.toStatus) || item.toStatus }}）
                  </span>
                </div>
                <div v-if="item.comment" class="muted">{{ item.comment }}</div>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <el-dialog v-model="contentDialog" :title="contentTitle" width="620px">
      <div v-loading="contentLoading">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="通过授权案件访问，本次访问已记录"
          style="margin-bottom: 12px"
        />
        <div class="secret-content">{{ contentText }}</div>
      </div>
    </el-dialog>
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
