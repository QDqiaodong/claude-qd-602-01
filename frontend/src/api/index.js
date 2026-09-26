import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

// 后端统一返回 {ok, message, data}：
// ok=false 时后端已经把中文业务原因放在 message 里，这里只负责弹出来并中断后续逻辑。
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.ok === false) {
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return body
  },
  (error) => {
    const body = error.response && error.response.data
    const message = (body && body.message) || error.message || '网络异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)

/** 去掉 params 里的空值，避免后端收到空串 */
const clean = (params) => {
  const result = {}
  Object.keys(params || {}).forEach((key) => {
    const value = params[key]
    if (value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)) {
      return
    }
    result[key] = Array.isArray(value) ? value.join(',') : value
  })
  return result
}

// ---------------- 乐器台账 ----------------
export const instrumentApi = {
  options: () => http.get('/instruments/options'),
  list: (params) => http.get('/instruments', { params: clean(params) }),
  history: (id) => http.get(`/instruments/${id}/history`),
  create: (data) => http.post('/instruments', data),
  changeStatus: (id, status) => http.post(`/instruments/${id}/status`, { status })
}

// ---------------- 租赁订单 ----------------
export const rentalApi = {
  options: () => http.get('/rentals/options'),
  list: (params) => http.get('/rentals', { params: clean(params) }),
  create: (data) => http.post('/rentals', data),
  returnOrder: (id, returnDate) => http.post(`/rentals/${id}/return`, { returnDate })
}

// ---------------- 调律与维修 ----------------
export const serviceApi = {
  options: () => http.get('/service-orders/options'),
  technicians: () => http.get('/service-orders/technicians'),
  list: (params) => http.get('/service-orders', { params: clean(params) }),
  create: (data) => http.post('/service-orders', data),
  changeStatus: (id, status, note) => http.post(`/service-orders/${id}/status`, { status, note }),
  // 调律师提交完工：本轮处理内容 / 检测结果 / 交付说明
  submitCompletion: (id, data) => http.post(`/service-orders/${id}/completions`, data),
  // 店员验收：decision=ACCEPTED 通过 / REJECTED 退回返修（退回须带 remainingFault / reworkRequest）
  accept: (id, data) => http.post(`/service-orders/${id}/acceptance`, data)
}

// ---------------- 学员与课程 ----------------
export const courseApi = {
  options: () => http.get('/options'),
  students: () => http.get('/students'),
  courses: () => http.get('/courses'),
  courseDetail: (id) => http.get(`/courses/${id}/detail`),
  enrollments: (params) => http.get('/enrollments', { params: clean(params) }),
  enroll: (data) => http.post('/enrollments', data),
  batchEnroll: (data) => http.post('/enrollments/batch', data)
}

export const formatDateTime = (value) => (value ? String(value).replace('T', ' ') : '—')
export const formatMoney = (value) => (value === null || value === undefined ? '—' : `¥${Number(value).toFixed(2)}`)
