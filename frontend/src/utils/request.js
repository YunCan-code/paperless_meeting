import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
    baseURL: '/api', // 使用代理
    timeout: 5000
})

// 响应拦截器
service.interceptors.response.use(
    response => {
        return response.data
    },
    error => {
        console.error('API Error:', error)
        ElMessage.error(error.message || '请求失败')
        return Promise.reject(error)
    }
)

export default service
