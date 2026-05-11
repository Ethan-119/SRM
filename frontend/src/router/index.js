import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/auth/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      redirect: '/supplier',
    },
    {
      path: '/supplier',
      name: 'supplier',
      component: () => import('@/views/SupplierWorkbench.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('@/views/OrderWorkbench.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to, _from, next) => {
  if (to.meta.public) {
    if (to.path === '/login' && getToken()) {
      next({ path: '/supplier' })
      return
    }
    next()
    return
  }
  if (to.meta.requiresAuth && !getToken()) {
    next({
      path: '/login',
      query: { redirect: to.fullPath },
    })
    return
  }
  next()
})

export default router
