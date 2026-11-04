// 供应链单据公共文案与格式化：采购订单/销售订单/库存页共用

/** 单据状态文案 */
export const SCM_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVED: '已审核',
  PARTIALLY_RECEIVED: '部分收货',
  RECEIVED: '已收货',
  PARTIALLY_SHIPPED: '部分发货',
  SHIPPED: '已发货',
  POSTED: '已过账',
  PUBLISHED: '已发布',
  RELEASED: '已下达',
  IN_PROGRESS: '生产中',
  COMPLETED: '已完工',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
};

/** 单据状态颜色 */
export function scmStatusColor(status: string): string {
  if (status === 'RECEIVED' || status === 'SHIPPED' || status === 'POSTED'
    || status === 'PUBLISHED' || status === 'COMPLETED') return 'green';
  if (status === 'PARTIALLY_RECEIVED' || status === 'PARTIALLY_SHIPPED'
    || status === 'IN_PROGRESS') return 'orange';
  if (status === 'CANCELLED') return 'red';
  if (status === 'APPROVED' || status === 'RELEASED') return 'blue';
  return 'default';
}

/** 数量格式化（最多 4 位小数，去掉末尾零） */
export function quantity(value: number | undefined): string {
  return String(Number(value ?? 0));
}

/** 金额格式化 */
export function money(value: number | undefined): string {
  return Number(value ?? 0).toFixed(2);
}
