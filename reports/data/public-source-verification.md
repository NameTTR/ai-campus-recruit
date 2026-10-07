# 公开资料与真实招聘核对记录

核对时间：2026-10-07T21:18:11+08:00（中国标准时间，UTC+08:00）。

目录文件为 `scripts/data/verified-public-sources.json`，本报告记录实际网络读取与来源核对。导入和系统验证结果另见 [本批验收报告](seed-data-acceptance.md)。

## 收录结果

| 内容 | Java | 前端 | 运营 | 合计 |
| --- | --- | --- | --- | --- |
| 知识资料 | 12 | 12 | 10 | 34 |
| 真实公开岗位 | 14 | 14 | 14 | 42 |

招聘分类：社会招聘 24 条，应届校招 7 条，实习 11 条。

知识正文全部是为校园招聘平台编写的短篇中文学习摘要，参考对应主题的官方文档组织内容，没有复制官方长篇原文。摘要中的练习场景是教学建议，不是实际业务结果。招聘标题、地点、职责和任职要求来自官方公开招聘接口；职位要求原文用于核对，薪资均未公开。目录没有把社会招聘改写成校招或实习。

## 核对方式与边界

- 所有知识来源执行完整 HTTP GET，并读取到响应结束；记录响应状态、最终地址、页面标题、正文长度、主题词、原文短证据和响应 SHA-256。未使用 HEAD 成功作为正文核对依据。
- 知识核对同时检查正文中与主题对应的关键词，避免把 HTTP 200 的首页、导航页或错误页面当作资料。例如旧 Grid 路径返回 404，最终收录的是能读取中文网格正文的新路径。
- 腾讯社会招聘使用官方 `Query` 读取列表，再对每个候选职位 GET `ByPostId` 的完整 JSON，核对 `PostId`、`RecruitPostName`、`LocationName`、`Requirement` 和官方工作年限；目录只选择其中 24 条。`ComName` 未单列时公司记为腾讯招聘站名称，不推断具体签约法人。
- 腾讯校园招聘使用官方公开脚本确认的 `searchPosition` **POST 查询**（只读取列表，不提交申请），再 GET `getJobDetailsByPostId` 的完整 JSON，核对 `postId`、`title`、`request`、`workCityList` 和招聘项目属性。
- 42 个职位页面链接均另行 GET。页面 HTML 为客户端渲染壳，状态记为 `CLIENT_RENDERED_SHELL`；招聘事实根据上述官方详情 JSON 核对，不把壳页面的 HTTP 200 当作招聘正文核对。
- 校招毕业范围来自 `getProjectMapping` 对应 `projectId` 的当前 **子项目** `recruitRangDesc`。该响应的父级提示含有较旧日期，目录保留完整映射证据，并使用具体子项目范围：2027 校招为“毕业时间：2026年1月1日-2027年12月31日”，应届实习为“毕业时间：2026年9月1日-2027年12月31日”，日常实习为“面向全体在校生，提供实习机会”。
- 校园岗位 `originalRequirements` 保留官方 `request`，`requirements` 追加注明来源的官方招聘项目范围及对应加分项；没有公开每周出勤天数或实习月数时，不补造条件。
- `skills` 只从官方任职要求明确出现的词中提取，并保存 `skillsEvidence`。保留 JS、TS、k8s 等原文缩写，不擅自把相关技能替换为其他技能。技能词用于检索，不表示每个词都是全部必须掌握：原文“其中一门”等限定仍保留在要求正文中。
- Java 分类包含明确提到 Java 的后台、测试开发、数据工程等岗位；前端分类也包含明确要求前端能力的全栈与运营开发岗位。分类是学习方向标签，不改动官方职位名称。
- 访问与岗位状态只代表此次读取时的公开内容；官方未来撤下岗位或调整条件后，应重新抓取核对。

## 知识资料逐项记录

| 分类 | 标题 | 官方来源 | 读取状态 | 响应字节 | 正文核对词 | 读取时间 |
| --- | --- | --- | --- | --- | --- | --- |
| Java | Redis 字符串：缓存对象与轻量计数 | [Redis 官方文档](https://redis.io/docs/latest/develop/data-types/strings/) | 200 / GET全文 | 1549342 | SET、GET、INCR | 2026-10-07T21:04:19+08:00 |
| Java | Redis 哈希：按字段管理对象数据 | [Redis 官方文档](https://redis.io/docs/latest/develop/data-types/hashes/) | 200 / GET全文 | 3805026 | HSET、HGET | 2026-10-07T21:04:19+08:00 |
| Java | Redis 列表：理解队列方向与消费方式 | [Redis 官方文档](https://redis.io/docs/latest/develop/data-types/lists/) | 200 / GET全文 | 7259876 | LPUSH、RPOP、BLPOP | 2026-10-07T21:04:19+08:00 |
| Java | Redis 集合：去重与集合关系计算 | [Redis 官方文档](https://redis.io/docs/latest/develop/data-types/sets/) | 200 / GET全文 | 3719019 | SADD、SINTER、SMEMBERS | 2026-10-07T21:04:19+08:00 |
| Java | Redis 有序集合：分数排序与排行榜 | [Redis 官方文档](https://redis.io/docs/latest/develop/data-types/sorted-sets/) | 200 / GET全文 | 3502540 | ZADD、ZRANGE、score | 2026-10-07T21:04:19+08:00 |
| Java | MySQL 索引：让筛选与关联减少扫描 | [Oracle 官方托管 MySQL 8.0 参考手册](https://docs.oracle.com/cd/E17952_01/mysql-8.0-en/mysql-indexes.html) | 200 / GET全文 | 12483 | WHERE、leftmost、indexes | 2026-10-07T21:04:19+08:00 |
| Java | 用 EXPLAIN 检查 MySQL 查询路径 | [Oracle 官方托管 MySQL 8.0 参考手册](https://docs.oracle.com/cd/E17952_01/mysql-8.0-en/using-explain.html) | 200 / GET全文 | 11750 | EXPLAIN、optimizer、ANALYZE TABLE | 2026-10-07T21:04:19+08:00 |
| Java | MySQL 事务：提交、回滚与自动提交 | [Oracle 官方托管 MySQL 8.0 参考手册](https://docs.oracle.com/cd/E17952_01/mysql-8.0-en/commit.html) | 200 / GET全文 | 23894 | START TRANSACTION、COMMIT、ROLLBACK、autocommit | 2026-10-07T21:04:19+08:00 |
| Java | Spring IoC：由容器连接服务依赖 | [Spring 官方文档](https://docs.spring.io/spring-framework/reference/core/beans/introduction.html) | 200 / GET全文 | 112534 | BeanFactory、ApplicationContext、dependencies | 2026-10-07T21:04:19+08:00 |
| Java | Spring 声明式事务：定义业务原子边界 | [Spring 官方文档](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html) | 200 / GET全文 | 113987 | AOP、transaction、rollback | 2026-10-07T21:04:19+08:00 |
| Java | Spring MVC：理解请求如何到达控制器 | [Spring 官方文档](https://docs.spring.io/spring-framework/reference/web/webmvc.html) | 200 / GET全文 | 110411 | Servlet、Spring Web MVC、WebFlux | 2026-10-07T21:04:19+08:00 |
| Java | Spring Boot Actuator：健康检查与端点暴露 | [Spring Boot 官方文档](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) | 200 / GET全文 | 236017 | health、metrics、exposure | 2026-10-07T21:04:20+08:00 |
| 前端 | Promise：组织异步结果与错误处理 | [MDN 中文 Web Docs](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Promise) | 200 / GET全文 | 216443 | then、catch、finally | 2026-10-07T21:04:20+08:00 |
| 前端 | Fetch：区分网络失败与 HTTP 错误 | [MDN 中文 Web Docs](https://developer.mozilla.org/zh-CN/docs/Web/API/Fetch_API/Using_Fetch) | 200 / GET全文 | 192640 | fetch、404、json | 2026-10-07T21:04:20+08:00 |
| 前端 | CORS：跨域访问由浏览器与服务端协作 | [MDN 中文 Web Docs](https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Guides/CORS) | 200 / GET全文 | 279435 | OPTIONS、Access-Control-Allow-Origin、credentials | 2026-10-07T21:04:21+08:00 |
| 前端 | Flex 布局：主轴、交叉轴与空间分配 | [MDN 中文 Web Docs](https://developer.mozilla.org/zh-CN/docs/Web/CSS/Guides/Flexible_box_layout/Basic_concepts) | 200 / GET全文 | 380572 | flex-grow、flex-shrink、flex-basis | 2026-10-07T21:04:21+08:00 |
| 前端 | Grid 布局：用行列轨道组织页面区域 | [MDN 中文 Web Docs](https://developer.mozilla.org/zh-CN/docs/Web/CSS/Guides/Grid_layout/Basic_concepts) | 200 / GET全文 | 393229 | grid-template-columns、fr、网格 | 2026-10-07T21:04:23+08:00 |
| 前端 | Vue 响应式：让状态更新驱动视图 | [Vue 官方中文指南](https://cn.vuejs.org/guide/essentials/reactivity-fundamentals.html) | 200 / GET全文 | 155237 | ref、reactive、nextTick | 2026-10-07T21:04:23+08:00 |
| 前端 | Vue 计算属性：集中表达派生数据 | [Vue 官方中文指南](https://cn.vuejs.org/guide/essentials/computed.html) | 200 / GET全文 | 129342 | computed、缓存、副作用 | 2026-10-07T21:04:23+08:00 |
| 前端 | Vue 表单绑定：状态、输入与修饰符 | [Vue 官方中文指南](https://cn.vuejs.org/guide/essentials/forms.html) | 200 / GET全文 | 142116 | v-model、.trim、.number、.lazy | 2026-10-07T21:04:24+08:00 |
| 前端 | Vue 组件：用属性与事件表达协作关系 | [Vue 官方中文指南](https://cn.vuejs.org/guide/essentials/component-basics.html) | 200 / GET全文 | 159805 | props、$emit、插槽 | 2026-10-07T21:04:25+08:00 |
| 前端 | React 状态提升：多个组件共享同一事实 | [React 官方文档](https://react.dev/learn/sharing-state-between-components) | 200 / GET全文 | 239448 | lifting state、controlled、props | 2026-10-07T21:04:26+08:00 |
| 前端 | React 列表：过滤、映射与稳定 key | [React 官方文档](https://react.dev/learn/rendering-lists) | 200 / GET全文 | 272255 | filter、map、key | 2026-10-07T21:04:26+08:00 |
| 前端 | TypeScript 类型收窄：在分支中安全处理联合类型 | [TypeScript 官方手册](https://www.typescriptlang.org/docs/handbook/2/narrowing.html) | 200 / GET全文 | 332677 | typeof、type guard、narrowing | 2026-10-07T21:04:26+08:00 |
| 运营 | Excel 数据透视表：按维度汇总运营明细 | [Microsoft Excel 官方支持](https://support.microsoft.com/en-us/office/create-a-pivottable-to-analyze-worksheet-data-a9a84538-bfe9-40a9-a8e9-f99134456576) | 200 / GET全文 | 149187 | PivotTable、Refresh、Sum | 2026-10-07T21:04:27+08:00 |
| 运营 | Excel 条件格式：让异常指标更易发现 | [Microsoft Excel 官方支持](https://support.microsoft.com/en-us/office/use-conditional-formatting-to-highlight-information-fed60dfa-1d3f-4e13-9ecb-f1951ff89d7f) | 200 / GET全文 | 202406 | conditional formatting、rules、data bars | 2026-10-07T21:04:27+08:00 |
| 运营 | Excel SUMIFS：按多个条件汇总数据 | [Microsoft Excel 官方支持](https://support.microsoft.com/en-us/office/sumifs-function-c9e748f5-7ea7-455d-9406-611cebce642b) | 200 / GET全文 | 222471 | SUMIFS、sum_range、criteria_range | 2026-10-07T21:04:27+08:00 |
| 运营 | Excel COUNTIF：按条件计数并核对名单 | [Microsoft Excel 官方支持](https://support.microsoft.com/en-us/office/countif-function-e0de10c6-f885-4e71-abb4-1f464816df34) | 200 / GET全文 | 111243 | COUNTIF、criteria、COUNTIFS | 2026-10-07T21:04:27+08:00 |
| 运营 | Excel XLOOKUP：用编号连接业务信息 | [Microsoft Excel 官方支持](https://support.microsoft.com/en-us/office/xlookup-function-b7fd680e-6d10-43e6-84f9-88eae8bf5929) | 200 / GET全文 | 230136 | XLOOKUP、lookup_array、return_array | 2026-10-07T21:04:28+08:00 |
| 运营 | Clarity 热力图：观察点击与滚动覆盖 | [Microsoft Clarity 官方文档](https://learn.microsoft.com/en-us/clarity/heatmaps/heatmaps-overview) | 200 / GET全文 | 46146 | Click maps、Scroll maps、Conversion maps | 2026-10-07T21:04:29+08:00 |
| 运营 | Clarity 会话回放：追踪用户操作中的阻塞 | [Microsoft Clarity 官方文档](https://learn.microsoft.com/en-us/clarity/session-recordings/recordings-overview) | 200 / GET全文 | 41869 | reconstructions、clicks、session | 2026-10-07T21:04:29+08:00 |
| 运营 | Power BI 星型模型：统一运营报表口径 | [Microsoft Power BI 官方文档](https://learn.microsoft.com/en-us/power-bi/guidance/star-schema) | 200 / GET全文 | 80634 | fact、dimension、granularity | 2026-10-07T21:04:29+08:00 |
| 运营 | Power BI 定时刷新：让报表按计划更新 | [Microsoft Power BI 官方文档](https://learn.microsoft.com/en-us/power-bi/connect-data/refresh-scheduled-refresh) | 200 / GET全文 | 60300 | scheduled refresh、credentials、gateway | 2026-10-07T21:04:29+08:00 |
| 运营 | A/B 实验分流：用功能变体定义用户体验 | [Microsoft Azure 官方文档](https://learn.microsoft.com/en-us/azure/azure-app-configuration/howto-variant-feature-flags) | 200 / GET全文 | 53454 | A/B testing、Allocation、DefaultWhenDisabled | 2026-10-07T21:04:29+08:00 |

## 真实公开招聘逐项记录

| 分类 | 官方职位链接 | 公司 | 官方地点 | 招聘类型 | 年限或项目范围 | 薪资 | 详情核对 | 查询时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Java | [云mall-java后端开发工程师-电商](https://careers.tencent.com/jobdesc.html?postId=2098594909744443392) | 腾讯云智研发子公司 | 重庆 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2098594909744443392&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| Java | [营销与交易产品部-云mall高级Java后端开发工程师](https://careers.tencent.com/jobdesc.html?postId=2089960263330742272) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2089960263330742272&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| Java | [元宝数据平台-数据工程](https://careers.tencent.com/jobdesc.html?postId=2041847335474065408) | 腾讯云智研发子公司 | 西安 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2041847335474065408&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| Java | [元宝数据平台-后台开发工程师](https://careers.tencent.com/jobdesc.html?postId=2041847334320631808) | 腾讯云智研发子公司 | 西安 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2041847334320631808&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| Java | [魔方技术中心-游戏测试开发工程师-客户端性能测试方向](https://careers.tencent.com/jobdesc.html?postId=2087806723221012480) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 一年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2087806723221012480&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| Java | [存储业务后台研发工程师](https://careers.tencent.com/jobdesc.html?postId=1811226147589988352) | 腾讯 | 成都 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=1811226147589988352&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| Java | [AI infra规划架构师](https://careers.tencent.com/jobdesc.html?postId=2092893584964628480) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 五年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2092893584964628480&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| Java | [ 大数据运维工程师(深圳)](https://careers.tencent.com/jobdesc.html?postId=2036976622594322432) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2036976622594322432&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| 前端 | [Marvis高级前端开发工程师](https://careers.tencent.com/jobdesc.html?postId=2094609259022172160) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2094609259022172160&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| 前端 | [腾讯营销-搜索营销前端开发工程师](https://careers.tencent.com/jobdesc.html?postId=2085193664560279552) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 一年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2085193664560279552&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| 前端 | [机器学习平台前端全栈工程师(（深圳/北京）](https://careers.tencent.com/jobdesc.html?postId=2096784591401697280) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2096784591401697280&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| 前端 | [微信小程序-前端开发工程师-开发者方向](https://careers.tencent.com/jobdesc.html?postId=1891443603608215552) | 腾讯 | 广州 | 社会招聘（岗位要求参考，非校招） | 一年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=1891443603608215552&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| 前端 | [《无畏契约》-前端开发工程师-（AI & 创新互动方向）](https://careers.tencent.com/jobdesc.html?postId=2088182151676870656) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 一年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2088182151676870656&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| 前端 | [智能体-全栈开发专家-CodeBuddy/WorkBuddy](https://careers.tencent.com/jobdesc.html?postId=2064295120924225536) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 十年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2064295120924225536&language=zh-cn) | 2026-10-07T21:06:11+08:00 |
| 前端 | [微信小店-全栈开发工程师-交易基建方向](https://careers.tencent.com/jobdesc.html?postId=2077293041282105344) | 腾讯 | 广州 | 社会招聘（岗位要求参考，非校招） | 一年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2077293041282105344&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| 前端 | [腾讯AI协作工具-全栈开发工程师](https://careers.tencent.com/jobdesc.html?postId=2089237110640001024) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2089237110640001024&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| 运营 | [微信小店-产品运营-行业运营（电商平台活动运营）](https://careers.tencent.com/jobdesc.html?postId=1946160913375666176) | 腾讯 | 广州 | 社会招聘（岗位要求参考，非校招） | 两年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=1946160913375666176&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| 运营 | [QQ-商业内容运营-AI漫剧运营](https://careers.tencent.com/jobdesc.html?postId=2047584818077794304) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2047584818077794304&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| 运营 | [《英雄联盟》-游戏运营（用户运营方向）](https://careers.tencent.com/jobdesc.html?postId=2100246114195390464) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2100246114195390464&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| 运营 | [微信小店-产品运营-商家运营方向](https://careers.tencent.com/jobdesc.html?postId=2041436703062917120) | 腾讯 | 广州 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2041436703062917120&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| 运营 | [3D动作游戏《狩》-游戏运营-版本运营](https://careers.tencent.com/jobdesc.html?postId=2090447014570672128) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2090447014570672128&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| 运营 | [AI产品运营](https://careers.tencent.com/jobdesc.html?postId=2089943379306786816) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2089943379306786816&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| 运营 | [小游戏运营](https://careers.tencent.com/jobdesc.html?postId=2076533135075618816) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 不限 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2076533135075618816&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| 运营 | [知名IP海外项目游戏运营-版本运营](https://careers.tencent.com/jobdesc.html?postId=2092449444971855872) | 腾讯 | 深圳 | 社会招聘（岗位要求参考，非校招） | 三年以上工作经验 | 未公开 | 200 / GET JSON / [详情JSON](https://careers.tencent.com/tencentcareer/api/post/ByPostId?postId=2092449444971855872&language=zh-cn) | 2026-10-07T21:06:12+08:00 |
| Java | [后台开发](https://join.qq.com/jobdesc.html?postId=1282707383965295616) | 腾讯 | 深圳总部、北京、上海、广州、成都、武汉、杭州 | 校园招聘（应届毕业生） | 毕业时间：2026年1月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1282707383965295616) | 2026-10-07T21:12:40+08:00 |
| Java | [后台开发](https://join.qq.com/jobdesc.html?postId=1200791473415778304) | 腾讯 | 深圳总部、北京、上海、广州、成都、杭州 | 实习（应届实习） | 毕业时间：2026年9月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1200791473415778304) | 2026-10-07T21:12:40+08:00 |
| Java | [后台开发](https://join.qq.com/jobdesc.html?postId=1210646470286783488) | 腾讯 | 深圳总部、北京、上海、广州、武汉、杭州 | 实习（日常实习） | 面向全体在校生，提供实习机会 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1210646470286783488) | 2026-10-07T21:12:40+08:00 |
| Java | [测试开发](https://join.qq.com/jobdesc.html?postId=1283126501084308480) | 腾讯 | 深圳总部、北京、上海、广州、成都 | 校园招聘（应届毕业生） | 毕业时间：2026年1月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1283126501084308480) | 2026-10-07T21:12:40+08:00 |
| Java | [测试开发](https://join.qq.com/jobdesc.html?postId=1210569901664899072) | 腾讯 | 深圳总部、北京、上海、广州、成都 | 实习（应届实习） | 毕业时间：2026年9月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1210569901664899072) | 2026-10-07T21:12:40+08:00 |
| Java | [测试开发](https://join.qq.com/jobdesc.html?postId=1212183855952704512) | 腾讯 | 深圳总部 | 实习（日常实习） | 面向全体在校生，提供实习机会 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1212183855952704512) | 2026-10-07T21:12:40+08:00 |
| 前端 | [前端开发](https://join.qq.com/jobdesc.html?postId=1282707439695012865) | 腾讯 | 深圳总部、北京、上海、广州、成都、武汉 | 校园招聘（应届毕业生） | 毕业时间：2026年1月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1282707439695012865) | 2026-10-07T21:12:40+08:00 |
| 前端 | [前端开发](https://join.qq.com/jobdesc.html?postId=1211674475813408768) | 腾讯 | 深圳总部、北京、上海、广州、成都、武汉 | 实习（应届实习） | 毕业时间：2026年9月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1211674475813408768) | 2026-10-07T21:12:40+08:00 |
| 前端 | [前端开发](https://join.qq.com/jobdesc.html?postId=1211741011563656192) | 腾讯 | 深圳总部、广州 | 实习（日常实习） | 面向全体在校生，提供实习机会 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1211741011563656192) | 2026-10-07T21:12:40+08:00 |
| 前端 | [AI全栈工程师](https://join.qq.com/jobdesc.html?postId=1282707398326592512) | 腾讯 | 深圳总部、北京、上海、广州、成都、杭州 | 校园招聘（应届毕业生） | 毕业时间：2026年1月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1282707398326592512) | 2026-10-07T21:12:40+08:00 |
| 前端 | [运营开发](https://join.qq.com/jobdesc.html?postId=1283126506864059392) | 腾讯 | 深圳总部、北京、上海、广州 | 校园招聘（应届毕业生） | 毕业时间：2026年1月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1283126506864059392) | 2026-10-07T21:12:40+08:00 |
| 前端 | [运营开发](https://join.qq.com/jobdesc.html?postId=1212080150968274944) | 腾讯 | 深圳总部、北京、上海、广州 | 实习（应届实习） | 毕业时间：2026年9月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1212080150968274944) | 2026-10-07T21:12:40+08:00 |
| 运营 | [产品运营](https://join.qq.com/jobdesc.html?postId=1282707379615819777) | 腾讯 | 深圳总部、北京、广州、成都 | 校园招聘（应届毕业生） | 毕业时间：2026年1月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1282707379615819777) | 2026-10-07T21:12:40+08:00 |
| 运营 | [产品运营](https://join.qq.com/jobdesc.html?postId=1212816229455368192) | 腾讯 | 深圳总部、北京、上海、广州、成都 | 实习（应届实习） | 毕业时间：2026年9月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1212816229455368192) | 2026-10-07T21:12:40+08:00 |
| 运营 | [产品运营](https://join.qq.com/jobdesc.html?postId=1218257147532668928) | 腾讯 | 深圳总部、北京、上海、广州 | 实习（日常实习） | 面向全体在校生，提供实习机会 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1218257147532668928) | 2026-10-07T21:12:40+08:00 |
| 运营 | [内容运营](https://join.qq.com/jobdesc.html?postId=1283126452350690304) | 腾讯 | 深圳总部、北京、广州 | 校园招聘（应届毕业生） | 毕业时间：2026年1月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1283126452350690304) | 2026-10-07T21:12:40+08:00 |
| 运营 | [内容运营](https://join.qq.com/jobdesc.html?postId=1224696522596614144) | 腾讯 | 深圳总部、北京、成都 | 实习（应届实习） | 毕业时间：2026年9月1日-2027年12月31日 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1224696522596614144) | 2026-10-07T21:12:40+08:00 |
| 运营 | [内容运营](https://join.qq.com/jobdesc.html?postId=1218257148728045568) | 腾讯 | 深圳总部、广州 | 实习（日常实习） | 面向全体在校生，提供实习机会 | 未公开 | 200 / GET JSON / [详情JSON](https://join.qq.com/api/v1/jobDetails/getJobDetailsByPostId?postId=1218257148728045568) | 2026-10-07T21:12:40+08:00 |

## 访问限制与未采用的来源

| 尝试来源 | 方法 | 实际结果 | 说明 | 查询时间 |
| --- | --- | --- | --- | --- |
| [mysql-primary-index](https://dev.mysql.com/doc/refman/8.4/en/mysql-indexes.html) | GET | ACCESS_LIMITED; HTTP 403 | HTTP Error 403: Forbidden | 2026-10-07T21:14:36+08:00 |
| [mysql-primary-explain](https://dev.mysql.com/doc/refman/8.4/en/using-explain.html) | GET | ACCESS_LIMITED; HTTP 403 | HTTP Error 403: Forbidden | 2026-10-07T21:14:36+08:00 |
| [mysql-primary-transaction](https://dev.mysql.com/doc/refman/8.4/en/commit.html) | GET | ACCESS_LIMITED; HTTP 403 | HTTP Error 403: Forbidden | 2026-10-07T21:14:36+08:00 |
| [microsoft-exp-primary](https://www.microsoft.com/en-us/research/group/experimentation-platform-exp/) | GET | ACCESS_LIMITED; HTTP 403 | HTTP Error 403: Forbidden | 2026-10-07T21:14:36+08:00 |
| [exp-historical-reference](https://exp-platform.com/) | GET | PUBLIC_REFERENCE_NOT_USED; HTTP 200 | 历史ExP公开研究资料站，由Ronny Kohavi维护；未将其当作当前微软官网资料导入 | 2026-10-07T21:14:36+08:00 |
| [ga4-events-attempt](https://support.google.com/analytics/answer/9322688?hl=zh-Hans) | GET | ACCESS_LIMITED; HTTP 未取得响应 | <urlopen error timed out> | 2026-10-07T21:14:36+08:00 |
| [ga4-exploration-attempt](https://support.google.com/analytics/answer/7579450?hl=zh-Hans) | GET | ACCESS_LIMITED; HTTP 未取得响应 | <urlopen error timed out> | 2026-10-07T21:14:36+08:00 |
| [ga4-funnel-attempt](https://support.google.com/analytics/answer/9327974?hl=zh-Hans) | GET | ACCESS_LIMITED; HTTP 未取得响应 | <urlopen error timed out> | 2026-10-07T21:14:36+08:00 |
| [mdn-obsolete-grid-path](https://developer.mozilla.org/zh-CN/docs/Web/CSS/Guides/Grid_layout/Basic_concepts_of_grid_layout) | GET | ACCESS_LIMITED; HTTP 404 | HTTP Error 404: Not Found | 2026-10-07T21:14:36+08:00 |
| [clarity-obsolete-recording-path](https://learn.microsoft.com/en-us/clarity/session-recordings/recordings-features) | GET | ACCESS_LIMITED; HTTP 404 | HTTP Error 404: Not Found | 2026-10-07T21:14:36+08:00 |
| [clarity-obsolete-dashboard-path](https://learn.microsoft.com/en-us/clarity/setup-and-installation/clarity-dashboard) | GET | ACCESS_LIMITED; HTTP 404 | HTTP Error 404: Not Found | 2026-10-07T21:14:36+08:00 |
| [tencent-campus-home](https://join.qq.com/) | GET | CLIENT_RENDERED_SHELL; HTTP 200 | 首页 \| 腾讯校招 | 2026-10-07T21:14:36+08:00 |
| [tencent-campus-list-page](https://join.qq.com/post.html) | GET | CLIENT_RENDERED_SHELL; HTTP 200 | 岗位投递 \| 腾讯校招 | 2026-10-07T21:14:37+08:00 |
| [tencent-campus-post-script](https://cdn.multilingualres.hr.tencent.com/joinqq/static2/js/p_zh-cn_post.build.js) | GET | VERIFIED_API_DISCOVERY; HTTP 200 |  | 2026-10-07T21:14:37+08:00 |
| [byte-campus-page-attempt](https://jobs.bytedance.com/campus/position) | GET | CLIENT_RENDERED_SHELL; HTTP 200 | 字节跳动校园招聘官网｜字节跳动校招 | 2026-10-07T21:14:37+08:00 |
| [byte-campus-old-api-attempt](https://jobs.bytedance.com/api/v1/search/job/posts?keyword=&limit=8&offset=0&recruitment_id_list=202) | GET | CLIENT_RENDERED_SHELL; HTTP 200 | 字节跳动猎头平台 | 2026-10-07T21:14:37+08:00 |
| [tencent-campus-search-get-method-check](https://join.qq.com/api/v1/position/searchPosition) | GET | WRONG_METHOD_NOT_USED; HTTP 200 | 405 | 2026-10-07T21:18:11+08:00 |

MySQL `dev.mysql.com` 的 8.4 文档入口在此次尝试中受到访问限制，最终使用 Oracle 官方托管的 **MySQL 8.0** 参考手册，版本没有冒充为 8.4。Microsoft Research 的 ExP 介绍页未用作知识引用；Azure 官方变体功能标志文档提供了可核对的实验分流资料。历史 `exp-platform.com` 页面虽能读取，但页面说明其为 Ronny Kohavi 维护的历史研究资料站，因此没有将它冒充为当前微软官方资料收录。

Google Analytics 官方帮助页面读取失败，未据此生成 GA4 摘要。初次尝试旧 Excel 中文地址时落入 Microsoft 365 帮助首页或返回 404，未计入核对；最终 5 篇 Excel 资料均使用能读取对应主题全文的官方英文地址。Microsoft Learn 正文中存在通用授权提示模板，但所收录的公开文章正文可完整读取，主题词和证据均来自正文。

腾讯校园首页最初仅返回壳页面，部分脚本初次请求受限；后续带校园页面正常 Referer 成功读取官方职位脚本，并据其中明确的 API 方法取得校招和实习详情。`searchPosition` 的 GET 请求返回业务层 405，因此采用脚本声明的 POST 查询。字节校园页及旧 API 探索没有取得可核对的招聘详情，未收录该企业岗位。

## 结构与编码验证

已通过：UTF-8严格读取与JSON解析；34篇知识字段完整、中文正文不少于150字符；42条招聘唯一ID、要求与技能非空；所有技能具有原文证据；所有收录来源URL均对应HTTP200且读到响应结束的记录；薪资全部为未公开；核心文本不含Unicode替换字符。

目录保存 172 条访问记录（含未收录候选岗位的详情核对和失败尝试）。状态数量：ACCESS_LIMITED=10, CLIENT_RENDERED_SHELL=46, PUBLIC_REFERENCE_NOT_USED=1, VERIFIED=34, VERIFIED_API_DISCOVERY=1, VERIFIED_CAMPUS_JOB_DETAIL=21, VERIFIED_JOB_DETAIL=50, VERIFIED_LIST_ONLY=7, VERIFIED_PROJECT_ELIGIBILITY=1, WRONG_METHOD_NOT_USED=1。

可复核的本地命令（从仓库根目录运行）：

```powershell
python -X utf8 -c "import json,pathlib,collections; d=json.loads(pathlib.Path('scripts/data/verified-public-sources.json').read_text(encoding='utf-8')); assert len(d['knowledgeMaterials'])>=30; assert all(x['status']=='VERIFIED' and x['verification']['readToEnd'] for x in d['knowledgeMaterials']); assert len(d['recruitmentJobs'])>=12; assert all(x['requirements'] and x['skills'] and x['verification']['readToEnd'] for x in d['recruitmentJobs']); assert len({x['id'] for x in d['recruitmentJobs']})==len(d['recruitmentJobs']); print(collections.Counter(x['category'] for x in d['knowledgeMaterials'])); print(collections.Counter(x.get('recruitmentKind') for x in d['recruitmentJobs']))"
```

联网证据集中保存在 `sourceChecks` 与各记录 `verification` 中，包括读取时间、实际方法、状态和响应指纹。以上本地验证只验证已保存目录的结构与证据字段，不会重新证明远端页面当前仍保持相同内容。
