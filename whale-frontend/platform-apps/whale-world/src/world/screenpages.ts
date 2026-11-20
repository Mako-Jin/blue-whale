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

/** 列排版口径：与 overview 契约的 format 同构，另加 date / status 两种表格专用口径 */
export type ColumnFormat = 'text' | 'count' | 'money' | 'percent' | 'date' | 'status';

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
};
