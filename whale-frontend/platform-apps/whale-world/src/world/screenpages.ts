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
  /** 取值字段名（后端 JSON 的 key）；支持 `a.b` 取嵌套字段（IM 的 lastMessage.content） */
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
   * 空值显示的文字，缺省 `—`。
   *
   * <p>单聊会话的标题为空是<b>平台约定</b>（2D 里显示"未命名会话"），不是数据缺失；
   * 一列 `—` 会让人以为接口没返回。这个字段让口径跟着列走，而不是让渲染器猜。</p>
   */
  emptyText?: string;
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
  /**
   * 接口返回的是"整份数组、不分页"时置 true：一次取回后前端切页。
   *
   * <p><b>为什么要这一档。</b>IM 的会话列表是普通数组（不是 `{records,total}`），
   * 服务端也没有分页参数。渲染器按后端分页的写法取回整份后不再切片，翻页按钮
   * 会显示"2/3"却仍然列同样 30 行——看着像坏了，而且不报错。置 true 后
   * 请求不再带 `current/size`，翻页只切本地数组。</p>
   */
  clientPaged?: boolean;
  /**
   * 页码参数名，缺省 `current`。
   *
   * <p><b>为什么要能改。</b>多数分页接口读 {@code current/size}（平台统一的
   * {@code PageQuery}），但舆图司的围栏接口读 {@code page/size}。字段名对不上时
   * 服务端<b>不报错</b>，直接按默认值返回第 1 页——屏风上的页码从 1 变到 3，
   * 表格却一直是同样的行。这类"看着在翻页、其实没翻"的坑只能靠配置对齐。</p>
   */
  pageParam?: string;
  /** 每页条数参数名，缺省 `size` */
  sizeParam?: string;
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
  listPath: '/erp/v1/scm/purchase-order/page',
  detailPath: (id) => `/erp/v1/scm/purchase-order/${id}`,
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
  listPath: '/pay/v1/order/page',
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
 * 表单定义状态：`form_definition.status`，1 正常 / 0 禁用。
 *
 * <p>禁用给 flat 而不是 bad：停用是主动决定（这张表不收了），不是故障。
 * 给红灯会让人每次路过都想点进去查一遍。</p>
 */
export const FORM_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  '1': { label: '正常', tone: 'good' },
  '0': { label: '禁用', tone: 'flat' },
};

/**
 * 表单定义页（S5 / T5.7）：考功殿的屏风页。
 *
 * <p>列只留五样：表单名、编码、分类、状态、最近更新。这一屏回答的是"有哪些表在收数、
 * 哪张表刚改过"，不是"某个字段怎么配"。</p>
 *
 * <p>关键字参数是 {@code name}（模糊匹配），不是 {@code keyword}——
 * `FormQueryRequest` 只有 {@code name / code / status / category} 四个条件，
 * 发一个后端不认的参数会被 Spring 静默忽略，搜索框就成了摆设。</p>
 *
 * <p>详情配 {@code detailPath}：定义详情会把字段配置（{@code fields}）一并带回，
 * 这是列表行里没有的信息——这张表到底有哪几个字段、哪些不在表单上显示。
 * 控件类型保持后端原值（text / select / date…）：前端没有一份可信的中文映射，
 * 猜一个不如照原文写。</p>
 */
export const FORM_DEFINITION_PAGE: ScreenListPageSpec = {
  key: 'forms',
  title: '表单定义',
  listPath: '/form/v1/definition/page',
  detailPath: (id) => `/form/v1/definition/${id}`,
  permission: 'sys:form:list',
  idKey: 'id',
  keywordParam: 'name',
  searchPlaceholder: '搜索表单名称…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: '1', label: '正常' },
    { value: '0', label: '禁用' },
  ],
  statusMap: FORM_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'name', label: '表单', width: '32%' },
    { key: 'code', label: '编码', width: '24%' },
    { key: 'category', label: '分类', width: '16%' },
    { key: 'status', label: '状态', width: '12%', format: 'status' },
    { key: 'updateTime', label: '更新', width: '16%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'name', label: '表单名称' },
    { key: 'code', label: '表单编码' },
    { key: 'category', label: '分类' },
    { key: 'version', label: '版本' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'tags', label: '标签' },
    { key: 'remark', label: '备注' },
    { key: 'createTime', label: '创建时间', format: 'datetime' },
    { key: 'updateTime', label: '更新时间', format: 'datetime' },
  ],
  lineColumns: [
    { key: 'fieldLabel', label: '字段', width: '38%' },
    { key: 'widgetType', label: '控件', width: '22%' },
    { key: 'sortNo', label: '排序', width: '16%', align: 'right', format: 'count' },
    { key: 'status', label: '状态', width: '24%', format: 'status' },
  ],
  lineSource: 'fields',
  titleKey: 'name',
};

/**
 * 消息记录状态：`msg_record.status`，由该消息全部投递结果聚合而来。
 *
 * <p>「待投递 / 投递中」给 warn 而不是 flat：它们不是"进行中"这么中性的事——
 * 停在 PENDING 意味着业务方已经收到成功响应、收件人却什么都没收到，
 * 这是消息服务最典型的静默失效。中性色会让它和"已发送"看起来一样。</p>
 */
export const MESSAGE_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  PENDING: { label: '待投递', tone: 'warn' },
  DISPATCHING: { label: '投递中', tone: 'warn' },
  SENT: { label: '已发送', tone: 'flat' },
  DELIVERED: { label: '已送达', tone: 'good' },
  PARTIAL: { label: '部分失败', tone: 'warn' },
  FAILED: { label: '失败', tone: 'bad' },
  MOCKED: { label: '演示', tone: 'flat' },
  SKIPPED: { label: '已跳过', tone: 'flat' },
  EXPIRED: { label: '过期未发', tone: 'bad' },
};

/**
 * 投递状态：`msg_delivery.status`，与消息状态<b>不是同一套码值</b>。
 *
 * <p>详情里的投递明细必须用这份字典，靠列级 {@link ScreenColumn#statusMap} 覆盖
 * 页面级字典：{@code READ}（已读）与 {@code BOUNCED}（退信）在消息状态里根本不存在，
 * 用页面级字典会把它们原样显示成英文，而且不报错。</p>
 */
export const DELIVERY_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  SENT: { label: '已提交', tone: 'flat' },
  DELIVERED: { label: '已送达', tone: 'good' },
  READ: { label: '已读', tone: 'good' },
  BOUNCED: { label: '退信', tone: 'bad' },
  FAILED: { label: '失败', tone: 'bad' },
  SKIPPED: { label: '已跳过', tone: 'flat' },
  MOCKED: { label: '演示', tone: 'flat' },
  EXPIRED: { label: '过期', tone: 'bad' },
};

/**
 * 消息记录页（S5 / T5.8）：通政殿的屏风页。
 *
 * <p>列只留五样：标题、业务类型、渠道、状态、创建时间。这一屏回答的是"最近这些信送给谁了、
 * 哪些没送出去"，不是"某条消息的正文写了什么"。</p>
 *
 * <p><b>没有搜索框</b>：`MessageRecordQueryRequest` 只有 {@code bizType / bizId / status /
 * channel} 四个条件，没有关键字（标题模糊匹配）——配一个 {@code keywordParam} 也会被
 * Spring 静默忽略，搜索框就成了纯摆设。宁可不给，也不给一个按了没反应的框。</p>
 *
 * <p>状态筛选项按"问题优先"排：失败、部分失败、待投递排在最前，正常态靠后。
 * 排查的人十次里有九次是来找坏消息的，不该让他每次先滚过五个正常态。</p>
 *
 * <p>详情配 {@code detailPath}：记录详情会把各渠道投递明细（{@code deliveries}）一并带回，
 * 这是列表行里没有的信息——一条消息发了几个渠道、每个渠道成没成、失败原因是什么。
 * 正文 {@code content} 不进详情：它是 TEXT，长度不受控，塞进 620px 的屏风会把版面顶穿，
 * 要看全文去 2D 后台。</p>
 */
export const MESSAGE_RECORD_PAGE: ScreenListPageSpec = {
  key: 'messages',
  title: '消息记录',
  listPath: '/message/v1/record/page',
  detailPath: (id) => `/message/v1/record/${id}`,
  permission: 'sys:message:record:list',
  idKey: 'id',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'FAILED', label: '失败' },
    { value: 'PARTIAL', label: '部分失败' },
    { value: 'PENDING', label: '待投递' },
    { value: 'DISPATCHING', label: '投递中' },
    { value: 'EXPIRED', label: '过期未发' },
    { value: 'DELIVERED', label: '已送达' },
    { value: 'SENT', label: '已发送' },
    { value: 'SKIPPED', label: '已跳过' },
    { value: 'MOCKED', label: '演示' },
  ],
  statusMap: MESSAGE_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'title', label: '标题', width: '34%' },
    { key: 'bizType', label: '业务类型', width: '20%' },
    { key: 'channels', label: '渠道', width: '16%' },
    { key: 'status', label: '状态', width: '14%', format: 'status' },
    { key: 'createTime', label: '创建', width: '16%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'title', label: '标题' },
    { key: 'bizType', label: '业务类型' },
    { key: 'bizId', label: '业务标识' },
    { key: 'templateCode', label: '模板编码' },
    { key: 'channels', label: '渠道' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'priority', label: '优先级' },
    { key: 'createTime', label: '创建时间', format: 'datetime' },
  ],
  lineColumns: [
    { key: 'channel', label: '渠道', width: '14%' },
    { key: 'recipient', label: '接收人', width: '24%' },
    {
      key: 'status',
      label: '结果',
      width: '14%',
      format: 'status',
      statusMap: DELIVERY_STATUS_STYLE,
    },
    { key: 'errorMessage', label: '失败原因', width: '28%' },
    { key: 'sentAt', label: '投递时间', width: '20%', format: 'datetime' },
  ],
  lineSource: 'deliveries',
  titleKey: 'title',
};

/**
 * 审计业务结果：`audit_log.biz_status`，1 成功 / 0 失败。
 *
 * <p>失败给 bad 而不是 flat：起居殿这一屏是给审计管理员查"谁做过什么、有没有失败"的，
 * 失败记录混在一片中性灰里等于没筛出来。</p>
 */
export const AUDIT_RESULT_STYLE: Record<string, ScreenStatusStyle> = {
  '1': { label: '成功', tone: 'good' },
  '0': { label: '失败', tone: 'bad' },
};

/**
 * 审计日志页（S5 / T5.9）：起居殿的屏风页，只读。
 *
 * <p>列只留五样：操作时间、操作人、模块、操作类型、结果。这一屏回答的是"最近谁在系统里
 * 做了什么、哪些失败了"，不是"某条操作的完整请求报文"——那是 2D 审计中心的活。</p>
 *
 * <p><b>关键字参数是 `username` 不是 `keyword`</b>：`AuditLogQueryRequest` 只有
 * {@code username}（模糊匹配）这个文本条件，配 `keyword` 会被 Spring 静默忽略。</p>
 *
 * <p><b>状态筛选走 `bizStatus`</b>（1/0），接口收 Integer；这里传字符串由 Spring 转换。
 * 不配 `detailPath`：详情接口 {@code /audit/v1/log/{id}} 返回的字段与列表行一致（同一个
 * {@code AuditLogVO}），再请求一次拿不到新信息，反而会在 620px 大屏上把 requestParams / resultBody
 * 两个 TEXT 顶进来撑破版面。要看完整报文去 2D 审计中心。</p>
 */
export const AUDIT_LOG_PAGE: ScreenListPageSpec = {
  key: 'audit-logs',
  title: '审计日志',
  listPath: '/audit/v1/log/page',
  permission: 'sys:audit:log:list',
  idKey: 'id',
  keywordParam: 'username',
  searchPlaceholder: '搜索操作人…',
  statusParam: 'bizStatus',
  statuses: [
    { value: '', label: '全部结果' },
    { value: '0', label: '失败' },
    { value: '1', label: '成功' },
  ],
  statusMap: AUDIT_RESULT_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'createTime', label: '操作时间', width: '22%', format: 'datetime' },
    { key: 'username', label: '操作人', width: '18%' },
    { key: 'module', label: '模块', width: '22%' },
    { key: 'operateType', label: '操作', width: '18%' },
    { key: 'bizStatus', label: '结果', width: '20%', format: 'status' },
  ],
  titleKey: 'operation',
};

/**
 * 工单状态：与后端 `MesWorkOrder.status` 一一对应，前端只做展示。
 *
 * <p>「暂停」给黄灯而不是灰灯：暂停的工单还占着工位与物料，它跟"已完成"完全不同——
 * 灰灯会让人以为这条线已经完结了。「已取消」才是灰的。</p>
 */
export const WORK_ORDER_STATUS: Record<string, ScreenStatusStyle> = {
  RECEIVED: { label: '已接收', tone: 'flat' },
  DISPATCHED: { label: '已派工', tone: 'flat' },
  IN_PROGRESS: { label: '生产中', tone: 'good' },
  PAUSED: { label: '暂停', tone: 'warn' },
  COMPLETED: { label: '已完工', tone: 'good' },
  CLOSED: { label: '已关闭', tone: 'flat' },
  CANCELLED: { label: '已取消', tone: 'bad' },
};

/**
 * 生产工单页（S6 / T6.5）：工部坊的屏风页。
 *
 * <p><b>关键字参数名是 `code`，不是 `keyword`。</b>MES 的工单分页查询
 * （`MesWorkOrderQueryRequest`）只有 `code / name / erpOrderNo / itemCode / status / priority`
 * 六个条件，<b>没有 keyword</b>。给一个搜索框、把关键字发成 `keyword`、后端默默忽略，
 * 是"按了没反应"这类最难查的问题。这里把关键字接到 `code`（工单编码，最常用的检索口），
 * 占位语也照实写成"搜索工单编码"。</p>
 *
 * <p><b>不配 detailPath。</b>列表行已经带回详情需要的全部字段（`MesWorkOrderVO` 只有主表、
 * 没有分录），再请求一次 `/mes/v1/work-order/{id}` 只会让"点开有半秒空白"。</p>
 *
 * <p>五列只留：哪张单、做什么、做多少、什么状态、什么时候开工。物料编码埋在详情里——
 * 列表是"今天哪几张单在跑"，详情才是"这张单的工艺路线是哪一版"。</p>
 */
export const WORK_ORDER_PAGE: ScreenListPageSpec = {
  key: 'work-orders',
  title: '生产工单',
  listPath: '/mes/v1/work-order/page',
  permission: 'sys:mes:workorder:list',
  idKey: 'id',
  keywordParam: 'code',
  searchPlaceholder: '搜索工单编码…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'RECEIVED', label: '已接收' },
    { value: 'DISPATCHED', label: '已派工' },
    { value: 'IN_PROGRESS', label: '生产中' },
    { value: 'PAUSED', label: '暂停' },
    { value: 'COMPLETED', label: '已完工' },
    { value: 'CLOSED', label: '已关闭' },
    { value: 'CANCELLED', label: '已取消' },
  ],
  statusMap: WORK_ORDER_STATUS,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'code', label: '工单编码', width: '24%' },
    { key: 'itemName', label: '物料', width: '30%' },
    { key: 'plannedQuantity', label: '计划量', width: '16%', format: 'count', align: 'right' },
    { key: 'status', label: '状态', width: '14%', format: 'status' },
    { key: 'plannedStart', label: '计划开工', width: '16%', format: 'date' },
  ],
  detailFields: [
    { key: 'code', label: '工单编码' },
    { key: 'name', label: '工单名称' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'erpOrderNo', label: 'ERP 生产订单' },
    { key: 'itemCode', label: '物料编码' },
    { key: 'itemName', label: '物料名称' },
    { key: 'plannedQuantity', label: '计划数量', format: 'count', align: 'right' },
    { key: 'uom', label: '计量单位' },
    { key: 'priority', label: '优先级' },
    { key: 'routingVersion', label: '工艺路线版本' },
    { key: 'plannedStart', label: '计划开工', format: 'datetime' },
    { key: 'plannedEnd', label: '计划完工', format: 'datetime' },
  ],
  titleKey: 'code',
};

/**
 * 考勤日状态：与后端 `AttendanceStatus.DAILY_*` 一一对应，前端只做展示。
 *
 * <p>「迟到+早退」单列一档，不拆成两个标签：同一个人同一天既迟到又早退，日汇总里
 * 就是一行 `LATE_AND_EARLY`；硬拆成两个状态，一行里要挤两个灯，列宽立刻不够。</p>
 */
export const ATTENDANCE_DAILY_STATUS: Record<string, ScreenStatusStyle> = {
  NORMAL: { label: '正常', tone: 'good' },
  LATE: { label: '迟到', tone: 'warn' },
  EARLY: { label: '早退', tone: 'warn' },
  LATE_AND_EARLY: { label: '迟到+早退', tone: 'warn' },
  ABSENT: { label: '缺卡', tone: 'bad' },
};

/**
 * 考勤日报页（S6 / T6.8）：吏部坊的屏风页。
 *
 * <p><b>这一页没有搜索框，是有意为之。</b>{@code AttendanceDailyQuery} 只有
 * `userId / status / dateFrom / dateTo` 四个条件，**没有 keyword**。给它配一个搜索框、
 * 把关键字发成后端不认识的参数名，后端会默默忽略——"按了没反应"是这类问题里最难查的
 * 一种。真要按人查，走状态筛选 + 2D 考勤页的日期区间，比在城门口造假接口强。</p>
 *
 * <p><b>不配 detailPath。</b>`/oa/v1/attendance/daily/page` 返回的 `DailyView` 已经是
 * 整行明细（含打卡时刻与备注），列表行就是详情；再请求一次只会多一次 404 ——
 * 这个模块根本没有 `/daily/{id}` 详情接口。</p>
 *
 * <p>列表只留六列：哪天、谁、哪个班次、迟到多少分钟、早退多少分钟、什么状态。
 * 打卡时刻（`firstIn / lastOut`）放进详情——列表是"今天谁不正常"，
 * 详情才是"他到底几点打的卡"。</p>
 */
export const ATTENDANCE_DAILY_PAGE: ScreenListPageSpec = {
  key: 'attendance-daily',
  title: '考勤日报',
  listPath: '/oa/v1/attendance/daily/page',
  permission: 'sys:oa:attendance:daily:list',
  idKey: 'id',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'NORMAL', label: '正常' },
    { value: 'LATE', label: '迟到' },
    { value: 'EARLY', label: '早退' },
    { value: 'LATE_AND_EARLY', label: '迟到+早退' },
    { value: 'ABSENT', label: '缺卡' },
  ],
  statusMap: ATTENDANCE_DAILY_STATUS,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  /* 列宽：班次只给 12%（DAY / NIGHT 是定长编码），把省下的宽度还给姓名与状态，
     否则表格中段会空出一大片，看着像少了一列。 */
  columns: [
    { key: 'workDate', label: '日期', width: '21%', format: 'date' },
    { key: 'userName', label: '姓名', width: '24%' },
    { key: 'shiftCode', label: '班次', width: '12%' },
    { key: 'lateMinutes', label: '迟到(分)', width: '13%', format: 'count', align: 'right' },
    { key: 'earlyMinutes', label: '早退(分)', width: '13%', format: 'count', align: 'right' },
    { key: 'status', label: '状态', width: '17%', format: 'status' },
  ],
  detailFields: [
    { key: 'workDate', label: '考勤日期', format: 'date' },
    { key: 'userName', label: '姓名' },
    { key: 'shiftCode', label: '班次' },
    { key: 'firstIn', label: '上班打卡', format: 'datetime' },
    { key: 'lastOut', label: '下班打卡', format: 'datetime' },
    { key: 'lateMinutes', label: '迟到分钟', format: 'count', align: 'right' },
    { key: 'earlyMinutes', label: '早退分钟', format: 'count', align: 'right' },
    { key: 'absent', label: '缺卡', format: 'count', align: 'right' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'remark', label: '备注' },
  ],
  titleKey: 'userName',
};

/**
 * 客户状态：与后端 `CustomerStatus` 一一对应，前端只做展示。
 *
 * <p>「已合并」给灰灯：它是只读的历史痕迹（这条记录已经并到另一家名下），
 * 不是故障，也不值得值班的人每次路过都点进去查。</p>
 */
export const CUSTOMER_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  ACTIVE: { label: '有效', tone: 'good' },
  INACTIVE: { label: '停用', tone: 'flat' },
  MERGED: { label: '已合并', tone: 'flat' },
};

/**
 * 客户等级字典：A 重点 / B 普通 / C 潜力 / D 观察。
 *
 * <p>兼容存量 `KEY / NORMAL / POTENTIAL`（后端 `CustomerLevel#from` 会映射成 A/C/D，
 * 但库里仍有按旧码存的行）。不写这三个键的后果是列里原样显示英文 `KEY` ——
 * 不报错、也不好看，而且看牌的人不知道 KEY 比 A 高还是低。</p>
 */
export const CUSTOMER_LEVEL_STYLE: Record<string, ScreenStatusStyle> = {
  A: { label: 'A 重点', tone: 'good' },
  B: { label: 'B 普通', tone: 'flat' },
  C: { label: 'C 潜力', tone: 'flat' },
  D: { label: 'D 观察', tone: 'warn' },
  KEY: { label: 'A 重点', tone: 'good' },
  NORMAL: { label: 'C 潜力', tone: 'flat' },
  POTENTIAL: { label: 'D 观察', tone: 'warn' },
};

/**
 * 客户台账页（S6 / T6.11）：礼部坊的屏风页。
 *
 * <p><b>这一屏讲什么。</b>礼部掌宾客：城里这一面屏风回答"我们手上有哪些客户、
 * 哪些重点客户最近没人跟"。销售看板（KPI 与柱图）在屏风上半部，
 * 下半部就是这张台账——两处数字出自同一套数据，不会出现"牌上说 12 个风险商机、
 * 表里找不到对应客户"的情况。</p>
 *
 * <p><b>关键字参数是 `keyword`，这次是真的。</b>`CustomerPageParams` 里有
 * `keyword`（编码 / 名称 / 简称模糊匹配）与 `status / level / source / industry`
 * 四个筛选位。给搜索框之前先确认后端认这个参数名，是 S5 起立的规矩。</p>
 *
 * <p><b>不配 detailPath。</b>`/crm/v1/customer/page` 返回的 `CustomerView` 已经是整行明细
 * （含税号、地址、公海状态、跟进时间），列表行就是详情；再请求一次
 * `/crm/v1/customer/{id}` 只会多一次往返，还可能因数据权限差异拿到不同的行。</p>
 *
 * <p>等级列用列级 {@link ScreenColumn#statusMap} 覆盖页面级字典：页面级是"
 * 客户状态（有效/停用）"，等级是另一套码值（A/B/C/D）。两套混用会把"有效"
 * 显示成英文 ACTIVE，而且不报错。</p>
 */
export const CUSTOMER_PAGE: ScreenListPageSpec = {
  key: 'customers',
  title: '客户台账',
  listPath: '/crm/v1/customer/page',
  permission: 'sys:crm:customer:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索客户编码 / 名称…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'ACTIVE', label: '有效' },
    { value: 'INACTIVE', label: '停用' },
    { value: 'MERGED', label: '已合并' },
  ],
  statusMap: CUSTOMER_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'name', label: '客户', width: '30%' },
    {
      key: 'level',
      label: '等级',
      width: '14%',
      format: 'status',
      statusMap: CUSTOMER_LEVEL_STYLE,
    },
    { key: 'industry', label: '行业', width: '18%' },
    { key: 'lastFollowUpAt', label: '最近跟进', width: '20%', format: 'datetime' },
    { key: 'status', label: '状态', width: '18%', format: 'status' },
  ],
  detailFields: [
    { key: 'customerCode', label: '客户编码' },
    { key: 'name', label: '客户名称' },
    { key: 'shortName', label: '客户简称' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'level', label: '等级', format: 'status', statusMap: CUSTOMER_LEVEL_STYLE },
    { key: 'industry', label: '行业' },
    { key: 'source', label: '来源' },
    { key: 'scale', label: '规模' },
    { key: 'website', label: '官网' },
    { key: 'address', label: '地址' },
    { key: 'ownerId', label: '负责人 ID' },
    { key: 'lastFollowUpAt', label: '最近跟进', format: 'datetime' },
    { key: 'createTime', label: '创建时间', format: 'datetime' },
  ],
  titleKey: 'name',
};

/**
 * 设备在线字典：`iot_device.online` 是 0/1 两个数字，不是字符串枚举。
 *
 * <p>数字码必须是**字符串键**（`'1'`/`'0'`）：渲染层查表前做 `String(value)`，
 * 写成数字键 `{ 1: … }` 在 JS 里其实也是 `'1'`，但混着写最容易出现"一个有灯一个没灯"。
 * 离线给黄灯而不是红灯：设备没上线可能只是还没装、在仓库里躺着，
 * 红牌留给"在线率掉了"这种整体结论，逐台设备一进来就满屏红，等于没有告警。</p>
 */
export const DEVICE_ONLINE_STYLE: Record<string, ScreenStatusStyle> = {
  '1': { label: '在线', tone: 'good' },
  '0': { label: '离线', tone: 'warn' },
};

/** 设备状态：与 `iot_device.status` 一一对应（active 启用 / disabled 禁用） */
export const DEVICE_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  active: { label: '启用', tone: 'good' },
  disabled: { label: '禁用', tone: 'flat' },
};

/**
 * 设备台账页（S6 / T6.13）：兵部坊的屏风页。
 *
 * <p><b>这一屏讲什么。</b>兵部掌哨探：城里这面屏风回答"探子都在不在岗"——
 * 哪台设备、挂在哪个产品下、在不在线、最后一次回报是什么时候。屏风上半部是
 * 设备总数 / 在线率 / 近 24h 上报 / 待回指令，下半部就是这张台账，
 * 两边出自同一批表。</p>
 *
 * <p><b>关键字参数是 `keyword`，这次是真的。</b>`IotDeviceController#page` 接
 * {@code productId / keyword / current / size}，`keyword` 按设备名与昵称模糊匹配。
 * <b>但没有状态筛选位</b>——设备分页接口不收 `status`，配一个下拉只会发出后端
 * 默默忽略的参数，"点了没反应"是这类问题里最难查的一种。要看禁用设备，
 * 走 2D 设备管理页。</p>
 *
 * <p><b>不配 detailPath。</b>`/iot/v1/devices/page` 返回的 `DeviceVO` 已经含产品名、
 * 最近在线/上报时间与状态，列表行就是详情；详情接口 `/{id}` 返回同一份 VO，
 * 再请求一次只是多一次往返。</p>
 *
 * <p>「在线」列走列级字典：页面级字典是设备状态（active/disabled），
 * 在线是 0/1 的另一套码值，混用会把"在线"显示成英文 1。</p>
 */
export const DEVICE_PAGE: ScreenListPageSpec = {
  key: 'devices',
  title: '设备台账',
  listPath: '/iot/v1/devices/page',
  permission: 'sys:iot:device',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索设备名 / 昵称…',
  statusMap: DEVICE_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'deviceName', label: '设备', width: '26%' },
    { key: 'productName', label: '产品', width: '24%' },
    {
      key: 'online',
      label: '在线',
      width: '14%',
      format: 'status',
      statusMap: DEVICE_ONLINE_STYLE,
    },
    { key: 'lastReportTime', label: '最近上报', width: '20%', format: 'datetime' },
    { key: 'status', label: '状态', width: '16%', format: 'status' },
  ],
  detailFields: [
    { key: 'deviceName', label: '设备名称' },
    { key: 'nickname', label: '昵称' },
    { key: 'productName', label: '所属产品' },
    { key: 'productCode', label: '产品编码' },
    { key: 'online', label: '在线', format: 'status', statusMap: DEVICE_ONLINE_STYLE },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'lastOnlineTime', label: '最近在线', format: 'datetime' },
    { key: 'lastReportTime', label: '最近上报', format: 'datetime' },
    { key: 'createTime', label: '创建时间', format: 'datetime' },
  ],
  titleKey: 'deviceName',
};

/**
 * 课程状态字典：与 `CourseStatus` 一一对应，发布状态机 DRAFT → PUBLISHED → ARCHIVED。
 *
 * <p>草稿与归档都给灰灯：前者还没打算开课，后者已经下架，都不是问题。
 * 只有"已发布"值得一提——它意味着现在真的有人在学。</p>
 */
export const COURSE_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  DRAFT: { label: '草稿', tone: 'flat' },
  PUBLISHED: { label: '已发布', tone: 'good' },
  ARCHIVED: { label: '已归档', tone: 'flat' },
};

/**
 * 课程目录页（S6 / T6.16）：杏坛坊的屏风页。
 *
 * <p><b>这一屏讲什么。</b>杏坛掌教化：上面是"该读书的人有没有在读书"
 * （在册学员 / 学习覆盖 / 学习完成 / 考试通过），下面这张表回答"到底开了哪些课、
 * 谁在教、是草稿还是已上架"。覆盖率低的时候，人第一件事就是问"课不够还是没人学"，
 * 这张表给的正是前半句。</p>
 *
 * <p><b>关键字与状态都是真的。</b>`CourseController#page` 接
 * {@code keyword / category / status / current / size}，`keyword` 按课程名与编码模糊匹配。
 * 这里只放 `status` 一个下拉：分类是自由文本，做成下拉会漏掉新分类。</p>
 *
 * <p><b>不配 detailPath。</b>详情接口返回的是 {@code CourseDetailView}——课程头
 * 包在 {@code course} 里、章节与课件是两层嵌套数组，而屏风详情只认平铺键值对。
 * 硬配上去会得到一张全空的详情页，比不给入口更糟。课程结构在 2D 教务台上看。</p>
 */
export const COURSE_PAGE: ScreenListPageSpec = {
  key: 'courses',
  title: '课程目录',
  listPath: '/lms/v1/course/page',
  permission: 'sys:lms:course:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索课程名 / 编码…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'DRAFT', label: '草稿' },
    { value: 'PUBLISHED', label: '已发布' },
    { value: 'ARCHIVED', label: '已归档' },
  ],
  statusMap: COURSE_STATUS_STYLE,
  /* 5 行/页：本页的折线是**双序列**，比单序列多一行图例（"学习人数 / 完成人数"），
     6 行会把最后一行压到分页栏底下（DOM 有 6 行、眼睛只看到 5 行）。
     宁可少一行也不要"页签说 6 行、实际看 5 行"。 */
  pageSize: 5,
  columns: [
    { key: 'title', label: '课程', width: '26%' },
    { key: 'category', label: '分类', width: '18%' },
    { key: 'lecturer', label: '讲师', width: '18%' },
    { key: 'createTime', label: '创建时间', width: '22%', format: 'datetime' },
    { key: 'status', label: '状态', width: '16%', format: 'status' },
  ],
  /* 详情只用列表行已有的字段（课程编码与简介列表里放不下，详情里补齐） */
  detailFields: [
    { key: 'courseCode', label: '课程编码' },
    { key: 'title', label: '课程名称' },
    { key: 'category', label: '分类' },
    { key: 'lecturer', label: '讲师' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'description', label: '简介' },
    { key: 'createTime', label: '创建时间', format: 'datetime' },
  ],
  titleKey: 'title',
};

/** 物料状态：与 `pdm_product.status` 一一对应（ENABLED 在册 / DISABLED 停用） */
export const PART_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  ENABLED: { label: '在册', tone: 'good' },
  DISABLED: { label: '停用', tone: 'flat' },
};

/** 物料类型：与 `PdmPartTypes` 一一对应，列级字典——类型不是状态，不该蹭页面级字典 */
export const PART_TYPE_STYLE: Record<string, ScreenStatusStyle> = {
  PRODUCT: { label: '成品', tone: 'good' },
  ASSEMBLY: { label: '部件', tone: 'flat' },
  PART: { label: '零件', tone: 'flat' },
  RAW_MATERIAL: { label: '原材料', tone: 'warn' },
};

/**
 * 物料台账页（S6 / T6.19）：将作坊的屏风页。
 *
 * <p><b>这一屏讲什么。</b>将作掌营造法式：造什么器物、用多少料。物料是四本账里
 * 的底账——BOM、变更、图纸都挂在物料上，所以坊里只摆这一页。</p>
 *
 * <p><b>关键字接 `name` 不接 `code`。</b>后端的查询条件是 code / name 分开的两个参数、
 * 没有合并的 keyword；屏风的搜索框只发一个参数，接 `name`（找料的人先想起名字，
 * 不是编码）。编码列仍然摆在第一列，看得见就行。</p>
 *
 * <p><b>不配 `detailPath`：列表行已经带回详情需要的全部字段（规格、材质、计量单位、
 * ERP 编码），再请求一次只会让点开有半秒空白。</b>被合并的物料（mergedTo 非空）在
 * 详情里能看到去向。</p>
 */
export const PART_PAGE: ScreenListPageSpec = {
  key: 'parts',
  title: '物料台账',
  listPath: '/pdm/v1/part/page',
  permission: 'sys:pdm:part:list',
  idKey: 'id',
  keywordParam: 'name',
  searchPlaceholder: '搜索物料名称…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'ENABLED', label: '在册' },
    { value: 'DISABLED', label: '停用' },
  ],
  statusMap: PART_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'code', label: '编码', width: '18%' },
    { key: 'name', label: '名称', width: '26%' },
    { key: 'partType', label: '类型', width: '14%', format: 'status', statusMap: PART_TYPE_STYLE },
    { key: 'spec', label: '规格', width: '24%' },
    { key: 'status', label: '状态', width: '18%', format: 'status' },
  ],
  detailFields: [
    { key: 'code', label: '编码' },
    { key: 'name', label: '名称' },
    { key: 'partType', label: '类型', format: 'status' },
    { key: 'material', label: '材质' },
    { key: 'spec', label: '规格' },
    { key: 'uom', label: '计量单位' },
    { key: 'categoryCode', label: '分类' },
    { key: 'erpItemCode', label: 'ERP 物料编码' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'updateTime', label: '更新时间', format: 'datetime' },
  ],
  titleKey: 'name',
};

/** 项目状态：与 `ProjectGuard` 一一对应（ACTIVE 在建 / ARCHIVED 已归档） */
export const PROJECT_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  ACTIVE: { label: '在建', tone: 'good' },
  ARCHIVED: { label: '已归档', tone: 'flat' },
};

/** 项目类型：与 `BoardType` 一一对应，列级字典——类型不是状态，不该蹭页面级字典 */
export const PROJECT_TYPE_STYLE: Record<string, ScreenStatusStyle> = {
  SCRUM: { label: 'Scrum', tone: 'flat' },
  KANBAN: { label: '看板', tone: 'flat' },
};

/**
 * 营造坊（S6/T6.22）项目台账页。
 *
 * <p><b>这一屏讲什么。</b>营造司掌工程调度：城里有多少项工程在同时开工。
 * 项目是三本账（项目 / 迭代 / 工作项）里的底账——迭代和工作项都挂在项目上，
 * 所以坊里只摆这一页。</p>
 *
 * <p><b>工作项不单摆一页。</b>后端的 `/pm/v1/work-item/page` 强制按项目过滤
 * （projectId @NotNull），租户级的工作项清单在 2D 后台也不存在；
 * 硬凑一页只会把"按项目逐个点"伪装成"租户总账"。</p>
 *
 * <p><b>关键字接 `keyword`。</b>项目查询是少数后端就有合并关键字的
 * （编码与名称一起模糊匹配），屏风搜索框直接接它，不用挑一个字段。
 * 负责人列（leadUserId）是用户 ID，屏风拿不到用户名，摆出来只会是一串数字——不摆。</p>
 *
 * <p><b>不配 `detailPath`：列表行已经带回详情需要的全部字段（描述、可见性、
 * 类型、状态），再请求一次只会让点开有半秒空白。</b></p>
 */
export const PROJECT_PAGE: ScreenListPageSpec = {
  key: 'projects',
  title: '项目台账',
  listPath: '/pm/v1/project/page',
  permission: 'sys:pm:project:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索项目编码 / 名称…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'ACTIVE', label: '在建' },
    { value: 'ARCHIVED', label: '已归档' },
  ],
  statusMap: PROJECT_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 图表 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'key', label: '编码', width: '14%' },
    { key: 'name', label: '名称', width: '30%' },
    {
      key: 'projectType',
      label: '类型',
      width: '14%',
      format: 'status',
      statusMap: PROJECT_TYPE_STYLE,
    },
    {
      key: 'visibility',
      label: '可见性',
      width: '14%',
      format: 'status',
      statusMap: {
        PRIVATE: { label: '私有', tone: 'flat' },
        TEAM: { label: '团队', tone: 'flat' },
        PUBLIC: { label: '公开', tone: 'flat' },
      },
    },
    { key: 'status', label: '状态', width: '14%', format: 'status' },
    { key: 'createTime', label: '创建时间', width: '14%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'key', label: '编码' },
    { key: 'name', label: '名称' },
    { key: 'projectType', label: '类型', format: 'status' },
    { key: 'visibility', label: '可见性', format: 'status' },
    { key: 'description', label: '描述' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'createTime', label: '创建时间', format: 'datetime' },
  ],
  titleKey: 'name',
};

/** 合同状态：与 {@code ContractStatus} 一一对应，前端只做展示 */
export const CONTRACT_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  DRAFT: { label: '草稿', tone: 'flat' },
  APPROVING: { label: '审批中', tone: 'warn' },
  APPROVED: { label: '已批待签', tone: 'flat' },
  REJECTED: { label: '已驳回', tone: 'bad' },
  WITHDRAWN: { label: '已撤回', tone: 'flat' },
  SIGNING: { label: '签署中', tone: 'warn' },
  SIGNED: { label: '已签待生效', tone: 'flat' },
  EFFECTIVE: { label: '生效中', tone: 'good' },
  ARCHIVED: { label: '已归档', tone: 'flat' },
  TERMINATED: { label: '已终止', tone: 'bad' },
  VOID: { label: '已作废', tone: 'bad' },
};

/** 合同类别：与迁移注释 PURCHASE/SALES/SERVICE/LEASE/OTHER 一一对应 */
export const CONTRACT_CATEGORY_STYLE: Record<string, ScreenStatusStyle> = {
  PURCHASE: { label: '采购', tone: 'flat' },
  SALES: { label: '销售', tone: 'good' },
  SERVICE: { label: '服务', tone: 'flat' },
  LEASE: { label: '租赁', tone: 'flat' },
  OTHER: { label: '其他', tone: 'flat' },
};

/**
 * 盟府（CONTRACT）合同台账页。
 *
 * <p><b>不配搜索框</b>：合同查询的 {@code code} 与 {@code title} 是两个独立条件
 * （后端没有合并 keyword），硬拼一个关键字要么只匹配编号要么只匹配标题，
 * 看起来像"搜不到"其实是没发对参数。</p>
 *
 * <p><b>状态筛选给全</b>：合同十一个状态都是真状态（不是实例/任务那种
 * 同码不同义），页面级字典全覆盖；类别是列级字典——类别与状态是两个
 * 独立码值空间，绝不能混用同一本字典。</p>
 *
 * <p><b>不配 `detailPath`：列表行已经带回 parties/versions/flows 三本分录，
 * 再请求一次只会让点开有半秒空白。</b></p>
 */
export const CONTRACT_PAGE: ScreenListPageSpec = {
  key: 'contracts',
  title: '合同台账',
  listPath: '/contract/v1/page',
  permission: 'sys:contract:list',
  idKey: 'id',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'EFFECTIVE', label: '生效中' },
    { value: 'SIGNING', label: '签署中' },
    { value: 'APPROVING', label: '审批中' },
    { value: 'DRAFT', label: '草稿' },
    { value: 'ARCHIVED', label: '已归档' },
    { value: 'TERMINATED', label: '已终止' },
  ],
  statusMap: CONTRACT_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 金额柱图 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'code', label: '编号', width: '14%' },
    { key: 'title', label: '标题', width: '26%' },
    {
      key: 'category',
      label: '类别',
      width: '10%',
      format: 'status',
      statusMap: CONTRACT_CATEGORY_STYLE,
    },
    { key: 'amount', label: '金额', width: '14%', format: 'money', align: 'right' },
    { key: 'status', label: '状态', width: '12%', format: 'status' },
    { key: 'expireDate', label: '到期日', width: '12%', format: 'date' },
  ],
  detailFields: [
    { key: 'code', label: '编号' },
    { key: 'title', label: '标题' },
    { key: 'category', label: '类别', format: 'status' },
    { key: 'amount', label: '金额', format: 'money', align: 'right' },
    { key: 'currency', label: '币种' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'effectiveDate', label: '生效日', format: 'date' },
    { key: 'expireDate', label: '到期日', format: 'date' },
    { key: 'daysToExpire', label: '距到期（天）' },
    { key: 'remark', label: '备注' },
  ],
  titleKey: 'title',
};

/** 党员状态：与 {@code MemberStatus} 一一对应，前端只做展示 */
export const PARTY_MEMBER_STATUS: Record<string, ScreenStatusStyle> = {
  ACTIVE: { label: '在册', tone: 'good' },
  TRANSFERRED: { label: '已转出', tone: 'flat' },
  SUSPENDED: { label: '停籍', tone: 'bad' },
};

/** 政治面貌：与 {@code PoliticalStatus} 一一对应（预备党员黄灯，转正要有人办） */
export const PARTY_POLITICAL_STYLE: Record<string, ScreenStatusStyle> = {
  FULL_MEMBER: { label: '正式党员', tone: 'flat' },
  PROBATIONARY: { label: '预备党员', tone: 'warn' },
};

/**
 * 宣抚坊（PARTY）党员台账页。
 *
 * <p><b>有搜索框</b>：党员查询有合并 keyword（编号与姓名一起模糊匹配），
 * 与项目查询同一待遇。</p>
 *
 * <p><b>政治面貌用列级字典</b>：FULL_MEMBER / PROBATIONARY 与状态 ACTIVE/TRANSFERRED/
 * SUSPENDED 是两个独立码空间，页面级字典会把"预备党员"误译成状态。
 * 预备党员黄灯——不是事故，是"转正手续得有人办"的提醒。</p>
 *
 * <p><b>不配 `detailPath`：列表行已经带回支部、面貌、党龄、状态等全部字段，
 * 再请求一次只会让点开有半秒空白。</b></p>
 */
export const MEMBER_PAGE: ScreenListPageSpec = {
  key: 'members',
  title: '党员台账',
  listPath: '/party/v1/member/page',
  permission: 'sys:party:member:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索党员编号 / 姓名…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'ACTIVE', label: '在册' },
    { value: 'TRANSFERRED', label: '已转出' },
    { value: 'SUSPENDED', label: '停籍' },
  ],
  statusMap: PARTY_MEMBER_STATUS,
  /* 6 行/页：大屏 620px 里 KPI + 年龄柱图 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'memberNo', label: '编号', width: '14%' },
    { key: 'memberName', label: '姓名', width: '14%' },
    { key: 'branchName', label: '支部', width: '22%' },
    {
      key: 'politicalStatus',
      label: '政治面貌',
      width: '16%',
      format: 'status',
      statusMap: PARTY_POLITICAL_STYLE,
    },
    { key: 'partyAgeYears', label: '党龄', width: '12%', format: 'count', align: 'right' },
    { key: 'status', label: '状态', width: '12%', format: 'status' },
  ],
  detailFields: [
    { key: 'memberNo', label: '编号' },
    { key: 'memberName', label: '姓名' },
    { key: 'branchName', label: '支部' },
    { key: 'politicalStatus', label: '政治面貌', format: 'status' },
    { key: 'employeeNo', label: '工号' },
    { key: 'education', label: '学历' },
    { key: 'joinPartyDate', label: '入党日期', format: 'date' },
    { key: 'partyAgeYears', label: '党龄（年）', format: 'count', align: 'right' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'phone', label: '联系电话' },
  ],
  titleKey: 'memberName',
};

/** 文档空间状态：与 `doc_space.status` 对应（1 正常 / 0 归档） */
export const DOC_SPACE_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  1: { label: '正常', tone: 'good' },
  0: { label: '已归档', tone: 'flat' },
};

/** 空间可见性：与 `DocSpaceVisibility` 一一对应，列级字典——可见性不是状态 */
export const DOC_SPACE_VISIBILITY_STYLE: Record<string, ScreenStatusStyle> = {
  PUBLIC: { label: '公开', tone: 'flat' },
  MEMBER: { label: '成员可见', tone: 'flat' },
};

/**
 * 翰林坊（DOC）空间台账页（S6 / T6.31）。
 *
 * <p><b>这一屏讲什么。</b>翰林院掌文书：空间（文书房）是页面的家——页面按
 * 空间树形组织，没有租户级的页面清单，所以坊里摆的是空间台账，
 * 点进空间才是 2D 里看页树的正确姿势。</p>
 *
 * <p><b>有搜索框</b>：空间查询有合并 keyword（名称与描述一起模糊匹配）。</p>
 *
 * <p><b>可见性用列级字典</b>：PUBLIC / MEMBER 与空间状态 1/0 是两个独立码空间
 * （一个字符串一个数字），页面级字典会把"成员可见"误译成状态。数字键
 * 能用是因为取值时统一 {@code String(value)} 后再查表。</p>
 *
 * <p><b>不配 `detailPath`：列表行已经带回编码、描述、可见性等全部字段，
 * 成员数在分页接口不返回（详情才算），摆了也只会是空的。</b></p>
 */
export const SPACE_PAGE: ScreenListPageSpec = {
  key: 'spaces',
  title: '空间台账',
  listPath: '/doc/v1/space/page',
  permission: 'sys:doc:space:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索空间名 / 描述…',
  statusParam: 'visibility',
  statuses: [
    { value: '', label: '全部可见性' },
    { value: 'PUBLIC', label: '公开' },
    { value: 'MEMBER', label: '成员可见' },
  ],
  statusMap: DOC_SPACE_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 状态柱图 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'name', label: '空间名', width: '22%' },
    { key: 'spaceKey', label: '编码', width: '14%' },
    {
      key: 'visibility',
      label: '可见性',
      width: '14%',
      format: 'status',
      statusMap: DOC_SPACE_VISIBILITY_STYLE,
    },
    { key: 'status', label: '状态', width: '12%', format: 'status' },
    { key: 'description', label: '描述', width: '22%' },
    { key: 'createTime', label: '创建时间', width: '16%', format: 'date' },
  ],
  detailFields: [
    { key: 'name', label: '空间名' },
    { key: 'spaceKey', label: '编码' },
    { key: 'visibility', label: '可见性', format: 'status' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'description', label: '描述' },
    { key: 'createTime', label: '创建时间', format: 'datetime' },
    { key: 'updateTime', label: '更新时间', format: 'datetime' },
  ],
  titleKey: 'name',
};

/**
 * 订单状态：与后端 {@code OrderStatus} 七态一一对应，页面级字典。
 *
 * <p>退款两态单列（退款中黄、已退款灰），不并进取消——钱卡在支付侧与
 * 「没成交」是两回事，混译会让退款积压在屏风上隐形。</p>
 */
export const MALL_ORDER_STATUS_STYLE: Record<string, ScreenStatusStyle> = {
  PENDING_PAYMENT: { label: '待支付', tone: 'flat' },
  PAID: { label: '待发货', tone: 'warn' },
  SHIPPED: { label: '已发货', tone: 'flat' },
  COMPLETED: { label: '已完成', tone: 'good' },
  CANCELLED: { label: '已取消', tone: 'flat' },
  REFUNDING: { label: '退款中', tone: 'warn' },
  REFUNDED: { label: '已退款', tone: 'flat' },
};

/**
 * 市舶坊（MALL）订单台账页（S6 / T6.34）。
 *
 * <p><b>这一屏讲什么。</b>市舶司掌通商，订单是主账：哪几单钱收了没发货、
 * 哪几单退款卡着，是运营在屏风上要先看的东西。</p>
 *
 * <p><b>有搜索框</b>：订单查询有合并 keyword（订单号与买家名一起模糊匹配），
 * 状态筛选用独立的 {@code status} 参数。</p>
 *
 * <p><b>不配 `detailPath`</b>：列表行已带回金额、件数、渠道、付款时间等
 * 全部台账字段；订单详情在 2D 里还要按到订单实体上点，屏风里摆行内字段
 * 就够看。</p>
 */
export const ORDER_PAGE: ScreenListPageSpec = {
  key: 'orders',
  title: '订单台账',
  listPath: '/mall/v1/order/page',
  permission: 'sys:mall:order:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索订单号 / 买家…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'PENDING_PAYMENT', label: '待支付' },
    { value: 'PAID', label: '待发货' },
    { value: 'SHIPPED', label: '已发货' },
    { value: 'COMPLETED', label: '已完成' },
    { value: 'REFUNDING', label: '退款中' },
  ],
  statusMap: MALL_ORDER_STATUS_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 状态柱图 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'orderNo', label: '订单号', width: '20%' },
    { key: 'buyerName', label: '买家', width: '14%' },
    { key: 'status', label: '状态', width: '12%', format: 'status' },
    { key: 'totalAmount', label: '金额', width: '14%', format: 'money' },
    { key: 'totalQuantity', label: '件数', width: '10%' },
    { key: 'createTime', label: '下单时间', width: '16%', format: 'datetime' },
    { key: 'channelCode', label: '渠道', width: '14%' },
  ],
  detailFields: [
    { key: 'orderNo', label: '订单号' },
    { key: 'buyerName', label: '买家' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'totalAmount', label: '金额', format: 'money' },
    { key: 'totalQuantity', label: '件数' },
    { key: 'channelCode', label: '渠道' },
    { key: 'payTime', label: '支付时间', format: 'datetime' },
    { key: 'shipTime', label: '发货时间', format: 'datetime' },
    { key: 'createTime', label: '下单时间', format: 'datetime' },
  ],
  titleKey: 'orderNo',
};

/**
 * 歌曲版权状态：列级字典（页面级字典被发布状态占用）。
 *
 * <p>下架（TAKEDOWN）给灰不给红：下架是处置<b>出了结论</b>的结果，
 * 有争议可申诉翻案，不是正在烧的事故；待复核（DECLARED）才是黄——
 * 协议签了没人过目，上架被卡在这一节。</p>
 */
export const MUSIC_COPYRIGHT_STYLE: Record<string, ScreenStatusStyle> = {
  DECLARED: { label: '待复核', tone: 'warn' },
  VALID: { label: '有效', tone: 'good' },
  TAKEDOWN: { label: '已下架', tone: 'flat' },
};

/**
 * 歌曲发布状态：页面级字典。
 *
 * <p>已上架绿（能播就是好事）、草稿与已下架灰——草稿是上传者自己的节奏，
 * 下架是处置结果，都不催办；运营要盯的待办（评论/申诉/版权复核）在 KPI 与告警条。</p>
 */
export const MUSIC_PUBLISH_STATE_STYLE: Record<string, ScreenStatusStyle> = {
  DRAFT: { label: '草稿', tone: 'flat' },
  PUBLISHED: { label: '已上架', tone: 'good' },
  OFFLINE: { label: '已下架', tone: 'flat' },
};

/**
 * 乐坊（MUSIC）歌曲台账页（S7 / T7.1）。
 *
 * <p><b>这一屏讲什么。</b>乐坊掌音律，歌曲是主账：哪几支压在草稿没上架、
 * 哪几支版权没过目，是运营在屏风上要先看的东西。</p>
 *
 * <p><b>版权用列级字典。</b>页面级字典让给发布状态（筛选走 publishState），
 * 版权是每行自己的属性——同一页里既有待复核也有已下架，打到列上才各归各位。</p>
 */
export const SONG_PAGE: ScreenListPageSpec = {
  key: 'songs',
  title: '歌曲台账',
  listPath: '/music/v1/song/page',
  permission: 'sys:music:song:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索曲名…',
  statusParam: 'publishState',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'DRAFT', label: '草稿' },
    { value: 'PUBLISHED', label: '已上架' },
    { value: 'OFFLINE', label: '已下架' },
  ],
  statusMap: MUSIC_PUBLISH_STATE_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 状态柱图 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'title', label: '曲名', width: '22%' },
    { key: 'language', label: '语言', width: '10%' },
    { key: 'publishState', label: '状态', width: '12%', format: 'status' },
    {
      key: 'copyrightState',
      label: '版权',
      width: '12%',
      format: 'status',
      statusMap: MUSIC_COPYRIGHT_STYLE,
    },
    { key: 'playCount', label: '播放', width: '12%' },
    { key: 'createTime', label: '收录时间', width: '18%', format: 'datetime' },
    { key: 'publishedAt', label: '上架时间', width: '14%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'title', label: '曲名' },
    { key: 'subtitle', label: '副题' },
    { key: 'publishState', label: '发布状态', format: 'status' },
    { key: 'copyrightState', label: '版权', format: 'status' },
    { key: 'language', label: '语言' },
    { key: 'playCount', label: '播放数' },
    { key: 'likeCount', label: '收藏数' },
    { key: 'commentCount', label: '评论数' },
    { key: 'createTime', label: '收录时间', format: 'datetime' },
    { key: 'publishedAt', label: '上架时间', format: 'datetime' },
  ],
  titleKey: 'title',
};

/**
 * 影戏院内容类型：列级字典。
 *
 * <p>短视频 / 视频 / 电影三类共存在一张 {@code vd_content} 表里，
 * 类型是每行自己的属性；电影走单独的授权与下线规则（授权到期自动下线），
 * 所以这三档不能并成一个"其它"——值班的人要靠这一列看出哪部片子受合同约束。</p>
 */
export const VIDEO_CONTENT_TYPE_STYLE: Record<string, ScreenStatusStyle> = {
  SHORT_VIDEO: { label: '短视频', tone: 'flat' },
  VIDEO: { label: '视频', tone: 'flat' },
  MOVIE: { label: '电影', tone: 'good' },
};

/**
 * 影戏院版权状态：列级字典。
 *
 * <p>与乐坊同理：下架（TAKEDOWN）给灰不给红——那是处置<b>出了结论</b>的结果，
 * 可走版权复核恢复；有效（AUTHORIZED）才是能播的那一档。</p>
 */
export const VIDEO_COPYRIGHT_STYLE: Record<string, ScreenStatusStyle> = {
  AUTHORIZED: { label: '有效', tone: 'good' },
  TAKEDOWN: { label: '已下架', tone: 'bad' },
};

/**
 * 影戏院内容发布状态：页面级字典。
 *
 * <p><b>待审给黄不给红。</b>待审是投稿人交了稿、运营还没过目——这是"欠账"
 * 不是"事故"，黄灯提醒值班的人去看一眼；已发布绿（能播就是好事）；
 * 草稿与已下线灰（投稿人自己的节奏、处置结果）。四态码值固定
 * （{@code PublishState}），屏风不译码。</p>
 */
export const VIDEO_PUBLISH_STATE_STYLE: Record<string, ScreenStatusStyle> = {
  DRAFT: { label: '草稿', tone: 'flat' },
  PENDING_REVIEW: { label: '待审', tone: 'warn' },
  PUBLISHED: { label: '已发布', tone: 'good' },
  OFFLINE: { label: '已下线', tone: 'flat' },
};

/**
 * 影戏院（VIDEO）内容台账页（S7 / T7.2）。
 *
 * <p><b>这一屏讲什么。</b>影戏院放片子，内容是主账：哪几部压在草稿没送审、
 * 哪几部待审没人放行、哪几部版权已下架，是运营在屏风上要先看的东西。</p>
 *
 * <p><b>三个字典各归各位。</b>发布态占页面级字典（筛选走 {@code publishState}），
 * 类型与版权是每行自己的属性，打到列上——同一页里既有电影也有短视频、
 * 既有有效也有下架，共用页面级字典会互相盖掉。</p>
 */
export const CONTENT_PAGE: ScreenListPageSpec = {
  key: 'contents',
  title: '内容台账',
  listPath: '/video/v1/admin/content/page',
  permission: 'sys:video:content:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索片名…',
  statusParam: 'publishState',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'DRAFT', label: '草稿' },
    { value: 'PENDING_REVIEW', label: '待审' },
    { value: 'PUBLISHED', label: '已发布' },
    { value: 'OFFLINE', label: '已下线' },
  ],
  statusMap: VIDEO_PUBLISH_STATE_STYLE,
  /* 6 行/页：大屏 620px 里 KPI + 状态柱图 + 工具栏之后只剩约 230px */
  pageSize: 6,
  columns: [
    { key: 'title', label: '片名', width: '22%' },
    {
      key: 'contentType',
      label: '类型',
      width: '10%',
      format: 'status',
      statusMap: VIDEO_CONTENT_TYPE_STYLE,
    },
    { key: 'publishState', label: '状态', width: '12%', format: 'status' },
    {
      key: 'copyrightState',
      label: '版权',
      width: '12%',
      format: 'status',
      statusMap: VIDEO_COPYRIGHT_STYLE,
    },
    { key: 'playCount', label: '播放', width: '10%' },
    { key: 'createTime', label: '收录时间', width: '18%', format: 'datetime' },
    { key: 'publishedAt', label: '上架时间', width: '16%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'title', label: '片名' },
    { key: 'subtitle', label: '副题' },
    { key: 'contentType', label: '类型', format: 'status', statusMap: VIDEO_CONTENT_TYPE_STYLE },
    { key: 'publishState', label: '发布状态', format: 'status' },
    { key: 'copyrightState', label: '版权', format: 'status', statusMap: VIDEO_COPYRIGHT_STYLE },
    { key: 'visibility', label: '可见性' },
    { key: 'durationMs', label: '时长（毫秒）' },
    { key: 'playCount', label: '播放数' },
    { key: 'likeCount', label: '点赞数' },
    { key: 'favoriteCount', label: '收藏数' },
    { key: 'commentCount', label: '评论数' },
    { key: 'createTime', label: '收录时间', format: 'datetime' },
    { key: 'publishedAt', label: '上架时间', format: 'datetime' },
  ],
  titleKey: 'title',
};

/**
 * 清议堂帖子类型：列级字典。
 *
 * <p>问答帖与投票帖是"有形态"的帖子（问答可采纳最佳答案、投票有选项与截止），
 * 公告帖只有管理端能发；普通帖与活动帖给灰——它们不改变这一行该怎么读，
 * 但值班的人一眼能分辨"这条要不要点进去看采纳/投票结果"。</p>
 */
export const THREAD_TYPE_STYLE: Record<string, ScreenStatusStyle> = {
  NORMAL: { label: '普通', tone: 'flat' },
  QA: { label: '问答', tone: 'good' },
  VOTE: { label: '投票', tone: 'good' },
  NOTICE: { label: '公告', tone: 'warn' },
  ACTIVITY: { label: '活动', tone: 'flat' },
  ARTICLE: { label: '长文', tone: 'flat' },
};

/**
 * 清议堂帖子状态：页面级字典。
 *
 * <p><b>待审黄、在议绿、驳回红。</b>待审是作者交了稿等着放行（欠账，黄）；
 * 在议是能正常回帖的（绿）；已解决与已关闭是事情有了结论（灰）；
 * 已驳回是审核不通过<b>而且作者可能来申诉</b>（红）——这一档要给红，
 * 否则值班的人看不出"这里有一批人正等着复核"。</p>
 *
 * <p>码值固定（{@code ThreadStatus}），屏风不译码。{@code DRAFT} 与
 * {@code SCHEDULED} 是作者自己的草稿箱，公开列表里不会出现，字典仍列出，
 * 免得将来出现时被渲染成裸码。</p>
 */
export const THREAD_STATE_STYLE: Record<string, ScreenStatusStyle> = {
  DRAFT: { label: '草稿', tone: 'flat' },
  SCHEDULED: { label: '定时', tone: 'flat' },
  PENDING: { label: '待审', tone: 'warn' },
  PUBLISHED: { label: '在议', tone: 'good' },
  SOLVED: { label: '已解决', tone: 'flat' },
  CLOSED: { label: '已关闭', tone: 'flat' },
  REJECTED: { label: '已驳回', tone: 'bad' },
  DELETED: { label: '已删除', tone: 'flat' },
};

/**
 * 清议堂（FORUM）帖子列表页（S7 / T7.3）。
 *
 * <p><b>这一屏讲什么。</b>清议堂是公开议事的地方，主账就是帖子：哪几篇在议、
 * 哪几篇已解决、哪几篇被驳回等着复核。列表按最后回复时间排（{@code latest}），
 * 因为"还有人在这条下面说话"比"发布得晚"更能说明它现在是活的。</p>
 *
 * <p><b>筛选框选的是排序不是状态。</b>公开列表按浏览，没有"状态"这个维度——
 * 待审、驳回都是审核链路的内部概念，不该让访客按它筛。所以这里把
 * {@code statusParam} 指向 {@code sort}（最新 / 热帖 / 精华）：控件还是那个下拉，
 * 语义是"看哪一类帖"。{@code statusMap} 仍管行上的状态灯。</p>
 *
 * <p><b>不配 {@code detailPath}。</b>`ThreadListItemVO` 已经把编号、类型、
 * 状态、回复/浏览/投花/收藏四个计数、发布时间与最后回复时间都带回来了——
 * 屏风上的详情是"这一行的展开"，不该为了同一批字段再打一次接口。</p>
 */
export const THREAD_PAGE: ScreenListPageSpec = {
  key: 'threads',
  title: '帖子列表',
  listPath: '/forum/v1/thread/page',
  permission: 'sys:forum:browse',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索标题…',
  statusParam: 'sort',
  statuses: [
    { value: '', label: '最新回帖' },
    { value: 'hot', label: '热帖' },
    { value: 'essence', label: '精华' },
  ],
  statusMap: THREAD_STATE_STYLE,
  /* 4 行/页：帖子状态是**五态**柱图，比四态页面多吃掉约 0.7 行高——实测表体
     只放得下 4.3 行，排 5 行会让第 5 行在表格下缘被切一半（屏风能滚，但半行
     看着像坏了）。4 行刚好排满不留半行。 */
  pageSize: 4,
  columns: [
    { key: 'title', label: '标题', width: '24%' },
    {
      key: 'threadType',
      label: '类型',
      width: '9%',
      format: 'status',
      statusMap: THREAD_TYPE_STYLE,
    },
    { key: 'status', label: '状态', width: '11%', format: 'status' },
    { key: 'replyCount', label: '回复', width: '8%' },
    { key: 'viewCount', label: '浏览', width: '8%' },
    { key: 'likeCount', label: '投花', width: '8%' },
    { key: 'publishTime', label: '发布时间', width: '16%', format: 'datetime' },
    { key: 'lastReplyTime', label: '最后回复', width: '16%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'title', label: '标题' },
    { key: 'threadNo', label: '帖号' },
    { key: 'threadType', label: '类型', format: 'status', statusMap: THREAD_TYPE_STYLE },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'boardId', label: '版块' },
    { key: 'authorId', label: '作者' },
    { key: 'anonymous', label: '匿名展示' },
    { key: 'anonymousNo', label: '匿名编号' },
    { key: 'pinned', label: '悬榜' },
    { key: 'essence', label: '题金榜' },
    { key: 'replyCount', label: '回复数' },
    { key: 'viewCount', label: '浏览数' },
    { key: 'likeCount', label: '投花数' },
    { key: 'collectCount', label: '收藏数' },
    { key: 'lastReplyTime', label: '最后回复', format: 'datetime' },
  ],
  titleKey: 'title',
};

/**
 * 邮件文件夹：与后端 {@code MailFolderType} 的 code 一一对应。
 *
 * <p>{@code CUSTOM} 单列一档：自定义文件夹在 2D 里是账号自建的归档目录，
 * 屏风上不提供按它筛选（值不知道），但列表里出现时不能显示成裸的 "CUSTOM"。</p>
 */
export const MAIL_FOLDER_TYPE: Record<string, ScreenStatusStyle> = {
  INBOX: { label: '收件箱', tone: 'good' },
  SENT: { label: '已发送', tone: 'flat' },
  DRAFT: { label: '草稿箱', tone: 'flat' },
  JUNK: { label: '垃圾邮件', tone: 'warn' },
  TRASH: { label: '已删除', tone: 'bad' },
  CUSTOM: { label: '自定义', tone: 'flat' },
};

/**
 * 已读状态：后端 {@code seen} 是 0/1。
 *
 * <p><b>字典键必须是字符串</b>：渲染层用 {@code String(value)} 查表，写成数字
 * {@code 0: {...}} 会变成对象索引，运行时永远查不到、且不报错——这一列就会
 * 一直显示裸的 "0"。</p>
 */
export const MAIL_SEEN_STATE: Record<string, ScreenStatusStyle> = {
  '0': { label: '未读', tone: 'warn' },
  '1': { label: '已读', tone: 'flat' },
};

/** 附件标记：{@code hasAttachment} 0/1，破折号而不是"无"——表格里越短越好 */
export const MAIL_ATTACHMENT_STATE: Record<string, ScreenStatusStyle> = {
  '0': { label: '—', tone: 'flat' },
  '1': { label: '附件', tone: 'good' },
};

/**
 * 邮驿（MAIL）邮件列表页。
 *
 * <p><b>筛选框选的是文件夹不是状态。</b>邮件没有"状态"这个维度：已读未读是行上的
 * 一列，而同一封信在不同文件夹里含义完全不同（草稿、已发送、垃圾）。所以
 * {@code statusParam} 指向 {@code folderCode}，控件语义是"看哪个抽屉"。
 * 第一项给空串：服务端 {@code resolveFolder} 在缺 {@code folderCode} 时取
 * INBOX，所以空串天然就是收件箱，不需要前端写死默认值。</p>
 *
 * <p><b>不配 {@code detailPath}。</b>{@code GET /mail/v1/message/{id}} 会触发正文
 * 回源（同步阶段只拉了 header，正文要现场再连一次 IMAP）。屏风上点一行就去打
 * 邮箱服务器，既慢又会因为对外网络不通而降级——列表行里的字段已经够答
 * "谁、什么时候、多大、读没读"。</p>
 */
export const MAIL_PAGE: ScreenListPageSpec = {
  key: 'messages',
  title: '邮件列表',
  listPath: '/mail/v1/message/page',
  permission: 'sys:mail:message:list',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索主题 / 发件人…',
  statusParam: 'folderCode',
  statuses: [
    { value: '', label: '收件箱' },
    { value: 'SENT', label: '已发送' },
    { value: 'DRAFT', label: '草稿箱' },
    { value: 'JUNK', label: '垃圾邮件' },
    { value: 'TRASH', label: '已删除' },
  ],
  /* 6 行/页：四态柱图（与影戏院同宽），KPI + 柱图 + 工具栏之后正好排满 */
  pageSize: 6,
  columns: [
    { key: 'subject', label: '主题', width: '33%' },
    { key: 'fromName', label: '发件人', width: '16%' },
    { key: 'seen', label: '状态', width: '8%', format: 'status', statusMap: MAIL_SEEN_STATE },
    {
      key: 'hasAttachment',
      label: '附件',
      width: '8%',
      format: 'status',
      statusMap: MAIL_ATTACHMENT_STATE,
    },
    { key: 'sizeBytes', label: '大小', width: '10%', format: 'size' },
    { key: 'sentTime', label: '收信时间', width: '17%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'subject', label: '主题' },
    { key: 'fromName', label: '发件人' },
    { key: 'fromAddr', label: '发件地址' },
    { key: 'toAddrs', label: '收件人' },
    { key: 'ccAddrs', label: '抄送' },
    { key: 'folderCode', label: '所在文件夹', format: 'status', statusMap: MAIL_FOLDER_TYPE },
    { key: 'seen', label: '状态', format: 'status', statusMap: MAIL_SEEN_STATE },
    {
      key: 'hasAttachment',
      label: '附件',
      format: 'status',
      statusMap: MAIL_ATTACHMENT_STATE,
    },
    { key: 'sizeBytes', label: '大小', format: 'size' },
    { key: 'sentTime', label: '收信时间', format: 'datetime' },
    { key: 'preview', label: '摘要' },
  ],
  titleKey: 'subject',
};

/**
 * 会话类型：与后端 {@code ImConversation.type} 一一对应。
 *
 * <p>机器人会话用黄灯：它不是异常，但它是"会话里混进了一个自动应答的东西"，
 * 值班的人应当一眼分得出来谁在说话。</p>
 */
export const IM_CONVERSATION_TYPE: Record<string, ScreenStatusStyle> = {
  SINGLE: { label: '单聊', tone: 'flat' },
  GROUP: { label: '群聊', tone: 'good' },
  BOT: { label: '机器人', tone: 'warn' },
};

/**
 * 最后发言者类型：与后端 {@code MessageVO.senderType} 一致。
 *
 * <p>详情里不能显示裸的 "USER"——屏风上出现英文码，读的人第一反应是"接口没翻译"。</p>
 */
export const IM_SENDER_TYPE: Record<string, ScreenStatusStyle> = {
  USER: { label: '用户', tone: 'flat' },
  BOT: { label: '机器人', tone: 'warn' },
  SYSTEM: { label: '系统', tone: 'good' },
};

/**
 * 传信司（IM）会话列表页（S7 / T7.5）。
 *
 * <p><b>这一屏讲什么。</b>传信司是即时会话的驿站：哪些话还在说、哪些会话已经
 * 没人开口。列表的主列不是"标题"而是<b>最后一句</b>——单聊在平台里本来就没有
 * 标题（2D 也显示"未命名会话"），一眼能读到"最后说了什么"比一个空标题有用。</p>
 *
 * <p><b>为什么要 {@code clientPaged}。</b>`GET /im/v1/conversations` 返回的是
 * 普通数组，没有分页参数；照其他殿的写法翻页会只翻页码不换内容。置 true 后
 * 一次取回、前端切页——会话列表最多几十条，一次取回比给 2D 接口加分页参数便宜。</p>
 *
 * <p><b>不配 {@code detailPath}。</b>后端没有"按会话取详情"的接口（只有成员表与
 * 消息游标），行展开就用列表带回的字段；硬配一个不存在的路径，点一行会 404。</p>
 */
export const IM_PAGE: ScreenListPageSpec = {
  key: 'conversations',
  title: '会话列表',
  listPath: '/im/v1/conversations',
  permission: 'sys:im:chat',
  idKey: 'id',
  clientPaged: true,
  /* 3 行/页：近七日消息是**横向柱图**，七根柱子比四态/五态柱图各多吃两行，
     实测表体只放得下 3.3 行；排 4 行会让第 4 行在表格下缘被切一半（截图可见）。 */
  pageSize: 3,
  columns: [
    { key: 'title', label: '会话', width: '27%', emptyText: '未命名会话' },
    { key: 'type', label: '类型', width: '11%', format: 'status', statusMap: IM_CONVERSATION_TYPE },
    { key: 'lastMessage.content', label: '最后一句', width: '29%', emptyText: '还没有消息' },
    { key: 'unread', label: '未读', width: '9%', align: 'right' },
    { key: 'lastActiveAt', label: '最后活跃', width: '24%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'title', label: '会话', emptyText: '未命名会话' },
    { key: 'type', label: '类型', format: 'status', statusMap: IM_CONVERSATION_TYPE },
    { key: 'ownerId', label: '会话主' },
    { key: 'lastSeq', label: '末序' },
    { key: 'unread', label: '未读' },
    { key: 'lastMessage.content', label: '最后一句', emptyText: '还没有消息' },
    { key: 'lastMessage.senderType', label: '最后发言', format: 'status', statusMap: IM_SENDER_TYPE },
    { key: 'lastMessage.createTime', label: '最后发言时间', format: 'datetime' },
    { key: 'lastActiveAt', label: '最后活跃', format: 'datetime' },
  ],
  titleKey: 'title',
};

/**
 * 场景状态：与后端 {@code TwinScene.status} 一一对应（draft → published ⇄ disabled）。
 *
 * <p>已停用给黄灯而不是红灯：停用是主动动作（改之前先停下来），不是故障；
 * 但它确实"不在运行"，所以也不能给绿灯。草稿翻灰灯——它还没生效。</p>
 */
export const TWIN_SCENE_STATUS: Record<string, ScreenStatusStyle> = {
  draft: { label: '草稿', tone: 'flat' },
  published: { label: '已发布', tone: 'good' },
  disabled: { label: '已停用', tone: 'warn' },
};

/**
 * 观星台（TWIN）场景台账页（S7 / T7.7）。
 *
 * <p><b>为什么只有这一页。</b>孪生的实体树、绑定、告警都挂在某个场景下，
 * 2D 里没有"跨场景的实体列表"这种读法——要看得先选场景。所以本殿的屏风上
 * 只排场景台账：它是唯一能独立打开的入口，也是"哪座场景在响"的索引。</p>
 *
 * <p>每行随带未恢复告警角标（{@code unresolvedAlerts}），分页接口一次分组查询
 * 装配好，不必先开全局告警抽屉。状态字典放在<b>页面级</b>：场景状态是字符串枚举，
 * 列级字典只用于 0/1 这类稳定码，页面级才能让筛选值与渲染共用同一张表——
 * 筛选项写中文、行里也写中文，两处各维护一份迟早会不一致。</p>
 *
 * <p>5 行/页：中部是七根<b>纵向</b>柱图，高度与邮驿的四态柱图差不多，
 * 但表头多一列"版本"，实测 6 行会把最后一行压到分页条上。</p>
 */
export const TWIN_SCENE_PAGE: ScreenListPageSpec = {
  key: 'twin-scenes',
  title: '场景台账',
  listPath: '/twin/v1/scenes/page',
  detailPath: (id) => `/twin/v1/scenes/${id}`,
  permission: 'sys:twin:scene',
  idKey: 'id',
  keywordParam: 'keyword',
  searchPlaceholder: '搜索场景名 / 编码…',
  statusParam: 'status',
  statuses: [
    { value: '', label: '全部状态' },
    { value: 'published', label: '已发布' },
    { value: 'draft', label: '草稿' },
    { value: 'disabled', label: '已停用' },
  ],
  statusMap: TWIN_SCENE_STATUS,
  pageSize: 5,
  columns: [
    { key: 'name', label: '场景', width: '27%', emptyText: '未名场景' },
    { key: 'code', label: '编码', width: '21%' },
    { key: 'status', label: '状态', width: '10%', format: 'status' },
    { key: 'entityCount', label: '实体', width: '8%', align: 'right' },
    { key: 'unresolvedAlerts', label: '未恢复', width: '9%', align: 'right' },
    { key: 'version', label: '版本', width: '7%', align: 'right' },
    { key: 'updateTime', label: '更新', width: '18%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'name', label: '场景' },
    { key: 'code', label: '编码' },
    { key: 'description', label: '描述', emptyText: '未填写' },
    { key: 'status', label: '状态', format: 'status' },
    { key: 'entityCount', label: '实体数' },
    { key: 'unresolvedAlerts', label: '未恢复告警' },
    { key: 'criticalAlerts', label: '其中严重' },
    { key: 'unackedAlerts', label: '待确认' },
    { key: 'version', label: '版本' },
    { key: 'createTime', label: '创建时间', format: 'datetime' },
    { key: 'updateTime', label: '更新时间', format: 'datetime' },
  ],
  titleKey: 'name',
};

/**
 * 围栏类型：与后端 {@code MapFence.fenceType} 一一对应。
 *
 * <p>圆形围栏多半是"以某个点为中心的一片"（门店辐射范围），多边形是"按街区划的
 * 一片"（配送区、考勤组）。两者在判定逻辑上没有区别，但运维看图时的直觉不同，
 * 所以用两种灯色分开。</p>
 */
export const MAP_FENCE_TYPE: Record<string, ScreenStatusStyle> = {
  CIRCLE: { label: '圆形', tone: 'good' },
  POLYGON: { label: '多边形', tone: 'flat' },
};

/**
 * 启用状态：键必须是字符串。
 *
 * <p>渲染层按 {@code String(value)} 查表，后端给的是数字 {@code 1}。
 * 键写成数字字面量（`1: {...}`）在 TS 里会静默变成字符串键，但在别的序列化
 * 路径上就不是了——统一写字符串是最省事的纪律（邮驿的已读/附件踩过这一脚）。</p>
 */
export const MAP_ENABLED_STATE: Record<string, ScreenStatusStyle> = {
  '1': { label: '启用', tone: 'good' },
  '0': { label: '停用', tone: 'flat' },
};

/**
 * 舆图司（MAP）围栏台账页（S7 / T7.6）。
 *
 * <p><b>选它当屏风页的理由。</b>舆图司的家底有三层：打点、围栏、解析缓存。
 * 打点跟着业务对象走（一张订单一个点，没有"点位列表"这种页面），解析缓存是
 * 内部加速用的中间表，都不该摆到屏风上；只有围栏是<b>人来划、人来改</b>的东西——
 * 划错了范围，打卡与配送会一起错，而且错得没有声音。</p>
 *
 * <p><b>两个坑写在配置里。</b>① 分页参数是 {@code page/size} 不是
 * {@code current/size}（见 {@link ScreenListPageSpec#pageParam}）——发错了服务端
 * 不报错，只是永远返回第 1 页；② 详情不配 {@code detailPath}：围栏详情里
 * 顶点是一串坐标，屏风上排不下，而且台账这一屏要回答的是"哪些围栏停了、
 * 谁在管哪片"，不是"这个多边形的第 7 个顶点在哪"。</p>
 */
export const MAP_FENCE_PAGE: ScreenListPageSpec = {
  key: 'fences',
  title: '围栏台账',
  listPath: '/map/v1/fence/page',
  permission: 'sys:map:fence:query',
  idKey: 'id',
  keywordParam: 'name',
  searchPlaceholder: '搜索围栏名…',
  statusParam: 'enabled',
  statuses: [
    { value: '', label: '全部状态' },
    { value: '1', label: '启用' },
    { value: '0', label: '停用' },
  ],
  statusMap: MAP_ENABLED_STATE,
  pageParam: 'page',
  sizeParam: 'size',
  /* 3 行/页：近七日调用量是横向柱图，七根柱子吃掉两行高度（与传信司同一笔账） */
  pageSize: 3,
  columns: [
    { key: 'name', label: '围栏', width: '27%' },
    { key: 'fenceType', label: '类型', width: '11%', format: 'status', statusMap: MAP_FENCE_TYPE },
    { key: 'bizType', label: '业务', width: '16%', emptyText: '通用' },
    { key: 'radiusMeters', label: '半径/米', width: '13%', align: 'right', emptyText: '多边形' },
    { key: 'enabled', label: '状态', width: '11%', format: 'status' },
    { key: 'updateTime', label: '更新时间', width: '22%', format: 'datetime' },
  ],
  detailFields: [
    { key: 'name', label: '围栏' },
    { key: 'fenceType', label: '类型', format: 'status', statusMap: MAP_FENCE_TYPE },
    { key: 'bizType', label: '业务类型', emptyText: '通用' },
    { key: 'bizId', label: '业务对象', emptyText: '—' },
    { key: 'centerLng', label: '圆心经度', emptyText: '多边形' },
    { key: 'centerLat', label: '圆心纬度', emptyText: '多边形' },
    { key: 'radiusMeters', label: '半径/米', emptyText: '多边形' },
    { key: 'coordType', label: '坐标系' },
    { key: 'enabled', label: '状态', format: 'status' },
    { key: 'remark', label: '备注' },
    { key: 'updateTime', label: '更新时间', format: 'datetime' },
  ],
  titleKey: 'name',
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
  [FORM_DEFINITION_PAGE.key]: FORM_DEFINITION_PAGE,
  [MESSAGE_RECORD_PAGE.key]: MESSAGE_RECORD_PAGE,
  [AUDIT_LOG_PAGE.key]: AUDIT_LOG_PAGE,
  [WORK_ORDER_PAGE.key]: WORK_ORDER_PAGE,
  [ATTENDANCE_DAILY_PAGE.key]: ATTENDANCE_DAILY_PAGE,
  [CUSTOMER_PAGE.key]: CUSTOMER_PAGE,
  [DEVICE_PAGE.key]: DEVICE_PAGE,
  [COURSE_PAGE.key]: COURSE_PAGE,
  [PART_PAGE.key]: PART_PAGE,
  [CONTRACT_PAGE.key]: CONTRACT_PAGE,
  [MEMBER_PAGE.key]: MEMBER_PAGE,
  [PROJECT_PAGE.key]: PROJECT_PAGE,
  [SPACE_PAGE.key]: SPACE_PAGE,
  [ORDER_PAGE.key]: ORDER_PAGE,
  [SONG_PAGE.key]: SONG_PAGE,
};
