'use strict'
// Every person and every experience in this catalog is synthetic teaching data.
const names = ['林晓雨','陈宇航','周可欣','许亦辰','苏文静','刘子昂','李思远','黄佳宁','王清越','赵铭轩','孙语桐','郑嘉禾','吴安然','何景行','张雨晨','罗依诺','梁书涵','宋知夏','唐明哲','谢芷晴']
const schools = ['浙江工业大学','杭州电子科技大学','南京工程学院','成都信息工程大学','武汉工程大学','江西理工大学','重庆科技大学','山东工商学院','广东技术师范大学','湖南工业大学','浙江机电职业技术大学','苏州职业技术大学']
const cities = ['杭州','南京','成都','武汉','广州','上海','苏州','深圳']
const roles = [
  { key:'Java', title:'Java 后端开发实习生', major:'软件工程', skills:['Java','MySQL','Git','Spring Boot','Redis','JUnit'], courses:['Java程序设计','数据结构','数据库原理','软件测试'], projects:[
    ['课程选课服务','实现课程列表、选课和退课接口，负责请求参数校验','Java、MySQL，使用唯一约束防止重复选课','完成正常选课、重复选课及名额不足的测试；并发抢课尚未实现',['Java','MySQL']],
    ['实验室设备预约','设计预约表与时段冲突检查，独立编写两个接口','Spring Boot、MySQL，使用事务处理预约与取消','课程验收可以完成预约和取消；尚未验证跨时区场景',['Java','Spring Boot','MySQL']],
    ['校园失物登记','整理接口文档并实现登记、筛选和关闭记录','Java、MySQL，按物品类型和日期组合查询','用人工样例核对筛选结果，发现并修正日期边界错误',['Java','MySQL']],
    ['二手书交换后台','负责书籍列表与详情接口，补充服务层单元测试','Spring Boot、JUnit、Git，按分支提交修改','覆盖空列表和不存在书籍等情况；项目仅在本地运行',['Java','Spring Boot','JUnit','Git']],
    ['图书借阅练习','实现借阅与归还状态流转，记录异常处理思路','Java、MySQL，事务控制借阅状态更新','手工验证重复归还和库存不足；没有线上用户数据',['Java','MySQL']],
    ['通知缓存小实验','在课程接口增加本地测试用的通知查询缓存','Spring Boot、Redis，缓存过期后重新读取数据库','记录正常过期和不存在键的返回值；缓存一致性仍需补充验证',['Java','Spring Boot','Redis']]
  ]},
  { key:'前端', title:'前端开发实习生', major:'计算机科学与技术', skills:['HTML','CSS','JavaScript','Git','Vue','TypeScript'], courses:['Web程序设计','人机交互','计算机网络','软件工程'], projects:[
    ['校园活动报名页','负责报名表单、错误提示和移动端布局','HTML、CSS、JavaScript，使用原生表单校验','验证空值、重复点击和窄屏展示；尚未接入正式后台',['HTML','CSS','JavaScript']],
    ['课程任务看板','拆分任务卡片和筛选组件，处理请求加载与失败','Vue、JavaScript、Git，组件属性与事件传递','完成课程展示，网络失败时可重试；未做大数据性能测试',['Vue','JavaScript','Git']],
    ['社团招新页面','制作社团介绍、时间表和报名入口，调整键盘操作顺序','HTML、CSS、JavaScript，语义化标签和响应式样式','同学试用发现小屏按钮难点，修改后可正常操作',['HTML','CSS','JavaScript']],
    ['实训商品列表','负责搜索和详情页，整理接口类型与状态','Vue、TypeScript，显式定义请求和响应类型','覆盖无结果、接口异常和慢网络；支付功能未开发',['Vue','TypeScript']],
    ['图书检索页面','实现检索、分页和查询条件保持','JavaScript、CSS，提交前去除输入首尾空格','验证翻页返回和中文检索；暂无访问量数据',['JavaScript','CSS']],
    ['个人作品目录','整理作品卡片、图片替代文字和链接说明','HTML、CSS、Git，静态页面布局','在电脑和手机上检查页面；作品地址暂未公开',['HTML','CSS','Git']]
  ]},
  { key:'运营', title:'内容与用户运营实习生', major:'市场营销', skills:['内容策划','Excel','数据分析','用户运营','活动策划','文案'], courses:['消费者行为学','市场调研','统计学','新媒体运营'], projects:[
    ['读书社群共读活动','整理报名需求，撰写活动说明并跟进群内提问','用户运营、Excel，按报名与实际参加分别记录','完成一次共读，保留匿名报名表；没有证明长期留存提升',['用户运营','Excel']],
    ['校园公众号栏目练习','负责选题、访谈整理和两篇初稿的修改','内容策划、文案，按受众问题组织标题和段落','初稿交社团审核，未公开发布；没有阅读转化数据',['内容策划','文案']],
    ['旧书交换活动复盘','负责登记流程、现场指引和复盘材料','活动策划、Excel，核对登记与交换完成记录','梳理重复登记和分类不清问题，提出下次改进步骤',['活动策划','Excel']],
    ['校园服务需求调研','设计访谈提纲，整理匿名反馈与需求分类','数据分析、Excel，区分有效反馈与重复反馈','得到样本内的高频问题；样本较小，不能外推全部学生',['数据分析','Excel']],
    ['社团招新内容排期','根据招新节点整理文案、素材清单与发布顺序','内容策划、活动策划、文案，交叉检查报名时间','按时完成素材交付；报名数量受其他渠道影响，未计算归因',['内容策划','活动策划','文案']],
    ['报名渠道数据练习','按渠道整理匿名报名表，计算各渠道报名占比','Excel、数据分析，统一时间窗口并去重','确认记录一致，无法获得曝光数据，因此未计算转化率',['Excel','数据分析']]
  ]}
]
const label = '匿名合成教学样例，不代表真实个人'
function source(id, quote) { return {kind:'TEST_FIXTURE',sourceId:id,quote,confirmed:true,assessment:label} }
function buildStudents(count=60) {
  if (!Number.isInteger(count) || count<1 || count>60) throw new Error('students must be between 1 and 60')
  return Array.from({length:count},(_,i)=>{
    const serial=String(i+1).padStart(2,'0'), username='seed_student_'+serial, role=roles[i%3]
    const stage=Math.floor(i/3), name=names[stage%names.length], city=cities[stage%cities.length], school=schools[stage%schools.length]
    const degree=stage===10||stage===11?'专科':stage===16?'硕士':'本科', graduationYear=stage%5===4?2028:2027
    const start=degree==='专科'||degree==='硕士'?'2024-09-01':graduationYear===2028?'2024-09-01':'2023-09-01'
    const end=graduationYear+'-06-30', skillCount=stage===0?2:Math.min(6,3+stage%4), skills=role.skills.slice(0,skillCount)
    const experiences=[]
    // First cohort has little material; later cohorts include one or two concrete pieces of work.
    if(stage>0) {
      const p=role.projects[(stage-1)%role.projects.length], id=username+'-project-1'
      p[4].forEach(s=>{if(!skills.includes(s))skills.push(s)})
      experiences.push({id,type:'PROJECT',title:p[0],organization:'校内课程小组',startDate:'2026-03-01',endDate:'2026-06-15',role:'小组成员',actions:p[1],methods:p[2],results:p[3],skills:p[4],links:[],source:source(id,p.slice(0,4).join('；')),confirmed:true})
    }
    if(stage%4===3) {
      const id=username+'-activity-1',actions=role.key==='运营'?'整理报名名单、答疑与现场签到':'维护社团活动登记页面，协助整理活动信息'
      experiences.push({id,type:'CAMPUS',title:'社团活动协作',organization:'校内学生社团',startDate:'2025-09-10',endDate:'2025-12-20',role:'活动协助',actions,methods:'根据负责人分工记录问题和处理结果',results:'完成分工，尚未量化个人贡献；没有商业运营成果',skills:[],links:[],source:source(id,actions),confirmed:true})
    }
    if(stage>=12&&stage%3===0) {
      const id=username+'-internship-1',actions=role.key==='Java'?'协助修复参数校验问题，整理接口测试记录':role.key==='前端'?'协助维护内部表单页面，按反馈调整错误提示':'协助整理素材、报名表和用户反馈，不负责预算决策'
      experiences.push({id,type:'INTERNSHIP',title:'暑期实习协作',organization:'本地小型团队（合成组织）',startDate:'2026-07-01',endDate:'2026-08-31',role:'实习协助',actions,methods:'由负责人审阅后合入或发布，保留修改记录',results:'按要求完成分配工作，没有独立负责业务指标',skills:[],links:[],source:source(id,actions),confirmed:true})
    }
    const educationId=username+'-edu', profileData={basics:{name,phone:'',email:username+'@example.invalid',city,portfolioUrl:'',photoObjectKey:''},
      education:[{id:educationId,school,major:role.major,degree,startDate:start,endDate:end,graduationDate:end,courses:role.courses,notes:label+'；未填写绩点及排名',source:source(educationId,school+' '+role.major+' '+degree)}],
      skills:skills.map((s,j)=>({id:username+'-skill-'+j,name:s,source:source(username+'-skill-'+j,s+'：学生样例自报，不能据此认定掌握')})),experiences,
      credentials:stage%6===5?[{id:username+'-cert',title:'大学英语四级',date:'2025-12-01',description:'匿名教学样例中的证书字段，未附真实证书',source:source(username+'-cert','大学英语四级')}]:[],
      availability:{cities:[city, cities[(stage+2)%cities.length]],earliestStartDate:stage%2===0?'2026-11-02':'2027-01-11',daysPerWeek:stage%4+2,continuousMonths:stage%3+2,graduationDate:end}}
    const resumeText=[label,name+' | '+role.title,'教育：'+school+' '+role.major+' '+degree+' '+start+' 至 '+end,'技能：'+skills.join('、'),...experiences.map(e=>'项目：'+e.title+'。个人职责：'+e.actions+'。方法：'+e.methods+'。验证与结果：'+e.results),'实习安排：'+profileData.availability.earliestStartDate+'起，每周'+profileData.availability.daysPerWeek+'天，连续'+profileData.availability.continuousMonths+'个月','缺少信息：联系方式为演示占位，作品链接未公开，绩点未填写'].join('\n')
    return {username,roleKey:role.key,targetRole:role.title,studentProfile:{displayName:name+'（匿名样例）',school,major:role.major,skills,targetPosition:role.title},profileData,resumeText,
      studySettings:{weeklyHours:[2,4,6,10][stage%4],durationWeeks:[2,4,8][stage%3],startDate:['2026-09-28','2026-10-05','2026-10-12'][stage%3],studyDays:['MONDAY','WEDNESDAY','FRIDAY'],dailyMinutesCap:[60,90,120,240][stage%4]},
      answer:experiences.length?'在'+experiences[0].title+'中，我负责'+experiences[0].actions+'。采用'+experiences[0].methods+'。验证与结果是'+experiences[0].results+'。我没有提供商业规模或提升比例，遇到超出实践范围的问题，需要先补充实验和依据。':'我目前主要完成课程基础练习，没有独立项目或实习经历。对这个问题只能解释我学过的基础内容，暂时无法提供个人实践结果，下一步需要做小实验验证。'}
  })
}
module.exports={buildStudents,roles,label}
