<template>
  <div class="page">
    <el-card shadow="never" class="hero">
      <div class="hero-head">
        <div>
          <h2>调律与维修</h2>
          <p>一位调律师同一时段只能有一张在途服务单；已报废乐器不允许派单；休假 / 离职的调律师不接单。施工完成不能直接结单，须提交完工记录（处理内容 / 检测结果 / 交付说明）进入「待验收」，由店员验收通过或退回返修；返修后再次提交，全过程按轮次留痕。</p>
        </div>
        <el-button type="primary" @click="openCreate">排班派单</el-button>
      </div>

      <div class="filters">
        <el-select
          v-model="filters.technicianIds"
          multiple
          collapse-tags
          placeholder="按调律师多选"
          style="width: 240px"
          @change="load"
        >
          <el-option
            v-for="item in technicians"
            :key="item.id"
            :label="`${item.techNo} ${item.name}`"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.instrumentIds"
          multiple
          collapse-tags
          filterable
          placeholder="按乐器多选"
          style="width: 280px"
          @change="load"
        >
          <el-option
            v-for="item in instruments"
            :key="item.id"
            :label="`${item.instrumentNo} ${item.name}`"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button text @click="resetFilters">清空筛选</el-button>
        <span class="count">共 {{ rows.length }} 张服务单</span>
      </div>
    </el-card>

    <!-- 店员待办：所有待验收单集中置顶，直接在维修页处理 -->
    <el-card v-if="pendingRows.length" shadow="never" class="todo-card">
      <div class="todo-head">
        <strong>店员验收待办（{{ pendingRows.length }}）</strong>
        <span class="muted">待验收单只能由店员验收通过或退回返修，刷新 / 重复提交都不会多生成一轮</span>
      </div>
      <div class="todo-list">
        <div v-for="row in pendingRows" :key="`todo-${row.id}`" class="todo-item">
          <div class="todo-main">
            <el-tag type="warning" size="small" effect="dark">待验收 · 第 {{ row.currentRound }} 轮</el-tag>
            <span class="todo-no">{{ row.serviceNo }}</span>
            <span class="muted">{{ row.instrumentNo }} {{ row.instrumentName }} · {{ row.serviceTypeName }} · {{ row.technicianName }}</span>
          </div>
          <div class="todo-ops">
            <el-button size="small" type="success" :loading="acting[`accept-${row.id}`]" @click="openAccept(row, 'ACCEPTED')">验收通过</el-button>
            <el-button size="small" type="danger" plain :loading="acting[`reject-${row.id}`]" @click="openAccept(row, 'REJECTED')">退回返修</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" row-key="id" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand">
              <el-descriptions :column="4" size="small" border>
                <el-descriptions-item label="服务单号">{{ row.serviceNo }}</el-descriptions-item>
                <el-descriptions-item label="乐器状态">{{ instrumentStatusName(row.instrumentStatus) }}</el-descriptions-item>
                <el-descriptions-item label="调律师等级">{{ techLevelName(row.technicianLevel) }}</el-descriptions-item>
                <el-descriptions-item label="计划工时">{{ row.planHours }} 小时</el-descriptions-item>
                <el-descriptions-item label="排期开始">{{ formatDateTime(row.planStart) }}</el-descriptions-item>
                <el-descriptions-item label="排期结束">{{ formatDateTime(row.planEnd) }}</el-descriptions-item>
                <el-descriptions-item label="服务费">{{ formatMoney(row.fee) }}</el-descriptions-item>
                <el-descriptions-item label="当前轮次">第 {{ row.currentRound || 0 }} 轮</el-descriptions-item>
              </el-descriptions>

              <!-- 按轮次还原完整过程：原完工记录与历次验收结论全部保留，不用新内容覆盖旧记录 -->
              <div v-if="(row.rounds && row.rounds.length) || (!row.currentRound && row.resultNote)" class="rounds">
                <div class="rounds-title">完工验收过程（{{ row.currentRound || 0 }} 轮）</div>
                <div v-for="item in row.rounds || []" :key="item.id" class="round">
                  <div class="round-head">
                    <el-tag :type="phaseTagType(item.phase)" size="small" effect="dark">第 {{ item.roundNo }} 轮 · {{ item.phaseName }}</el-tag>
                    <span class="muted">{{ formatDateTime(item.createdAt) }} · {{ item.operatorName }}</span>
                  </div>
                  <div v-if="item.phase === 'COMPLETION'" class="round-body">
                    <p><b>处理内容：</b>{{ item.processNote }}</p>
                    <p><b>检测结果：</b>{{ item.checkResult }}</p>
                    <p><b>交付说明：</b>{{ item.deliveryNote }}</p>
                  </div>
                  <div v-else-if="item.phase === 'REJECTED'" class="round-body reject">
                    <p><b>仍存在故障：</b>{{ item.remainingFault }}</p>
                    <p><b>返修要求：</b>{{ item.reworkRequest }}</p>
                  </div>
                  <div v-else class="round-body accept">
                    <p>店员验收通过，服务单完成。</p>
                  </div>
                </div>
                <!-- 旧数据兼容：验收链路上线前的已完成单没有轮次记录，继续用结果备注正常展示 -->
                <div v-if="!row.currentRound && row.resultNote" class="round legacy">
                  <div class="round-head">
                    <el-tag type="info" size="small" effect="plain">历史记录</el-tag>
                  </div>
                  <div class="round-body"><p>{{ row.resultNote }}</p></div>
                </div>
              </div>
              <div v-else class="muted no-round">尚无完工 / 验收记录</div>

              <div class="advance">
                <span class="label">操作</span>
                <template v-if="row.status === 'PLANNED'">
                  <el-button size="small" type="primary" :loading="acting[`do-${row.id}`]" @click="advance(row, 'DOING')">开始施工</el-button>
                  <el-button size="small" plain :loading="acting[`cancel-${row.id}`]" @click="advance(row, 'CANCELED')">取消派单</el-button>
                </template>
                <template v-else-if="row.status === 'DOING' || row.status === 'REWORKING'">
                  <el-button size="small" type="warning" :loading="acting[`done-${row.id}`]" @click="openSubmit(row)">
                    {{ row.status === 'REWORKING' ? `再次提交完工（第 ${(row.currentRound || 0) + 1} 轮）` : '提交完工' }}
                  </el-button>
                  <el-button v-if="row.status === 'REWORKING'" size="small" plain :loading="acting[`cancel-${row.id}`]" @click="advance(row, 'CANCELED')">取消派单</el-button>
                </template>
                <template v-else-if="row.status === 'PENDING_ACCEPT'">
                  <el-button size="small" type="success" :loading="acting[`accept-${row.id}`]" @click="openAccept(row, 'ACCEPTED')">验收通过</el-button>
                  <el-button size="small" type="danger" plain :loading="acting[`reject-${row.id}`]" @click="openAccept(row, 'REJECTED')">退回返修</el-button>
                </template>
                <el-tag v-else type="info" size="small" effect="plain">{{ terminalHint(row.status) }}</el-tag>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="serviceNo" label="服务单号" width="185" />
        <el-table-column label="乐器" min-width="220">
          <template #default="{ row }">
            <strong>{{ row.instrumentNo }}</strong>
            <span class="sub">{{ row.instrumentName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="调律师" width="120">
          <template #default="{ row }">{{ row.technicianName }}</template>
        </el-table-column>
        <el-table-column prop="serviceTypeName" label="类型" width="90" />
        <el-table-column label="排期" min-width="250">
          <template #default="{ row }">
            {{ formatDateTime(row.planStart) }} ~ {{ formatDateTime(row.planEnd) }}
          </template>
        </el-table-column>
        <el-table-column label="服务费" width="110">
          <template #default="{ row }">{{ formatMoney(row.fee) }}</template>
        </el-table-column>
        <el-table-column label="轮次" width="70" align="center">
          <template #default="{ row }">{{ row.currentRound || 0 }}</template>
        </el-table-column>
        <el-table-column label="最新结论" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.latestDecisionName" :type="row.latestDecisionName === '验收通过' ? 'success' : 'danger'" size="small" effect="light">{{ row.latestDecisionName }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 派单 -->
    <el-dialog v-model="createVisible" title="排班派单" width="560">
      <el-form :model="form" label-width="96px">
        <el-form-item label="服务乐器">
          <el-select v-model="form.instrumentId" filterable placeholder="选择乐器" style="width: 100%">
            <el-option
              v-for="item in instruments"
              :key="item.id"
              :label="`${item.instrumentNo} ${item.name}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调律师">
          <el-select v-model="form.technicianId" placeholder="选择调律师" style="width: 100%">
            <el-option
              v-for="item in technicians"
              :key="item.id"
              :label="`${item.techNo} ${item.name}（${techLevelName(item.level)} / ${techStatusName(item.status)}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="服务类型">
          <el-select v-model="form.serviceType" placeholder="选择类型" style="width: 100%">
            <el-option v-for="item in serviceTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.planStart"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="yyyy-MM-dd HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.planEnd"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="yyyy-MM-dd HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="服务费">
          <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结果备注"><el-input v-model="form.resultNote" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确认派单</el-button>
      </template>
    </el-dialog>

    <!-- 调律师完工提交 -->
    <el-dialog v-model="submitVisible" :title="submitTitle" width="560">
      <el-form :model="submitForm" label-width="96px">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          :title="`提交后服务单进入「待验收」，由店员验收通过或退回返修；这是第 ${(submitRow?.currentRound || 0) + 1} 轮完工记录，历史轮次不会被覆盖。`"
        />
        <el-form-item label="处理内容">
          <el-input v-model="submitForm.processNote" type="textarea" :rows="3" placeholder="本轮做了哪些处理 / 更换了哪些配件" />
        </el-form-item>
        <el-form-item label="检测结果">
          <el-input v-model="submitForm.checkResult" type="textarea" :rows="3" placeholder="逐项检测的结果、是否复现原故障" />
        </el-form-item>
        <el-form-item label="交付说明">
          <el-input v-model="submitForm.deliveryNote" type="textarea" :rows="2" placeholder="交付状态、保修 / 复查说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="confirmSubmit">提交完工验收</el-button>
      </template>
    </el-dialog>

    <!-- 店员验收 -->
    <el-dialog v-model="acceptVisible" :title="acceptDecision === 'ACCEPTED' ? '验收通过' : '退回返修'" width="560">
      <el-form label-width="96px">
        <el-form-item label="验收店员">
          <el-input v-model="acceptForm.operatorName" placeholder="请输入店员姓名" style="width: 100%" />
        </el-form-item>
        <template v-if="acceptDecision === 'ACCEPTED'">
          <el-alert
            type="success"
            :closable="false"
            show-icon
            :title="acceptPassHint"
          />
        </template>
        <template v-else>
          <el-form-item label="仍存故障">
            <el-input v-model="acceptForm.remainingFault" type="textarea" :rows="3" placeholder="退回返修必须写清仍然存在的故障" />
          </el-form-item>
          <el-form-item label="返修要求">
            <el-input v-model="acceptForm.reworkRequest" type="textarea" :rows="3" placeholder="要求调律师如何处理、复检哪些项目" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="acceptVisible = false">取消</el-button>
        <el-button :type="acceptDecision === 'ACCEPTED' ? 'success' : 'danger'" :loading="accepting" @click="confirmAccept">
          {{ acceptDecision === 'ACCEPTED' ? '确认验收通过' : '确认退回返修' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDateTime, formatMoney, instrumentApi, serviceApi } from '../api'

const rows = ref([])
const instruments = ref([])
const technicians = ref([])
const statuses = ref([])
const serviceTypes = ref([])
const acting = reactive({})

const filters = reactive({ instrumentIds: [], technicianIds: [], status: '' })
const createVisible = ref(false)
const form = reactive({
  instrumentId: null,
  technicianId: null,
  serviceType: '',
  planStart: '',
  planEnd: '',
  fee: 200,
  resultNote: ''
})

const submitVisible = ref(false)
const submitting = ref(false)
const submitRow = ref(null)
const submitForm = reactive({ processNote: '', checkResult: '', deliveryNote: '' })

const acceptVisible = ref(false)
const accepting = ref(false)
const acceptRow = ref(null)
const acceptDecision = ref('ACCEPTED')
const acceptForm = reactive({ operatorName: '许棠', remainingFault: '', reworkRequest: '' })

const INSTRUMENT_STATUS_NAME = { IN_STOCK: '在库', RENTED: '租出', REPAIRING: '维修中', SCRAPPED: '报废' }
const TECH_LEVEL_NAME = { JUNIOR: '初级', SENIOR: '高级', MASTER: '首席' }
const TECH_STATUS_NAME = { ACTIVE: '在岗', LEAVE: '休假', OFF: '离职' }

const instrumentStatusName = (value) => INSTRUMENT_STATUS_NAME[value] || value || '—'
const techLevelName = (value) => TECH_LEVEL_NAME[value] || value || '—'
const techStatusName = (value) => TECH_STATUS_NAME[value] || value || '—'

const tagType = (status) => {
  if (status === 'PLANNED') return 'primary'
  if (status === 'DOING') return 'warning'
  if (status === 'PENDING_ACCEPT') return 'warning'
  if (status === 'REWORKING') return 'danger'
  if (status === 'DONE') return 'success'
  return 'info'
}

const phaseTagType = (phase) => {
  if (phase === 'ACCEPTED') return 'success'
  if (phase === 'REJECTED') return 'danger'
  return 'warning'
}

const terminalHint = (status) => {
  if (status === 'DONE') return '已完成（验收通过）'
  if (status === 'CANCELED') return '已取消（终态）'
  return '终态'
}

const pendingRows = computed(() => rows.value.filter((r) => r.status === 'PENDING_ACCEPT'))

const submitTitle = computed(() =>
  submitRow.value && submitRow.value.status === 'REWORKING'
    ? `返修后再次提交完工 · 第 ${(submitRow.value.currentRound || 0) + 1} 轮`
    : '提交完工'
)

const acceptPassHint = computed(() => {
  const r = acceptRow.value
  if (!r) return ''
  if (r.serviceType === 'TUNING') {
    return '调律单验收通过：服务单完成，乐器保持原有可用状态不变。'
  }
  return '验收通过后服务单完成；维修 / 大修类乐器恢复在库（若处理期间已报废则保留报废状态，不恢复在库）。'
})

const load = async () => {
  const res = await serviceApi.list({
    instrumentIds: filters.instrumentIds,
    technicianIds: filters.technicianIds,
    status: filters.status
  })
  rows.value = res.data || []
}

const resetFilters = async () => {
  filters.instrumentIds = []
  filters.technicianIds = []
  filters.status = ''
  await load()
}

const advance = async (row, status) => {
  const key = `${status === 'DOING' ? 'do' : 'cancel'}-${row.id}`
  acting[key] = true
  try {
    await serviceApi.changeStatus(row.id, status, row.resultNote)
    ElMessage.success(`【${row.serviceNo}】已推进`)
    await load()
  } finally {
    acting[key] = false
  }
}

const openCreate = () => {
  Object.assign(form, {
    instrumentId: null,
    technicianId: null,
    serviceType: '',
    planStart: '',
    planEnd: '',
    fee: 200,
    resultNote: ''
  })
  createVisible.value = true
}

const submitCreate = async () => {
  await serviceApi.create({ ...form })
  ElMessage.success('派单成功')
  createVisible.value = false
  await load()
}

const openSubmit = (row) => {
  submitRow.value = row
  Object.assign(submitForm, { processNote: '', checkResult: '', deliveryNote: '' })
  submitVisible.value = true
}

const confirmSubmit = async () => {
  const row = submitRow.value
  submitting.value = true
  acting[`done-${row.id}`] = true
  try {
    await serviceApi.submitCompletion(row.id, { ...submitForm })
    ElMessage.success('完工已提交，等待店员验收')
    submitVisible.value = false
    await load()
  } finally {
    submitting.value = false
    acting[`done-${row.id}`] = false
  }
}

const openAccept = (row, decision) => {
  acceptRow.value = row
  acceptDecision.value = decision
  Object.assign(acceptForm, { operatorName: acceptForm.operatorName || '许棠', remainingFault: '', reworkRequest: '' })
  acceptVisible.value = true
}

const confirmAccept = async () => {
  const row = acceptRow.value
  accepting.value = true
  acting[`${acceptDecision.value === 'ACCEPTED' ? 'accept' : 'reject'}-${row.id}`] = true
  try {
    const payload =
      acceptDecision.value === 'ACCEPTED'
        ? { decision: 'ACCEPTED', operatorName: acceptForm.operatorName }
        : {
            decision: 'REJECTED',
            operatorName: acceptForm.operatorName,
            remainingFault: acceptForm.remainingFault,
            reworkRequest: acceptForm.reworkRequest
          }
    await serviceApi.accept(row.id, payload)
    ElMessage.success(acceptDecision.value === 'ACCEPTED' ? '验收通过，服务单完成' : '已退回返修')
    acceptVisible.value = false
    await load()
  } finally {
    accepting.value = false
    acting[`${acceptDecision.value === 'ACCEPTED' ? 'accept' : 'reject'}-${row.id}`] = false
  }
}

onMounted(async () => {
  const [options, techRes, instrumentRes] = await Promise.all([
    serviceApi.options(),
    serviceApi.technicians(),
    instrumentApi.list({})
  ])
  serviceTypes.value = options.data.serviceTypes
  statuses.value = options.data.statuses
  technicians.value = techRes.data || []
  instruments.value = instrumentRes.data || []
  await load()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.hero :deep(.el-card__body) { padding: 18px 20px; }
.hero-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.hero-head h2 { margin: 0 0 6px; font-size: 20px; color: #1e857b; }
.hero-head p { margin: 0; font-size: 13px; color: #7a8a89; max-width: 860px; line-height: 1.6; }
.filters { display: flex; align-items: center; gap: 10px; margin-top: 16px; flex-wrap: wrap; }
.filters .count { margin-left: auto; font-size: 13px; color: #8a9a99; }
.todo-card :deep(.el-card__body) { padding: 14px 18px; }
.todo-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 10px; }
.todo-head strong { color: #b8821d; font-size: 14px; }
.todo-list { display: flex; flex-direction: column; gap: 8px; }
.todo-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; background: #fffaf0; border: 1px solid #f5e2bd; border-radius: 6px; padding: 8px 12px; }
.todo-main { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.todo-no { font-weight: 600; }
.table-card :deep(.el-card__body) { padding: 0; }
.expand { padding: 14px 18px 18px; background: #f7fbfb; }
.rounds { margin-top: 14px; }
.rounds-title { font-size: 13px; font-weight: 600; color: #1e857b; margin-bottom: 8px; }
.round { border-left: 3px solid #1e857b; background: #fff; border-radius: 0 6px 6px 0; padding: 10px 14px; margin-bottom: 8px; }
.round.reject { border-left-color: #e4562f; }
.round.accept { border-left-color: #2e9e5b; }
.round.legacy { border-left-color: #b9c4c3; }
.round-head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.round-body p { margin: 2px 0; font-size: 13px; color: #455554; line-height: 1.6; }
.round-body.reject p { color: #b04a2f; }
.no-round { margin-top: 12px; font-size: 12px; }
.advance { display: flex; align-items: center; gap: 10px; margin-top: 14px; }
.advance .label { font-size: 13px; color: #1e857b; font-weight: 600; }
.sub { display: block; font-size: 12px; color: #8a9a99; }
.muted { color: #93a09f; font-size: 12px; }
</style>
