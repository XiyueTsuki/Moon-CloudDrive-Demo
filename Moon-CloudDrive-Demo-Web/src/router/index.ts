import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/Register.vue'),
    },
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/Home.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/shares',
      name: 'ShareManage',
      component: () => import('@/views/ShareManage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/share/:shareCode',
      name: 'ShareAccess',
      component: () => import('@/views/ShareAccess.vue'),
    },
  ],
})

router.beforeEach((to, _from) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    return '/login'
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    return '/'
  }
})

export default router