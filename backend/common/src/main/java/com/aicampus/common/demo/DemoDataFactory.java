package com.aicampus.common.demo;

import com.aicampus.common.dto.AiCallRecord;
import com.aicampus.common.dto.AiPlanningRecord;
import com.aicampus.common.dto.CandidateScreenRecord;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.CandidateScreenTask;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.dto.InterviewRecord;
import com.aicampus.common.dto.InterviewSchedule;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.KnowledgeDocument;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.common.dto.NotificationMessage;
import com.aicampus.common.dto.ResumeRewriteResponse;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.dto.UserProfile;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.aicampus.common.enums.CandidateScreenTaskStatus;
import com.aicampus.common.enums.DeliveryStatus;
import com.aicampus.common.enums.InterviewScheduleStatus;
import com.aicampus.common.enums.Role;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class DemoDataFactory {
    public static final int DEFAULT_SIZE = 120;
    private static final LocalDateTime BASE_DATE_TIME = LocalDateTime.of(2026, 6, 12, 9, 0);
    private static final Instant BASE_INSTANT = BASE_DATE_TIME.toInstant(ZoneOffset.ofHours(8));

    private static final String[] FAMILY_NAMES = {
            "Chen", "Li", "Wang", "Zhang", "Liu", "Yang", "Huang", "Zhao", "Wu", "Zhou",
            "Xu", "Sun", "Ma", "Zhu", "Hu", "Guo", "He", "Gao", "Lin", "Luo"
    };
    private static final String[] GIVEN_NAMES = {
            "Yichen", "Zihan", "Mingyu", "Haoran", "Yutong", "Jingyi", "Xinyu", "Bowen", "Siyuan", "Qianwen",
            "Jiahao", "Ruoxi", "Yuxuan", "Tianqi", "Anqi", "Zixuan", "Yiming", "Shuyi", "Junhao", "Yiran"
    };
    private static final String[] SCHOOLS = {
            "Zhejiang University", "Nanjing University", "Wuhan University", "Xidian University",
            "Beijing University of Posts and Telecommunications", "South China University of Technology",
            "Sichuan University", "Shandong University", "Hunan University", "Shanghai University"
    };
    private static final String[] MAJORS = {
            "Software Engineering", "Computer Science", "Data Science", "Information Security",
            "Artificial Intelligence", "Network Engineering", "Internet of Things", "Automation"
    };
    private static final String[] COMPANIES = {
            "星河科技", "启航教育集团", "新芽培训学校", "远航销售服务", "云栖智能", "嘉禾零售",
            "蓝海供应链", "华信金融", "康悦医疗", "城际文旅", "安居地产", "明德咨询",
            "青橙传媒", "锦程人力", "云帆外贸", "绿洲环保", "博雅学校", "同城物业",
            "未来汽车", "瑞禾会计师事务所", "知行公益", "海纳制造", "星火直播", "安心保险"
    };
    private static final String[] CITIES = {
            "杭州", "上海", "北京", "深圳", "广州", "南京", "成都", "武汉", "苏州", "西安", "厦门", "合肥"
    };
    private static final JobTemplate[] JOB_TEMPLATES = {
            job("Java 后端实习生", "校园招聘平台研发",
                    "参与招聘平台、数据看板和中台接口开发，负责接口设计、业务建模和问题排查",
                    "适合具备 Java Web 项目经验，理解 MySQL、Redis 和接口设计的应届生。",
                    "Java", "Spring Boot", "MySQL", "Redis", "接口设计"),
            job("前端开发实习生", "学生端与企业端页面建设",
                    "建设岗位浏览、简历诊断、投递看板和企业审核页面，关注组件复用和交互体验",
                    "适合有 Vue 项目经历，能独立完成列表、表单、图表和接口联调的同学。",
                    "Vue", "TypeScript", "Vite", "组件化", "接口联调"),
            job("AI/RAG 应用实习生", "智能问答与知识库检索",
                    "接入大模型、构建知识切片与检索流程，并把 RAG 证据用于简历诊断和面试题生成",
                    "适合熟悉 Prompt、向量检索和后端接口封装，愿意做 AI 工程落地的候选人。",
                    "Python", "Prompt", "RAG", "向量检索", "大模型应用"),
            job("数据分析实习生", "招聘漏斗与经营分析",
                    "维护指标看板，分析投递转化、渠道效果和用户行为，输出业务复盘结论",
                    "适合 SQL 基础扎实，能把数据现象拆成业务原因和行动建议的同学。",
                    "SQL", "Excel", "Python", "指标分析", "可视化"),
            job("测试开发实习生", "质量平台与自动化回归",
                    "设计接口自动化、UI 回归和压测脚本，沉淀缺陷定位报告和质量指标",
                    "适合会写测试代码，能用数据说明稳定性、性能和覆盖率改进的候选人。",
                    "Java", "JUnit", "Postman", "Selenium", "JMeter"),
            job("小学数学教师", "K12 数学教学",
                    "负责小学数学班课授课、作业批改、家校沟通和阶段测评反馈",
                    "适合数学基础扎实、有耐心、表达清楚并愿意长期从事教学工作的候选人。",
                    "数学基础", "课程设计", "课堂管理", "家校沟通", "作业批改"),
            job("初中英语教师", "K12 英语教学",
                    "负责初中英语语法、阅读和听说课程教学，跟进学生学习效果",
                    "适合英语表达流利，能设计课堂互动和阶段性学习计划的同学。",
                    "英语口语", "语法教学", "阅读训练", "课堂互动", "学习反馈"),
            job("高中物理教师", "高中理科教学",
                    "讲授力学、电学和实验专题，负责备课、答疑和阶段考试分析",
                    "适合理科基础扎实，能把抽象概念讲清楚并善于总结题型的候选人。",
                    "物理基础", "题型讲解", "实验设计", "备课", "答疑"),
            job("语文助教", "语文阅读与写作辅导",
                    "协助主讲老师完成作文批改、阅读训练、课堂资料整理和学生跟进",
                    "适合文字表达能力好，认真细致并愿意和学生持续沟通的同学。",
                    "写作批改", "阅读理解", "资料整理", "学生沟通", "教务协作"),
            job("在线课程班主任", "在线学习服务",
                    "负责在线班级学习提醒、作业跟进、续报线索识别和家长反馈",
                    "适合沟通耐心、执行力强，能用表格和系统跟进多个学生状态的候选人。",
                    "学习督导", "社群维护", "家长沟通", "数据记录", "续班跟进"),
            job("课程顾问", "教育咨询与招生转化",
                    "接待家长咨询，了解学生学习情况，匹配课程方案并跟进报名转化",
                    "适合表达亲和、抗压能力强，愿意在教育行业做销售咨询的候选人。",
                    "客户沟通", "需求挖掘", "课程介绍", "销售转化", "CRM"),
            job("销售管培生", "企业客户开发",
                    "参与客户线索收集、电话邀约、方案介绍、合同跟进和销售复盘",
                    "适合目标感强、表达清晰，能接受结果导向和外部客户沟通的应届生。",
                    "客户开发", "电话沟通", "商务谈判", "销售漏斗", "复盘"),
            job("大客户销售实习生", "B2B 大客户拓展",
                    "协助销售经理梳理行业客户、准备拜访材料、跟进商机和回款节点",
                    "适合逻辑清晰、执行力强，愿意学习企业级销售流程的候选人。",
                    "行业研究", "客户拜访", "方案讲解", "商机跟进", "合同流程"),
            job("电商运营实习生", "商品运营与店铺增长",
                    "维护商品上下架、活动报名、价格库存和竞品数据，跟进店铺指标",
                    "适合熟悉电商平台规则，能处理表格数据和活动节奏的同学。",
                    "商品运营", "平台规则", "Excel", "活动报名", "竞品分析"),
            job("新媒体运营实习生", "内容发布与账号增长",
                    "负责公众号、短视频和小红书内容选题、发布排期、数据复盘和评论互动",
                    "适合网感好、文字表达稳定，能根据数据调整内容方向的候选人。",
                    "内容选题", "文案写作", "短视频", "账号运营", "数据复盘"),
            job("内容策划实习生", "品牌内容与活动传播",
                    "协助完成活动主题、推文脚本、海报文案和传播节奏设计",
                    "适合创意表达强，能把业务卖点转成清晰内容结构的同学。",
                    "内容策划", "文案", "活动主题", "用户洞察", "排期管理"),
            job("市场推广实习生", "校园渠道与地推活动",
                    "执行校园宣讲、社群拉新、物料投放和活动数据统计",
                    "适合外向主动、组织能力强，能落地执行线下活动的候选人。",
                    "校园推广", "活动执行", "社群拉新", "物料管理", "数据统计"),
            job("品牌公关实习生", "品牌传播与媒体关系",
                    "协助撰写新闻稿、维护媒体清单、跟进传播素材和舆情日报",
                    "适合文字能力扎实、细致负责并理解品牌传播节奏的同学。",
                    "新闻稿", "媒体沟通", "舆情监测", "品牌传播", "素材管理"),
            job("HR 招聘实习生", "招聘交付与候选人运营",
                    "发布职位、筛选简历、邀约面试、维护招聘系统并跟进候选人体验",
                    "适合沟通稳定、责任心强，对人力资源和招聘流程感兴趣的候选人。",
                    "简历筛选", "面试邀约", "招聘系统", "候选人沟通", "Excel"),
            job("人力资源助理", "员工关系与培训支持",
                    "协助入离转调、培训组织、档案维护和员工活动执行",
                    "适合细心、有服务意识，能处理流程性事务和跨部门协作的同学。",
                    "员工关系", "培训组织", "档案管理", "流程跟进", "沟通协作"),
            job("财务助理", "费用报销与账务支持",
                    "协助审核报销单据、整理凭证、核对往来账和输出月度基础报表",
                    "适合会计基础扎实、细致严谨，熟悉 Excel 的候选人。",
                    "会计基础", "费用报销", "凭证整理", "Excel", "财务报表"),
            job("审计实习生", "审计底稿与内控测试",
                    "协助完成凭证抽查、数据核对、访谈记录和审计底稿整理",
                    "适合财会专业、逻辑严谨，能接受出差和高强度项目节奏的同学。",
                    "审计底稿", "凭证抽查", "内控测试", "Excel", "访谈记录"),
            job("行政前台实习生", "办公支持与访客接待",
                    "负责访客接待、会议室管理、办公用品登记和基础行政流程跟进",
                    "适合形象亲和、服务意识好，做事细致有条理的候选人。",
                    "访客接待", "会议室管理", "行政流程", "物资管理", "服务意识"),
            job("法务助理", "合同审查与合规支持",
                    "协助整理合同台账、检索法规案例、审核标准条款和归档法律文件",
                    "适合法学基础扎实，文字严谨并能处理大量文档的同学。",
                    "合同审查", "法律检索", "合规", "文档归档", "风险意识"),
            job("客服专员实习生", "用户咨询与问题闭环",
                    "通过在线客服和电话处理用户咨询，记录问题分类并推动闭环解决",
                    "适合情绪稳定、表达清楚，愿意从一线用户问题理解业务的候选人。",
                    "在线客服", "电话沟通", "工单处理", "问题记录", "服务意识"),
            job("用户运营实习生", "用户留存与活跃提升",
                    "维护用户社群，设计触达话术，跟进活跃、留存和转化指标",
                    "适合喜欢和用户交流，能用数据复盘运营动作的同学。",
                    "用户分层", "社群运营", "触达策略", "留存分析", "活动复盘"),
            job("产品经理实习生", "需求分析与产品迭代",
                    "梳理用户问题、输出需求文档、跟进研发排期并参与上线验收",
                    "适合逻辑清楚、沟通主动，能把复杂流程拆成可交付需求的候选人。",
                    "需求分析", "PRD", "原型设计", "用户故事", "验收测试"),
            job("UI 设计实习生", "界面设计与设计规范",
                    "协助完成移动端和后台页面设计、组件整理、设计走查和素材交付",
                    "适合审美稳定，熟悉 Figma，能兼顾可用性和视觉一致性的同学。",
                    "Figma", "界面设计", "设计规范", "组件库", "可用性"),
            job("平面设计实习生", "品牌物料与视觉创意",
                    "设计活动海报、宣传单页、社媒配图和线下物料",
                    "适合掌握基础设计软件，能根据品牌调性快速产出视觉方案的候选人。",
                    "Photoshop", "Illustrator", "海报设计", "排版", "品牌视觉"),
            job("视频剪辑实习生", "短视频剪辑与素材管理",
                    "负责课程、活动和品牌短视频剪辑，整理素材库并输出多平台版本",
                    "适合节奏感好，熟悉剪辑工具和短视频平台内容风格的同学。",
                    "剪映", "Premiere", "脚本理解", "素材整理", "短视频"),
            job("直播运营实习生", "直播活动与转化跟进",
                    "协助直播排期、商品或课程讲解脚本、场控互动和数据复盘",
                    "适合反应快、执行力强，能处理直播现场节奏和转化数据的候选人。",
                    "直播排期", "场控", "脚本准备", "互动运营", "转化复盘"),
            job("门店储备干部", "零售门店管理",
                    "轮岗学习收银、陈列、库存、会员运营和门店人员排班",
                    "适合愿意从一线业务做起，有服务意识和现场管理潜力的应届生。",
                    "门店运营", "库存管理", "顾客服务", "陈列", "排班"),
            job("零售督导实习生", "门店巡检与标准落地",
                    "协助门店巡检、陈列检查、活动执行追踪和销售数据整理",
                    "适合执行力强、能发现现场问题并推动门店改善的候选人。",
                    "巡店", "陈列检查", "销售数据", "活动执行", "问题反馈"),
            job("供应链计划实习生", "需求预测与库存计划",
                    "协助整理销量预测、库存水位、补货计划和异常订单跟进",
                    "适合数据敏感、逻辑严谨，愿意学习供应链计划方法的同学。",
                    "需求预测", "库存计划", "Excel", "订单跟进", "跨部门协作"),
            job("采购助理", "供应商与采购流程",
                    "维护供应商资料，跟进询价、比价、合同和到货进度",
                    "适合沟通细致、成本意识强，能规范处理采购台账的候选人。",
                    "供应商管理", "询价比价", "合同跟进", "采购台账", "成本意识"),
            job("物流调度实习生", "运输调度与异常处理",
                    "协助车辆排班、路线跟进、异常反馈和运输时效统计",
                    "适合抗压能力强，能在多方沟通中保持信息准确的同学。",
                    "运输调度", "路线跟进", "异常处理", "时效统计", "沟通协调"),
            job("仓储运营实习生", "仓库现场与库存准确率",
                    "参与入库、拣货、盘点、库位优化和现场安全检查",
                    "适合责任心强，能接受仓储现场环境并关注流程效率的候选人。",
                    "入库", "拣货", "库存盘点", "库位管理", "现场安全"),
            job("外贸业务员实习生", "海外客户开发",
                    "协助开发海外客户，回复询盘，准备报价单和跟进样品寄送",
                    "适合英语读写良好，愿意学习外贸流程和客户沟通的同学。",
                    "英语邮件", "客户开发", "报价单", "外贸流程", "样品跟进"),
            job("跨境电商运营实习生", "海外平台店铺运营",
                    "维护海外商品刊登、关键词优化、广告数据和订单问题处理",
                    "适合英语基础好，熟悉跨境平台规则或愿意学习店铺运营的候选人。",
                    "跨境平台", "商品刊登", "关键词优化", "广告数据", "英语"),
            job("银行柜员实习生", "网点运营与客户服务",
                    "学习网点基础业务、客户接待、资料审核和合规操作流程",
                    "适合细心稳重、服务意识强，愿意从金融一线岗位成长的同学。",
                    "客户服务", "资料审核", "合规意识", "金融基础", "柜面流程"),
            job("证券投顾助理", "投资顾问支持",
                    "协助整理市场资讯、客户资料、产品材料和投资者教育内容",
                    "适合金融基础扎实，表达清楚并具备合规意识的候选人。",
                    "金融市场", "资料整理", "客户维护", "合规", "投资者教育"),
            job("保险理赔助理", "理赔材料审核",
                    "协助收集理赔材料、核对案件信息、跟进补件和整理结案记录",
                    "适合耐心细致，理解服务流程和风险控制要求的同学。",
                    "理赔流程", "材料审核", "客户沟通", "风险识别", "档案整理"),
            job("医药代表实习生", "医药产品推广",
                    "协助拜访终端客户，整理产品资料，跟进会议和市场反馈",
                    "适合医学、药学或市场方向学生，沟通主动并重视合规要求。",
                    "产品知识", "客户拜访", "市场反馈", "会议支持", "合规推广"),
            job("临床协调员实习生", "临床项目支持",
                    "协助项目资料整理、受试者随访、伦理文件和研究进度跟踪",
                    "适合医学相关专业，严谨细致，理解临床研究规范的候选人。",
                    "临床研究", "资料整理", "随访", "伦理文件", "GCP"),
            job("酒店前厅管培生", "酒店运营与宾客服务",
                    "轮岗学习前台接待、客诉处理、客房协同和会员服务",
                    "适合服务意识好、形象亲和，能适应排班和现场运营的同学。",
                    "前台接待", "客诉处理", "会员服务", "排班", "英语口语"),
            job("旅游产品运营实习生", "线路产品与用户体验",
                    "协助整理线路资源、供应商报价、产品上架和用户评价分析",
                    "适合热爱文旅行业，能把资源信息整理成清晰产品卖点的候选人。",
                    "线路设计", "供应商沟通", "产品上架", "用户评价", "报价整理"),
            job("房产销售顾问实习生", "新房与租赁业务",
                    "负责客户接待、房源介绍、带看安排和交易流程协助",
                    "适合目标感强、沟通主动，能接受外勤和客户跟进节奏的同学。",
                    "客户接待", "房源介绍", "带看", "销售转化", "合同协助"),
            job("物业运营实习生", "社区物业服务",
                    "协助业主沟通、报修跟进、费用台账、巡检记录和活动执行",
                    "适合服务意识强，能处理现场事务和多方沟通的候选人。",
                    "业主沟通", "报修跟进", "巡检", "费用台账", "社区活动"),
            job("社群运营实习生", "用户社群与私域转化",
                    "维护微信群和企业微信用户，设计话术、活动和转化跟进表",
                    "适合沟通频率高也能保持耐心，能用数据复盘私域动作的同学。",
                    "社群维护", "私域运营", "活动策划", "用户分层", "转化跟进"),
            job("活动执行实习生", "线下活动与会务支持",
                    "协助场地、物料、嘉宾、签到、摄影和现场问题处理",
                    "适合执行力强、细节意识好，能适应活动现场节奏的候选人。",
                    "会务执行", "物料管理", "现场协调", "签到", "供应商沟通"),
            job("政务项目助理", "政企项目交付",
                    "协助整理项目材料、会议纪要、进度台账和验收文档",
                    "适合文档能力强、流程意识好，能配合多方推进项目的同学。",
                    "项目台账", "会议纪要", "验收文档", "沟通协调", "流程管理"),
            job("乡村振兴项目专员", "公益项目与基层调研",
                    "参与乡村项目调研、资料整理、活动组织和项目成效记录",
                    "适合愿意走进基层，具备调研、沟通和文字整理能力的候选人。",
                    "基层调研", "项目执行", "资料整理", "活动组织", "成效记录"),
            job("心理咨询助理", "心理服务与测评支持",
                    "协助预约管理、测评问卷整理、活动组织和来访者资料归档",
                    "适合心理学相关专业，保密意识强，沟通温和细致的同学。",
                    "心理测评", "预约管理", "保密意识", "活动组织", "资料归档"),
            job("美术教师", "少儿美术教学",
                    "负责少儿美术课堂、作品点评、材料准备和家长沟通",
                    "适合美术基础好，喜欢儿童教育并能设计有趣课堂的候选人。",
                    "美术基础", "课堂互动", "作品点评", "材料准备", "家长沟通"),
            job("体育教师", "青少年体能与体育教学",
                    "负责体能训练、运动技能教学、安全保护和训练记录",
                    "适合体育相关专业，责任心强并具备课堂组织能力的同学。",
                    "体能训练", "运动教学", "安全保护", "课堂组织", "训练记录"),
            job("幼儿园教师", "幼儿保教与班级管理",
                    "负责幼儿日常照护、游戏活动、家园沟通和班级环境创设",
                    "适合学前教育方向，耐心细致并具备安全责任意识的候选人。",
                    "幼儿照护", "游戏活动", "家园沟通", "班级管理", "安全意识"),
            job("机械工艺实习生", "制造工艺与现场改善",
                    "协助编制工艺文件、跟进试产问题、整理设备参数和改善记录",
                    "适合机械相关专业，愿意深入生产现场并关注工艺细节的同学。",
                    "机械制图", "工艺文件", "试产跟进", "设备参数", "现场改善"),
            job("质量管理实习生", "制造质量与流程改进",
                    "协助来料检验、过程巡检、不良分析和质量报表整理",
                    "适合细致严谨，理解质量意识和基本统计分析方法的候选人。",
                    "质量检验", "不良分析", "质量报表", "流程改进", "统计分析"),
            job("环保项目助理", "环保工程与项目申报",
                    "协助采集项目资料、整理环保台账、跟进现场检查和申报文件",
                    "适合环境相关专业，文档能力好并愿意参与现场项目的同学。",
                    "环保台账", "项目申报", "现场检查", "资料整理", "环境工程")
    };
    private static final String[] TITLES = titlesFromTemplates();
    private static final String[][] SKILL_POOLS = skillsFromTemplates();
    private static final DeliveryStatus[] DELIVERY_STATUSES = {
            DeliveryStatus.SUBMITTED, DeliveryStatus.VIEWED, DeliveryStatus.INTERVIEW, DeliveryStatus.OFFER, DeliveryStatus.REJECTED
    };
    private static final InterviewScheduleStatus[] SCHEDULE_STATUSES = {
            InterviewScheduleStatus.PROPOSED, InterviewScheduleStatus.CONFIRMED, InterviewScheduleStatus.DECLINED,
            InterviewScheduleStatus.COMPLETED, InterviewScheduleStatus.CANCELLED
    };

    private DemoDataFactory() {
    }

    public static List<UserProfile> studentProfiles() {
        List<UserProfile> profiles = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            profiles.add(new UserProfile(
                    studentId(i),
                    studentName(i),
                    Role.STUDENT,
                    SCHOOLS[(i - 1) % SCHOOLS.length],
                    MAJORS[(i + 1) % MAJORS.length],
                    skills(i),
                    TITLES[(i - 1) % TITLES.length]));
        }
        return profiles;
    }

    public static List<ResumeSummary> resumes() {
        List<ResumeSummary> resumes = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            if (i == 1) {
                resumes.add(new ResumeSummary(
                        "R001",
                        "S001",
                        "demo-resume.pdf",
                        "Demo University / Software Engineering / 2026 Bachelor",
                        List.of("Java", "Spring Boot", "MySQL", "Redis"),
                        List.of("Campus second-hand trading system", "Online exam platform"),
                        "Resume structure is complete; add quantified impact and internship evidence.",
                        82,
                        "resumes/R001/demo-resume.pdf",
                        "local-demo",
                        "SEEDED",
                        "PDF",
                        "SEEDED",
                        62));
                continue;
            }
            String format = i % 7 == 0 ? "DOCX" : i % 5 == 0 ? "TXT" : "PDF";
            List<String> skills = skills(i);
            resumes.add(new ResumeSummary(
                    resumeId(i),
                    studentId(i),
                    "resume-" + studentId(i).toLowerCase() + "." + format.toLowerCase(),
                    SCHOOLS[(i - 1) % SCHOOLS.length] + " / " + MAJORS[(i + 1) % MAJORS.length] + " / 2026 Bachelor",
                    skills,
                    projects(i),
                    "Resume has clear project ownership, " + skills.get(0) + " evidence, and " + (78 + i % 18) + " readiness score.",
                    68 + (i % 29),
                    "resumes/" + resumeId(i) + "/resume-" + studentId(i).toLowerCase() + "." + format.toLowerCase(),
                    "seed-demo",
                    "SEEDED",
                    format,
                    i % 11 == 0 ? "UNPARSED" : "TEXT_EXTRACTED",
                    850 + i * 17));
        }
        return resumes;
    }

    public static List<String> resumeTexts() {
        List<String> texts = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            if (i == 1) {
                texts.add("Demo University software engineering bachelor. Skills: Java, Spring Boot, MySQL, Redis. Projects: campus second-hand trading system and online exam platform.");
                continue;
            }
            List<String> skills = skills(i);
            texts.add(studentName(i) + " from " + SCHOOLS[(i - 1) % SCHOOLS.length]
                    + ". Target role: " + TITLES[(i - 1) % TITLES.length]
                    + ". Skills: " + String.join(", ", skills)
                    + ". Projects: " + String.join("; ", projects(i))
                    + ". Internship evidence includes API design, database modeling, deployment logs, and performance review.");
        }
        return texts;
    }

    public static List<JobSummary> jobs() {
        List<JobSummary> jobs = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            JobTemplate template = jobTemplate(i);
            List<String> skills = template.skills();
            jobs.add(new JobSummary(
                    jobId(i),
                    companyId(i),
                    companyName(i),
                    template.title(),
                    CITIES[(i - 1) % CITIES.length],
                    salary(i),
                    skills,
                    template.description() + "。工作方向：" + template.domain() + "。",
                    template.aiSummary()));
        }
        return jobs;
    }

    public static List<MatchResult> matches() {
        List<MatchResult> matches = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            List<String> skills = skills(i);
            matches.add(new MatchResult(
                    matchId(i),
                    resumeId(i),
                    jobId(((i + 11) % DEFAULT_SIZE) + 1),
                    studentId(i),
                    58 + (i * 7 % 41),
                    List.of(
                            "Resume skills overlap with " + skills.get(0) + " and " + skills.get(1),
                            "Project evidence mentions API design, data model, and deployment workflow",
                            "Career target is aligned with the job title"),
                    List.of(
                            "Needs more quantified results for latency, traffic, or conversion",
                            "Distributed troubleshooting experience should be explained in more detail"),
                    List.of(
                            "Add one metric for request latency or data volume",
                            "Prepare a STAR story for " + skills.get(0) + " project ownership",
                            "Review MySQL index and Redis cache consistency scenarios")));
        }
        return matches;
    }

    public static List<DeliveryRecord> deliveries() {
        List<DeliveryRecord> deliveries = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            if (i == 1) {
                deliveries.add(new DeliveryRecord(
                        "D001",
                        "S001",
                        "R001",
                        "J001",
                        "C001",
                        "PDF",
                        "SEEDED",
                        62,
                        DeliveryStatus.SUBMITTED,
                        BASE_DATE_TIME.minusDays(1)));
                continue;
            }
            int jobIndex = ((i + 11) % DEFAULT_SIZE) + 1;
            deliveries.add(new DeliveryRecord(
                    deliveryId(i),
                    studentId(i),
                    resumeId(i),
                    jobId(jobIndex),
                    companyId(jobIndex),
                    i % 7 == 0 ? "DOCX" : "PDF",
                    i % 13 == 0 ? "UNPARSED" : "TEXT_EXTRACTED",
                    820 + i * 16,
                    DELIVERY_STATUSES[(i - 1) % DELIVERY_STATUSES.length],
                    BASE_DATE_TIME.minusHours(i * 3L)));
        }
        return deliveries;
    }

    public static List<NotificationMessage> notifications() {
        List<NotificationMessage> messages = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            boolean studentTarget = i % 2 == 1;
            String sourceId = studentTarget ? deliveryId(i) : deliveryId(((i + 17) % DEFAULT_SIZE) + 1);
            messages.add(new NotificationMessage(
                    "N-DEMO-" + "%03d".formatted(i),
                    studentTarget ? "STUDENT" : "COMPANY",
                    studentTarget ? studentId(i) : companyId(i),
                    studentTarget ? "Application progress updated" : "New candidate needs review",
                    studentTarget
                            ? "Your application " + sourceId + " has a new status in the campus recruitment workflow."
                            : "Candidate " + studentId(i) + " submitted a resume for " + jobId(i) + ". Please finish screening within 48 hours.",
                    studentTarget ? "DELIVERY_STATUS" : "DELIVERY",
                    sourceId,
                    i % 4 == 0,
                    BASE_DATE_TIME.minusMinutes(i * 37L)));
        }
        return messages;
    }

    public static List<InterviewSchedule> interviewSchedules() {
        List<InterviewSchedule> schedules = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            if (i == 1) {
                schedules.add(new InterviewSchedule(
                        "IS-DEMO-001",
                        "D003",
                        "C001",
                        "S003",
                        "J002",
                        "Java backend technical interview",
                        BASE_DATE_TIME.plusDays(2).withSecond(0).withNano(0),
                        45,
                        "Online",
                        "https://meet.example.com/demo-java-backend",
                        "Prepare one backend project and one MySQL troubleshooting case.",
                        InterviewScheduleStatus.PROPOSED,
                        BASE_DATE_TIME.minusHours(2),
                        BASE_DATE_TIME.minusHours(2)));
                continue;
            }
            int jobIndex = ((i + 11) % DEFAULT_SIZE) + 1;
            LocalDateTime createdAt = BASE_DATE_TIME.minusHours(i);
            schedules.add(new InterviewSchedule(
                    "IS-DEMO-" + "%03d".formatted(i),
                    deliveryId(i),
                    companyId(jobIndex),
                    studentId(i),
                    jobId(jobIndex),
                    interviewTitle(i),
                    BASE_DATE_TIME.plusDays(1 + i % 14).withHour(9 + i % 8).withMinute((i % 2) * 30),
                    30 + (i % 3) * 15,
                    i % 4 == 0 ? CITIES[i % CITIES.length] + " R&D Center" : "Online Tencent Meeting",
                    "https://meet.example.com/campus/" + "%03d".formatted(i),
                    "Prepare one project deep dive, one SQL troubleshooting case, and questions for the interviewer.",
                    SCHEDULE_STATUSES[(i - 1) % SCHEDULE_STATUSES.length],
                    createdAt,
                    createdAt.plusMinutes(15 + i)));
        }
        return schedules;
    }

    public static List<CandidateScreenRecord> candidateScreenRecords() {
        List<CandidateScreenRecord> records = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            int jobIndex = ((i + 11) % DEFAULT_SIZE) + 1;
            List<String> skills = skills(i);
            int score = 60 + (i * 5 % 39);
            records.add(new CandidateScreenRecord(
                    "CS-DEMO-" + "%03d".formatted(i),
                    companyId(jobIndex),
                    deliveryId(i),
                    studentId(i),
                    jobId(jobIndex),
                    i % 7 == 0 ? "DOCX" : "PDF",
                    i % 13 == 0 ? "UNPARSED" : "TEXT_EXTRACTED",
                    820 + i * 16,
                    score,
                    score >= 85 ? "Strong recommend for technical interview" : score >= 72 ? "Recommend with focused follow-up" : "Keep in talent pool",
                    List.of("Core skill " + skills.get(0) + " matches the role", "Project work covers API, data, and deployment evidence"),
                    List.of("Quantified impact is still limited", "Needs deeper explanation of failures and tradeoffs"),
                    List.of(
                            "Explain one " + skills.get(0) + " project with personal ownership",
                            "How would you debug a slow API from logs to SQL plan?",
                            "What tradeoff did you make in database or cache design?"),
                    List.of("Invite to first interview", "Ask for project metrics", "Check internship availability"),
                    true,
                    BASE_INSTANT.minusSeconds(i * 1800L)));
        }
        return records;
    }

    public static List<CandidateScreenTask> candidateScreenTasks() {
        List<CandidateScreenTask> tasks = new ArrayList<>();
        List<CandidateScreenRecord> records = candidateScreenRecords();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            CandidateScreenRecord record = records.get(i - 1);
            CandidateScreenResult result = new CandidateScreenResult(
                    record.deliveryId(),
                    record.studentId(),
                    record.jobId(),
                    record.resumeSourceFormat(),
                    record.resumeParseStatus(),
                    record.resumeParsedTextLength(),
                    record.score(),
                    record.recommendation(),
                    record.strengths(),
                    record.risks(),
                    record.interviewQuestions(),
                    record.nextActions(),
                    true);
            CandidateScreenTaskStatus status = i % 9 == 0 ? CandidateScreenTaskStatus.FAILED : CandidateScreenTaskStatus.COMPLETED;
            Instant createdAt = BASE_INSTANT.minusSeconds(i * 1700L);
            tasks.add(new CandidateScreenTask(
                    "AST-DEMO-" + "%03d".formatted(i),
                    record.deliveryId(),
                    record.companyId(),
                    record.studentId(),
                    resumeId(i),
                    record.jobId(),
                    status,
                    i % 3 == 0 ? CandidateScreenTaskSource.ROCKETMQ : CandidateScreenTaskSource.DEMO,
                    status == CandidateScreenTaskStatus.FAILED ? "Demo task failed for retry validation" : "Demo screening completed",
                    status == CandidateScreenTaskStatus.COMPLETED ? result : null,
                    createdAt,
                    createdAt.plusSeconds(40 + i)));
        }
        return tasks;
    }

    public static List<AiPlanningRecord> planningRecords() {
        List<AiPlanningRecord> records = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            boolean rewrite = i % 2 == 0;
            String studentId = studentId(((i - 1) % 20) + 1);
            String targetRole = TITLES[(i - 1) % TITLES.length];
            records.add(new AiPlanningRecord(
                    "PLAN-DEMO-" + "%03d".formatted(i),
                    studentId,
                    rewrite ? "resume-rewrite" : "career-plan",
                    resumeId(((i - 1) % DEFAULT_SIZE) + 1),
                    targetRole,
                    rewrite ? resumeRewrite(studentId, targetRole, i) : null,
                    rewrite ? null : careerPlan(studentId, targetRole, i),
                    true,
                    BASE_INSTANT.minusSeconds(i * 2100L)));
        }
        return records;
    }

    public static List<AiCallRecord> aiCallRecords() {
        List<AiCallRecord> records = new ArrayList<>();
        String[] operations = {"analyze", "resume-rewrite", "career-plan", "candidate-screening", "interview-feedback", "semantic-search"};
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            boolean success = i % 17 != 0;
            boolean mocked = i % 5 == 0;
            records.add(new AiCallRecord(
                    "AI-CALL-DEMO-" + "%03d".formatted(i),
                    operations[(i - 1) % operations.length],
                    mocked ? "mock-dashscope" : "dashscope",
                    i % 4 == 0 ? "qwen-max" : "qwen-plus",
                    success,
                    mocked,
                    180 + (i * 37L % 1200),
                    320 + i * 9,
                    180 + i * 7,
                    success ? null : "Simulated provider timeout for observability drill",
                    BASE_INSTANT.minusSeconds(i * 900L)));
        }
        return records;
    }

    public static List<InterviewRecord> interviewRecords() {
        List<InterviewRecord> records = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            List<String> skills = skills(i);
            records.add(new InterviewRecord(
                    "IR-DEMO-" + "%03d".formatted(i),
                    studentId(((i - 1) % 20) + 1),
                    TITLES[(i - 1) % TITLES.length],
                    "IQ-DEMO-" + "%03d".formatted(i),
                    "How did you use " + skills.get(0) + " in a real project, and what metric improved after your change?",
                    "I owned the module design, added tests, reviewed logs, and compared before/after latency and error rate.",
                    62 + (i * 3 % 36),
                    "Answer includes project context and technical action; add more numbers for stronger evidence.",
                    List.of("Add traffic or latency data", "Explain a failure case", "Clarify team collaboration boundary"),
                    true,
                    BASE_INSTANT.minusSeconds(i * 1600L)));
        }
        return records;
    }

    public static List<KnowledgeDocument> knowledgeDocuments() {
        List<KnowledgeDocument> documents = new ArrayList<>();
        String[] categories = {"resume", "interview", "delivery", "microservice", "ai", "operations", "security", "company"};
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            String category = categories[(i - 1) % categories.length];
            List<String> skillSet = skills(i);
            documents.add(new KnowledgeDocument(
                    "KB-DEMO-BULK-" + "%03d".formatted(i),
                    knowledgeTitle(category, i),
                    knowledgeContent(category, skillSet, i),
                    category,
                    "seed-demo-bulk",
                    List.of(category, skillSet.get(0), "campus-recruit", "v3.12-demo-data"),
                    i % 5 == 0 ? List.of("ADMIN") : List.of("STUDENT", "COMPANY", "ADMIN"),
                    "demo-seeder",
                    BASE_DATE_TIME.minusMinutes(i * 11L)));
        }
        return documents;
    }

    public static String companyId(int index) {
        return "C" + "%03d".formatted(((index - 1) % 24) + 1);
    }

    public static String studentId(int index) {
        return "S" + "%03d".formatted(index);
    }

    public static String resumeId(int index) {
        return "R" + "%03d".formatted(index);
    }

    public static String jobId(int index) {
        return "J" + "%03d".formatted(index);
    }

    public static String deliveryId(int index) {
        return "D" + "%03d".formatted(index);
    }

    private static String matchId(int index) {
        return "M" + "%03d".formatted(index);
    }

    private static String studentName(int index) {
        return FAMILY_NAMES[(index - 1) % FAMILY_NAMES.length] + " " + GIVEN_NAMES[(index + 3) % GIVEN_NAMES.length];
    }

    private static String companyName(int index) {
        return COMPANIES[(index - 1) % COMPANIES.length];
    }

    private static List<String> skills(int index) {
        return List.of(SKILL_POOLS[(index - 1) % SKILL_POOLS.length]);
    }

    private static JobTemplate jobTemplate(int index) {
        return JOB_TEMPLATES[(index - 1) % JOB_TEMPLATES.length];
    }

    private static JobTemplate job(
            String title,
            String domain,
            String description,
            String aiSummary,
            String... skills) {
        return new JobTemplate(title, domain, description, aiSummary, List.of(skills));
    }

    private static String[] titlesFromTemplates() {
        String[] titles = new String[JOB_TEMPLATES.length];
        for (int i = 0; i < JOB_TEMPLATES.length; i++) {
            titles[i] = JOB_TEMPLATES[i].title();
        }
        return titles;
    }

    private static String[][] skillsFromTemplates() {
        String[][] skills = new String[JOB_TEMPLATES.length][];
        for (int i = 0; i < JOB_TEMPLATES.length; i++) {
            skills[i] = JOB_TEMPLATES[i].skills().toArray(String[]::new);
        }
        return skills;
    }

    private static List<String> projects(int index) {
        List<String> skills = skills(index);
        return List.of(
                "Campus recruitment workflow with " + skills.get(0) + " and " + skills.get(1),
                "Resume diagnosis and delivery analytics dashboard",
                "Three-VM microservice deployment and smoke testing");
    }

    private static String salary(int index) {
        if (index == 1) {
            return "180-260/天";
        }
        int low = 120 + (index % 8) * 20;
        return low + "-" + (low + 80) + "/天";
    }

    private static String businessDomain(int index) {
        return jobTemplate(index).domain();
    }

    private static String interviewTitle(int index) {
        String[] rounds = {"HR screening", "Technical first round", "Project deep-dive", "Final manager interview"};
        return TITLES[(index - 1) % TITLES.length] + " - " + rounds[(index - 1) % rounds.length];
    }

    private static ResumeRewriteResponse resumeRewrite(String studentId, String targetRole, int index) {
        List<String> skillSet = skills(index);
        return new ResumeRewriteResponse(
                studentId,
                resumeId(((index - 1) % DEFAULT_SIZE) + 1),
                targetRole,
                "Candidate highlights " + skillSet.get(0) + ", " + skillSet.get(1)
                        + ", and a campus recruitment project with measurable delivery workflow impact.",
                List.of(
                        "Owned " + skillSet.get(0) + " module design and implemented API, validation, and tests.",
                        "Improved dashboard loading by caching core statistics and reducing repeated service calls."),
                List.of(skillSet.get(0), skillSet.get(1), skillSet.get(2), "project metrics", "deployment evidence"),
                List.of("Add exact QPS or latency numbers", "Clarify personal contribution in team project"),
                List.of("Rewrite first project with STAR", "Add one production-like debugging case", "Prepare a two-minute project pitch"),
                true);
    }

    private static CareerPlanResponse careerPlan(String studentId, String targetRole, int index) {
        List<String> skillSet = skills(index);
        return new CareerPlanResponse(
                studentId,
                targetRole,
                60 + (index * 3 % 38),
                "Focus on " + skillSet.get(0) + " fundamentals, measurable project proof, and interview drills for " + targetRole + ".",
                List.of(
                        new CareerPlanResponse.Milestone("Evidence polish", "Week 1-2", List.of("Quantify project result", "Prepare architecture diagram")),
                        new CareerPlanResponse.Milestone("Technical depth", "Week 3-5", List.of("Review " + skillSet.get(0), "Practice troubleshooting")),
                        new CareerPlanResponse.Milestone("Interview sprint", "Week 6-8", List.of("Mock interview", "Target company delivery"))),
                List.of(skillSet.get(2) + " depth", "System design tradeoffs", "Production troubleshooting"),
                List.of("Review one technical topic", "Update resume evidence", "Submit to three matching roles"),
                List.of("Architecture diagram", "API test report", "Deployment screenshot"),
                List.of("Project deep dive", "SQL and cache troubleshooting", "Behavioral STAR answer"),
                true);
    }

    private static String knowledgeTitle(String category, int index) {
        return switch (category) {
            case "resume" -> "Resume evidence checklist " + index;
            case "interview" -> "Interview follow-up guide " + index;
            case "delivery" -> "Delivery conversion playbook " + index;
            case "microservice" -> "Microservice deployment note " + index;
            case "ai" -> "AI screening prompt policy " + index;
            case "operations" -> "Operations smoke test runbook " + index;
            case "security" -> "Recruitment RBAC audit note " + index;
            default -> "Company campus hiring FAQ " + index;
        };
    }

    private static String knowledgeContent(String category, List<String> skillSet, int index) {
        return "Scenario " + index + " for " + category + ": use " + skillSet.get(0) + ", " + skillSet.get(1)
                + ", and " + skillSet.get(2)
                + " evidence to answer campus recruitment questions. Include owner, timeline, measurable result, risk, and next action. "
                + "This document is seeded for RAG retrieval, citation testing, admin upload comparison, and vector-index fallback validation.";
    }

    private record JobTemplate(
            String title,
            String domain,
            String description,
            String aiSummary,
            List<String> skills) {
    }
}
