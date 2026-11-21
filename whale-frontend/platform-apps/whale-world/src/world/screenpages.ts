/**
 * 屏风页配置（S4 / T4.5）：一殿一页，页里"有哪些列表、列表长什么样"全在这里。
 *
 * <p>和 {@link halls} 的分工一样：这里是数据，渲染在 `screenpage.ts`。加一张列表 =
 * 在 `SCREEN_PAGES` 里加一条，不改渲染器；接口路径、权限码、列口径都跟着后端走，
 * 前端只负责"把这些字段排成表"。</p>
 *
 * <p><b>口径不猜。</b>金额一律 `money`、日期一律 `date`、状态一律 `status`（走
 * {@link PurchaseStatusMap} 转中文与灯色）。S3 的教训是"前端猜百分比"，
 * 屏风页不重复这个错误：猜不出来就按原文显示，宁可不漂亮，也不要显示错的数。</p>
 */

/** 列排版口径：与 overview 契约的 format 同构，另加 date / datetime / status / size 几种表格专用口径 */
export type ColumnFormat =
  | 'text'
  | 'count'
  | 'money'
  | 'percent'
  | 'date'
  | 'datetime'
  | 'status'
  /* 字节数 → "1.2 MB"：文件大小不能套 money（会显示成 ¥1.2），也不能套 count
     （1234567 个字节没人读得出来是 1.2MB） */
  | 'size';

/** 一列 */
export interface ScreenColumn {
  /** 取值字段名（后端 JSON 的 key） */
  key: string;
  /** 表头 */
  label: string;
  /** 列宽，例如 `18%`；缺省按内容分配 */
  width?: string;
  /** 右对齐（数字列） */
  align?: 'left' | 'right';
  /** 排版口径 */
  format?: ColumnFormat;
  /**
   * 列级状态字典；缺省用页面级 {@link ScreenListPageSpec#statusMap}。
   *
   * <p><b>为什么需要列级覆盖</b>：实例与任务的状态码是同一套数字、含义却不同
   * （实例 0 = 运行中，任务 0 = 待办）。只有页面级字典时，详情里的审批链会按实例的
   * 字典翻译任务行——"待办"会被显示成"运行中"，而且不报错。同一张表里混着两种
   * 状态空间时（流程实例 + 它的审批链），必须能逐列指定。</p>
   */
  statusMap?: Record<string, ScreenStatusStyle>;
}

/** 状态码 → 中文 + 灯色 */
export interface ScreenStatusStyle {
  label: string;
  tone: 'good' | 'warn' | 'bad' | 'flat';
}

/** 一张列表页 */
export interface ScreenListPageSpec {
  /** 与 `/overview` 的 `links[].key` 对齐；未在概览里登记的也能单独打开 */
  key: string;
  /** 页名（标签文字），概览下发 label 时以概览为准 */
  title: string;
  /** 列表接口（GET），与 2D 后台同路径 */
  listPath: string;
  /** 详情接口；缺省表示"列表行就是详情"，不二次请求 */
  detailPath?: (id: string) => string;
  /** 接口要求的权限码；缺它就不发请求 */
  permission?: string;
  /** 主键字段名，缺省 `id` */
  idKey?: string;
  /** 关键字查询参数名；缺省不给搜索框 */
  keywordParam?: string;
  /** 搜索框提示语；缺省「搜索…」 */
  searchPlaceholder?: string;
  /** 状态查询参数名；缺省不给状态筛选 */
  statusParam?: string;
  /** 状态筛选项（第一项通常是"全部"，值为空串） */
  statuses?: { value: string; label: string }[];
  /** 状态码 → 中文/灯色 */
  statusMap?: Record<string, ScreenStatusStyle>;
  /** 每页条数，缺省 8（大屏 620px 高，8 行刚好不用滚；再多就靠滚动） */
  pageSize?: number;
  /** 列表列 */
  columns: ScreenColumn[];
  /** 详情页的字段（键值对） */
  detailFields?: ScreenColumn[];
  /** 详情页分录表 */
  lineColumns?: ScreenColumn[];
  /** 详情 JSON 里分录数组的字段名，缺省 `lines` */
  lineSource?: string;
  /** 详情页标题用的字段，缺省第一列 */
  titleKey?: string;
}

/** 采购订单状态：与 `ScmStatus` 一一对应，前端只做展示 */
export const PURCHASE_STATUS: Record<string, ScreenStatusStyle> = {
  DRAFT: { label: '草稿', tone: 'flat' },
  APPROVED: { label: '已审核', tone: 'good' },
  PARTIALLY_RECEIVED: { label: '部分收货', tone: 'warn' },
  RECEIVED: { label: '已收货', tone: 'good' },
  CLOSED: { label: '已关闭', tone: 'flat' },
  CANCELLED: { label: '已取消', tone: 'bad' },
};

/**
 * 户部（ERP）采购订单页。
 *
 * <p>为什么是它：采购订单是 ERP 里"既有主表又有分录"的典型单据，一张页能同时验证
 * 列表滚动、关键字筛选、状态筛选、详情字段、分录表格五件事。做完这一页，
 * 其余列表页基本是复制配置。</p>
 */
export const PURCHASE_ORDER_PAGE: ScreenListPageSpec = {
  key: 'purchase-orders',
  title: '采购订单',
  listPath: '/erp/scm/purchase-order/page',
  detailPath: (id) => `/erp/scm/purchase-order/${id}`,
  permission: 'sys:erp:purchase:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索单号 / 供应商…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'DRAFT', label: '草稿' },
    { value: 'APPROVED', label: '已审核' },
    { value: 'PARTIALLY_RECEIVED', label: '部分收货' },
    { value: 'RECEIVED', label: '已收货' },
    { value: 'CLOSED', label: '已关闭' },
    { value: 'CANCELLED', label: '已取消' },
  ],
  statusMap: PURCHASE_STATUS,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px，
     6 行刚好排满且不需要滚动——列表翻页比"滚到第 7 行"更像台账。 */
  pageSize: 6,
  columns: [
    { key: 'docNo', label: '单号', width: '24%' },
    { key: 'supplierName', label: '供应商', width: '30%' },
    { key: 'docDate', label: '日期', width: '16%', format: 'date' },
    { key: 'totalAmount', label: '金额', width: '16%', format: 'money', align: 'right' },
    { key: 'status', label: '状态', width: '14%', format: 'status' },
  ],
  detailFields: [
    { key: 'docNo', label: '单号' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'supplierName', label: '供应商' },
    { key: 'docDate', label: '单据日期', format: 'date' },
    { key: 'expectedDate', label: '预计到货', format: 'date' },
    { key: 'currency', label: '币种' },
    { key: 'totalAmount', label: '订单总额', format: 'money', align: 'right' },
    { key: 'receivedAmount', label: '已收金额', format: 'money', align: 'right' },
  ],
  lineColumns: [
    { key: 'lineNo', label: '#', width: '8%' },
    { key: 'itemCode', label: '物料编码', width: '24%' },
    { key: 'itemName', label: '物料', width: '28%' },
    { key: 'quantity', label: '数量', width: '14%', format: 'count', align: 'right' },
    { key: 'unitPrice', label: '单价', width: '12%', format: 'money', align: 'right' },
    { key: 'amount', label: '金额', width: '14%', format: 'money', align: 'right' },
  ],
  lineSource: 'lines',
  titleKey: 'docNo',
};

/**
 * 登录结果：与后端 `login_result` 一一对应。
 *
 * <p>「失败」用红灯而不是黄灯：登录失败是枢密殿唯一需要立刻看的信号，
 * 黄灯在夜间监视器上基本看不出来。</p>
 */
export const LOGIN_RESULT_STYLE: Record<string, ScreenStatusStyle> = {
  SUCCESS: { label: '成功', tone: 'good' },
  FAILURE: { label: '失败', tone: 'bad' },
  LOGOUT: { label: '登出', tone: 'flat' },
};

/**
 * 登录历史页（S5 / T5.2）：枢密殿的屏风页。
 *
 * <p><b>为什么这张表不配搜索框。</b>2D 的登录历史接口只有
 * `userId / result / startTime / endTime`，<b>没有 keyword</b>。给一个搜索框、
 * 把关键字发过去、后端默默忽略，是"按了没反应"这类最难查的问题——
 * 宁可少一个框，也不要一个不生效的框。</p>
 *
 * <p>列只留五样：谁、成没成、用什么登的、从哪来、什么时候。
 * 失败原因埋在详情里：撞库排查要看的是"同一 IP 大量失败"，
 * 而不是某一行的具体原因。</p>
 */
export const LOGIN_HISTORY_PAGE: ScreenListPageSpec = {
  key: 'login-history',
  title: '登录历史',
  listPath: '/login-history/v1/page',
  permission: 'sys:login:history:list',
  idKey: 'id',
  statusParam: 'result',
  statuses: [
    { value: '', label: '全部结果' },
    { value: 'SUCCESS', label: '成功' },
    { value: 'FAILURE', label: '失败' },
    { value: 'LOGOUT', label: '登出' },
  ],
  statusMap: LOGIN_RESULT_STYLE,
  pageSize: 6,
  columns: [
    { key: 'username', label: '账号', width: '24%' },
    { key: 'loginResult', label: '结果', width: '14%', format: 'status' },
    { key: 'loginType', label: '方式', width: '14%' },
    { key: 'ip', label: '来源 IP', width: '26%' },
    { key: 'loginTime', label: '时间', width: '22%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'username', label: '账号' },
    { key: 'loginResult', label: '结果', format: 'status' },
    { key: 'loginType', label: '登录方式' },
    { key: 'failureReason', label: '失败原因' },
    { key: 'ip', label: '来源 IP' },
    { key: 'userAgent', label: '客户端' },
    { key: 'loginTime', label: '事件时间', format: 'datetime' },
    { key: 'deviceFingerprint', label: '设备指纹' },
  ],
  titleKey: 'username',
};

/**
 * 支付单状态：与后端 `PayOrderStatus` 一一对应。
 *
 * <p>「待支付」给黄灯而不是灰灯：它不是历史状态，是**还没做完的事**。
 * 一张挂着 3 天的待支付单，比一条已退款记录更值得值班的人看一眼。</p>
 */
export const PAY_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  CREATED: { label: '待支付', tone: 'warn' },
  PAID: { label: '已支付', tone: 'good' },
  CANCELLED: { label: '已取消', tone: 'flat' },
  REFUNDED: { label: '已退款', tone: 'warn' },
};

/**
 * 支付单管理页（S5 / T5.3）：度支殿的屏风页。
 *
 * <p>列只留五样：单号、业务标识、金额、状态、建单时间。渠道与流水号在详情里——
 * 列表是"哪笔单出问题了"，详情才是"这笔单怎么走的"。</p>
 *
 * <p>关键字筛选是**真支持**的（后端 `PayOrderQueryRequest.keyword` 会同时模糊匹配
 * 单号与业务标识），所以这里给搜索框；状态筛选用 `status`，与后端字段同名。</p>
 *
 * <p>不配 `detailPath`：列表行已经带回详情需要的全部字段，再请求一次只会让
 * "点开有半秒空白"，而支付单的详情不会在列表与详情之间变化。</p>
 */
export const PAY_ORDER_PAGE: ScreenListPageSpec = {
  key: 'pay-orders',
  title: '支付单',
  listPath: '/pay/order/page',
  permission: 'sys:pay:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索单号 / 业务标识…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'CREATED', label: '待支付' },
    { value: 'PAID', label: '已支付' },
    { value: 'CANCELLED', label: '已取消' },
    { value: 'REFUNDED', label: '已退款' },
  ],
  statusMap: PAY_STATUS_STYLE,
  pageSize: 6,
  columns: [
    { key: 'orderNo', label: '单号', width: '26%' },
    { key: 'businessKey', label: '业务标识', width: '22%' },
    { key: 'amount', label: '金额', width: '16%', format: 'money', align: 'right' },
    { key: 'status', label: '状态', width: '16%', format: 'status' },
    { key: 'createTime', label: '建单', width: '20%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'orderNo', label: '单号' },
    { key: 'businessKey', label: '业务标识' },
    { key: 'amount', label: '金额', format: 'money' },
    { key: 'currency', label: '币种' },
    { key: 'channel', label: '渠道' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'payTime', label: '支付时间', format: 'datetime' },
    { key: 'channelTradeNo', label: '渠道流水号' },
    { key: 'refundAmount', label: '已退金额', format: 'money' },
    { key: 'createTime', label: '建单时间', format: 'datetime' },
  ],
  titleKey: 'orderNo',
};

/**
 * 文件台账页（S5 / T5.4）：藏书殿的屏风页。
 *
 * <p>列只留五样：文件名、桶、大小、类型、上传时间。objectKey / md5 / 上传人放详情——
 * 列表要回答的是"这一批东西是什么"，不是"这个对象落在哪个键上"。</p>
 *
 * <p>关键字筛选是**真支持**的：后端 `FilePageRequest.originalName` 会按原始文件名模糊匹配。
 * **不给状态筛选**：`FilePageRequest` 里没有 status 参数（文件状态 1/0/2 只在管理端用），
 * 给一个不生效的下拉比少一个框更难查。</p>
 *
 * <p>不配 `detailPath`：列表行已经带回详情需要的全部字段，再请求一次只会让点开有半秒空白。</p>
 */
export const FILE_ASSET_PAGE: ScreenListPageSpec = {
  key: 'files',
  title: '文件台账',
  listPath: '/file/v1/page',
  permission: 'sys:file:list',
  idKey: 'id',
  keywordParam: 'originalName',
  searchPlaceholder: '搜索文件名…',
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'originalName', label: '文件名', width: '34%' },
    { key: 'bucket', label: '桶', width: '18%' },
    { key: 'fileSize', label: '大小', width: '16%', format: 'size', align: 'right' },
    { key: 'contentType', label: '类型', width: '16%' },
    { key: 'createTime', label: '上传', width: '16%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'originalName', label: '原始文件名' },
    { key: 'fileName', label: '存储文件名' },
    { key: 'bucket', label: '桶' },
    { key: 'objectKey', label: '对象键' },
    { key: 'fileSize', label: '大小', format: 'size' },
    { key: 'contentType', label: '类型' },
    { key: 'md5', label: 'MD5' },
    { key: 'uploaderId', label: '上传人 ID' },
    { key: 'createTime', label: '上传时间', format: 'datetime' },
  ],
  titleKey: 'originalName',
};

/**
 * 任务状态：`job_info.status` 只有两位（1 运行中 / 0 已停止），前端只做展示。
 *
 * <p>「已停止」给 flat 而不是 bad：停用是**主动决定**，不是故障。
 * 给红灯会让值班的人以为任务挂了，然后去查一个本来就不该在跑的任务。</p>
 */
export const JOB_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  '1': { label: '运行中', tone: 'good' },
  '0': { label: '已停止', tone: 'flat' },
};

/**
 * 定时任务页（S5 / T5.5）：时辰殿的屏风页。
 *
 * <p>列只留五样：任务、分组、计划、状态、下次。handler / 执行器 / 重试策略放详情——
 * 列表要回答的是"哪些任务在跑、按什么节奏跑"，不是"这个 Bean 叫什么"。</p>
 *
 * <p>关键字与状态**两个筛选都给**：`JobInfoQueryRequest` 真支持 `keyword`（任务名模糊）
 * 与 `status`（1/0），给了就真发。状态值是字符串 `'1'`/`'0'`，与行里的数字用
 * `String()` 对齐——发成 `"RUNNING"` 这种自造枚举后端会当成不匹配，静默返回全部。</p>
 *
 * <p>不配 `detailPath`：列表行已带回详情需要的全部字段，再请求一次只会让点开有半秒空白。</p>
 */
export const JOB_INFO_PAGE: ScreenListPageSpec = {
  key: 'jobs',
  title: '定时任务',
  listPath: '/job/v1/info/page',
  permission: 'sys:job:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索任务名…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: '1', label: '运行中' },
    { value: '0', label: '已停止' },
  ],
  statusMap: JOB_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'jobName', label: '任务', width: '30%' },
    { key: 'jobGroup', label: '分组', width: '16%' },
    { key: 'cronExpression', label: '计划', width: '22%' },
    { key: 'status', label: '状态', width: '14%', format: 'status' },
    { key: 'nextFireTime', label: '下次', width: '18%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'jobName', label: '任务名' },
    { key: 'jobGroup', label: '分组' },
    { key: 'handlerBean', label: '执行器 Bean' },
    { key: 'executorApp', label: '执行器应用' },
    { key: 'jobCode', label: '任务编码' },
    { key: 'cronExpression', label: 'Cron 表达式' },
    { key: 'misfirePolicy', label: '错过策略' },
    { key: 'maxRetry', label: '重试上限' },
    { key: 'failCount', label: '累计失败' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'nextFireTime', label: '下次执行', format: 'datetime' },
    { key: 'remark', label: '备注' },
  ],
  titleKey: 'jobName',
};

/**
 * 流程实例状态：`process_instance.status`，0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销。
 *
 * <p>「运行中」给 warn 而不是 good：它不是一种健康状态，而是"还没批完"——
 * 绿灯会让人以为这一屏全都没问题。</p>
 */
export const INSTANCE_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  '0': { label: '运行中', tone: 'warn' },
  '1': { label: '已通过', tone: 'good' },
  '2': { label: '已驳回', tone: 'bad' },
  '3': { label: '已撤销', tone: 'flat' },
};

/**
 * 审批任务状态：`process_task.status`，0 待办 / 1 已同意 / 2 已驳回 / 3 已取消。
 *
 * <p><b>与实例状态码值相同、含义不同</b>（实例 0 = 运行中，任务 0 = 待办）。详情里的
 * 审批链必须用这份字典，靠列级 {@link ScreenColumn#statusMap} 覆盖页面级字典——
 * 否则"待办"会被译成"运行中"，而且不报错。</p>
 */
export const TASK_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  '0': { label: '待办', tone: 'warn' },
  '1': { label: '已同意', tone: 'good' },
  '2': { label: '已驳回', tone: 'bad' },
  '3': { label: '已取消', tone: 'flat' },
};

/**
 * 流程实例页（S5 / T5.6）：政事殿的屏风页。
 *
 * <p>列只留五样：标题、流程、发起人、状态、发起时间。列表回答的是"哪些奏折在途"，
 * 不是"这条单子的每个字段"。</p>
 *
 * <p>关键字参数是 {@code title}（模糊匹配），不是 {@code keyword}——
 * `ProcessInstanceQueryRequest` 只有 {@code title / status / definitionCode / initiator}
 * 四个条件。发一个后端不认的参数，Spring 会静默忽略，搜索框就成了摆设。</p>
 *
 * <p>详情配 {@code detailPath}：实例详情接口会把该实例的审批链（{@code tasks}）一并带回，
 * 这是整个页面最值得看的东西——它卡在谁那里、批注写了什么。审批链用
 * {@link TASK_STATUS_STYLE} 翻译，不能沿用实例字典。</p>
 */
export const PROCESS_INSTANCE_PAGE: ScreenListPageSpec = {
  key: 'instances',
  title: '流程实例',
  listPath: '/workflow/v1/instance/page',
  detailPath: (id) => `/workflow/v1/instance/${id}`,
  permission: 'sys:workflow:instance:list',
  idKey: 'id',
  keywordParam: 'title',
  searchPlaceholder: '搜索流程标题…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: '0', label: '运行中' },
    { value: '1', label: '已通过' },
    { value: '2', label: '已驳回' },
    { value: '3', label: '已撤销' },
  ],
  statusMap: INSTANCE_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'title', label: '流程标题', width: '34%' },
    { key: 'definitionCode', label: '流程', width: '20%' },
    { key: 'initiator', label: '发起人', width: '16%' },
    { key: 'status', label: '状态', width: '14%', format: 'status' },
    { key: 'createTime', label: '发起', width: '16%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'title', label: '流程标题' },
    { key: 'definitionCode', label: '流程编码' },
    { key: 'definitionVersion', label: '定义版本' },
    { key: 'businessKey', label: '业务键' },
    { key: 'initiator', label: '发起人' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'currentNodeKey', label: '当前节点' },
    { key: 'createTime', label: '发起时间', format: 'datetime' },
    { key: 'updateTime', label: '最近变更', format: 'datetime' },
  ],
  lineColumns: [
    { key: 'nodeName', label: '审批节点', width: '26%' },
    { key: 'assignee', label: '审批人', width: '18%' },
    { key: 'status', label: '结果', width: '14%', format: 'status', statusMap: TASK_STATUS_STYLE },
    { key: 'comment', label: '意见', width: '26%' },
    { key: 'updateTime', label: '时间', width: '16%', format: 'datetime' },
  ],
  lineSource: 'tasks',
  titleKey: 'title',
};

/**
 * 殿的连通状态：与后端 {@code HubHallState} 一一对应，前端只做展示。
 *
 * <p>「无凭据」单独一档而不是并进「未通」：前者要去要权限，后者要去查服务，
 * 混成一档会让值班的人先翻半小时日志才发现是自己没权限。</p>
 */
export const HUB_HALL_STATE: Record<string, ScreenStatusStyle> = {
  live: { label: '通畅', tone: 'good' },
  stale: { label: '陈旧', tone: 'warn' },
  degraded: { label: '未通', tone: 'bad' },
  denied: { label: '无凭据', tone: 'flat' },
};

/**
 * 数枢殿总控页（S5 / T5.1）：把所有在册各殿的概览摆成一张表。
 *
 * <p>为什么这张表值得单独一页：总控的价值不在"再多一块看板"，而在
 * <b>一眼看出哪座殿没数、为什么没数</b>。所以列只有四列——殿、主指标、状态、说明，
 * 说明列直接写"问询超时""无此殿凭据"，而不是让人点进每一座殿去猜。</p>
 *
 * <p>数据来自 {@code /monitor/overview/halls}（与 {@code /monitor/overview} 同一次聚合），
 * 分页与关键字都由服务端生效：前端把 {@code current/size/keyword} 原样发出去。</p>
 */
export const HUB_HALLS_PAGE: ScreenListPageSpec = {
  key: 'hall-health',
  title: '八方宫署',
  listPath: '/monitor/overview/halls',
  detailPath: (id) => `/monitor/overview/halls/${id}`,
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索殿名…',
  statusParam: 'state',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'live', label: '通畅' },
    { value: 'stale', label: '陈旧' },
    { value: 'degraded', label: '未通' },
    { value: 'denied', label: '无凭据' },
  ],
  statusMap: HUB_HALL_STATE,
  /* 6 行/页：屏风是 620px 高，KPI 条 + 环形图之后只剩约 230px，6 行刚好排满 */
  pageSize: 6,
  columns: [
    { key: 'name', label: '殿', width: '20%' },
    { key: 'headline', label: '主指标', width: '38%' },
    { key: 'state', label: '状态', width: '14%', format: 'status' },
    { key: 'note', label: '说明', width: '28%' },
  ],
  detailFields: [
    { key: 'name', label: '殿' },
    { key: 'state', label: '状态', format: 'status' },
    { key: 'subtitle', label: '口径' },
    { key: 'updatedLabel', label: '数据时刻' },
    { key: 'note', label: '说明' },
  ],
  lineColumns: [
    { key: 'no', label: '#', width: '8%' },
    { key: 'label', label: '指标', width: '62%' },
    { key: 'value', label: '数值', width: '30%', align: 'right' },
  ],
  lineSource: 'lines',
  titleKey: 'name',
};

/**
 * 屏风页登记表：key = 概览 `links[].key`。
 *
 * <p>概览没下发（或概览整个 403）时，这份表就是唯一入口——屏风页不依赖概览活着，
 * 只是少了 KPI 与图表那一层。</p>
 */
export const SCREEN_PAGES: Record<string, ScreenListPageSpec> = {
  [PURCHASE_ORDER_PAGE.key]: PURCHASE_ORDER_PAGE,
  [HUB_HALLS_PAGE.key]: HUB_HALLS_PAGE,
  [LOGIN_HISTORY_PAGE.key]: LOGIN_HISTORY_PAGE,
  [PAY_ORDER_PAGE.key]: PAY_ORDER_PAGE,
  [FILE_ASSET_PAGE.key]: FILE_ASSET_PAGE,
  [JOB_INFO_PAGE.key]: JOB_INFO_PAGE,
  [PROCESS_INSTANCE_PAGE.key]: PROCESS_INSTANCE_PAGE,
};
