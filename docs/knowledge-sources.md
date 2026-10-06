# 中文知识专题来源清单

本清单面向知识库维护人员和项目负责人, 从
`backend/ai-service/src/main/resources/knowledge-topics/campus-topics-zh.json`
登记的 30 个专题整理。Java、前端、运营各 10 篇, 正文、例子和练习均为平台自编。
下表保留 JSON 中的 source、sourceUrl、applicableVersion、checkedAt, 不把自编内容称作外部原文。

`checkedAt` 是素材作者登记的核对日期。此次整理验证字段完整、URL 格式、前置关系和服务中的引用位置;
未完成所有外部页面的逐句审核。内容与评估标签状态仍为 `OWNER_REVIEW_PENDING`, 由项目负责人复核。
不要将尚未完成的审核标记为已完成, 也不要使用检索测试结果反向修改测试标签。

## Java

| 专题 ID / 技能 | 登记来源 source | 来源链接 sourceUrl | 适用版本 applicableVersion | 核对日期 checkedAt |
| --- | --- | --- | --- | --- |
| KT-JAVA-01 / Java | 平台中文自编，参考 Oracle Java SE 集合接口文档 | [Collection API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html) | Java 21；集合接口基础适用于常见 Java 版本 | 2026-10-06 |
| KT-JAVA-02 / 异常处理 | 平台中文自编，参考 Oracle Java 异常与资源管理文档 | [try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html) | Java 7+ try-with-resources | 2026-10-06 |
| KT-JAVA-03 / 并发 | 平台中文自编，参考 Oracle Java 并发 API | [java.util.concurrent](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html) | Java 21 并发 API | 2026-10-06 |
| KT-JAVA-04 / Spring Boot | 平台中文自编，参考 Spring Boot 官方参考文档 | [Using Spring Boot](https://docs.spring.io/spring-boot/reference/using/index.html) | Spring Boot 3；项目实际版本需核对 | 2026-10-06 |
| KT-JAVA-05 / 接口设计 | 平台中文自编，参考 MDN HTTP 方法与语义 | [HTTP 方法](https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Reference/Methods) | HTTP 接口通用约束 | 2026-10-06 |
| KT-JAVA-06 / SQL | 平台中文自编，参考 MySQL SELECT 文档 | [MySQL SELECT](https://dev.mysql.com/doc/refman/8.0/en/select.html) | MySQL 8.0 | 2026-10-06 |
| KT-JAVA-07 / MySQL | 平台中文自编，参考 MySQL InnoDB 与索引文档 | [InnoDB transaction model](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-model.html) | MySQL 8.0 InnoDB | 2026-10-06 |
| KT-JAVA-08 / Redis | 平台中文自编，参考 Redis 官方缓存介绍 | [Using Redis](https://redis.io/docs/latest/develop/use/) | Redis 通用缓存设计；具体命令按服务版本核对 | 2026-10-06 |
| KT-JAVA-09 / 测试 | 平台中文自编，参考 JUnit 用户指南 | [JUnit user guide](https://docs.junit.org/current/user-guide/) | JUnit Jupiter；项目依赖版本需核对 | 2026-10-06 |
| KT-JAVA-10 / 排障 | 平台中文自编，参考 Spring Boot Actuator 文档 | [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html) | Spring Boot 3 Actuator | 2026-10-06 |

## 前端

| 专题 ID / 技能 | 登记来源 source | 来源链接 sourceUrl | 适用版本 applicableVersion | 核对日期 checkedAt |
| --- | --- | --- | --- | --- |
| KT-FRONT-01 / HTML | 平台中文自编，参考 MDN HTML 文档 | [MDN HTML](https://developer.mozilla.org/zh-CN/docs/Web/HTML) | HTML Living Standard 常用语义 | 2026-10-06 |
| KT-FRONT-02 / CSS | 平台中文自编，参考 MDN CSS 布局文档 | [CSS 布局](https://developer.mozilla.org/zh-CN/docs/Learn_web_development/Core/CSS_layout) | 现代浏览器 Flex/Grid | 2026-10-06 |
| KT-FRONT-03 / JavaScript | 平台中文自编，参考 MDN JavaScript 指南 | [JavaScript 指南](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Guide) | 现代 ECMAScript | 2026-10-06 |
| KT-FRONT-04 / 异步请求 | 平台中文自编，参考 MDN Fetch 文档 | [使用 Fetch](https://developer.mozilla.org/zh-CN/docs/Web/API/Fetch_API/Using_Fetch) | 现代浏览器 Fetch 与 AbortController | 2026-10-06 |
| KT-FRONT-05 / TypeScript | 平台中文自编，参考 TypeScript Handbook | [TypeScript narrowing](https://www.typescriptlang.org/docs/handbook/2/narrowing.html) | TypeScript 5 常用类型收窄 | 2026-10-06 |
| KT-FRONT-06 / Vue | 平台中文自编，参考 Vue 官方指南 | [Vue 组件基础](https://cn.vuejs.org/guide/essentials/component-basics.html) | Vue 3 Composition API | 2026-10-06 |
| KT-FRONT-07 / React | 平台中文自编，参考 React 官方文档 | [Synchronizing with Effects](https://react.dev/learn/synchronizing-with-effects) | React 18+ 常用状态与 Effect | 2026-10-06 |
| KT-FRONT-08 / 状态管理 | 平台中文自编，参考 Vue 状态管理指南 | [Vue 状态管理](https://cn.vuejs.org/guide/scaling-up/state-management.html) | Vue 3；状态原则也适用于其他组件框架 | 2026-10-06 |
| KT-FRONT-09 / 性能优化 | 平台中文自编，参考 Google PageSpeed Insights 性能文档 | [PageSpeed Insights](https://developers.google.cn/speed/docs/insights/v5/about) | 当前 Web Vitals 定义，以页面指标版本为准 | 2026-10-06 |
| KT-FRONT-10 / 测试与无障碍 | 平台中文自编，参考 Playwright 与 W3C 无障碍指南 | [Playwright best practices](https://playwright.dev/docs/best-practices) | Playwright 当前稳定版；WCAG 通用实践 | 2026-10-06 |

## 运营

| 专题 ID / 技能 | 登记来源 source | 来源链接 sourceUrl | 适用版本 applicableVersion | 核对日期 checkedAt |
| --- | --- | --- | --- | --- |
| KT-OPS-01 / 用户分析 | 平台中文自编，参考 Microsoft Clarity 行为分析官方文档 | [Microsoft Clarity](https://learn.microsoft.com/en-us/clarity/) | 行为分析概念；本任务无需接入外部平台 | 2026-10-06 |
| KT-OPS-02 / 内容策略 | 平台中文自编，参考 Google Search 内容质量建议 | [以用户为本的内容](https://developers.google.cn/search/docs/fundamentals/creating-helpful-content?hl=zh-cn) | 面向用户的内容设计原则 | 2026-10-06 |
| KT-OPS-03 / 文案 | 平台中文自编，参考 GOV.UK 内容设计与清晰语言原则 | [Writing for GOV.UK](https://www.gov.uk/guidance/content-design/writing-for-gov-uk) | 清晰表达通用原则；中文文案为自编 | 2026-10-06 |
| KT-OPS-04 / 活动策划 | 平台中文自编，参考 Atlassian 项目规划实践 | [Project poster](https://www.atlassian.com/team-playbook/plays/project-poster) | 项目规划通用方法 | 2026-10-06 |
| KT-OPS-05 / 社群运营 | 平台中文自编，参考 Atlassian 团队协作约定 | [Working agreements](https://www.atlassian.com/team-playbook/plays/working-agreements) | 社群运营通用原则；协作约定为参考 | 2026-10-06 |
| KT-OPS-06 / 数据分析 | 平台中文自编，参考 Google Analytics 指标与维度说明 | [GA4 API schema](https://developers.google.cn/analytics/devguides/reporting/data/v1/api-schema) | GA4 维度与指标概念 | 2026-10-06 |
| KT-OPS-07 / 漏斗分析 | 平台中文自编，参考 Microsoft Clarity 漏斗官方文档 | [Clarity funnels](https://learn.microsoft.com/en-us/clarity/funnels) | 漏斗分析概念；以统计工具实际口径为准 | 2026-10-06 |
| KT-OPS-08 / Excel | 平台中文自编，参考 Microsoft Excel 官方帮助 | [Excel 帮助](https://support.microsoft.com/zh-cn/excel) | Excel Microsoft 365/2021 常用分析功能 | 2026-10-06 |
| KT-OPS-09 / A/B测试 | 平台中文自编，参考 Microsoft Experimentation Platform 官方研究 | [Experimentation Platform](https://www.microsoft.com/en-us/research/group/experimentation-platform-exp/) | 实验设计通用原则；无需接入广告平台 | 2026-10-06 |
| KT-OPS-10 / 复盘 | 平台中文自编，参考 Atlassian Retrospective 实践 | [Retrospective](https://www.atlassian.com/team-playbook/plays/retrospective) | 团队复盘通用方法 | 2026-10-06 |

## 复核标准

先确认链接指向官方、标准或明确署名的参考资料, 再核对概念适用范围及版本。
例如 Java 的单进程锁不保证多实例全局约束, Redis TTL 不保证强一致性,
运营指标必须注明分子、分母和观察窗口。具体例子是合成实践, 不代表真实业务成果或性能数字。

目前部分 URL 是文档入口而非专题全部结论的逐句依据。负责人复核时应补充相应具体章节:
Collection 链接不替代 Map/HashMap 合同说明, InnoDB 事务页面不替代复合索引章节,
Playwright 页面不替代 WCAG 标准, 协作约定也不等于完整社群运营方法。
可核对 [Map 合同](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html),
[MySQL 复合索引](https://dev.mysql.com/doc/refman/8.0/en/multiple-column-indexes.html),
[WCAG 2.2](https://www.w3.org/TR/WCAG22/)。这些补充阅读不是已完成审核的声明。

自编正文是应用内检索引用的直接来源。引用需要定位到保存的全文片段及版本,
不能因为有外部 URL 就声称模型生成的任意内容得到该外部资料支撑。
资料编辑、下架或权限改变后, 应重新验证历史阅读及缓存能否继续返回旧材料。
