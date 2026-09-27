<template>
  <div class="page">
    <el-card shadow="never" class="hero">
      <div class="hero-head">
        <div>
          <h2>租赁订单</h2>
          <p>同一台乐器同一时间段只能有一张有效订单；维修中与已报废的乐器不允许出租；晚于租期结束日归还按「日租金 × 逾期天数 × 1.5」计违约金。</p>
        </div>
        <el-button type="primary" @click="openCreate">开出租单</el-button>
      </div>

      <div class="filters">
        <el-select
          v-model="filters.instrumentIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          filterable
          placeholder="按乐器多选（批量筛单）"
          style="width: 300px"
          @change="load"
        >
          <el-option
            v-for="item in instruments"
            :key="item.id"
            :label="`${item.instrumentNo} ${item.name}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px" @change="load">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button text @click="resetFilters">清空筛选</el-button>
        <span class="count">共 {{ rows.length }} 张订单</span>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" row-key="id" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand">
              <el-descriptions :column="4" size="small" border>
                <el-descriptions-item label="订单号">{{ row.orderNo }}</el-descriptions-item>
                <el-descriptions-item label="下单时间">{{ formatDateTime(row.createdAt) }}</el-descriptions-item>
                <el-descriptions-item label="日租金快照">{{ formatMoney(row.dailyRent) }}</el-descriptions-item>
                <el-descriptions-item label="租期天数">{{ row.rentDays }} 天</el-descriptions-item>
                <el-descriptions-item label="押金">{{ formatMoney(row.deposit) }}</el-descriptions-item>
                <el-descriptions-item label="租金合计">{{ formatMoney(row.rentAmount) }}</el-descriptions-item>
                <el-descriptions-item label="实际归还">{{ row.returnDate || '未归还' }}</el-descriptions-item>
                <el-descriptions-item label="违约金">{{ formatMoney(row.overdueFee) }}</el-descriptions-item>
              </el-descriptions>

              <div class="return-box">
                <span class="label">办理归还</span>
                <el-date-picker
                  v-model="returnDates[row.id]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="实际归还日期"
                  style="width: 190px"
                  :disabled="!!row.returnDate"
                />
                <el-button
                  type="primary"
                  size="small"
                  :disabled="!!row.returnDate"
                  @click="doReturn(row)"
                >确认归还并结算</el-button>
                <span class="tip">
                  <template v-if="row.returnDate && row.overdueDays > 0">该订单已按逾期归还结算（违约金保留原结算值）</template>
                  <template v-else-if="row.returnDate">该订单已归还完毕</template>
                  <template v-else-if="row.overdueDays > 0">已逾期 {{ row.overdueDays }} 天未归还，尽快办理结算</template>
                  <template v-else>填一个晚于 {{ row.endDate }} 的日期即可看到逾期违约金</template>
                </span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="orderNo" label="订单号" width="170" />
        <el-table-column label="乐器" min-width="200">
          <template #default="{ row }">
            <strong>{{ row.instrumentNo }}</strong>
            <span class="sub">{{ row.instrumentName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="studentName" label="承租学员" width="110" />
        <el-table-column label="租期" width="210">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column prop="rentDays" label="天数" width="70" />
        <el-table-column label="租金" width="110">
          <template #default="{ row }">{{ formatMoney(row.rentAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="tagType(row)" size="small" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="逾期" width="150">
          <template #default="{ row }">
            <span v-if="row.overdueDays > 0" class="overdue">
              {{ row.overdueDays }} 天 / {{ formatMoney(row.overdueFee) }}<template v-if="!row.returnDate">（试算）</template>
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="开出租单" width="540">
      <el-form :model="form" label-width="96px">
        <el-form-item label="出租乐器">
          <el-select v-model="form.instrumentId" filterable placeholder="选择乐器" style="width: 100%">
            <el-option
              v-for="item in instruments"
              :key="item.id"
              :label="`${item.instrumentNo} ${item.name}（日租 ¥${item.dailyRent}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="承租学员">
          <el-select v-model="form.studentId" filterable placeholder="选择学员" style="width: 100%">
            <el-option
              v-for="item in students"
              :key="item.id"
              :label="`${item.studentNo} ${item.name}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="租期开始">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="租期结束">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="押金">
          <el-input-number v-model="form.deposit" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确认开单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { courseApi, formatDateTime, formatMoney, instrumentApi, rentalApi } from '../api'

const rows = ref([])
const instruments = ref([])
const students = ref([])
const statuses = ref([])
const returnDates = reactive({})

const filters = reactive({ instrumentIds: [], status: '' })
const createVisible = ref(false)
const form = reactive({ instrumentId: null, studentId: null, startDate: '', endDate: '', deposit: 0 })

// 标签颜色按后端统一装配的状态文案判断：逾期中 / 逾期归还 都高亮，已归还转绿
const tagType = (row) => {
  if (row.statusName === '逾期中' || row.statusName === '逾期归还') return 'danger'
  if (row.statusName === '租用中') return 'warning'
  if (row.statusName === '已预约') return 'primary'
  return 'success'
}

const load = async () => {
  const res = await rentalApi.list({ instrumentIds: filters.instrumentIds, status: filters.status })
  rows.value = res.data || []
}

const resetFilters = async () => {
  filters.instrumentIds = []
  filters.status = ''
  await load()
}

const doReturn = async (row) => {
  const date = returnDates[row.id]
  if (!date) {
    ElMessage.warning('请先选择实际归还日期')
    return
  }
  await rentalApi.returnOrder(row.id, date)
  ElMessage.success(`【${row.orderNo}】归还完成`)
  delete returnDates[row.id]
  await load()
}

const openCreate = () => {
  Object.assign(form, { instrumentId: null, studentId: null, startDate: '', endDate: '', deposit: 0 })
  createVisible.value = true
}

const submitCreate = async () => {
  await rentalApi.create({ ...form })
  ElMessage.success('开单成功')
  createVisible.value = false
  await load()
}

onMounted(async () => {
  const [options, instrumentRes, studentRes] = await Promise.all([
    rentalApi.options(),
    instrumentApi.list({}),
    courseApi.students()
  ])
  statuses.value = options.data.statuses
  instruments.value = instrumentRes.data || []
  students.value = studentRes.data || []
  await load()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.hero :deep(.el-card__body) { padding: 18px 20px; }
.hero-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.hero-head h2 { margin: 0 0 6px; font-size: 20px; color: #1e857b; }
.hero-head p { margin: 0; font-size: 13px; color: #7a8a89; max-width: 760px; line-height: 1.6; }
.filters { display: flex; align-items: center; gap: 10px; margin-top: 16px; }
.filters .count { margin-left: auto; font-size: 13px; color: #8a9a99; }
.table-card :deep(.el-card__body) { padding: 0; }
.expand { padding: 14px 18px 18px; background: #f7fbfb; }
.return-box { display: flex; align-items: center; gap: 10px; margin-top: 14px; }
.return-box .label { font-size: 13px; color: #1e857b; font-weight: 600; }
.return-box .tip { font-size: 12px; color: #8a9a99; }
.sub { display: block; font-size: 12px; color: #8a9a99; }
.muted { color: #a8b3b2; }
.overdue { color: #e4562f; font-size: 12px; }
</style>
