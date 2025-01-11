<template>
  <div id="userRegisterPage">
    <h2 calss="title">云图库-用户登录</h2>
    <div class="desc">企业级智能协同云图库</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码长度不能少于8位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <a-form-item name="checkPassword" :rules="[{ required: true, message: '请输入确认密码' }]">
        <a-input-password v-model:value="formState.checkPassword" placeholder="请输入确认密码" />
      </a-form-item>
      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <a-form-item :wrapper-col="{ offset: 8, span: 16 }">
        <a-button type="primary" html-type="submit">注册</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>
<script lang="ts" setup>
import { reactive } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { userRegisterUsingPost } from '@/api/userController.ts'

// 接受表单输入的值
const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 表单信息
 */
interface RegisterFormValues {
  userAccount: string
  userPassword: string
  checkPassword: string
}
const router = useRouter()
// const loginUserStore = useLoginUserStore()

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: RegisterFormValues) => {
  try {
    // 判断两个密码是否一致
    if (values.userPassword !== values.checkPassword) {
      message.error('两次输入的密码不一致')
      return
    }
    const res = await userRegisterUsingPost(values)
    // 检查注册是否成功
    if (res.data.code === 0 && res.data.data) {
      message.success('注册成功')
      router.push({ path: '/user/login', replace: true })
    } else {
      message.error('注册失败，' + res.data.message)
    }
  } catch (error) {
    message.error('注册请求失败，请稍后再试')
  }
}
</script>

<style>
#userRegisterPage {
  max-width: 360px;
  margin: 0 auto;
}
.title {
  text-align: center;
  margin-bottom: 16px;
}
.desc {
  text-align: center;
  color: #bbb;
  margin-bottom: 16px;
}

.tips {
  margin-bottom: 16px;
  color: #bbb;
  font-size: 13px;
  text-align: right;
}
</style>
