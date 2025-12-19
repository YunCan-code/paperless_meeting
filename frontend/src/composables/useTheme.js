import { ref, onMounted } from 'vue'

export const useTheme = () => {
    const isDark = ref(false)

    // Initialize state
    const initTheme = () => {
        const saved = localStorage.getItem('vueuse-color-scheme')
        const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches

        if (saved === 'dark' || (!saved && systemDark)) {
            setDark(true)
        } else {
            setDark(false)
        }
    }

    const setDark = (val) => {
        isDark.value = val
        if (val) {
            document.documentElement.classList.add('dark')
            localStorage.setItem('vueuse-color-scheme', 'dark')
        } else {
            document.documentElement.classList.remove('dark')
            localStorage.setItem('vueuse-color-scheme', 'light')
        }
    }

    const toggleDark = () => {
        setDark(!isDark.value)
    }

    onMounted(() => {
        initTheme()
    })

    return {
        isDark,
        toggleDark
    }
}
