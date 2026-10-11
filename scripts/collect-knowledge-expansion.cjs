/* Fetch and verify the next public knowledge batch.  This file intentionally
 * does not import the seed catalogue: the batch is reviewed before merging. */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const outFile = path.join(__dirname, 'data', 'knowledge-expansion-20261011.json');
const logDir = path.join(__dirname, '..', 'logs', 'public-expansion');
fs.mkdirSync(logDir, { recursive: true });
const sources = [];
const add = (category, id, title, url, sourceName, keywords, focus) => sources.push({ category, id, title, url, sourceName, keywords, focus });
const oracle = 'Oracle Java 官方文档';
const spring = 'Spring 官方参考文档';
const mdn = 'MDN Web Docs';
const vue = 'Vue 官方文档';
const react = 'React 官方文档';
const ts = 'TypeScript 官方文档';
const ms = 'Microsoft 官方文档';

const java = [
 ['lexical','Java 词法结构：源码如何被切分','https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html','Unicode、token、注释','标识符、关键字、字面量和分隔符属于不同词法元素'],
 ['types','Java 类型、值与变量：声明决定可用操作','https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html','primitive、reference、type','类型决定值范围、可用操作和赋值兼容关系'],
 ['conversions','Java 类型转换：编译期与运行期的边界','https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html','widening、narrowing、cast','扩大、缩小、装箱和拆箱转换需要分别验证'],
 ['packages','Java 包与模块命名：控制类型可见范围','https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html','package、import、module','import 只简化名称，不改变访问权限'],
 ['classes','Java 类与对象：状态、构造和继承关系','https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html','class、constructor、inheritance','构造过程应保证对象达到可用状态'],
 ['interfaces','Java 接口：约定能力而非保存实现细节','https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html','interface、default、implements','默认方法和多接口冲突需要显式处理'],
 ['arrays','Java 数组：固定长度的同类型容器','https://docs.oracle.com/javase/specs/jls/se21/html/jls-10.html','array、length、covariance','数组长度固定，协变可能把错误推迟到运行时'],
 ['exceptions','Java 异常：用控制流表达失败','https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html','exception、throw、catch','异常类型、抛出点和处理位置共同表达失败路径'],
 ['expressions','Java 表达式：求值顺序与结果类型','https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html','expression、evaluation、operator','表达式类型、优先级和短路求值都需要检查'],
 ['statements','Java 语句：分支循环与控制转移','https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html','if、switch、loop、break','循环退出条件和 switch 分支是边界测试重点'],
 ['collections','Java 集合框架：按访问模式选择容器','https://docs.oracle.com/javase/8/docs/technotes/guides/collections/overview.html','List、Set、Map、Queue','接口表达顺序、唯一性、键值和队列等需求'],
 ['list','List：有序序列与索引访问','https://docs.oracle.com/javase/8/docs/api/java/util/List.html','List、index、duplicate','List 表示有序序列，通常允许重复'],
 ['set','Set：唯一元素与相等性规则','https://docs.oracle.com/javase/8/docs/api/java/util/Set.html','Set、equals、hashCode','元素唯一性依赖相等性规则和具体实现'],
 ['map','Map：键值关联与键的稳定性','https://docs.oracle.com/javase/8/docs/api/java/util/Map.html','Map、key、value','键的相等性和稳定性决定取值可靠性'],
 ['queue','Queue：排队语义与取出方法','https://docs.oracle.com/javase/8/docs/api/java/util/Queue.html','Queue、offer、poll、peek','不同取出方法对空队列有不同约定'],
 ['comparable','Comparable：对象的自然顺序','https://docs.oracle.com/javase/8/docs/api/java/lang/Comparable.html','Comparable、compareTo、ordering','自然顺序与 equals 一致性要特别检查'],
 ['regex','Java 正则表达式：匹配文本而非解析结构','https://docs.oracle.com/javase/tutorial/essential/regex/','Pattern、Matcher、group','Pattern 描述规则，Matcher 在输入上执行匹配'],
 ['annotations','Java 注解：给代码附加可读取元数据','https://docs.oracle.com/javase/tutorial/java/annotations/','annotation、retention、target','保留策略决定注解能否在运行时读取'],
 ['reflection','Java 反射：运行时读取类型信息','https://docs.oracle.com/javase/tutorial/reflect/','Class、reflection、access','反射提高动态性，也增加复杂度和访问控制风险'],
 ['autoclose','try-with-resources：可靠关闭资源','https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html','try、AutoCloseable、suppressed','资源按顺序关闭，关闭异常可保留为 suppressed'],
 ['custom-exception','自定义异常：保留领域语义和失败原因','https://docs.oracle.com/javase/tutorial/essential/exceptions/creating.html','custom exception、cause、message','领域异常应保留 cause、上下文和可读消息'],
 ['thread','Java 线程：任务执行与共享状态','https://docs.oracle.com/javase/tutorial/essential/concurrency/runthread.html','Thread、Runnable、start','调用 start 才交给线程调度，共享状态需要同步策略'],
 ['executors','Executor：把任务提交和线程管理分开','https://docs.oracle.com/javase/tutorial/essential/concurrency/executors.html','Executor、ExecutorService、shutdown','任务提交和线程池生命周期需要同时管理'],
 ['locks','Lock：显式控制临界区','https://docs.oracle.com/javase/tutorial/essential/concurrency/newlocks.html','Lock、unlock、tryLock','显式锁必须确保在 finally 中释放'],
 ['atomic','Atomic 变量：单变量原子更新','https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/package-summary.html','AtomicInteger、CAS、atomic','原子类适合独立变量，不能自动保证复合不变量'],
 ['concurrent-map','ConcurrentHashMap：并发访问的键值表','https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html','ConcurrentHashMap、computeIfAbsent','并发 Map 的原子复合方法可减少竞态'],
 ['forkjoin','Fork/Join：拆分可合并的计算任务','https://docs.oracle.com/javase/tutorial/essential/concurrency/forkjoin.html','ForkJoinPool、RecursiveTask、join','可递归拆分且可合并的计算适合 Fork/Join'],
 ['jdbc','JDBC 基础：连接、语句与结果集','https://docs.oracle.com/javase/tutorial/jdbc/basics/','JDBC、Connection、ResultSet','连接、准备、执行和关闭构成数据访问流程'],
 ['prepared','PreparedStatement：参数化 SQL','https://docs.oracle.com/javase/tutorial/jdbc/basics/prepared.html','PreparedStatement、parameter、SQL injection','参数化语句分离 SQL 结构与输入值'],
 ['jdbc-tx','JDBC 事务：明确提交和回滚边界','https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html','commit、rollback、auto-commit','事务边界决定多个更新是否一致完成'],
 ['spring-config','Spring Boot 外部化配置：环境与代码解耦','https://docs.spring.io/spring-boot/reference/features/external-config.html','properties、profiles、configuration','外部配置用于不同环境替换参数'],
 ['spring-test','Spring Boot 测试：按边界选择测试切片','https://docs.spring.io/spring-boot/reference/testing/index.html','SpringBootTest、test slice、JUnit','按行为和上下文范围选择测试方式'],
 ['spring-profile','Spring Boot Profile：分隔环境配置','https://docs.spring.io/spring-boot/reference/features/profiles.html','profile、active、environment','Profile 按环境组织配置和条件 Bean'],
 ['spring-logging','Spring Boot 日志：让问题可追踪','https://docs.spring.io/spring-boot/reference/features/logging.html','logging、levels、structured','日志级别和输出格式应服务于排障与审计'],
];
java.forEach((x,i)=>add('Java',`java-${x[0]}`,x[1],x[2],x[2].includes('spring.io')?spring:oracle,x[3],x[4]));

const front = [
 ['event-loop','JavaScript 事件循环：安排异步任务','https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Execution_model','event loop、job queue、execution','执行上下文和任务队列决定异步回调何时运行'],
 ['async-function','async 函数：把异步流程写成可读步骤','https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Statements/async_function','async、await、promise','async 函数返回 Promise，await 只暂停当前异步函数'],
 ['error-handling','JavaScript 错误处理：区分可预期失败','https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Guide/Control_flow_and_error_handling','throw、try、catch、finally','错误处理应覆盖可恢复情况并保留上下文'],
 ['modules','JavaScript 模块：显式管理依赖','https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Guide/Modules','import、export、module','模块通过 import/export 建立明确依赖边界'],
 ['classes','JavaScript 类：组织实例行为','https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Classes','class、constructor、extends','类语法组织构造器、方法和继承关系'],
 ['map-set','Map 与 Set：按语义存储数据','https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Map','Map、Set、key','Map 适合键值关联，Set 适合唯一值集合'],
 ['json','JSON：在文本边界交换数据','https://developer.mozilla.org/zh-CN/docs/Learn_web_development/Core/Scripting/JSON','JSON、parse、stringify','JSON 是受限文本格式，解析失败必须被处理'],
 ['web-storage','Web Storage：保存浏览器端小量状态','https://developer.mozilla.org/zh-CN/docs/Web/API/Web_Storage_API','localStorage、sessionStorage、storage','存储范围和生命周期不同，不能把它当安全密钥仓库'],
 ['url','URL 与 URLSearchParams：安全构造查询参数','https://developer.mozilla.org/zh-CN/docs/Web/API/URLSearchParams','URL、query、encoding','URLSearchParams 负责编码和读取查询参数'],
 ['websocket','WebSocket：保持双向长连接','https://developer.mozilla.org/zh-CN/docs/Web/API/WebSockets_API','WebSocket、open、message、close','连接生命周期和断线处理决定实时功能可靠性'],
 ['dom','DOM：通过节点表达页面结构','https://developer.mozilla.org/zh-CN/docs/Web/API/Document_Object_Model','DOM、node、document','DOM API 操作节点树，更新应考虑性能与可访问性'],
 ['accessibility','ARIA：补足可访问语义','https://developer.mozilla.org/zh-CN/docs/Web/Accessibility/ARIA','ARIA、role、label','优先使用原生语义元素，再补充必要 ARIA 属性'],
 ['forms','HTML 表单：让输入可校验可提交','https://developer.mozilla.org/zh-CN/docs/Learn_web_development/Extensions/Forms','form、input、validation','表单控件的类型、约束和错误提示共同影响输入质量'],
 ['semantic-html','语义化 HTML：结构服务于理解','https://developer.mozilla.org/zh-CN/docs/Glossary/Semantics','semantic、article、nav','元素语义帮助浏览器、辅助技术和搜索工具理解页面'],
 ['css-cascade','CSS 层叠：明确样式优先级','https://developer.mozilla.org/zh-CN/docs/Learn_web_development/Core/Styling_basics/Handling_conflicts','cascade、specificity、inheritance','层叠、优先级和继承共同决定最终样式'],
 ['css-grid','CSS Grid：用轨道组织二维布局','https://developer.mozilla.org/zh-CN/docs/Web/CSS/CSS_grid_layout','grid、track、area','Grid 将容器划分为行列轨道以表达区域布局'],
 ['css-media','媒体查询：适配不同视口','https://developer.mozilla.org/zh-CN/docs/Web/CSS/CSS_media_queries/Using_media_queries','media query、viewport、breakpoint','响应式规则根据环境条件调整呈现'],
 ['web-performance','Web 性能：从加载到交互逐项测量','https://developer.mozilla.org/zh-CN/docs/Web/Performance','performance、loading、interaction','性能优化要先测量加载、渲染和交互瓶颈'],
 ['resource-timing','Resource Timing：观察资源加载耗时','https://developer.mozilla.org/zh-CN/docs/Web/API/PerformanceResourceTiming','resource timing、duration、transfer','资源计时可帮助定位请求、传输和缓存问题'],
 ['intersection','IntersectionObserver：感知元素可见性','https://developer.mozilla.org/zh-CN/docs/Web/API/Intersection_Observer_API','intersection、threshold、lazy','观察可见性可支持懒加载和曝光统计'],
 ['vue-lifecycle','Vue 生命周期：在正确阶段做副作用','https://cn.vuejs.org/guide/essentials/lifecycle.html','Vue、lifecycle、onMounted','生命周期钩子用于安排与组件创建、更新和卸载相关的工作'],
 ['vue-watchers','Vue 侦听器：响应特定状态变化','https://cn.vuejs.org/guide/essentials/watchers.html','Vue、watch、watchEffect','侦听器适合异步或副作用逻辑，应避免重复触发'],
 ['vue-computed','Vue 计算属性：缓存派生结果','https://cn.vuejs.org/guide/essentials/computed.html','Vue、computed、derived','计算属性表达派生状态并在依赖变化时更新'],
 ['vue-components','Vue 组件基础：用 props 和事件协作','https://cn.vuejs.org/guide/essentials/component-basics.html','Vue、props、emit','父子组件通过 props 向下传值、事件向上通知'],
 ['vue-routing','Vue Router：让视图与 URL 同步','https://router.vuejs.org/guide/','Vue Router、route、navigation','路由把 URL、组件和导航守卫连接起来'],
 ['react-effects','React Effect：同步外部系统','https://react.dev/learn/synchronizing-with-effects','React、Effect、cleanup','Effect 用于与组件外部系统同步并需要清理副作用'],
 ['react-forms','React 表单：状态驱动输入','https://react.dev/learn/reacting-to-input-with-state','React、form、state','状态模型应覆盖输入、校验和提交状态'],
 ['react-context','React Context：传递跨层共享值','https://react.dev/learn/passing-data-deeply-with-context','React、Context、provider','Context 适合跨层共享稳定的上下文值'],
 ['react-reducer','React Reducer：集中管理复杂状态','https://react.dev/learn/extracting-state-logic-into-a-reducer','React、reducer、action','Reducer 将状态转移集中为可测试的纯函数'],
 ['ts-generics','TypeScript 泛型：保留类型关系','https://www.typescriptlang.org/docs/handbook/2/generics.html','TypeScript、generics、constraint','泛型表达输入输出之间的类型关系并减少重复断言'],
 ['ts-utility','TypeScript 工具类型：从已有类型派生约束','https://www.typescriptlang.org/docs/handbook/utility-types.html','TypeScript、Partial、Pick','工具类型从已有类型派生常见变体'],
 ['ts-functions','TypeScript 函数类型：描述调用契约','https://www.typescriptlang.org/docs/handbook/2/functions.html','TypeScript、parameter、return','参数、返回值和重载共同描述可调用契约'],
 ['ts-modules','TypeScript 模块：保持边界清晰','https://www.typescriptlang.org/docs/handbook/2/modules.html','TypeScript、module、export','模块配置影响导入解析和最终输出边界'],
 ['ts-tsconfig','tsconfig：明确编译项目边界','https://www.typescriptlang.org/tsconfig/','TypeScript、tsconfig、strict','编译选项决定检查强度、模块目标和文件范围'],
 ['web-workers','Web Workers：把计算移出主线程','https://developer.mozilla.org/zh-CN/docs/Web/API/Web_Workers_API','Worker、postMessage、main thread','Worker 通过消息与主线程通信，适合独立计算'],
];
front.forEach((x)=>add('前端',`front-${x[0]}`,x[1],x[2],x[2].includes('vuejs')?vue:x[2].includes('react.dev')?react:x[2].includes('typescriptlang')?ts:mdn,x[3],x[4]));

const ops = [
 ['pivot','Excel 数据透视表：按维度汇总运营明细','https://support.microsoft.com/en-us/office/create-a-pivottable-to-analyze-worksheet-data-a9a84538-bfe9-40a9-a8e9-f99134456576','PivotTable、dimension、measure','透视表按行列维度汇总明细，先保证字段和口径稳定'],
 ['conditional','Excel 条件格式：让异常指标更易发现','https://support.microsoft.com/en-us/office/use-conditional-formatting-to-highlight-information-fed60dfa-1d3f-4e13-9ecb-f1951ff89d7f','conditional formatting、rule、threshold','条件格式把阈值、趋势或重复值转成视觉提示'],
 ['sumifs','Excel SUMIFS：按多个条件汇总数据','https://support.microsoft.com/en-us/office/sumifs-function-c9e748f5-7ea7-455d-9406-611cebce642b','SUMIFS、criteria、sum range','多条件汇总要求条件区域与汇总区域对齐'],
 ['countif','Excel COUNTIF：按条件计数并核对名单','https://support.microsoft.com/en-us/office/countif-function-e0de10c6-f885-4e71-abb4-1f464816df34','COUNTIF、criteria、count','条件计数适合核对状态、名单和重复出现次数'],
 ['xlookup','Excel XLOOKUP：用编号连接业务信息','https://support.microsoft.com/en-us/office/xlookup-function-b7fd680e-6d10-43e6-9f9e-0a4d5c8c1f4c','XLOOKUP、lookup、match','XLOOKUP 按键查找并允许显式处理找不到的情况'],
 ['filter','Excel FILTER：按条件生成动态结果','https://support.microsoft.com/en-us/office/filter-function-f4f7cb66-82eb-4767-8f7f-3f6f04d8f0c8','FILTER、array、include','FILTER 用条件数组返回动态结果，空结果需有提示'],
 ['unique','Excel UNIQUE：识别去重后的业务集合','https://support.microsoft.com/en-us/office/unique-function-c5ab87fd-30a3-4ce9-9b0f-7f1c35f3d4b4','UNIQUE、duplicate、array','UNIQUE 用于提取不重复值，分析前仍要确认空值口径'],
 ['textsplit','Excel TEXTSPLIT：拆分结构化文本','https://support.microsoft.com/en-us/office/textsplit-function-b1ca414e-4c21-4ca0-b1f7-6c2f4a4b5c63','TEXTSPLIT、delimiter、column','按分隔符拆列能整理导入文本但要检查缺失分隔符'],
 ['powerquery','Power Query：记录可重复的数据清洗','https://learn.microsoft.com/en-us/power-query/power-query-what-is-power-query','Power Query、transform、refresh','Power Query 将导入、转换和刷新步骤记录为可复用流程'],
 ['powerquery-merge','Power Query 合并：按键连接表','https://learn.microsoft.com/en-us/power-query/merge-queries-overview','Power Query、merge、join','合并查询依赖键和连接类型，先检查一对多是否放大行数'],
 ['powerquery-group','Power Query 分组：生成可复用汇总','https://learn.microsoft.com/en-us/power-query/group-by','Power Query、group by、aggregation','分组把明细按字段汇总为统计结果'],
 ['powerbi-model','Power BI 数据建模：把事实与维度分开','https://learn.microsoft.com/en-us/power-bi/guidance/star-schema','Power BI、star schema、fact、dimension','星型模型将事实表度量与维度表描述分离'],
 ['powerbi-measures','Power BI 度量值：让指标随筛选重算','https://learn.microsoft.com/en-us/power-bi/transform-model/desktop-measures','Power BI、measure、DAX','度量值在查询上下文中计算，区别于静态列'],
 ['powerbi-slicers','Power BI 切片器：给报表增加可控筛选','https://learn.microsoft.com/en-us/power-bi/visuals/power-bi-visualization-slicers','Power BI、slicer、filter','切片器把常用筛选条件直接交给报表使用者'],
 ['powerbi-refresh','Power BI 定时刷新：让报表按计划更新','https://learn.microsoft.com/en-us/power-bi/connect-data/refresh-scheduled-refresh','Power BI、refresh、schedule','刷新计划必须考虑数据源凭据、时区和失败告警'],
 ['powerbi-bookmarks','Power BI 书签：保存分析视角','https://learn.microsoft.com/en-us/power-bi/create-reports/desktop-bookmarks','Power BI、bookmark、view','书签保存页面状态，适合运营报告的预设视角'],
 ['clarity-overview','Clarity：观察真实用户交互','https://learn.microsoft.com/en-us/clarity/setup-and-installation/about-clarity','Clarity、session、heatmap','行为分析工具帮助观察点击、滚动和会话线索'],
 ['clarity-filters','Clarity 筛选器：缩小行为分析范围','https://learn.microsoft.com/en-us/clarity/filters/clarity-filters','Clarity、filter、segment','筛选条件让运营从总体数据定位特定人群或页面'],
 ['clarity-segments','Clarity 分群：保存可复用用户范围','https://learn.microsoft.com/en-us/clarity/filters/clarity-segments','Clarity、segment、audience','分群将常用行为条件保存为可重复观察的范围'],
 ['clarity-click','Clarity 点击热图：观察互动集中区域','https://learn.microsoft.com/en-us/clarity/heatmaps/click-maps','Clarity、click map、heatmap','点击热图展示页面区域互动分布，不能直接等同转化'],
 ['clarity-scroll','Clarity 滚动热图：检查内容到达程度','https://learn.microsoft.com/en-us/clarity/heatmaps/scroll-maps','Clarity、scroll map、coverage','滚动热图可观察用户到达页面不同位置的比例'],
 ['clarity-recordings','Clarity 会话回放：定位操作阻塞','https://learn.microsoft.com/en-us/clarity/session-recordings/recordings-overview','Clarity、recording、session','会话回放用于形成问题假设，不能代替总体统计'],
 ['clarity-events','Clarity 自定义标签：标记业务事件','https://learn.microsoft.com/en-us/clarity/setup-and-installation/custom-tags','Clarity、custom tag、event','自定义标签把业务语义附加到行为数据'],
 ['journey-email','Customer Insights 邮件：从发送到分析','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/real-time-marketing-email','email、journey、delivery','运营邮件流程需要关注内容、发送条件和结果指标'],
 ['journey-segment','Customer Insights 分群：定义触达对象','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/real-time-marketing-build-segments','segment、audience、journey','触达前先定义稳定且可解释的受众条件'],
 ['journey-trigger','Customer Insights 触发式旅程：响应行为事件','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/real-time-marketing-trigger-based-journey','trigger、event、journey','触发式旅程根据事件启动后续沟通或任务'],
 ['journey-forms','营销表单：规范收集意向信息','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/real-time-marketing-form-overview','form、consent、field','表单字段和同意信息应服务后续触达与数据治理'],
 ['journey-analytics','旅程分析：比较触达结果','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/real-time-marketing-cross-journey-analytics','journey、analytics、conversion','跨旅程分析帮助比较触达路径和结果指标'],
 ['dax-calculate','DAX CALCULATE：在上下文中重算指标','https://learn.microsoft.com/en-us/dax/calculate-function-dax','DAX、CALCULATE、filter context','CALCULATE 修改筛选上下文来表达可解释指标'],
 ['dax-filter','DAX FILTER：构造条件表表达式','https://learn.microsoft.com/en-us/dax/filter-function-dax','DAX、FILTER、table','FILTER 返回满足条件的表，条件应尽量清晰可验证'],
 ['dax-divide','DAX DIVIDE：安全处理除零','https://learn.microsoft.com/en-us/dax/divide-function-dax','DAX、DIVIDE、zero','DIVIDE 对除数为零提供显式替代结果，避免报表错误'],
 ['data-quality','Power BI 数据质量：先检查再解释','https://learn.microsoft.com/en-us/power-query/data-profiling-tools','Power Query、quality、profile','数据剖析工具帮助发现空值、错误和分布异常'],
 ['excel-validation','Excel 数据验证：限制输入范围','https://support.microsoft.com/en-us/office/apply-data-validation-to-cells-29fccbcc-d7a2-4a0d-9c8f-7a3f7e2be0b5','Excel、validation、list','数据验证把业务允许的输入范围前置到录入阶段'],
 ['excel-charts','Excel 图表：选择与问题匹配的编码','https://support.microsoft.com/en-us/office/create-a-chart-from-start-to-finish-0baf399e-dd61-4b2f-8e4e-0f6e0ceeb1b8','Excel、chart、visualization','图表类型应匹配比较、趋势或构成问题'],
 ['powerbi-drill','Power BI 下钻：从总览进入明细','https://learn.microsoft.com/en-us/power-bi/create-reports/desktop-drillthrough','Power BI、drillthrough、detail','下钻页面把汇总异常连接到可行动明细'],
];
ops.forEach((x)=>add('运营',`ops-${x[0]}`,x[1],x[2],x[2].includes('support.microsoft')?'Microsoft Support':ms,x[3],x[4]));

// Keep this increment independent of the already-published catalogue. Failed
// discovery URLs remain in sourceChecks, but never become student materials.
const existing = JSON.parse(fs.readFileSync(path.join(__dirname,'data','verified-public-sources.json'),'utf8'));
const oldUrls = new Set(existing.knowledgeMaterials.map(x=>x.sourceUrl));
const replacedIds = new Set(['ops-xlookup','ops-filter','ops-unique','ops-textsplit','ops-excel-validation','ops-excel-charts','front-css-grid']);
for(let i=sources.length-1;i>=0;i--) if(oldUrls.has(sources[i].url)||replacedIds.has(sources[i].id)) sources.splice(i,1);
sources.find(x=>x.id==='ops-clarity-events').url='https://learn.microsoft.com/en-us/clarity/filters/custom-tags';
add('前端','front-vue-slots','Vue 插槽：让组件结构可复用','https://cn.vuejs.org/guide/components/slots.html',vue,'slot、fallback、scoped','插槽把可复用结构与调用方内容分开');
add('前端','front-vue-performance','Vue 性能：从测量定位更新成本','https://cn.vuejs.org/guide/best-practices/performance.html',vue,'performance、props、lazy','先分析加载和更新成本，再选择优化手段');
const opsMore = [
 ['countifs','Excel COUNTIFS：统一多条件计数口径','https://support.microsoft.com/en-us/office/countifs-function-dda3dc6e-f74e-4aee-88bc-aa8c2a866842','Microsoft Support','COUNTIFS、criteria、range','多个条件同时满足时才计入记录'],
 ['if','Excel IF：把业务规则转成可核对分支','https://support.microsoft.com/en-us/office/if-function-69aed7c9-4e8a-4755-a9bc-aa8bbff73be2','Microsoft Support','IF、logical、true、false','条件判断把规则分为满足和不满足两条路径'],
 ['iferror','Excel IFERROR：区分计算失败与真实零值','https://support.microsoft.com/en-us/office/iferror-function-c526fd07-caeb-47b8-8bb6-63f3e417f611','Microsoft Support','IFERROR、error、value','错误替代结果应保留问题信号而非静默变成零'],
 ['histogram','运营数据直方图：观察分布而非只看均值','https://www.itl.nist.gov/div898/handbook/eda/section3/histogra.htm','NIST 工程统计手册','histogram、distribution、bin','分箱频数帮助发现偏态、多峰和异常范围'],
 ['boxplot','运营数据箱线图：识别中位数和异常点','https://www.itl.nist.gov/div898/handbook/eda/section3/boxplot.htm','NIST 工程统计手册','box plot、median、quartile','箱线图使用分位数描述中心与分散程度'],
 ['paired-test','配对比较：不要忽略同一对象的前后关联','https://www.itl.nist.gov/div898/handbook/eda/section3/eda35a.htm','NIST 工程统计手册','paired、test、mean','成对样本和独立样本应使用不同分析前提'],
 ['journey-ab','A/B 测试：先定义假设和胜出指标','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/real-time-marketing-ab-tests-in-marketing-journeys',ms,'A/B、test、winner','分组测试必须先指定比较对象和胜出依据'],
 ['event-setup','活动筹备：明确时间、场地与报名规则','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/set-up-event',ms,'event、session、registration','活动执行需要统一活动信息和报名入口'],
 ['event-waitlist','活动候补：管理名额和候补转正','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/set-up-and-manage-waitlist',ms,'waitlist、capacity、registration','候补策略处理容量限制和取消后的补位'],
 ['event-followup','活动复盘与跟进：让后续触达可解释','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/followup-after-event',ms,'event、follow、attendee','活动结束后区分报名、实际参与和后续触达'],
 ['email-personalize','内容个性化：只使用有依据的受众字段','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/real-time-marketing-personalization',ms,'personalization、content、data','个性化表达依赖可用字段、默认值和预览验证'],
 ['email-preview','内容发布前检查：预览和测试发送','https://learn.microsoft.com/en-us/dynamics365/customer-insights/journeys/email-preview',ms,'preview、test、email','发布前检查内容、链接和设备呈现'],
];
opsMore.forEach(x=>add('运营',`ops-${x[0]}`,x[1],x[2],x[3],x[4],x[5]));

const details = {
 'front-event-loop': {
  summary:'JavaScript 使用执行栈运行同步代码，把后续工作交给任务队列。一个任务执行完成后才轮到其他任务；Promise 后续处理属于作业队列，不能仅按代码出现位置判断输出先后。理解执行模型有助于定位点击迟滞与状态更新顺序问题。',
  pitfall:'耗时同步循环会占用当前执行线程。把函数写成 async 并不自动把计算移到后台，也不能用固定延迟保证另一项操作已经完成。',
  exercise:'10 分钟：写出同步输出、Promise.then 和定时器各两条日志，先预测顺序再运行；增加一个有限的耗时循环，观察回调何时开始。',
  acceptance:'保存预测、实际顺序和解释，能指出同步部分、异步调度入口与阻塞来源；不把一次耗时值当成跨设备保证。'
 },
 'front-async-function': {
  summary:'async 函数始终返回 Promise。await 在当前异步函数中等待结果，不会让整页停止处理其他事件。串行依赖可以逐步 await，没有依赖的请求应先讨论是否可并行，错误仍需要被捕获并反馈给调用者。',
  pitfall:'忘记 return 或遗漏 await 会改变调用方看到的结果；把所有请求串行化可能增加等待，盲目并行又会破坏前后依赖。',
  exercise:'10 分钟：模拟先取用户再取其收藏的依赖请求，以及两个独立请求；分别写出串行与并行版本，给一个请求加入拒绝分支。',
  acceptance:'测试成功与拒绝场景，调用方能收到结果或错误；解释哪些操作可并行、哪些必须等待，保留可运行的小例子。'
 },
 'front-error-handling': {
  summary:'try、catch、finally 表达可能失败的执行路径与清理动作。throw 可以主动拒绝不满足前置条件的输入；catch 应判断哪些错误可恢复，哪些需要继续抛出。用户提示与开发日志可以分开，但不能因此丢失错误上下文。',
  pitfall:'空 catch 会把失败伪装成成功；finally 中的返回或新错误可能覆盖先前结果。异步 Promise 拒绝还需在异步边界处理。',
  exercise:'10 分钟：为解析 JSON 和异步读取分别写正常、非法输入、拒绝样例；输出可读错误提示，并在 finally 记录清理步骤。',
  acceptance:'失败不会返回成功状态，原始原因可查，清理在成功和异常路径都执行；说清哪些异常留给上层处理。'
 },
 'front-modules': {
  summary:'JavaScript 模块用 export 声明公开能力，用 import 表达依赖。浏览器中的模块加载与普通脚本不同，模块作用域可以减少全局变量冲突；默认导出与具名导出也有不同调用形式。清楚的边界比随处相互引用更利于测试。',
  pitfall:'循环依赖、路径大小写和部署地址差异会造成只在部分环境出现的加载问题。不要把内部实现全部导出，也不要依靠隐含全局状态。',
  exercise:'15 分钟：将一个表单校验器拆成校验模块与页面模块，分别尝试默认和具名导入；制造一次错误路径并观察浏览器错误。',
  acceptance:'只公开必要函数，模块可单独测试，错误路径有明确定位；用本地服务运行示例并记录文件组织方式。'
 },
 'front-classes': {
  summary:'class 为构造对象、实例方法、静态成员和继承提供语法组织。实例保存各自状态，静态方法通过类调用；extends 与 super 连接父子类行为。使用类前先确认对象确实需要长期状态，简单转换逻辑也可以用普通函数。',
  pitfall:'把实例方法脱离对象传给回调时，this 可能与预期不同；静态成员不是每个实例都拥有的属性。继承链过长也会增加理解成本。',
  exercise:'15 分钟：写一个带名称、计数方法和静态校验方法的类，创建两个实例；把方法传给回调，对比直接调用和显式绑定。',
  acceptance:'两实例状态互不覆盖，能解释 this 与静态成员归属，并为无效构造参数提供可测试的失败行为。'
 },
 'front-map-set': {
  summary:'Map 保存键值对并按插入顺序迭代，键可以是对象而不仅是字符串。它提供 size、get、set、has、delete 等明确操作。需要频繁增删和按键查找时，可以比较 Map 与普通对象的语义再决定，不能只把它看成另一种 JSON。',
  pitfall:'对 Map 使用方括号赋值不是 set 操作；两个内容相同但引用不同的对象也不是同一个键。JSON 序列化前要明确转换策略。',
  exercise:'10 分钟：用岗位编号建立 Map，执行添加、覆盖、删除与迭代；再以两个不同对象作为键，观察查找结果。',
  acceptance:'展示各步骤的 size 和值，说明对象键的身份关系；能写出显式的序列化与还原格式，不丢失关键字段。'
 },
 'front-json': {
  summary:'JSON 是数据交换文本，支持对象、数组、字符串、数值、布尔与 null，不包含函数和注释。JSON.parse 将文本转成数据，JSON.stringify 执行反向转换。解析成功仅说明格式成立，还要检查字段是否满足业务要求。',
  pitfall:'把单引号、尾随逗号或注释当作合法 JSON 会失败；日期和特殊对象序列化后不一定能按原类型恢复。不要直接把外部字段当可信配置。',
  exercise:'10 分钟：准备正常、非法格式、缺字段三份资料文本；捕获解析错误，再独立校验姓名与经历数组的类型。',
  acceptance:'格式错误与业务错误分别提示，合法资料能往返转换；记录转换后类型变化，不编造缺失字段。'
 },
 'front-web-storage': {
  summary:'Web Storage 提供键值字符串存储。localStorage 面向同源持久状态，sessionStorage 与页面会话相关；保存复杂资料需要显式序列化。存储读取、容量与浏览器设置都可能失败，因此页面恢复应保留默认值与错误处理。',
  pitfall:'浏览器存储不是秘密保险箱，也不等于服务端账号数据。未校验的旧结构可能破坏页面，频繁写入大对象还会增加主线程负担。',
  exercise:'15 分钟：保存带版本号的表单草稿，刷新后恢复；测试空值、损坏 JSON、旧版字段和删除草稿，添加读取失败提示。',
  acceptance:'恢复只覆盖允许字段，损坏内容不让页面崩溃；说明存储作用域、清理方式，以及跨设备恢复为何需要服务端。'
 },
 'front-url': {
  summary:'URLSearchParams 以明确的 API 构造、读取和修改查询参数，并处理必要编码。get 返回首个值，getAll 可读取重复参数，set 与 append 语义不同。把中文、空格与特殊符号交给参数 API，比手工拼接更易核对。',
  pitfall:'重复编码可能把百分号再编码；重复参数不应无意覆盖。查询字符串适合非敏感筛选条件，不能把密钥和完整个人资料放进 URL。',
  exercise:'10 分钟：构造包含中文岗位、多个技能、空值和带 & 的关键词链接；重新读取参数，比较 get/getAll 与 set/append。',
  acceptance:'参数能往返还原，重复值符合预期；记录空值与缺参数的区别，并确认链接中没有敏感字段。'
 },
 'front-websocket': {
  summary:'WebSocket 在客户端与服务器之间建立持续的双向通信。应用需要处理建立、收到消息、错误与关闭等事件；消息格式也要像普通接口一样校验。连接存在不等于业务消息一定送达，重连与重复消息应由业务设计明确。',
  pitfall:'页面卸载未关闭连接可能残留资源；无节制发送和缓存消息会放大压力。生产地址、安全连接与鉴权不能仅靠前端约定。',
  exercise:'15 分钟：画出连接状态图，用可控测试服务记录 open/message/error/close；断开后尝试发送，设计退避与重复处理说明。',
  acceptance:'每个状态都有可见反馈，关闭后停止发送，坏消息不会破坏页面；说明本练习未验证的服务端容量与可靠性边界。'
 },
 'front-dom': {
  summary:'DOM 把文档表达为可读写的节点树，脚本可以查询节点、调整属性和处理事件。页面更新要关注节点是否仍存在、事件是否重复绑定，以及文本是否来自不可信输入。理解树结构有助于定位复杂界面中“值变了但视图未更新”的问题。',
  pitfall:'innerHTML 直接插入外部文本可能改变结构或引入风险；在循环中反复读布局并写样式也可能增加渲染成本。',
  exercise:'10 分钟：动态添加三个任务节点，使用 textContent 展示含尖括号的输入；删除一个节点后再查询，处理查询结果为空。',
  acceptance:'输入按文本呈现，缺节点不会报错，事件处理不重复；解释节点、元素、属性之间的关系并保留最小页面。'
 },
 'front-accessibility': {
  summary:'ARIA 用角色、状态和属性补充可访问信息，但优先使用原生 HTML 控件。一个能点击的元素还应能用键盘操作，并有可辨识名称。组件视觉状态改变时，可访问状态也要同步，不能只靠颜色或图标传达结果。',
  pitfall:'给普通 div 添加 role 不会自动获得按钮的键盘行为；错误使用 aria-hidden 会让重要内容对辅助技术不可见。',
  exercise:'15 分钟：做一个折叠按钮和内容区，维护 aria-expanded；仅用 Tab、Enter、Space 操作，并检查控件名称与焦点顺序。',
  acceptance:'键盘可完成全部操作，状态属性随展开变化，焦点没有陷阱；记录使用原生元素与 ARIA 补充各自承担的职责。'
 },
 'front-forms': {
  summary:'表单以 label、输入控件和提交行为组织用户数据。恰当的 input 类型与 required、长度等约束可提前发现问题，但浏览器校验不能替代服务端验证。错误提示应说明哪一项需要修改，并保留用户已经输入的内容。',
  pitfall:'占位文字不能代替稳定标签；禁用所有校验或提交失败后清空表单会降低可用性。字段格式正确也不等于内容真实。',
  exercise:'15 分钟：创建姓名、邮箱和日期表单，测试空值、非法邮箱和有效输入；模拟服务端拒绝，保持输入并定位到对应错误字段。',
  acceptance:'标签关联正确、键盘可提交，错误可读且不只依靠红色；前端与服务端验证边界分别写清。'
 },
 'front-semantic-html': {
  summary:'语义描述元素所代表的内容，而不仅是默认外观。标题应形成层级，导航使用合适结构，主要内容应可被定位。选择元素时先问它表示什么，再通过 CSS 控制样式；这能帮助辅助工具、维护者和浏览器理解页面。',
  pitfall:'用大量 div 模拟标题和按钮会丢失语义；多个视觉大标题不一定都应使用 h1，也不应为了字号跳过标题层级。',
  exercise:'10 分钟：将一个由 div 组成的文章页改为 header/nav/main/article/footer，给章节建立连续标题层级并检查键盘链接。',
  acceptance:'页面结构能在没有样式时读懂，标题与区域职责清楚，所有操作保留可访问名称；说明仍需测试的辅助技术场景。'
 },
 'front-css-cascade': {
  summary:'CSS 最终样式由来源、层叠顺序、选择器优先级与继承等规则共同决定。调试时应查看计算样式和命中规则，而不是不断增加更强选择器。可维护的样式结构应减少跨页面覆盖，并给组件保留清晰边界。',
  pitfall:'滥用 !important 或长链选择器会让后续覆盖更难；继承不是所有属性都会发生，同名 class 也可能在全局样式中互相影响。',
  exercise:'15 分钟：为一个按钮同时设置元素、class 和容器规则；逐项删除覆盖，观察颜色、字体和间距最终来源。',
  acceptance:'能指出每个最终值来自哪条规则，并用较少规则保持相同效果；记录继承属性与非继承属性的不同。'
 },
 'front-css-media': {
  summary:'媒体查询根据视口、设备特征或用户偏好应用条件样式。响应式界面应从内容能否阅读、按钮能否操作和滚动能否完成出发。断点不是某个手机型号的固定真理，而是布局在特定宽度需要改变的边界。',
  pitfall:'只缩小字体或固定容器宽度仍可能导致横向溢出；嵌套滚动和固定高度会让长内容看不全。动画还应尊重减少动态效果的偏好。',
  exercise:'15 分钟：把双栏卡片在窄视口改为单栏，检查 320、390、1440 像素；增加长中文标题并测试从页首滚到页尾。',
  acceptance:'无横向溢出、遮挡和不可点击按钮，内容自然滚动；保存各宽度截图与触发布局变化的理由。'
 },
 'front-web-performance': {
  summary:'Web 性能包含资源加载、渲染、动画和交互响应等多个环节。优化应先记录基线与用户可感知的问题，再使用浏览器工具查看耗时；网络传输慢和主线程计算慢需要不同处理，不能把所有问题都归因于文件大小。',
  pitfall:'只在缓存已热、机器较快时测量会忽略首次访问；删除必要内容换取分数也不代表更好的体验。单次数字应结合环境说明。',
  exercise:'15 分钟：记录一个页面首次与再次加载，观察网络瀑布和长任务；选一项可回退修改，再按相同条件重复测量。',
  acceptance:'报告注明设备、网络与缓存条件，前后比较使用同一流程；解释改善的是加载还是交互，并保留功能可用性检查。'
 },
 'front-resource-timing': {
  summary:'PerformanceResourceTiming 提供资源请求的时间和传输信息，可帮助区分等待、连接、响应与总时长。属性是否有值受同源和服务端允许条件影响，因此缺字段不能直接当零耗时。测量适合支持排障，不应把一次记录作为固定性能保证。',
  pitfall:'跨源资源可能没有完整时间信息；缓存命中和传输字节变化也需要结合上下文解释。不能用它单独判断服务端业务处理原因。',
  exercise:'15 分钟：读取页面的 resource 条目，整理名称、duration 和字节字段；刷新后对比，并标注跨源或不可见的信息。',
  acceptance:'输出可复查表格，缺失字段显示未知；用请求记录解释差异，区分可观测事实与尚未验证的原因。'
 },
 'front-intersection': {
  summary:'IntersectionObserver 异步报告目标与根区域的交叉情况，可用于图片懒加载和曝光观察。root、rootMargin 与 threshold 定义触发条件；它提供的是可见性线索，业务仍要定义停留时长和去重规则。',
  pitfall:'元素进入视口一次不等于用户实际读完，也不等于完成转化；忘记 unobserve 或 disconnect 会留下不必要观察。',
  exercise:'10 分钟：观察三张图片，记录进入和离开视口；修改阈值并对比触发记录，删除目标时清理观察。',
  acceptance:'截图与日志能对应触发位置，图片只按预定规则加载；明确“曝光”定义以及本例未验证的阅读行为。'
 },
 'front-vue-lifecycle': {
  summary:'Vue 生命周期钩子围绕组件创建、挂载、更新和卸载安排工作。访问已渲染 DOM 与订阅外部事件应放在合适阶段；计时器、监听器和请求副作用也要有卸载清理。组合式 API 的钩子应在组件建立期间同步注册。',
  pitfall:'把状态变化全写进 updated 可能造成更新循环；组件卸载后迟到响应仍修改状态，会产生难复现的界面问题。',
  exercise:'15 分钟：组件挂载时添加计时器，切换页面后清理；给请求增加延迟，观察卸载后如何忽略迟到结果。',
  acceptance:'重复进入不会叠加计时器，离开后没有残留监听；记录钩子顺序与资源清理位置，并解释异常路径。'
 },
 'front-vue-watchers': {
  summary:'watch 明确监听数据源，watchEffect 在执行时追踪依赖，适合与状态相关的副作用。异步搜索常需清理旧请求或忽略过期结果；侦听器选项和触发时机应服务实际需求。纯粹的派生值通常可先考虑 computed。',
  pitfall:'深度侦听大型对象可能增加成本；快速输入时旧响应晚到会覆盖新结果。不要在侦听器里反复改写同一依赖形成循环。',
  exercise:'15 分钟：实现关键词变化后的异步查询，模拟先发请求晚返回；加入清理或请求序号，比较修复前后。',
  acceptance:'页面最终对应最近输入，空关键词有明确行为；说清侦听源、清理步骤和为何未使用计算属性。'
 },
 'front-vue-routing': {
  summary:'Vue Router 将 URL、路由配置与组件展示连接起来。声明路由、使用 RouterLink 与 RouterView 可以形成可分享的页面地址；导航参数与查询参数应分别校验。刷新后的地址能否访问，还取决于服务器的回退配置。',
  pitfall:'仅靠前端守卫不能替代服务端权限；路由跳转后的旧请求和未保存输入也需要处理。相同组件的参数变化未必触发重新挂载。',
  exercise:'15 分钟：建立列表和详情路由，测试直接打开、前进后退和不存在 ID；在参数变化时更新数据并保留明确加载状态。',
  acceptance:'链接可刷新、返回位置合理，无效参数不请求任意资料；记录路由模式和服务端部署要求。'
 },
 'front-react-effects': {
  summary:'Effect 用于把 React 组件与外部系统同步，例如订阅、连接或第三方控件。每次同步应考虑对应清理，依赖决定何时重新执行。由已有状态计算出的值不一定需要 Effect，事件触发工作也可以放在事件处理器中。',
  pitfall:'缺依赖会使用旧值，无条件更新状态可能形成循环；开发检查中重复执行会暴露缺少清理的问题，不能靠禁用检查掩盖。',
  exercise:'15 分钟：Effect 中订阅一个外部事件，切换参数并卸载组件；记录连接与清理次数，检查快速切换是否残留。',
  acceptance:'每次同步都有匹配清理，依赖能解释；派生状态无需重复保存，测试重复挂载后行为仍正确。'
 },
 'front-react-forms': {
  summary:'React 表单可通过状态表达编辑、提交、成功和失败阶段。先定义界面允许出现的状态，再由状态决定控件是否可用和提示内容，能减少互相矛盾的标记。受控输入需要把值与 onChange 保持一致。',
  pitfall:'同时维护多个可矛盾的布尔值会出现“已成功又在提交”；请求失败后清空输入会使学生失去修改依据。',
  exercise:'15 分钟：用明确状态实现回答提交表单，测试双击、失败、成功和再次编辑；给请求增加延迟查看按钮与输入行为。',
  acceptance:'不会重复提交，失败保留输入，状态切换可画图解释；成功与错误信息互斥，键盘可完成流程。'
 },
 'front-react-context': {
  summary:'Context 将共享值提供给子树，减少跨多层传递属性。它适合主题、当前账号等跨层上下文，但局部状态仍可留在局部。Provider 的位置决定哪些组件读取同一份值，默认值只在没有对应 Provider 时使用。',
  pitfall:'把所有频繁变化状态放进单一 Context 可能扩大更新范围；读取不到 Provider 时不能默认为已经鉴权。',
  exercise:'15 分钟：为页面提供主题上下文，嵌套一个局部 Provider，比较读取结果；拆开账号信息与局部搜索输入。',
  acceptance:'能说明每个组件值来自哪一级，局部输入不污染全局；记录无 Provider 的行为与更新范围。'
 },
 'front-react-reducer': {
  summary:'Reducer 将状态更新表达为“旧状态加 action 得到新状态”的纯函数。复杂表单或任务列表可用它集中维护转移规则，事件处理器只描述发生了什么。纯函数方便单独验证，不应在 reducer 内请求网络或修改外部对象。',
  pitfall:'直接改原状态会破坏 React 对变化的判断；action 太模糊会让业务规则散落在调用方。异步工作应在合适边界执行。',
  exercise:'15 分钟：为任务增加、开始、完成和删除编写 reducer，输入一串 action；冻结旧状态后运行并检查非法转移。',
  acceptance:'旧对象不被修改，同输入同输出，非法操作有明确行为；保存转移测试与 action 含义说明。'
 },
 'front-ts-generics': {
  summary:'泛型保留输入、输出和成员之间的类型关系。约束可以要求类型提供特定属性，而不把所有信息都抹成 any。使用泛型时应明确哪个类型关系需要保持；仅为了复杂而添加类型参数会增加阅读负担。',
  pitfall:'使用类型断言强行返回任意值会破坏泛型承诺；约束允许某些操作，不代表可以构造所有满足约束的具体类型。',
  exercise:'15 分钟：写一个保留元素类型的首项函数，再写按属性取值函数；准备数字、字符串与对象数组以及错误调用。',
  acceptance:'正确调用保留推断类型，错误键名编译失败；不用 any 掩盖问题，解释每个类型参数连接了哪些位置。'
 },
 'front-ts-utility': {
  summary:'Partial、Required、Pick、Omit、Readonly 等工具类型从已有类型生成常见变体。它们描述编译期约束，可减少更新请求和展示对象的重复定义；运行时数据验证仍需单独执行。嵌套结构是否也受约束要逐层检查。',
  pitfall:'Readonly 不等于运行时冻结，Partial 也不保证嵌套对象的所有字段都可缺省；错误组合可能让必须提交字段变得可选。',
  exercise:'10 分钟：从主资料类型派生展示卡片和更新请求，尝试缺少必需 ID 与包含禁止字段；再检查嵌套教育经历。',
  acceptance:'类型边界符合业务意图，错误样例能触发编译检查；列出哪些规则仍由服务端校验，不把类型当安全防线。'
 },
 'front-ts-functions': {
  summary:'函数类型描述参数、返回值、可选项与调用签名。重载适合不同输入对应不同输出的明确关系，简单情况可先用联合类型。回调参数的类型关系与返回值也应写清，便于调用者正确处理异步结果。',
  pitfall:'过多重载让实现难覆盖；把可能没有的参数当必需值会在运行时失败。void 与返回具体值也不能仅凭直觉混用。',
  exercise:'15 分钟：为检索函数声明查询参数和 Promise 返回结果；加入一个可选筛选项，测试正确与错误回调。',
  acceptance:'调用方能推断返回结构，可选值有检查；用最少签名表达关系，并保存不能编译的边界样例。'
 },
 'front-ts-modules': {
  summary:'TypeScript 在 JavaScript 模块基础上提供类型导入与导出，编译配置决定解析和输出方式。一个类型只用于检查时可显式使用类型导入；路径与包导出边界需要和实际运行环境一致，不能只看编辑器是否报错。',
  pitfall:'类型检查通过不保证运行时加载路径正确；把仅有类型的符号当运行时值使用会失败。混用模块体系需核对工具链。',
  exercise:'15 分钟：拆出接口类型与运行函数，分别使用 import type 和普通 import；检查编译输出并运行一次构建结果。',
  acceptance:'输出不包含无用类型引用，运行路径有效；说明解析配置与环境，保留错误导入时的诊断。'
 },
 'front-ts-tsconfig': {
  summary:'tsconfig 定义项目文件范围和编译器选项。strict、target、module 与模块解析选项会影响检查和输出，但不会自动补齐所有运行时能力。更改配置前应理解已有工具链如何读取它，避免多个配置互相覆盖。',
  pitfall:'关闭严格检查只会隐藏风险；错误的 include/exclude 可漏掉文件，较新 target 也不意味着旧浏览器能直接运行。',
  exercise:'15 分钟：为小项目开启严格检查，加入 null、未初始化和错误导入样例；检查文件列表及构建输出，记录新增诊断。',
  acceptance:'配置可重复构建，预期文件被检查；每个重要选项有用途说明，明确仍需转译或兼容处理的环境。'
 },
 'front-web-workers': {
  summary:'Worker 在独立执行上下文中运行脚本，通过 postMessage 等消息接口与主线程通信。它适合不依赖 DOM 的计算工作，页面仍负责交互与展示。数据传递方式和终止生命周期需要设计，不能直接共享普通对象引用。',
  pitfall:'Worker 不能像页面脚本一样操作 DOM；搬运大量数据的成本可能抵消收益。错误、取消和迟到消息也必须处理。',
  exercise:'15 分钟：把数组统计放进 Worker，输入消息带请求 ID；测试连续请求、主动终止和坏消息，页面保持可点击。',
  acceptance:'结果对应正确请求，错误有反馈，结束后清理 Worker；记录主线程与 Worker 各自职责和未测试的规模限制。'
 },
 'front-vue-slots': {
  summary:'插槽让组件负责外壳结构，调用方提供局部内容。具名插槽用于区分不同区域，作用域插槽由子组件向插槽内容暴露必要数据。默认内容可在调用方未提供时保持组件可用，接口仍应尽量稳定清晰。',
  pitfall:'插槽内容的作用域与子组件内部作用域不同；把大量内部状态暴露会增加耦合。空内容与未提供插槽也需按设计判断。',
  exercise:'15 分钟：制作含标题、正文和操作区的卡片，设置默认内容；再通过作用域插槽展示一项状态，测试缺省与完整调用。',
  acceptance:'各区域内容正确替换，默认值可读，调用方只读取允许数据；说明 props 与插槽各自适合的输入。'
 },
 'front-vue-performance': {
  summary:'Vue 性能优化区分页面加载和更新成本。应先测量，再考虑稳定 props、按需加载、列表处理或减少不必要响应式开销。组件数量和数据规模影响优化价值；小页面未必需要复杂缓存或手动控制更新。',
  pitfall:'只凭感觉增加缓存会造成过期数据；把所有内容虚拟化或拆分过细也可能增加复杂度。开发环境耗时不直接等同生产表现。',
  exercise:'15 分钟：记录一个列表筛选的基线，检查 props 是否每次产生新对象；做一项可回退优化后用相同数据重复测量。',
  acceptance:'给出生产构建下的前后记录，功能和排序保持一致；说明瓶颈、修改依据以及无改善时的结论。'
 },
 'ops-countifs': {
  summary:'COUNTIFS 在多个条件区域中按对应位置判断，只有同一条记录满足全部条件才计数。它适合统计指定渠道、时间与状态的报名数量。使用前应先统一“一条记录代表什么”，否则多次报名和多次触达可能被误当成独立用户。',
  pitfall:'各条件区域大小不一致会出错；日期保存为文本或条件写错，会得到看似合理但口径不对的数值。计数记录不等于去重人数。',
  exercise:'10 分钟：制作十行匿名报名明细，按渠道和状态统计；加入重复用户、空状态及文本日期，手工逐行核对条件。',
  acceptance:'公式结果与手工计数相符，明确分母、去重规则与时间范围；保存异常行清单，不静默删除不匹配记录。'
 },
 'ops-if': {
  summary:'IF 按逻辑条件返回两种结果，可把“达到阈值”这类业务规则变成可核对公式。规则需要先定义指标单位、阈值与边界，再填写逻辑测试。它表达的是已定义规则的执行，不会替运营人员决定合理目标。',
  pitfall:'把空值当零、把文本数字当真实数值会改变判断；多个嵌套条件容易漏掉等于阈值的情况，也可能隐藏规则优先级。',
  exercise:'10 分钟：给活动进度设置一个明确阈值，用 IF 标记达标；准备小于、等于、大于阈值和缺数据四类记录。',
  acceptance:'边界结果符合书面规则，缺数据单独显示未知；解释公式每个参数和业务含义，避免把规则标签当事实评价。'
 },
 'ops-iferror': {
  summary:'IFERROR 在公式计算出错误时返回指定替代值，适合给查找或比率计算提供可读提示。替代值必须反映数据状态，例如“未找到”或“需核对”。真正的零值与计算失败是不同事实，不应在报表中合并。',
  pitfall:'一律用零替换会掩盖除零、坏引用和类型问题，让汇总结果失真。错误处理不能替代对原始错误原因的检查。',
  exercise:'10 分钟：准备正确比率、零分母、错误引用三组公式；分别显示原错误与可读提示，再记录每类需要修复的原因。',
  acceptance:'汇总不把失败当零，异常有独立清单；说明哪些问题可暂时展示提示、哪些必须修复后才发布。'
 },
 'ops-powerquery': {
  summary:'Power Query 把连接数据、转换结构和加载结果组织成可刷新步骤。运营周报可以把重命名、类型转换与过滤规则保存下来，减少每次手工清洗。流程应保留来源及步骤含义，以便数据格式改变时定位问题。',
  pitfall:'删除异常行但不记录原因，会让报表口径不可追溯；自动识别类型也可能把编号或日期识别错。刷新不是保证所有源数据正确。',
  exercise:'15 分钟：导入一份匿名 CSV，统一列名和类型，保留空值异常表；替换源文件后刷新，检查步骤是否仍然有效。',
  acceptance:'清洗步骤可复用且有名称，刷新结果与手工样本一致；记录输入格式假设、被排除行数和失败提示。'
 },
 'ops-powerquery-merge': {
  summary:'合并查询按一列或多列键连接两张表，连接类型决定保留哪些记录。把报名明细与渠道字典合并前，应确认键类型一致、字典是否唯一。连接后的行数变化能帮助发现一对多关系或未匹配记录。',
  pitfall:'字典重复键会放大行数并重复计量；直接选择内连接可能丢掉无法匹配的明细。文本空格与大小写差异也需明确处理。',
  exercise:'15 分钟：建立报名表和含一个重复键的渠道表，比较左外连接与内连接；展开字段后检查行数及未匹配项。',
  acceptance:'每条保留或丢弃记录都有解释，重复键被识别；写清选择连接类型的理由，不把放大后的行数当人数。'
 },
 'ops-powerquery-group': {
  summary:'分组将具有相同字段值的明细聚成集合，并计算数量、合计或其他汇总。运营日报可按日期与渠道组合分组，但要先确认记录粒度和数值单位。保留明细入口能让汇总异常回到原始行核对。',
  pitfall:'按日期分组前未处理时区或文本日期，会把同一天拆开；求和与计数不同，用户数还可能需要去重。',
  exercise:'10 分钟：将匿名活动明细按日期、渠道分组，同时输出行数与金额合计；手工核对两组，并查看一组包含的原始行。',
  acceptance:'汇总与明细总量一致，分组键与统计方式可读；说明空键、重复行及异常金额如何处理。'
 },
 'ops-powerbi-measures': {
  summary:'度量值在报表查询时依据当前筛选上下文计算，适合表达总量、比率和差额等指标。它不同于逐行保存的计算列。运营指标要把分子、分母、时间与去重定义写明，才能理解切换筛选后为何变化。',
  pitfall:'把记录行数当人数或把行级比率直接平均，会改变口径；度量值名称清楚也不代表底层关系和数据类型正确。',
  exercise:'15 分钟：在小样本模型中定义报名记录数与确认记录数，计算确认比例；切换日期和渠道筛选，逐组手工核对。',
  acceptance:'每个数能回到明细，指标定义包含分母与未知值处理；区分动态度量值和静态列的用途。'
 },
 'ops-powerbi-slicers': {
  summary:'切片器把筛选字段直接放到报表中，让使用者选择日期、渠道或状态。它影响可视化范围，交互设置还可能改变哪些图表响应。报表应显示当前选择，避免把局部数据误解成全量结果。',
  pitfall:'筛选隐藏但仍生效会造成“总数不对”的错觉；单选、多选和清空行为不同，跨页同步也需明确说明。',
  exercise:'10 分钟：为活动报表添加渠道切片器，测试单选、多选和清空；调整一张图的交互范围并记录差异。',
  acceptance:'页面能辨认当前范围，清空后回到预期全量；说明每张图受哪些筛选影响，并核对显示标题与数据一致。'
 },
 'ops-powerbi-bookmarks': {
  summary:'书签保存报表页面的特定视图状态，可用于“总览、渠道分析、异常核对”等预设分析路径。保存内容包括哪些状态，需要按实际选项确认。书签应帮助复现问题视角，而不是让筛选变化难以被察觉。',
  pitfall:'更新页面后旧书签可能保留不符合预期的筛选或显示状态；把书签当数据快照会误以为历史数值也被固定。',
  exercise:'10 分钟：建立总览和异常两个书签，改变源数据与筛选后再切换；记录哪些状态恢复、哪些结果随刷新变化。',
  acceptance:'书签名称表达用途，恢复状态可核对；明确它保存的是视图而非不可变数据历史，旧配置有更新流程。'
 },
 'ops-clarity-overview': {
  summary:'Clarity 用会话和热图帮助观察用户在页面上的交互。它可提供点击、滚动和行为线索，适合提出“为什么此处可能阻塞”的调查问题。运营分析仍需结合业务统计和其他证据，不能凭单个回放推断全部用户原因。',
  pitfall:'工具看到的会话受采集条件和范围影响；热度高不等于业务效果好。涉及个人信息的页面还要核对数据遮罩与适用政策。',
  exercise:'10 分钟：使用官方演示观察一个页面，写三条可见行为和两个待验证假设；将行为、解释和结论分栏记录。',
  acceptance:'事实能定位到演示页面，假设不写成确定原因；列出需要的业务指标和隐私边界，不保存真实个人资料。'
 },
 'ops-clarity-filters': {
  summary:'筛选器按日期、设备、页面或行为条件缩小会话范围，帮助定位特定体验问题。应用筛选前先写清研究对象，并记录筛选条件；同一问题的前后对比应使用可比较的范围，而不是只挑选支持观点的案例。',
  pitfall:'条件叠加过多会使样本很小；不一致的日期和设备筛选可能制造假差异。过滤后的会话不能代表未被选中的用户。',
  exercise:'10 分钟：在官方演示分别筛选移动端和桌面端，记录条件、样本量和一条共同现象；清空后确认范围恢复。',
  acceptance:'每条观察注明筛选范围，小样本保留不确定说明；能够复现条件，并区分筛选事实与用户行为解释。'
 },
 'ops-clarity-segments': {
  summary:'分群把一组筛选条件保存为可重复使用的分析范围。运营可为新访客、某类页面访问者等建立命名分群，但含义应依赖已提供的字段和条件。保存条件不意味着固定一批永远不变的用户。',
  pitfall:'名字叫“高意向”不等于已经证明购买意愿；时间范围或业务规则改变后，分群结果可能变化，需要核对定义版本。',
  exercise:'10 分钟：在演示中保存一个清晰分群，例如指定设备和页面；改变日期后比较结果，写下分群规则与适用限制。',
  acceptance:'名称不超出数据含义，条件能被他人复现；说明群体为何会变化，并保留无法从行为直接判断的意图。'
 },
 'ops-clarity-click': {
  summary:'点击热图显示页面交互在位置或元素上的分布，可用于检查按钮是否被注意、非交互区域是否被反复点击。不同页面版本和设备布局需要分别观察。热图提供行为线索，不能自动解释用户想法或业务转化结果。',
  pitfall:'把总点击量当点击率，或忽略分母和页面版本，会导致错误对比；重复点击可能来自阻塞，也可能是正常操作。',
  exercise:'10 分钟：观察官方演示中两个页面区域，记录点击分布与元素功能；提出一个可验证改动，并列出需要的补充指标。',
  acceptance:'观察可定位且注明页面范围，假设与事实分开；建议有后续验证方式，不宣称热图已经证明转化提升。'
 },
 'ops-clarity-scroll': {
  summary:'滚动热图观察用户到达页面不同深度的情况，适合检查关键内容是否处在多数用户可能看到的位置。页面高度、设备与内容版本会影响解释。滚动到某位置只能说明到达线索，不能证明读懂或接受了内容。',
  pitfall:'长短页面的相同比例不一定代表相同内容位置；只看整体百分比可能忽略移动端布局和锚点跳转。',
  exercise:'10 分钟：在官方演示定位核心按钮的滚动深度，分别查看不同设备；写一个内容调整假设和验证所需的业务指标。',
  acceptance:'位置、设备和观察范围完整记录，说明到达与阅读的区别；不把滚动统计直接用作技能或转化证据。'
 },
 'ops-clarity-events': {
  summary:'自定义标签给会话附加业务属性，帮助按特定状态或类别分析行为。标签名、允许值和设置时机需要与业务字段一致，避免多个页面用不同含义。它是分析标识，不应携带密钥、完整联系方式或其他不必要敏感信息。',
  pitfall:'重复设置或含义变化会让分群难比较；标签存在不等于事件成功完成。设置标签后还应检查采集与筛选结果。',
  exercise:'15 分钟：设计三个低敏感标签，如页面类型、匿名实验组和状态；写字段字典及设置时机，并用测试页面核对。',
  acceptance:'每个标签有定义和允许值，失败不会误标成功；验证只包含必要信息，说明标签数据的可观测边界。'
 },
 'ops-journey-email': {
  summary:'Customer Insights 的营销邮件围绕内容、受众和发送流程工作。邮件设计后需要检查字段、链接和合规设置，再用于旅程或指定发送场景。运营练习应先学会记录邮件目标与可观测结果，不能把发送成功直接当用户已读或有效转化。',
  pitfall:'不同邮件用途与产品配置有不同边界；收件人数、送达、互动和转化是不同指标。不能从模板示例推断真实活动成果。',
  exercise:'15 分钟：依据文档列出一封匿名活动通知的发送清单，包含目标、字段、链接、默认值、受众和检查项。',
  acceptance:'每个环节有负责人或检查方式，指标名称不混用；在无产品账号时只交付流程草案，不伪造发送数据。'
 },
 'ops-journey-segment': {
  summary:'受众分群用资料字段和条件表达哪些对象应进入触达范围。条件组合应来自可解释业务规则，并在发布前预览成员范围。运营需要记录数据更新频率和缺失字段处理，避免把某个标签当成真实需求已被证明。',
  pitfall:'不同字段来源可能更新不同步；规则过窄会减少样本，规则过宽可能触达无关对象。缺数据不应默认为满足条件。',
  exercise:'15 分钟：为匿名活动定义报名者与未完成确认者两组条件，用十行样本手工演算；加入缺字段和重复记录。',
  acceptance:'每个入群与排除决定都能解释，未知信息单独处理；保存条件版本与预览结果，不使用真实联系方式。'
 },
 'ops-journey-trigger': {
  summary:'触发式旅程在特定事件发生时进入后续流程。设计需要明确触发事件、字段、受众、等待条件和退出方式；业务事件与页面点击不一定相同。把事件定义写清，有助于检查重复触发与异常路径。',
  pitfall:'同一事件重复到达可能造成重复沟通；只写“用户活跃”而不说明触发标准，流程无法核对。触发不代表后续动作成功。',
  exercise:'15 分钟：画出报名完成后的通知旅程，列出正常、重复事件、无有效联系字段与取消报名四条路径。',
  acceptance:'每条路径有明确条件与终点，事件字段不超出来源；标记待验证的去重与送达行为，不自动假定产品配置。'
 },
 'ops-journey-forms': {
  summary:'营销表单把提交字段与后续业务资料连接起来，也需要考虑同意信息和发布方式。应从用途倒推必须收集的字段，给可选项与错误提示清晰说明。提交完成只是获得记录，不等于用户已经同意所有后续使用。',
  pitfall:'过多必填项可能增加负担；默认勾选和含糊文案会让数据使用边界不清。重复提交与来源识别也应提前设计。',
  exercise:'15 分钟：为校园公开活动设计匿名表单字段清单，分必需与可选；编写用途说明、成功提示和重复提交处理。',
  acceptance:'每个字段都有用途，未确认的同意状态保持未知；错误不清空已填内容，说明数据保留与后续触达范围。'
 },
 'ops-journey-analytics': {
  summary:'跨旅程分析汇总不同触达流程的表现，可用于对比范围与阶段结果。分析前应确认日期、旅程版本、对象和指标含义。多个旅程的结果不能只按总量排名，还要讨论受众规模和起点差异。',
  pitfall:'不同目标或不同入口人群的旅程不一定可直接比较；把每次交互计数当独立用户，可能放大触达或转化指标。',
  exercise:'15 分钟：为两条虚构演示旅程制作口径表，列出对象数、目标、时间和阶段指标；只演算已明确的样本数字。',
  acceptance:'数据标明演示性质，结论限定比较范围；每个比例有分母，列出不能直接比较的差异与补充资料。'
 },
 'ops-dax-calculate': {
  summary:'CALCULATE 在调整后的筛选上下文中求值，用于表达“指定条件下的合计”等指标。它不只是给公式加一层括号，筛选如何增加或替换会影响结果。运营报表需要用小样本展示当前上下文与公式新增条件的关系。',
  pitfall:'没有检查筛选上下文就复制公式，可能让某渠道总量错误覆盖页面筛选；指标同名不代表分子分母处于同一范围。',
  exercise:'15 分钟：为十行明细写总量和指定状态总量度量值，切换渠道切片器；手工列出每个结果使用了哪些行。',
  acceptance:'结果可回到具体行，解释筛选变化；公式只采用文档支持的语法，并注明当前模型关系和口径。'
 },
 'ops-dax-filter': {
  summary:'FILTER 对输入表逐行判断条件并返回满足条件的表，常作为其他表达式的输入。它不是简单的界面筛选按钮；输入表范围和条件都影响最终结果。设计指标时先把条件写成业务语言，再验证每条样本是否被保留。',
  pitfall:'对不必要的大表逐行计算可能增加成本；条件中的空值和类型差异会改变保留范围。不要用复杂表达式掩盖不清楚的口径。',
  exercise:'15 分钟：给匿名活动明细标出满足状态与金额条件的行，再用 FILTER 表达同样规则；加入空金额和无效状态。',
  acceptance:'返回行与人工标签一致，条件及未知值处理可读；区分表表达式与最终汇总指标，不猜测大规模性能。'
 },
 'ops-dax-divide': {
  summary:'DIVIDE 用于计算比率，并允许给除数为零时提供替代结果。运营比率必须先定义分母对象与范围；零分母通常意味着没有可计算样本，与实际比率为零不同。报表应保留空白或清晰提示，而不是强行制造百分比。',
  pitfall:'把所有缺失和零分母替换为零，会让数据质量问题变成差表现；分子分母筛选不一致也会造成失真。',
  exercise:'10 分钟：用三组演示数据计算完成比例，包含正常、零分母和缺失；比较空白与零替代的呈现效果。',
  acceptance:'每组显示状态符合定义，零分母标记不可计算；写清分子分母与替代值用途，汇总不会混入伪造零值。'
 },
 'ops-data-quality': {
  summary:'Power Query 数据剖析展示列质量、分布和概要，帮助识别有效值、空值、错误与重复模式。分析口径前先检查数据形态，能避免把导入错误解释为业务趋势。剖析范围可能只覆盖部分行，应确认当前设置再下结论。',
  pitfall:'只查看样本就声称全量数据无错误不可靠；把空字符串、空值和异常编码当同一种问题，也可能误删可用信息。',
  exercise:'15 分钟：导入包含空值、重复编号、文本数字和错误日期的 CSV；查看质量与分布，建立待核对问题清单。',
  acceptance:'每类异常能定位到行，注明剖析范围；修复规则有理由和前后数量，不通过静默删行美化数据。'
 },
 'ops-powerbi-drill': {
  summary:'钻取页面把汇总图中的指定对象连接到细节页面，帮助从异常指标查看对应明细。目标页需要匹配筛选字段，并提供返回入口。它支持分析路径，但仍要确认明细与汇总使用同一口径和数据范围。',
  pitfall:'目标页保留额外筛选会让明细与原图不一致；缺少返回按钮或不清楚当前对象会让使用者迷失。',
  exercise:'15 分钟：建立渠道总览与单渠道明细页，从图表钻取后核对对象、日期及合计；测试返回与不存在对象。',
  acceptance:'目标页标题显示当前对象，明细合计可核对，返回可用；说明传递了哪些筛选和保留哪些上下文。'
 },
 'ops-histogram': {
  summary:'直方图按区间统计数值出现频数，可观察中心、分散、偏态或多峰等形态。运营分析响应时间、客单价或参与时长时，分布常比单一平均数更有帮助。分箱宽度会改变图形观感，因此需要同时记录样本和分箱规则。',
  pitfall:'图形不规则不必然意味着数据有错；小样本或不同分箱可能造成不同印象。直方图也不直接给出异常的业务原因。',
  exercise:'15 分钟：用二十个标明为演示的时长值绘制两种分箱直方图，比较集中区间和长尾；保留原始数值。',
  acceptance:'频数合计与样本量一致，轴与单位清楚；描述可见分布而不编造因果，说明分箱对解读的影响。'
 },
 'ops-boxplot': {
  summary:'箱线图用中位数与分位数概括数据中心和离散程度，并标记按规则识别的潜在异常点。对比多组运营时长时，它能减少单个极端值对直观判断的影响。异常标记只提示核对，不能据此自动删除业务记录。',
  pitfall:'不同工具的异常点规则可能不同；相同中位数不等于整个分布相同。组间样本量很小时，结论仍需保留不确定性。',
  exercise:'15 分钟：为两组匿名演示时长绘制箱线图，手算中位数并核对四分位；检查被标记点的原始记录。',
  acceptance:'注明工具规则、单位和样本量，标记点没有被静默删除；区分图上事实与需要进一步解释的原因。'
 },
 'ops-paired-test': {
  summary:'比较均值前应判断数据是否来自同一对象的配对观察，还是两个独立样本。配对分析关注同一对象前后差值，独立分析有不同假设。统计检验还需要考虑样本来源、变异和适用条件，不能把一个概率值当作业务收益本身。',
  pitfall:'把重复观察当成独立样本可能低估不确定性；事后挑选样本或只看显著性，会忽略效应大小和实际意义。',
  exercise:'15 分钟：列出同一用户前后耗时与两组独立用户耗时两种匿名样本结构，计算配对差值并写出各自分析前提。',
  acceptance:'标明数据为演示，配对关系可追溯，报告同时写样本数与变化量；未满足条件时不强行给出检验结论。'
 },
 'ops-journey-ab': {
  summary:'A/B 测试在旅程中比较变体，需先设定变体、参与范围、测试时长和胜出依据。它适合检验具体改动，例如不同主题或消息内容；比较应围绕事前目标，不应在结果出现后随意换指标来宣布胜出。',
  pitfall:'多个变化同时发生会降低解释性；样本少或测试提前停止都需要谨慎。产品选出的赢家也不等于对所有未来人群都有效。',
  exercise:'15 分钟：为一封匿名活动通知写两种主题，保持其他条件一致；制定目标指标、观察窗口和未能判断时的处理。',
  acceptance:'假设与主要指标事前写明，变体只有可解释差异；流程草案不伪造统计结果，列出复盘时必须核对的限制。'
 },
 'ops-event-setup': {
  summary:'活动设置把名称、日期、场地、议程和报名安排组织成统一信息。运营计划应让参与者知道何时、在哪里、如何报名，也让执行者知道容量与负责事项。工具字段不能替代线下条件确认，公开前仍需逐项核对。',
  pitfall:'时间范围、时区或线上线下地点不清会导致误报名；议程变更却未同步通知，会使活动资料版本不一致。',
  exercise:'15 分钟：为演示校园分享会建立活动信息表，列出两段议程、报名规则和发布检查项，模拟一次时间变更。',
  acceptance:'日期地点一致，变更有版本与通知动作；容量及报名入口明确，未确定事项标待确认，不编造实际参与人数。'
 },
 'ops-event-waitlist': {
  summary:'候补名单用于活动容量已满时记录后续报名对象，并在名额释放时按规则处理。需要明确候补排序、转正条件、通知和确认期限。它帮助管理容量，不表示候补者一定能参加，也不能让同一名额被多人同时确认。',
  pitfall:'取消后未同步容量或多次通知同一人可能造成超额；规则只存在执行人员口头记忆中，会让排序难核对。',
  exercise:'10 分钟：用五个匿名演示编号模拟两个名额和三个候补，执行报名、取消、转正及未回应四种事件。',
  acceptance:'每一步占用数不超容量，候补顺序和通知可追溯；说明何时释放名额，演示记录不冒充真实名单。'
 },
 'ops-event-followup': {
  summary:'活动结束后的跟进应区分注册、实际参加和后续反馈等状态，再安排适当沟通。复盘要保存议程、互动与可观测结果，下一步联系应符合既有同意与用途。没有反馈不能被自动解释成满意或不满意。',
  pitfall:'把报名人数当到场人数，会高估执行效果；统一发送不考虑参与状态，也可能让内容不相关。结果数据需要保留来源。',
  exercise:'15 分钟：为匿名演示名单列出报名、到场和未到场状态，设计两种跟进内容及反馈问题，建立一页复盘口径表。',
  acceptance:'每类触达有理由，指标分母清楚；没有实际数据时只保留计划和待采集项，不填入虚构满意度。'
 },
 'ops-email-personalize': {
  summary:'内容个性化用资料字段和条件替换部分文案，使消息更符合受众情境。应先确认字段可用、默认值与预览对象，再发布。对姓名或活动信息的替换只代表展示数据，不能据此推断未提供的兴趣、职责或真实经历。',
  pitfall:'缺字段导致空白称呼或错误信息；预览只看一个样本容易遗漏其他受众。个性化不应暴露不必要敏感资料。',
  exercise:'10 分钟：准备有昵称、无昵称和含特殊字符三种匿名样本，设计默认称呼与活动字段；逐个预览并核对链接。',
  acceptance:'每种输入都有可读结果，默认值不虚构事实；字段来源、用途和测试样本被记录，错误资料不会直接发布。'
 },
 'ops-email-preview': {
  summary:'预览与测试发送帮助在正式触达前检查内容、布局、链接和个性化呈现。测试对象与正式受众应分开，检查还需覆盖缺省数据和不同显示环境。一次预览正常不意味着所有客户端都已验证，报告应注明实际检查范围。',
  pitfall:'只检查文字不检查链接和默认字段，会留下关键错误；把测试发送算入活动正式指标会污染口径。',
  exercise:'15 分钟：制作发布前检查表，包含主题、日期、称呼、按钮、备用文本和退订信息；用两种匿名字段样本执行预览。',
  acceptance:'问题有修复与复查记录，正式发布需重新确认；标出未验证客户端，不伪造真实送达和打开数据。'
 },
};

Object.assign(details, require('./data/knowledge-expansion-java-content.cjs'));
for(let i=sources.length-1;i>=0;i--) if(['java-regex','java-reflection','java-annotations'].includes(sources[i].id)) sources.splice(i,1);
sources.find(x=>x.id==='java-spring-test').url='https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html';
const redis='Redis 官方文档';
sources.find(x=>x.id==='java-custom-exception').keywords='exception、Throwable、class';
sources.find(x=>x.id==='front-web-performance').keywords='性能、加载、Web';
sources.find(x=>x.id==='ops-paired-test').url='https://www.itl.nist.gov/div898/handbook/prc/section3/prc311.htm';
sources.find(x=>x.id==='ops-paired-test').keywords='paired、sample、difference';
add('Java','java-redis-expiry','Redis 过期策略：区分到期和主动清理','https://redis.io/docs/latest/commands/expire/',redis,'EXPIRE、TTL、timeout','超时设置与键更新行为需要逐项核对');
add('Java','java-redis-pipeline','Redis 流水线：减少往返但不等于事务','https://redis.io/docs/latest/develop/using-commands/pipelining/',redis,'pipeline、round trip、reply','批量发送命令减少网络往返，但要控制批量大小');
add('Java','java-redis-tx','Redis 事务：命令排队与执行边界','https://redis.io/docs/latest/develop/using-commands/transactions/',redis,'MULTI、EXEC、WATCH','事务排队和条件执行不同于自动回滚的关系数据库事务');
Object.assign(details,{
 'java-redis-expiry':{
  summary:'EXPIRE 为键设置超时时间，TTL 用于观察剩余生命周期。键是否保留过期信息还取决于具体更新命令，不能只在写入时设置一次就假设永远有效。缓存场景要同时考虑数据源更新与键失效，超时只是其中一种机制。',
  pitfall:'过期时间为非正值与正常倒计时不同；替换值、重命名和条件选项各有语义。不要把键消失误当数据源也被删除。',
  exercise:'15 分钟：在隔离测试库创建三个演示键，设置超时并查看 TTL；分别执行覆盖值、修改字段和再次设超时，记录返回值及键是否存在。',
  acceptance:'每一步能回到命令文档解释，正常与无过期情况分开；测试不操作生产键，明确缓存与持久数据的职责，提出一条可验证失效策略。'
 },
 'java-redis-pipeline':{
  summary:'流水线在不等待每条回复的情况下发送多条命令，减少网络往返等待。它适合独立或可按顺序处理的批量操作；客户端仍需读取所有回复并识别失败。批量大小应受资源与实际请求规模约束。',
  pitfall:'流水线本身不提供事务原子性，也不能消除所有服务端计算成本；一次发送过多命令会积累回复，顺序依赖与错误处理仍要设计。',
  exercise:'15 分钟：在隔离环境对十个演示键分别执行单次请求和有限批流水线，记录命令数、总耗时和逐条回复；加入一个会失败的命令。',
  acceptance:'结果逐项核对且没有丢回复，错误可定位；说明本次环境和规模，不把单次耗时宣称为固定提升，明确为何需要或不需要事务。'
 },
 'java-redis-tx':{
  summary:'MULTI 开始命令排队，EXEC 执行队列；WATCH 为条件执行提供乐观检查。需要区分排队时错误与执行时错误，Redis 事务不是把所有运行时失败都自动回滚的关系数据库事务。',
  pitfall:'把失败回复忽略会留下部分命令已成功的误解；客户端断开、受监视键变化和命令错误都应有明确处理。事务不意味着业务与另一数据源同步一致。',
  exercise:'15 分钟：在隔离测试库执行一组正常事务，再加入类型不符命令；用两个客户端修改被 WATCH 的键，检查 EXEC 的返回。',
  acceptance:'记录全部回复和最终键值，指出各失败阶段；解释何时需要重试与补偿，不编造自动回滚保证，并注明测试仅涉及演示键。'
 }});
const rejectedDiscoverySources=[
 ['xlookup','https://support.microsoft.com/en-us/office/xlookup-function-b7fd680e-6d10-43e6-9f9e-0a4d5c8c1f4c'],
 ['filter','https://support.microsoft.com/en-us/office/filter-function-f4f7cb66-82eb-4767-8f7f-3f6f04d8f0c8'],
 ['unique','https://support.microsoft.com/en-us/office/unique-function-c5ab87fd-30a3-4ce9-9b0f-7f1c35f3d4b4'],
 ['textsplit','https://support.microsoft.com/en-us/office/textsplit-function-b1ca414e-4c21-4ca0-b1f7-6c2f4a4b5c63'],
 ['custom-tags','https://learn.microsoft.com/en-us/clarity/setup-and-installation/custom-tags'],
 ['validation','https://support.microsoft.com/en-us/office/apply-data-validation-to-cells-29fccbcc-d7a2-4a0d-9c8f-7a3f7e2be0b5'],
 ['chart','https://support.microsoft.com/en-us/office/create-a-chart-from-start-to-finish-0baf399e-dd61-4b2f-8e4e-0f6e0ceeb1b8'],
].map(([id,url])=>({id:'rejected-discovery-'+id,title:'来源发现阶段失效地址：'+id,category:'运营',url,sourceName:'Microsoft',keywords:'discovery verification'}));
function stripHtml(html) {
 return html.replace(/<script[\s\S]*?<\/script>/gi,' ').replace(/<style[\s\S]*?<\/style>/gi,' ')
  .replace(/<!--[\s\S]*?-->/g,' ').replace(/<[^>]+>/g,' ')
  .replace(/&#x([0-9a-f]+);/gi,(_,v)=>String.fromCodePoint(parseInt(v,16)))
  .replace(/&#(\d+);/g,(_,v)=>String.fromCodePoint(Number(v)))
  .replace(/&nbsp;/g,' ').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>')
  .replace(/&apos;|&#39;/g,"'").replace(/&quot;/g,'"').replace(/\s+/g,' ').trim();
}
function extractArticle(html, url) {
 if(url.includes('vuejs.org')) {
  const vp=html.indexOf('<main');
  if(vp>=0) {const end=html.indexOf('</main>',vp);return html.slice(vp,end>vp?end:html.length);}
 }
 if (url.includes('learn.microsoft.com')) {
  const start = html.indexOf('<div class="content"><h1');
  const finish = html.indexOf('<div id="ms--inline-notifications"',start);
  if(start>=0) return html.slice(start, finish>start?finish:html.length);
 }
 if(url.includes('support.microsoft.com')) {
  const m=html.match(/<main[^>]*id="supMainContent"[^>]*>([\s\S]*?)<\/main>/i);
  if(m) return m[1];
 }
 const article=html.match(/<article(?:\s[^>]*)?>([\s\S]*?)<\/article>/i);
 if(article) return article[1];
 const main=html.match(/<main(?:\s[^>]*)?>([\s\S]*?)<\/main>/i);
 if(main) return main[1];
 const chapter=html.search(/<div[^>]+class="chapter"/i);
 if(chapter>=0) return html.slice(chapter);
 const content=html.indexOf('<div class="contentContainer"');
 if(content>=0) return html.slice(content);
 const body=html.match(/<body[^>]*>([\s\S]*?)<\/body>/i);
 return body?body[1]:html;
}
function makeContent(s) {
 const d=details[s.id];
 if(!d) throw Error('Missing independent topic content: '+s.id);
 const minutes=s.category==='Java'?'建议用 15—20 分钟完成。':'';
 return [s.title, d.summary, '易错点：'+d.pitfall, '小练习：'+minutes+d.exercise, '验收标准：'+d.acceptance].join('\n\n');
}
function isoShanghai() { return new Date(Date.now()+8*3600000).toISOString().replace('Z','+08:00'); }
function evidenceFrom(article,terms) {
 const text=stripHtml(article);
 const paragraphs=[...article.matchAll(/<(p|li|td)[^>]*>([\s\S]*?)<\/\1>/gi)]
  .map(m=>stripHtml(m[2])).filter(p=>p.length>=55&&p.length<=3500);
 const selected=[];
 for(const term of terms) {
  const contains=t=> {
   if(/^[A-Za-z0-9_ /-]+$/.test(term))return new RegExp('(^|[^A-Za-z0-9_])'+term.replace(/[.*+?^()|[\]\\]/g,'\\$&')+'(?=$|[^A-Za-z0-9_])','i').test(t);
   return t.toLowerCase().includes(term.toLowerCase());
  };
  const paragraph=paragraphs.find(p=>contains(p)&&text.includes(p)&&!/^(Java SE|Skip to|Applies to|In this article|Table of Contents)/i.test(p));
  if(!paragraph) continue;
  const sentences=paragraph.split(/(?<=[。！？.?!])\s+/);
  const chosen=sentences.find(p=>p.length>=35&&contains(p))||paragraph;
  const occurrence=chosen.toLowerCase().indexOf(term.toLowerCase());
  const cutStart=chosen.length<=320?0:Math.max(0,occurrence-90);
  const quote=chosen.slice(cutStart,cutStart+320).trim();
  const offset=text.indexOf(quote);
  if(offset>=0&&!selected.some(x=>x.quote===quote)) selected.push({term,quote,startOffset:offset,endOffset:offset+quote.length});
 }
 return {text,selected};
}
async function fetchOne(s) {
 const started = isoShanghai(); let status = 0, finalUrl = s.url, body='', raw=Buffer.alloc(0); let error = null; const attempts=[];
 for(let attempt=1;attempt<=3;attempt++){
  try {
   const r = await fetch(s.url, { redirect:'follow', signal: AbortSignal.timeout(30000), headers:{'user-agent':'ai-campus-knowledge-verifier/1.0'} });
   status=r.status; finalUrl=r.url; raw=Buffer.from(await r.arrayBuffer()); body=raw.toString('utf8'); error=null;
   attempts.push({attempt,httpStatus:status,fetchedAt:isoShanghai(),bytes:raw.length});
   if(status===200) break;
  } catch (e) { error=String(e.message||e); attempts.push({attempt,httpStatus:status,error,fetchedAt:isoShanghai()}); }
 }
 const article=extractArticle(body,finalUrl);
 const termList=s.keywords.split('、').filter(Boolean);
 if(s.id==='front-semantic-html')termList.unshift('语义','HTML','h1');
 const {text,selected}=evidenceFrom(article,termList);
 const bytes=raw.length; const sha=crypto.createHash('sha256').update(raw).digest('hex');
 const artifactPrefix='knowledge-source-'+s.id;
 if(body){fs.writeFileSync(path.join(logDir,artifactPrefix+'.html'),raw);fs.writeFileSync(path.join(logDir,artifactPrefix+'.txt'),text,'utf8');}
 const matched=termList.filter(t=>text.toLowerCase().includes(t.toLowerCase()));
 const titleMatch=body.match(/<title[^>]*>([\s\S]*?)<\/title>/i); const pageTitle=titleMatch?stripHtml(titleMatch[1]):s.title;
 const verification={ finalUrl, method:'GET', httpStatus:status, readToEnd:!!body && !error, contentBytes:bytes, bodyTextCharacters:text.length, pageTitle, expectedTerms:termList, matchedTerms:matched, bodyEvidence:selected.map(x=>x.quote), evidenceLocations:selected, locationBasis:'normalized extracted article text; UTF-16 character offsets', rawFile:'logs/public-expansion/'+artifactPrefix+'.html', textFile:'logs/public-expansion/'+artifactPrefix+'.txt', sha256:sha, fetchedAt:started, attempts, error:error||undefined };
 const ok=status===200&&body.length>1000&&selected.length>0&&!error;
 const version=applicableVersion(s.url,body);
 return { spec:s, ok, item: ok ? { id:s.id,title:s.title,category:s.category,skills:termList,content:makeContent(s),sourceName:s.sourceName,sourceUrl:s.url,applicableVersion:version,fetchedAt:started,verification,status:'VERIFIED',reviewStatus:'AGENT_SOURCE_CHECKED' } : null, check:{id:s.id,category:s.category,title:s.title,url:s.url,ok,verification} };
}
function applicableVersion(url,body) {
 if(url.includes('/se21/'))return 'Java SE 21 语言规范';
 if(url.includes('/javase/8/')||url.includes('/javase/tutorial/'))return 'Java SE 8 基础文档；较新运行环境需核对差异';
 if(url.includes('spring.io')) {const v=body.match(/<meta name="version" content="([^"]+)"/);return v?'Spring Boot '+v[1]+'（官方页面版本）':'Spring Boot 核对日在线文档；需按项目版本复核';}
 if(url.includes('cn.vuejs.org'))return 'Vue 3 官方文档';
 if(url.includes('router.vuejs.org'))return 'Vue Router 核对日在线指南；需按已安装版本复核';
 if(url.includes('react.dev'))return 'React 官方在线文档（核对日版本，旧项目需核对差异）';
 if(url.includes('typescriptlang.org'))return 'TypeScript 在线 Handbook/配置参考；具体编译器版本另行核对';
 if(url.includes('redis.io'))return 'Redis latest 在线命令与开发指南；命令选项按服务端版本复核';
 if(url.includes('nist.gov'))return 'NIST/SEMATECH e-Handbook 工程统计手册';
 if(url.includes('support.microsoft.com'))return 'Microsoft Excel；文档适用产品范围见官方页面';
 if(url.includes('/clarity/'))return 'Microsoft Clarity 在线产品文档';
 if(url.includes('/customer-insights/'))return 'Dynamics 365 Customer Insights - Journeys 在线产品文档';
 if(url.includes('/power-query/'))return 'Microsoft Power Query 在线产品文档';
 if(url.includes('/dax/')||url.includes('/power-bi/'))return 'Microsoft Power BI/DAX 在线产品文档';
 return 'MDN Web Docs；具体 Web API 支持情况按目标浏览器核对';
}
function saveSnapshot(results,reviewStatus,discoveryChecks=[]) {
 const items=results.filter(x=>x.ok).map(x=>x.item),checks=results.map(x=>x.check);
 const envelope={schemaVersion:1,reviewStatus,reviewNote:'READY 表示代理逐篇读取官方正文并核对定位；不等于人工全面事实审阅或真实学生结果。练习均为教学建议。',description:'2026-10-11 官方公开文档中文自编知识扩展；失败来源不进入学习资料。',generatedAt:isoShanghai(),knowledgeMaterials:items,sourceChecks:checks.concat(discoveryChecks)};
 fs.writeFileSync(outFile,JSON.stringify(envelope,null,2)+'\n');
 return envelope;
}
async function main(){
 const discoveryChecks=(await Promise.all(rejectedDiscoverySources.map(s=>fetchOne(s)))).map(r=>r.check);
 const results=[];
 for(let i=0;i<sources.length;i+=6){
  const batch=await Promise.all(sources.slice(i,i+6).map(s=>fetchOne(s)));
  results.push(...batch); console.error('verified '+Math.min(i+6,sources.length)+'/'+sources.length);
  saveSnapshot(results,'COLLECTING',discoveryChecks);
 }
 const items=results.filter(x=>x.ok).map(x=>x.item);
 const complete=items.length===102&&['Java','前端','运营'].every(c=>items.filter(x=>x.category===c).length===34);
 const envelope=saveSnapshot(results,complete?'READY':'INCOMPLETE',discoveryChecks);
 console.log(JSON.stringify({total:sources.length,verified:items.length,failed:envelope.sourceChecks.filter(x=>!x.ok).length,byCategory:Object.fromEntries(['Java','前端','运营'].map(c=>[c,items.filter(x=>x.category===c).length])),out:outFile},null,2));
}
if(require.main===module) main().catch(e=>{console.error(e);process.exit(1)});
module.exports={sources,details,makeContent,stripHtml,extractArticle,evidenceFrom,isoShanghai,fetchOne,applicableVersion,rejectedDiscoverySources};
