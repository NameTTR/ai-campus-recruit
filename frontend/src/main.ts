import { createApp } from 'vue'
import { ElAlert } from 'element-plus/es/components/alert/index'
import { ElButton } from 'element-plus/es/components/button/index'
import { ElDialog } from 'element-plus/es/components/dialog/index'
import { ElEmpty } from 'element-plus/es/components/empty/index'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index'
import { ElInput } from 'element-plus/es/components/input/index'
import { ElInputNumber } from 'element-plus/es/components/input-number/index'
import { ElLoading } from 'element-plus/es/components/loading/index'
import { ElProgress } from 'element-plus/es/components/progress/index'
import { ElRadio, ElRadioGroup } from 'element-plus/es/components/radio/index'
import { ElSegmented } from 'element-plus/es/components/segmented/index'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index'
import { ElSwitch } from 'element-plus/es/components/switch/index'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index'
import { ElTag } from 'element-plus/es/components/tag/index'
import { ElTimeline, ElTimelineItem } from 'element-plus/es/components/timeline/index'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/app.css'

const app = createApp(App)

const elementPlusComponents = [
  ElAlert,
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElLoading,
  ElProgress,
  ElRadio,
  ElRadioGroup,
  ElSegmented,
  ElSelect,
  ElOption,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimeline,
  ElTimelineItem
]

elementPlusComponents.forEach((component) => app.use(component))

app.use(router).mount('#app')
