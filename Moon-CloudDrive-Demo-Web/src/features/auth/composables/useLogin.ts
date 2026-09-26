/**
 * 登录页面逻辑 Composable
 * 抽离页面状态管理与表单提交逻辑，保持视图层简洁
 */
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

export function useLogin() {
  const router = useRouter()
  const userStore = useUserStore()

  const loginFormRef = ref<FormInstance>()
  const loading = ref(false)

  const loginForm = reactive({
    email: '',
    password: '',
  })

  /** 表单校验规则 */
  const rules: FormRules = {
    email: [
      { required: true, message: '请输入邮箱', trigger: 'blur' },
      { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
    ],
  }

  /** 处理登录提交 */
  async function handleLogin() {
    if (!loginFormRef.value) return
    const valid = await loginFormRef.value.validate().catch(() => false)
    if (!valid) return

    loading.value = true
    try {
      await userStore.login({
        email: loginForm.email,
        password: loginForm.password,
      })
      ElMessage.success('登录成功')
      router.push('/')
    } catch {
      // 错误已在拦截器中处理
    } finally {
      loading.value = false
    }
  }

  return {
    loginFormRef,
    loading,
    loginForm,
    rules,
    handleLogin,
  }
}