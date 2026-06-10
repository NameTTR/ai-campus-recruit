import { createApp } from 'vue'
import { ElButton } from 'element-plus/es/components/button/index'
import { ElEmpty } from 'element-plus/es/components/empty/index'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index'
import { ElInput } from 'element-plus/es/components/input/index'
import { ElLoading } from 'element-plus/es/components/loading/index'
import { ElProgress } from 'element-plus/es/components/progress/index'
import { ElRadio, ElRadioGroup } from 'element-plus/es/components/radio/index'
import { ElSegmented } from 'element-plus/es/components/segmented/index'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index'
import { ElTag } from 'element-plus/es/components/tag/index'
import { ElTimeline, ElTimelineItem } from 'element-plus/es/components/timeline/index'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/app.css'

const app = createApp(App)

const elementPlusComponents = [
  ElButton,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElLoading,
  ElProgress,
  ElRadio,
  ElRadioGroup,
  ElSegmented,
  ElSelect,
  ElOption,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimeline,
  ElTimelineItem
]

elementPlusComponents.forEach((component) => app.use(component))

app.use(router).mount('#app')
