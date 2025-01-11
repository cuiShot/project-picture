<template>
  <div class="picture-upload">
    <a-upload
      list-type="picture-card"
      :show-upload-list="false"
      :custom-request="handleUpload"
      :before-upload="beforeUpload"
    >
      <img v-if="picture?.url" :src="picture?.url" alt="avatar" />
      <div v-else>
        <loading-outlined v-if="loading"></loading-outlined>
        <plus-outlined v-else></plus-outlined>
        <div class="ant-upload-text">点击或拖拽上传图片</div>
      </div>
    </a-upload>
  </div>
</template>
<script lang="ts" setup>
import { ref } from 'vue'
import { LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { UploadProps } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'

/**
 * 该组件是受控组件，由父组件（图片管理页面）来管理，需要定义属性
 * picture 是已上传的图片信息，onSuccess是上传成功后，需要将新图片信息返回给父组件，更新Picture的值
 * 其中定义的属性在可以直接使用
 */
interface Props {
  picture?: API.PictureVO
  //函数定义 接收参数为 newPicture 类型为 PictureVO ，返回值 void
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

/**
 * 上传图片，将图片发送给后端服务器
 * any 表示传入的参数可以是任意类型
 * { file }: any 是一个 解构赋值 的写法：表示只取出 any 中的 file属性
 * @param file
 */
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const params = props.picture ? { id: props.picture.id } : {}
    const res = await uploadPictureUsingPost(params, {}, file)
    if (res.data.data && res.data.code === 0) {
      message.success('图片上传成功')
      // 将上传成功的图片信息传递给父组件
      props.onSuccess?.(res.data.data)
    } else {
      message.error('图片上传失败，' + res.data.message)
    }
  } catch (error) {
    console.error('图片上传失败', error)
    message.error('图片上传失败，' + error.message)
  }
  loading.value = false
}

const loading = ref<boolean>(false)

/**
 * 上传前的校验
 * @param file
 *
 * 总结： file: UploadProps['fileList'][number] 的意思是：
 * 参数 file 的类型是 UploadProps 的 fileList 数组中任意一个元素的类型。
 */
const beforeUpload = (file: UploadProps['fileList'][number]) => {
  // 校验图片格式
  const isJpgOrPng =
    file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/png'
  if (!isJpgOrPng) {
    message.error('不支持上传该格式的图片，推荐 jpg 或 png')
  }
  // 校验图片大小
  const isLt2M = file.size / 1024 / 1024 < 4
  if (!isLt2M) {
    message.error('不能上传超过 4MB 的图片')
  }
  return isJpgOrPng && isLt2M
}
</script>
<style scoped>
.picture-upload :deep(.ant-upload) {
  width: 100% !important;
  height: 100% !important;
  min-width: 152px;
  min-height: 152px;
}

.picture-upload img {
  max-width: 100%;
  max-height: 480px;
}

.ant-upload-select-picture-card i {
  font-size: 32px;
  color: #999;
}

.ant-upload-select-picture-card .ant-upload-text {
  margin-top: 8px;
  color: #666;
}
</style>
