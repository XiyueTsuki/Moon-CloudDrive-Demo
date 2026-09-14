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
      path: '/share/:shareCode',
      name: 'ShareAccess',
      component: () => import('@/views/ShareAccess.vue'),
    },
    // ------ 认证后可访问的页面（共享导航栏） ------
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'Home',
          component: () => import('@/views/Home.vue'),
        },
        {
          path: 'shares',
          name: 'ShareManage',
          component: () => import('@/views/ShareManage.vue'),
        },
        {
          path: 'recycle-bin',
          name: 'RecycleBin',
          component: () => import('@/views/RecycleBin.vue'),
        },
      ],
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