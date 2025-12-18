import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            redirect: '/admin'
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
                }
            ]
        }
    ]
})

export default router
