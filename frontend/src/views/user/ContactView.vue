<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { contactApi } from '@/api'
import type { ContactRelation } from '@/api/types'
import { contactLabel, contactType, fmtTime } from '@/utils/labels'

const loading = ref(false)
const relations = ref<ContactRelation[]>([])

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', note: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入对方用户名', trigger: 'blur' }]
}

/** 我邀请的人 */
const invitedByMe = computed(() => relations.value.filter((r) => r.iAmOwner))
/** 邀请我的人 */
const invitingMe = computed(() => relations.value.filter((r) => !r.iAmOwner))
/** 待我处理的邀请 */
const pendingForMe = computed(() => invitingMe.value.filter((r) => r.canRespond))

async function load() {
  loading.value = true
  try {
    relations.value = await contactApi.list()
  } finally {
    loading.value = false
  }
}

function openInvite() {
  form.username = ''
  form.note = ''
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await contactApi.invite({ username: form.username, note: form.note || undefined })
    ElMessage.success('邀请已发出，等待对方确认')
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function accept(row: ContactRelation) {
  await ElMessageBox.confirm(
    `确认接受「${row.ownerDisplayName}」的邀请成为其可信联系人吗？`,
    '接受邀请',
    { type: 'info' }
  )
  await contactApi.accept(row.id)
  ElMessage.success('已接受')
  await load()
}

async function reject(row: ContactRelation) {
  await ElMessageBox.confirm(`确定拒绝「${row.ownerDisplayName}」的邀请吗？`, '拒绝邀请', {
    type: 'warning'
  })
  await contactApi.reject(row.id)
  ElMessage.success('已拒绝')
  await load()
}

async function revoke(row: ContactRelation) {
  await ElMessageBox.confirm(
    `确定撤销与「${row.contactDisplayName}」的联系人关系吗？撤销后对方将无法再发起新的授权申请，`
      + '但已经生效的授权不受影响。',
    '撤销关系',
    { type: 'warning' }
  )
  await contactApi.revoke(row.id)
  ElMessage.success('已撤销')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">可信联系人</h2>
      <el-button type="primary" @click="openInvite">
        <el-icon><Plus /></el-icon>邀请联系人
      </el-button>
    </div>

    <el-alert
      v-if="pendingForMe.length"
      type="warning"
      :closable="false"
      show-icon
      :title="`你有 ${pendingForMe.length} 条待处理的邀请`"
      style="margin-bottom: 16px"
    />

    <el-card v-loading="loading" shadow="never">
      <el-tabs>
        <el-tab-pane label="我邀请的人">
          <el-table :data="invitedByMe" border empty-text="还没有邀请任何人">
            <el-table-column label="联系人" min-width="140">
              <template #default="{ row }">{{ row.contactDisplayName }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="contactType(row.status)" size="small">
                  {{ contactLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="note" label="备注" min-width="140">
              <template #default="{ row }">
                <span class="muted">{{ row.note || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="邀请时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.invitedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING' || row.status === 'ACTIVE'"
                  text
                  type="danger"
                  size="small"
                  @click="revoke(row)"
                >
                  撤销
                </el-button>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="邀请我的人">
          <el-table :data="invitingMe" border empty-text="暂无邀请">
            <el-table-column label="邀请人" min-width="140">
              <template #default="{ row }">{{ row.ownerDisplayName }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="contactType(row.status)" size="small">
                  {{ contactLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="note" label="备注" min-width="140">
              <template #default="{ row }">
                <span class="muted">{{ row.note || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.invitedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <template v-if="row.canRespond">
                  <el-button text type="primary" size="small" @click="accept(row)">接受</el-button>
                  <el-button text type="danger" size="small" @click="reject(row)">拒绝</el-button>
                </template>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialogVisible" title="邀请可信联系人" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="对方必须已注册平台账号" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" placeholder="例如：弟弟" maxlength="255" />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="只有对方确认后关系才会生效"
        description="生效的联系人才有资格在你长期未确认状态时发起授权申请。"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">发送邀请</el-button>
      </template>
    </el-dialog>
  </div>
</template>
