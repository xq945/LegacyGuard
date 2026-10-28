<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api'
import type { CaseSummary } from '@/api/types'
import { caseLabel, caseType, fmtTime } from '@/utils/labels'

const router = useRouter()

const loading = ref(false)
const cases = ref<CaseSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const scanning = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await adminApi.pendingCases(page.value, size.value)
    cases.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

/**
 * 手动触发等待期扫描。
 *
 * 等价于定时任务跑一轮，但只处理"已经过了等待期且复核通过"的案件 ——
 * 不是一个"立即授权"的后门。
 */
async function scan() {
  scanning.value = true
  try {
    const result = await adminApi.scanExpired()
    ElMessage.success(`扫描完成，本次生效 ${result.authorized} 个案件`)
    await load()
  } finally {
    scanning.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">案件审核</h2>
      <div>
        <el-button :loading="scanning" @click="scan">触发等待期扫描</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="审批与生效是两个阶段"
      description="批准只会让案件进入等待期；真正生效要等等待期结束，并重新复核所有者是否仍失联、关系是否有效、有无异议。"
      style="margin-bottom: 16px"
    />

    <el-table v-loading="loading" :data="cases" border empty-text="暂无待处理案件">
      <el-table-column prop="caseNo" label="案件号" width="210">
        <template #default="{ row }">
          <el-link type="primary" @click="router.push(`/admin/cases/${row.id}`)">
            <span class="mono">{{ row.caseNo }}</span>
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="caseType(row.status)" size="small">
            {{ caseLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="申请理由" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="muted">{{ row.reason || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="router.push(`/admin/cases/${row.id}`)">
            审核
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
  </div>
</template>
