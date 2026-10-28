<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'
import type { HeartbeatStatus } from '@/api/types'
import { fmtTime } from '@/utils/labels'

const loading = ref(false)
const queue = ref<HeartbeatStatus[]>([])

async function load() {
  loading.value = true
  try {
    queue.value = await adminApi.verifyQueue()
  } finally {
    loading.value = false
  }
}

/**
 * 核验失联成立。
 *
 * 这是系统里唯一能把用户从"提醒完毕"推进到"可申请授权"的操作，
 * 必须由人做决定 —— 再长的超时也换不来这一步。
 */
async function verify(item: HeartbeatStatus) {
  await ElMessageBox.confirm(
    `确认用户（周期 ${item.cycleNo}，已提醒 ${item.reminderCount}/${item.reminderRounds} 轮）`
      + '确实处于失联状态吗？核验通过后，其可信联系人将可以发起授权申请。',
    '核验失联',
    { type: 'warning', confirmButtonText: '确认失联成立' }
  )
  await adminApi.verifyHeartbeat(item.userId)
  ElMessage.success('已核验，该用户进入可申请状态')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">失联核验</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-alert
      type="error"
      :closable="false"
      show-icon
      title="这一步不可轻率"
      description="核验通过意味着允许他人申请访问该用户的资产。系统会记录操作人与时间，请确认已做过独立核实。"
      style="margin-bottom: 16px"
    />

    <el-table v-loading="loading" :data="queue" border empty-text="暂无需核验的用户">
      <el-table-column label="用户 ID" width="120">
        <template #default="{ row }">
          <span class="mono">{{ row.userId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="周期" width="100">
        <template #default="{ row }">第 {{ row.cycleNo }} 期</template>
      </el-table-column>
      <el-table-column label="提醒进度" width="120">
        <template #default="{ row }">{{ row.reminderCount }} / {{ row.reminderRounds }} 轮</template>
      </el-table-column>
      <el-table-column label="逾期" width="120">
        <template #default="{ row }">
          <span class="danger">{{ Math.abs(row.daysRemaining) }} 天</span>
        </template>
      </el-table-column>
      <el-table-column label="下次截止" width="180">
        <template #default="{ row }">{{ fmtTime(row.nextDeadlineAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button type="warning" size="small" @click="verify(row)">核验失联</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.danger {
  color: #f56c6c;
  font-weight: 600;
}
</style>
