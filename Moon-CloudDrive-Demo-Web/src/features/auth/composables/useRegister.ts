/**
 * 注册页面逻辑 Composable
 * 抽离页面状态管理、验证码发送、表单提交等逻辑
 */
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

export function useRegister() {
  const router = useRouter()
  const userStore = useUserStore()

  const registerFormRef = ref<FormInstance>()
  const loading = ref(false)
  const sendingCode = ref(false)
  const countdown = ref(0)

  const registerForm = reactive({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    code: '',
  })

  /** 自定义校验：确认密码是否一致 */
  const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
    if (value !== registerForm.password) {
      callback(new Error('两次输入的密码不一致'))
    } else {
      callback()
    }
  }

  /** 表单校验规则 */
  const rules: FormRules = {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { min: 2, max: 20, message: '用户名长度在2-20个字符之间', trigger: 'blur' },
    ],
    email: [
      { required: true, message: '请输入邮箱', trigger: 'blur' },
      { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
    ],
    confirmPassword: [
      { required: true, message: '请确认密码', trigger: 'blur' },
      { validator: validateConfirmPassword, trigger: 'blur' },
    ],
    code: [
      { required: true, message: '请输入验证码', trigger: 'blur' },
    ],
  }

  /** 发送邮箱验证码（60秒倒计时） */
  async function handleSendCode() {
    if (!registerForm.email) {
      ElMessage.warning('请先输入邮箱')
      return
    }
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailPattern.test(registerForm.email)) {
      ElMessage.warning('请输入正确的邮箱格式')
      return
    }

    sendingCode.value = true
    try {
      await userStore.sendCode({ email: registerForm.email })
      ElMessage.success('验证码已发送，请查收邮件')
      countdown.value = 60
      const timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0) {
          clearInterval(timer)
        }
      }, 1000)
    } catch {
      // 错误已在拦截器中处理
    } finally {
      sendingCode.value = false
    }
  }

  /** 处理注册提交 */
  async function handleRegister() {
    if (!registerFormRef.value) return
    const valid = await registerFormRef.value.validate().catch(() => false)
    if (!valid) return

    loading.value = true
    try {
      await userStore.register({
        username: registerForm.username,
        email: registerForm.email,
        password: registerForm.password,
        code: registerForm.code,
      })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch {
      // 错误已在拦截器中处理
    } finally {
      loading.value = false
    }
  }

  return {
    registerFormRef,
    loading,
    sendingCode,
    countdown,
    registerForm,
    rules,
    handleSendCode,
    handleRegister,
  }
}