<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { assetApi } from '@/api'
import type { Asset, AssetContent } from '@/api/types'
import { ASSET_TYPE_LABEL, ASSET_TYPE_OPTIONS, fmtTime } from '@/utils/labels'

const loading = ref(false)
const assets = ref<Asset[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  assetType: 'NOTE',
  description: '',
  content: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入资产名称', trigger: 'blur' }],
  assetType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入要托管的内容', trigger: 'blur' }]
}

const contentDialog = ref(false)
const contentLoading = ref(false)
const currentContent = ref<AssetContent | null>(null)

async function load() {
  loading.value = true
  try {
    const result = await assetApi.list(page.value, size.value)
    assets.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.name = ''
  form.assetType = 'NOTE'
  form.description = ''
  form.content = ''
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await assetApi.create({
      name: form.name,
      assetType: form.assetType,
      description: form.description || undefined,
      content: form.content
    })
    ElMessage.success('资产已创建，内容已加密存储')
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

/**
 * 读取明文。
 *
 * 这一次调用会在服务端重新校验权限并写入访问流水 ——
 * 界面上会有提示，让用户知道"看一次就会留一条痕"。
 */
async function viewContent(row: Asset) {
  contentLoading.value = true
  contentDialog.value = true
  currentContent.value = null
  try {
    currentContent.value = await assetApi.content(row.id)
  } catch {
    contentDialog.value = false
  } finally {
    contentLoading.value = false
  }
}

async function remove(row: Asset) {
  await ElMessageBox.confirm(
    `确定删除资产「${row.name}」吗？如果它正被进行中的授权案件引用，删除会被拒绝。`,
    '删除确认',
    { type: 'warning' }
  )
  await assetApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">我的资产</h2>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新建资产
      </el-button>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="内容以信封加密存储，明文不落库"
      description="描述字段是明文，会出现在列表里；真正的敏感内容请填在「托管内容」中。"
      style="margin-bottom: 16px"
    />

    <el-table v-loading="loading" :data="assets" border>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column label="类型" width="140">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ ASSET_TYPE_LABEL[row.assetType] || row.assetType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="muted">{{ row.description || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="viewContent(row)">查看内容</el-button>
          <el-button text type="danger" size="small" @click="remove(row)">删除</el-button>
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

    <!-- 新建 -->
    <el-dialog v-model="dialogVisible" title="新建资产" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：书房硬盘存放说明" maxlength="100" />
        </el-form-item>
        <el-form-item label="类型" prop="assetType">
          <el-select v-model="form.assetType" style="width: 100%">
            <el-option
              v-for="opt in ASSET_TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            placeholder="不含敏感信息的简短说明，会明文展示"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="托管内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="这里填写真正需要保密的内容，将以 AES-256-GCM 加密后存储"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
      </template>
    </el-dialog>

    <!-- 查看内容 -->
    <el-dialog
      v-model="contentDialog"
      :title="currentContent?.name || '资产内容'"
      width="640px"
    >
      <div v-loading="contentLoading">
        <el-alert
          v-if="currentContent"
          :type="currentContent.accessType === 'OWNER_VIEW' ? 'info' : 'success'"
          :closable="false"
          show-icon
          :title="
            currentContent.accessType === 'OWNER_VIEW'
              ? '所有者本人查看，本次访问已记录审计'
              : '通过生效中的授权案件查看'
          "
          style="margin-bottom: 12px"
        />
        <div v-if="currentContent" class="secret-content">{{ currentContent.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>
