<template>
  <div class="page">
    <el-card shadow="never" class="hero">
      <div class="hero-head">
        <div>
          <h2>乐器台账</h2>
          <p>每台乐器一个编号；状态只能沿「在库 / 租出 / 维修中 → 报废（终态）」流转。展开行看这台琴的全部来龙去脉。</p>
        </div>
        <el-button type="primary" @click="openCreate">登记入库</el-button>
      </div>
      <div class="filters">
        <el-select v-model="filters.category" placeholder="全部分类" clearable style="width: 160px">
          <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 160px">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button @click="load">按条件筛选</el-button>
        <el-button text @click="reset">重置</el-button>
        <span class="count">共 {{ rows.length }} 台乐器</span>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" row-key="id" border @expand-change="onExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand">
              <el-descriptions :column="4" size="small" border class="expand-desc">
                <el-descriptions-item label="品牌">{{ row.brand || '—' }}</el-descriptions-item>
                <el-descriptions-item label="购入日期">{{ row.purchaseDate || '—' }}</el-descriptions-item>
                <el-descriptions-item label="日租金">{{ formatMoney(row.dailyRent) }}</el-descriptions-item>
                <el-descriptions-item label="备注">{{ row.note || '—' }}</el-descriptions-item>
              </el-descriptions>

              <el-skeleton v-if="loading[row.id]" :rows="3" animated />

              <template v-else-if="history[row.id]">
                <div class="expand-block">
                  <h4>租赁历史（{{ history[row.id].rentals.length }} 单）</h4>
                  <el-table :data="history[row.id].rentals" size="small" border>
                    <el-table-column prop="orderNo" label="订单号" width="170" />
                    <el-table-column prop="studentName" label="承租学员" width="110" />
                    <el-table-column label="租期" width="200">
                      <template #default="{ row: r }">{{ r.startDate }} ~ {{ r.endDate }}</template>
                    </el-table-column>
                    <el-table-column prop="rentDays" label="天数" width="70" />
                    <el-table-column label="租金" width="110">
                      <template #default="{ row: r }">{{ formatMoney(r.rentAmount) }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="110">
                      <template #default="{ row: r }">
                        <el-tag :type="rentalTagType(r.status)" size="small" effect="light">{{ r.statusName }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="归还 / 逾期" min-width="180">
                      <template #default="{ row: r }">
                        <span v-if="r.returnDate">{{ r.returnDate }}</span>
                        <span v-else class="muted">未归还</span>
                        <span v-if="r.overdueDays > 0" class="overdue">逾期 {{ r.overdueDays }} 天 · {{ formatMoney(r.overdueFee) }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

                <div class="expand-block">
                  <h4>维修 / 调律历史（{{ history[row.id].services.length }} 单）</h4>
                  <el-table :data="history[row.id].services" size="small" border row-key="id">
                    <el-table-column type="expand">
                      <template #default="{ row: s }">
                        <div class="sv-rounds">
                          <template v-if="s.rounds && s.rounds.length">
                            <div v-for="item in s.rounds" :key="item.id" class="sv-round">
                              <div class="sv-round-head">
                                <el-tag :type="servicePhaseTagType(item.phase)" size="small" effect="dark">第 {{ item.roundNo }} 轮 · {{ item.phaseName }}</el-tag>
                                <span class="muted">{{ formatDateTime(item.createdAt) }} · {{ item.operatorName }}</span>
                              </div>
                              <div v-if="item.phase === 'COMPLETION'" class="sv-round-body">
                                <p><b>处理内容：</b>{{ item.processNote }}</p>
                                <p><b>检测结果：</b>{{ item.checkResult }}</p>
                                <p><b>交付说明：</b>{{ item.deliveryNote }}</p>
                              </div>
                              <div v-else-if="item.phase === 'REJECTED'" class="sv-round-body">
                                <p><b>仍存在故障：</b>{{ item.remainingFault }}</p>
                                <p><b>返修要求：</b>{{ item.reworkRequest }}</p>
                              </div>
                              <div v-else class="sv-round-body"><p>店员验收通过，服务单完成。</p></div>
                            </div>
                          </template>
                          <p v-else-if="s.resultNote" class="muted">历史记录：{{ s.resultNote }}</p>
                          <p v-else class="muted">尚无完工 / 验收记录</p>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column prop="serviceNo" label="服务单号" width="180" />
                    <el-table-column prop="technicianName" label="调律师" width="100" />
                    <el-table-column prop="serviceTypeName" label="类型" width="80" />
                    <el-table-column label="排期" min-width="220">
                      <template #default="{ row: s }">{{ formatDateTime(s.planStart) }} ~ {{ formatDateTime(s.planEnd) }}</template>
                    </el-table-column>
                    <el-table-column label="费用" width="100">
                      <template #default="{ row: s }">{{ formatMoney(s.fee) }}</template>
                    </el-table-column>
                    <el-table-column label="轮次" width="60" align="center">
                      <template #default="{ row: s }">{{ s.currentRound || 0 }}</template>
                    </el-table-column>
                    <el-table-column label="最新结论" width="100">
                      <template #default="{ row: s }">
                        <el-tag v-if="s.latestDecisionName" :type="s.latestDecisionName === '验收通过' ? 'success' : 'danger'" size="small" effect="light">{{ s.latestDecisionName }}</el-tag>
                        <span v-else class="muted">—</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="状态" width="90">
                      <template #default="{ row: s }">
                        <el-tag :type="serviceTagType(s.status)" size="small" effect="light">{{ s.statusName }}</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="instrumentNo" label="编号" width="110" />
        <el-table-column prop="name" label="乐器" min-width="180" />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ categoryName(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="brand" label="品牌" width="90" />
        <el-table-column label="日租金" width="100">
          <template #default="{ row }">{{ formatMoney(row.dailyRent) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="instrumentTagType(row.status)" effect="dark" size="small">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态流转" width="150" align="center">
          <template #default="{ row }">
            <el-dropdown v-if="targets(row.status).length" @command="(cmd) => changeStatus(row, cmd)">
              <el-button size="small" plain>流转到 ▾</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="t in targets(row.status)" :key="t.value" :command="t.value">
                    {{ t.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-tag v-else type="info" size="small" effect="plain">终态</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="登记入库" width="520">
      <el-form :model="form" label-width="92px">
        <el-form-item label="乐器编号"><el-input v-model="form.instrumentNo" placeholder="如 G-1003" /></el-form-item>
        <el-form-item label="乐器名称"><el-input v-model="form.name" placeholder="如 民谣吉他 D-35" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌"><el-input v-model="form.brand" /></el-form-item>
        <el-form-item label="日租金"><el-input-number v-model="form.dailyRent" :min="1" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="购入日期">
          <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.note" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确认入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { instrumentApi, formatDateTime, formatMoney } from '../api'

const rows = ref([])
const categories = ref([])
const statuses = ref([])
const history = reactive({})
const loading = reactive({})
const expandedIds = ref([])

const filters = reactive({ category: '', status: '' })
const createVisible = ref(false)
const form = reactive({ instrumentNo: '', name: '', category: '', brand: '', dailyRent: 50, purchaseDate: '', note: '' })

const CATEGORY_NAME = { GUITAR: '吉他', PIANO: '键盘钢琴', WIND: '管乐', STRING: '弦乐', PERCUSSION: '打击乐' }
const STATUS_NAME = { IN_STOCK: '在库', RENTED: '租出', REPAIRING: '维修中', SCRAPPED: '报废' }
const FLOW = {
  IN_STOCK: ['RENTED', 'REPAIRING', 'SCRAPPED'],
  RENTED: ['IN_STOCK', 'REPAIRING', 'SCRAPPED'],
  REPAIRING: ['IN_STOCK', 'RENTED', 'SCRAPPED'],
  SCRAPPED: []
}

const categoryName = (value) => CATEGORY_NAME[value] || value
const statusName = (value) => STATUS_NAME[value] || value
const targets = (value) => (FLOW[value] || []).map((item) => ({ value: item, label: STATUS_NAME[item] }))

const instrumentTagType = (status) => {
  if (status === 'IN_STOCK') return 'success'
  if (status === 'RENTED') return 'warning'
  if (status === 'REPAIRING') return 'primary'
  return 'info'
}
const rentalTagType = (status) => {
  if (status === 'RENTING') return 'warning'
  if (status === 'BOOKED') return 'primary'
  if (status === 'OVERDUE' || status === 'OVERDUE_UNRETURNED') return 'danger'
  return 'success'
}
const serviceTagType = (status) => {
  if (status === 'DONE') return 'success'
  if (status === 'DOING' || status === 'PENDING_ACCEPT') return 'warning'
  if (status === 'REWORKING') return 'danger'
  if (status === 'CANCELED') return 'info'
  return 'primary'
}
const servicePhaseTagType = (phase) => {
  if (phase === 'ACCEPTED') return 'success'
  if (phase === 'REJECTED') return 'danger'
  return 'warning'
}

const load = async () => {
  const res = await instrumentApi.list({ category: filters.category, status: filters.status })
  rows.value = res.data || []
  // 筛选 / 刷新后旧的展开结论一律作废；仍展开的行立即按最新数据批量重取，不看旧缓存
  Object.keys(history).forEach((key) => delete history[key])
  const stillExpanded = expandedIds.value.filter((id) => rows.value.some((row) => row.id === id))
  await fetchHistories(stillExpanded)
}

const reset = async () => {
  filters.category = ''
  filters.status = ''
  await load()
}

// 批量取回多台乐器的展开历史：一个请求拿全部，不逐台追加请求
const fetchHistories = async (ids) => {
  if (!ids.length) return
  ids.forEach((id) => { loading[id] = true })
  try {
    const res = await instrumentApi.histories(ids)
    ;(res.data || []).forEach((item) => {
      history[item.instrument.id] = item
    })
  } finally {
    ids.forEach((id) => { delete loading[id] })
  }
}

const onExpand = async (row, expandedRows) => {
  const expanded = Array.isArray(expandedRows) ? expandedRows.map((item) => item.id) : []
  expandedIds.value = expanded
  if (!expanded.includes(row.id)) {
    // 收起即作废缓存，下次展开必须重新取最新结论
    delete history[row.id]
    return
  }
  // 本次新展开的行（可能多台）一次性批量取回；已有数据的行不重复请求
  const pending = expanded.filter((id) => !history[id])
  await fetchHistories(pending)
}

const changeStatus = async (row, status) => {
  const res = await instrumentApi.changeStatus(row.id, status)
  ElMessage.success(`【${row.instrumentNo}】已流转为「${statusName(res.data.status)}」`)
  await load()
}

const openCreate = () => {
  Object.assign(form, { instrumentNo: '', name: '', category: '', brand: '', dailyRent: 50, purchaseDate: '', note: '' })
  createVisible.value = true
}

const submitCreate = async () => {
  await instrumentApi.create({ ...form })
  ElMessage.success('入库成功')
  createVisible.value = false
  await load()
}

onMounted(async () => {
  const options = await instrumentApi.options()
  categories.value = options.data.categories
  statuses.value = options.data.statuses
  await load()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.hero :deep(.el-card__body) { padding: 18px 20px; }
.hero-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.hero-head h2 { margin: 0 0 6px; font-size: 20px; color: #1e857b; }
.hero-head p { margin: 0; font-size: 13px; color: #7a8a89; max-width: 720px; line-height: 1.6; }
.filters { display: flex; align-items: center; gap: 10px; margin-top: 16px; }
.filters .count { margin-left: auto; font-size: 13px; color: #8a9a99; }
.table-card :deep(.el-card__body) { padding: 0; }
.expand { padding: 14px 18px 18px; background: #f7fbfb; }
.expand-desc { margin-bottom: 14px; }
.expand-block { margin-top: 12px; }
.expand-block h4 { margin: 0 0 8px; font-size: 13px; color: #1e857b; font-weight: 600; }
.muted { color: #a8b3b2; font-size: 12px; }
.overdue { display: block; margin-top: 2px; color: #e4562f; font-size: 12px; }
.sv-rounds { padding: 10px 14px; }
.sv-round { border-left: 3px solid #1e857b; background: #fff; border-radius: 0 6px 6px 0; padding: 8px 12px; margin-bottom: 8px; }
.sv-round-head { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.sv-round-body p { margin: 2px 0; font-size: 12px; color: #455554; line-height: 1.6; }
</style>
