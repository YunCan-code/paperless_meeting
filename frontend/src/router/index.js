import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'landing',
            component: () => import('../views/Landing.vue')
        },
        {
            path: '/admin',
            name: 'admin',
            component: () => import('../views/Admin/Dashboard.vue'),
            children: [
                {
                    path: 'meetings',
                    name: 'meetings',
                    component: () => import('../views/Admin/MeetingManage.vue')
                },
                {
                    path: 'users',
                    name: 'users',
                    component: () => import('../views/Admin/UserManage.vue')
                },
                {
                    path: 'types',
                    name: 'types',
                    component: () => import('../views/Admin/MeetingTypeManage.vue')
                },
                {
                    path: 'settings',
                    name: 'settings',
                    component: () => import('../views/Admin/Settings.vue')
                },
                {
                    path: 'followup',
                    name: 'followup',
                    component: () => import('../views/Admin/FollowUp.vue')
                },
                {
                    path: 'devices',
                    name: 'devices',
                    component: () => import('../views/Admin/DeviceManage.vue')
                }
            ]
        },
        {
            path: '/mobile',
            name: 'mobile',
            redirect: '/mobile/login',
            children: [
                {
                    path: 'login',
                    component: () => import('../views/Mobile/Login.vue')
                },
                {
                    path: 'home',
                    component: () => import('../views/Mobile/Home.vue')
                }
            ]
        }
    ]
})

export default router
