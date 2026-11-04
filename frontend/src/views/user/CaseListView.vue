<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { caseApi, contactApi } from '@/api'
import type {
  ApplicableAssetItem,
  ApplicableAssets,
  CaseSummary,
  ContactRelation
} from '@/api/types'
import { ASSET_TYPE_LABEL, caseLabel, caseType, fmtTime } from '@/utils/labels'

const router = useRouter()

const loading = ref(false)
const cases = ref<CaseSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

async function load() {
  loading.value = true
  try {
    const result = await caseApi.list(page.value, size.value)
    cases.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

// ==================== 新建申请 ====================

const dialogVisible = ref(false)
const relations = ref<ContactRelation[]>([])
const relationsLoading = ref(false)
const selectedContactId = ref<number | null>(null)
const applicable = ref<ApplicableAssets | null>(null)
const applicableLoading = ref(false)
const selectedAssetIds = ref<number[]>([])
const reason = ref('')
const submitting = ref(false)

/** 只有"对方邀请我、且关系已生效"的资格才能发起申请 */
const activeRelations = computed(() =>
  relations.value.filter((item) => !item.iAmOwner && item.status === 'ACTIVE')
)

const canSubmit = computed(
  () =>
    !!applicable.value?.canApply &&
    selectedAssetIds.value.length > 0 &&
    !submitting.value
)

async function openCreate() {
  dialogVisible.value = true
  selectedContactId.value = null
  applicable.value = null
  selectedAssetIds.value = []
  reason.value = ''

  relationsLoading.value = true
  try {
    relations.value = await contactApi.list()
  } finally {
    relationsLoading.value = false
  }
}

async function onContactChange() {
  applicable.value = null
  selectedAssetIds.value = []
  if (!selectedContactId.value) {
    return
  }

  applicableLoading.value = true
  try {
    applicable.value = await caseApi.applicableAssets(selectedContactId.value)
  } finally {
    applicableLoading.value = false
  }
}

/** 被其他进行中案件占用的资产不可勾选 */
function selectableAsset(row: ApplicableAssetItem) {
  return !row.occupied
}

function onSelectionChange(rows: ApplicableAssetItem[]) {
  selectedAssetIds.value = rows.map((row) => row.id)
}

async function submitCreate() {
  if (!selectedContactId.value || !selectedAssetIds.value.length) {
    return
  }

  submitting.value = true
  try {
    const created = await caseApi.create({
      contactId: selectedContactId.value,
      assetIds: selectedAssetIds.value,
      reason: reason.value || undefined
    })

    try {
      await caseApi.submit(created.id)
      ElMessage.success('申请已提交审核')
    } catch {
      // 草稿已经创建成功，只是提交这一步失败；让用户去详情页重试，不要丢掉申请
      ElMessage.warning('草稿已创建，但提交审核失败，请在案件详情中重试')
    }

    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">授权案件</h2>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新建申请
      </el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="这里列出的是与你相关的案件"
      description="作为资产所有者：有人申请访问你的资产；作为联系人：你发起的申请。点击可查看完整审批链。"
      style="margin-bottom: 16px"
    />

    <el-table v-loading="loading" :data="cases" border empty-text="暂无案件">
      <el-table-column prop="caseNo" label="案件号" width="210">
        <template #default="{ row }">
          <el-link type="primary" @click="router.push(`/cases/${row.id}`)">
            <span class="mono">{{ row.caseNo }}</span>
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="我的身份" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="row.iAmOwner ? 'info' : 'primary'">
            {{ row.iAmOwner ? '资产所有者' : '申请人' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="caseType(row.status)" size="small">
            {{ caseLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="申请理由" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="muted">{{ row.reason || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="router.push(`/cases/${row.id}`)">
            详情
          </el-button>
        </template>
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

    <el-dialog
      v-model="dialogVisible"
      title="发起授权申请"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-alert
        v-if="!relationsLoading && !activeRelations.length"
        type="warning"
        :closable="false"
        show-icon
        title="没有已生效的联系人关系"
        description="需要对方邀请你成为可信联系人，并且你已接受邀请，之后才能发起申请。"
        style="margin-bottom: 16px"
      />

      <el-form label-width="96px">
        <el-form-item label="联系对象">
          <el-select
            v-model="selectedContactId"
            :loading="relationsLoading"
            :disabled="!activeRelations.length"
            placeholder="选择一位已生效的联系人关系的对方"
            style="width: 100%"
            @change="onContactChange"
          >
            <el-option
              v-for="item in activeRelations"
              :key="item.id"
              :label="item.ownerDisplayName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="选择资产">
          <div v-loading="applicableLoading" style="width: 100%">
            <el-empty
              v-if="!selectedContactId"
              description="请先选择联系对象"
              :image-size="60"
            />

            <template v-else-if="applicable">
              <el-alert
                v-if="!applicable.canApply"
                type="warning"
                :closable="false"
                show-icon
                :title="applicable.blockReason || '当前暂不能发起申请'"
              />

              <template v-else>
                <el-alert
                  v-if="!applicable.assets.length"
                  type="info"
                  :closable="false"
                  show-icon
                  title="该用户名下暂无可用资产"
                />

                <el-table
                  v-else
                  :data="applicable.assets"
                  row-key="id"
                  size="small"
                  border
                  max-height="320"
                  @selection-change="onSelectionChange"
                >
                  <el-table-column
                    type="selection"
                    width="46"
                    :selectable="selectableAsset"
                  />
                  <el-table-column prop="name" label="名称" min-width="150" />
                  <el-table-column label="类型" width="130">
                    <template #default="{ row }">
                      {{ ASSET_TYPE_LABEL[row.assetType] || row.assetType }}
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="description"
                    label="描述"
                    min-width="150"
                    show-overflow-tooltip
                  />
                  <el-table-column label="状态" width="110">
                    <template #default="{ row }">
                      <el-tag v-if="row.occupied" type="info" size="small">已被占用</el-tag>
                      <el-tag v-else type="success" size="small">可选</el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </template>
            </template>
          </div>
        </el-form-item>

        <el-form-item label="申请理由">
          <el-input
            v-model="reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说明申请原因，会展示给所有者和审核管理员"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!canSubmit"
          :loading="submitting"
          @click="submitCreate"
        >
          提交申请
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
