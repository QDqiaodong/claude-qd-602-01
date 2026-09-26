import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/instruments' },
  {
    path: '/instruments',
    name: 'instruments',
    component: () => import('../views/InstrumentView.vue'),
    meta: { label: '乐器台账' }
  },
  {
    path: '/rentals',
    name: 'rentals',
    component: () => import('../views/RentalView.vue'),
    meta: { label: '租赁订单' }
  },
  {
    path: '/services',
    name: 'services',
    component: () => import('../views/ServiceView.vue'),
    meta: { label: '调律与维修' }
  },
  {
    path: '/courses',
    name: 'courses',
    component: () => import('../views/CourseView.vue'),
    meta: { label: '学员与课程' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
