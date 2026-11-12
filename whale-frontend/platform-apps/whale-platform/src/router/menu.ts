// 静态菜单配置 - 基座占位菜单与子应用入口
// 图标在 MainLayout 中通过 MENU_ICONS 映射，保持本文件为纯数据

export interface MenuNode {
  key: string;
  label: string;
  /** 菜单可见所需权限码；缺省表示登录即可见（工作台/门户等无数据权限的入口） */
  permission?: string;
  children?: MenuNode[];
}

export const menuConfig: MenuNode[] = [
  { key: '/', label: '工作台' },
  // 通知偏好（Wave 6.17d：个人设置，登录即可见，无需权限码）
  { key: '/preferences', label: '通知偏好' },
  {
    key: '/system',
    label: '系统管理',
    children: [
      { key: '/system/users', label: '用户管理', permission: 'sys:user:list' },
      { key: '/system/roles', label: '角色管理', permission: 'sys:role:list' },
      { key: '/system/menus', label: '菜单管理', permission: 'sys:menu:list' },
    ],
  },
  { key: '/portal', label: '门户应用' },
  {
    key: '/erp',
    label: 'ERP 应用',
    children: [
      { key: '/erp/items', label: '物料管理', permission: 'sys:erp:item:list' },
      { key: '/erp/suppliers', label: '供应商管理', permission: 'sys:erp:supplier:list' },
      { key: '/erp/master-data', label: 'ERP 主数据', permission: 'sys:erp:account:list' },
      { key: '/erp/finance/voucher', label: '会计凭证', permission: 'sys:erp:voucher:list' },
      { key: '/erp/finance/arap', label: '应收应付', permission: 'sys:erp:receivable:list' },
      { key: '/erp/finance/report', label: '财务报表', permission: 'sys:erp:report:view' },
      { key: '/erp/scm/purchase', label: '采购订单', permission: 'sys:erp:purchase:list' },
      { key: '/erp/scm/sales', label: '销售订单', permission: 'sys:erp:sales:list' },
      { key: '/erp/scm/stock', label: '库存台账', permission: 'sys:erp:stock:list' },
      { key: '/erp/scm/transfer', label: '库存调拨', permission: 'sys:erp:transfer:list' },
      { key: '/erp/scm/stocktake', label: '库存盘点', permission: 'sys:erp:stocktake:list' },
      { key: '/erp/scm/purchase-return', label: '采购退货', permission: 'sys:erp:purchase-return:list' },
      { key: '/erp/scm/sales-return', label: '销售退货', permission: 'sys:erp:sales-return:list' },
      { key: '/erp/scm/purchase-requisition', label: '采购申请', permission: 'sys:erp:purchase-requisition:list' },
      { key: '/erp/scm/sales-quotation', label: '销售报价', permission: 'sys:erp:sales-quotation:list' },
      { key: '/erp/scm/price-list', label: '价格表', permission: 'sys:erp:price-list:list' },
      { key: '/erp/scm/safety-stock', label: '安全库存', permission: 'sys:erp:safety-stock:list' },
      { key: '/erp/mfg/forecast', label: '需求预测', permission: 'sys:erp:forecast:list' },
      { key: '/erp/mfg/mps', label: '主生产计划', permission: 'sys:erp:mps:list' },
      { key: '/erp/mfg/production', label: '生产订单', permission: 'sys:erp:production:list' },
      { key: '/erp/hr/employee', label: '组织与员工', permission: 'sys:erp:hr:employee:list' },
      { key: '/erp/hr/attendance', label: '考勤汇总', permission: 'sys:erp:hr:attendance:list' },
      { key: '/erp/hr/payroll', label: '薪资批次', permission: 'sys:erp:hr:payroll:list' },
      { key: '/erp/hr/performance', label: '绩效管理', permission: 'sys:erp:hr:performance:list' },
      { key: '/erp/qc/inspection', label: '检验与不合格品', permission: 'sys:erp:qc:inspection:list' },
      { key: '/erp/qc/equipment', label: '设备与保养', permission: 'sys:erp:qc:equipment:list' },
      { key: '/erp/qc/metrology', label: '量具与校准', permission: 'sys:erp:qc:metrology:list' },
      { key: '/erp/bi/cockpit', label: '经营驾驶舱', permission: 'sys:erp:bi:cockpit:list' },
      { key: '/erp/bi/cube', label: '多维分析', permission: 'sys:erp:bi:cube:query' },
      { key: '/erp/bi/report', label: '报表中心', permission: 'sys:erp:bi:report:list' },
      { key: '/erp/bi/alert', label: '预警中心', permission: 'sys:erp:bi:alert:list' },
    ],
  },
  { key: '/oa', label: 'OA 应用', permission: 'sys:oa:leave:list' },
  { key: '/mes', label: 'MES 应用', permission: 'sys:mes:workorder:list' },
  {
    key: '/pdm',
    label: 'PDM 应用',
    children: [
      { key: '/pdm/document', label: '图文档中心', permission: 'sys:pdm:doc:list' },
      { key: '/pdm/product', label: '产品结构', permission: 'sys:pdm:product:list' },
    ],
  },
  { key: '/im', label: '消息', permission: 'sys:im:chat' },
  { key: '/pm', label: '项目管理', permission: 'sys:pm:project:list' },
  { key: '/contract', label: '合同管理', permission: 'sys:contract:list' },
  { key: '/doc', label: '文档中心', permission: 'sys:doc:space:list' },
  { key: '/music', label: '音乐中心', permission: 'sys:music:browse' },
  // 邮箱（whale-mail 子应用，Wave 5.89 M0 独立起服务）
  { key: '/mail', label: '邮箱', permission: 'sys:mail:message:list' },
  // 学习培训（whale-lms 子应用，Wave 5.87；子应用内部自带横向导航，基座只留一个入口）
  { key: '/lms', label: '学习培训', permission: 'sys:lms:browse' },
  // 党政工会（whale-party 子应用，Wave 5.88；同上）
  { key: '/party', label: '党政工会', permission: 'sys:party:browse' },
  {
    key: '/scene',
    label: '城池导航',
    children: [
      { key: '/scene/navigation', label: '2D 导航' },
      { key: '/scene/manage', label: '场景管理', permission: 'sys:scene:list' },
      { key: '/scene/bindings', label: '入口绑定', permission: 'sys:scene:binding:manage' },
      { key: '/scene/visibility', label: '可见性', permission: 'sys:scene:visibility:manage' },
    ],
  },
  // AI 助手（whale-assistant 子应用，Wave 6.01；问答入口，定义/用量管理在 admin）
  { key: '/assistant', label: 'AI 助手', permission: 'sys:assistant:chat' },
  // 知识助手（whale-agent 子应用，Wave 6.18 接入；权限码与后端 @RequirePermission 一致）
  { key: '/agent', label: '知识助手', permission: 'sys:agent:chat' },
  // IoT 设备联网（whale-iot 子应用，Wave 6.02；产品/设备/遥测/规则四页由子应用内部导航承载）
  { key: '/iot', label: 'IoT 设备', permission: 'sys:iot:device' },
  // 数字孪生（whale-twin 子应用，Wave 6.03；场景管理/3D 查看器由子应用内部导航承载）
  { key: '/twin', label: '数字孪生', permission: 'sys:twin:scene' },
  // 员工论坛（whale-forum 子应用，Wave 6.43；版块/收藏/管理由子应用内部导航承载）
  { key: '/forum', label: '鲸议台', permission: 'sys:forum:browse' },
];

/**
 * 按当前用户权限裁剪菜单：无权限的叶子节点隐藏；目录在子节点全部隐藏时一并隐藏。
 *
 * <p>未声明 {@code permission} 的节点始终可见（工作台/门户为无数据权限的入口）。
 * 静态菜单与后端 {@code /auth/me} 的权限集合同源，避免「菜单可见但接口 403」。</p>
 *
 * @param nodes 原始菜单树
 * @param permissions 当前用户权限码集合
 * @returns 过滤后的菜单树（保持原顺序）
 */
export function filterMenuByPermissions(
  nodes: MenuNode[],
  permissions: readonly string[],
): MenuNode[] {
  const granted = new Set(permissions);
  const visible: MenuNode[] = [];
  for (const node of nodes) {
    if (node.permission && !granted.has(node.permission)) continue;
    if (node.children?.length) {
      const children = filterMenuByPermissions(node.children, permissions);
      if (children.length === 0) continue;
      visible.push({ ...node, children });
    } else {
      visible.push(node);
    }
  }
  return visible;
}

const routeTitles = new Map<string, string>();

function collectTitles(nodes: MenuNode[]): void {
  nodes.forEach((node) => {
    routeTitles.set(node.key, node.label);
    if (node.children) collectTitles(node.children);
  });
}
collectTitles(menuConfig);

// 根据路径取页面标题：精确匹配菜单项；子应用子路由取第一段匹配；兜底返回路径本身
export function getRouteTitle(pathname: string): string {
  const exact = routeTitles.get(pathname);
  if (exact) return exact;
  const segment = `/${pathname.split('/')[1]}`;
  return routeTitles.get(segment) ?? pathname;
}

/**
 * 判断路径是否在静态菜单中登记（即「用户从菜单点进来的页面」）。
 *
 * <p>用于区分两类路径：菜单页（如 {@code /erp/items}，每个都应独立成标签）与子应用内部
 * 跳转的路径（如 {@code /agent/chat}，应归并到子应用入口标签，否则出现同名重复标签）。</p>
 *
 * @param pathname 当前路由路径
 * @returns 是菜单项返回 true
 */
export function hasMenuEntry(pathname: string): boolean {
  return routeTitles.has(pathname);
}

// 根据路径取面包屑文本链（如 ['系统管理', '用户管理']）
export function getBreadcrumbItems(pathname: string): string[] {
  for (const item of menuConfig) {
    if (item.children) {
      const child = item.children.find((node) => node.key === pathname);
      if (child) return [item.label, child.label];
    } else if (item.key === pathname) {
      return [item.label];
    }
  }
  if (pathname !== '/') {
    const segment = `/${pathname.split('/')[1]}`;
    const parent = routeTitles.get(segment);
    if (parent) return [parent];
  }
  return [];
}
