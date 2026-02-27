import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
    baseURL: '/api',
    timeout: 120000
})

// 请求拦截器：附加 token（为未来认证预留）
service.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// 响应拦截器
service.interceptors.response.use(
    response => {
        return response.data
    },
    error => {
        const status = error.response?.status
        const detail = error.response?.data?.detail

        if (status === 401) {
            ElMessage.error('登录已过期，请重新登录')
        } else if (detail) {
            // 后端返回了具体的错误信息
            ElMessage.error(typeof detail === 'string' ? detail : detail.message || '请求失败')
        } else {
            ElMessage.error(error.message || '请求失败')
        }

        console.error('API Error:', error)
        return Promise.reject(error)
    }
)

export default service
