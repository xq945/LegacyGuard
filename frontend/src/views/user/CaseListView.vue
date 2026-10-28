<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { caseApi } from '@/api'
import type { CaseSummary } from '@/api/types'
import { caseLabel, caseType, fmtTime } from '@/utils/labels'

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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">授权案件</h2>
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
  </div>
</template>
