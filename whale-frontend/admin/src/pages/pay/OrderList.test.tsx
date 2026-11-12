// 支付单管理页组件测试：列表渲染 / 状态与权限门控 / 详情抽屉流水与对账 / 支付确认 / 新增
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderList from './OrderList';
import {
  createPayOrder,
  listPayTransactions,
  pagePayOrders,
  payPayOrder,
  reconcilePayOrder,
  type PayOrderVO,
  type PayReconcileVO,
  type PayTransactionVO,
} from '../../api/pay';
import { pageResult, renderPage } from '../../test/test-utils';

vi.mock('../../api/pay', () => ({
  pagePayOrders: vi.fn(),
  createPayOrder: vi.fn(),
  payPayOrder: vi.fn(),
  cancelPayOrder: vi.fn(),
  refundPayOrder: vi.fn(),
  reconcilePayOrder: vi.fn(),
  listPayTransactions: vi.fn(),
}));

const PAY_PERMS = [
  'sys:pay:add',
  'sys:pay:pay',
  'sys:pay:cancel',
  'sys:pay:refund',
  'sys:pay:reconcile',
];

const createdOrder: PayOrderVO = {
  id: '1',
  orderNo: 'PAY20261028001',
  businessKey: 'BIZ-1',
  amount: 9.9,
  currency: 'CNY',
  channel: 'ALIPAY',
  status: 'CREATED',
  createTime: '2026-10-28 10:00:00',
};

const paidOrder: PayOrderVO = {
  ...createdOrder,
  id: '2',
  orderNo: 'PAY20261028002',
  businessKey: undefined,
  amount: 99,
  status: 'PAID',
  payTime: '2026-10-28 10:05:00',
  channelTradeNo: 'CH-20261028-0002',
};

const transaction: PayTransactionVO = {
  id: '100',
  orderId: '1',
  orderNo: 'PAY20261028001',
  type: 'PAY',
  channel: 'ALIPAY',
  amount: 9.9,
  currency: 'CNY',
  providerTradeNo: 'PT-1',
  status: 'SUCCESS',
  createTime: '2026-10-28 10:05:00',
};

const reconcileResult: PayReconcileVO = {
  orderId: '1',
  orderNo: 'PAY20261028001',
  localStatus: 'CREATED',
  channelStatus: 'CREATED',
  consistent: true,
  providerTradeNo: 'PT-1',
  checkedAt: '2026-10-28 11:00:00',
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(pagePayOrders).mockResolvedValue(pageResult([createdOrder, paidOrder]));
  vi.mocked(listPayTransactions).mockResolvedValue([transaction]);
});

describe('OrderList 支付单页', () => {
  it('渲染列表：单号、金额、渠道与状态标签', async () => {
    renderPage(<OrderList />, PAY_PERMS);

    expect(await screen.findByText('PAY20261028001')).toBeInTheDocument();
    expect(screen.getByText('9.9')).toBeInTheDocument();
    expect(screen.getByText('待支付')).toBeInTheDocument();
    expect(screen.getByText('已支付')).toBeInTheDocument();
    expect(screen.getByText('支付单')).toBeInTheDocument();
  });

  it('CREATED 行显示支付/取消，PAID 行显示退款，退款不重复', async () => {
    renderPage(<OrderList />, PAY_PERMS);

    await screen.findByText('PAY20261028001');
    // 正则锚定全名，避免命中工具栏「新增支付单」
    expect(screen.getAllByRole('button', { name: /^支\s*付$/ })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: /^取\s*消$/ })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: /^退\s*款$/ })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: /^详\s*情$/ })).toHaveLength(2);
  });

  it('无操作权限时只保留详情按钮', async () => {
    renderPage(<OrderList />, []);

    await screen.findByText('PAY20261028001');
    expect(screen.queryByRole('button', { name: /^支\s*付$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^取\s*消$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^退\s*款$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增支付单' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^详\s*情$/ })).toHaveLength(2);
  });

  it('支付经确认后调用支付接口并刷新列表', async () => {
    vi.mocked(payPayOrder).mockResolvedValue({ ...createdOrder, status: 'PAID' });
    renderPage(<OrderList />, PAY_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /^支\s*付$/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(payPayOrder).toHaveBeenCalledWith('1');
    });
    await waitFor(() => {
      expect(pagePayOrders).toHaveBeenCalledTimes(2);
    });
  });

  it('详情抽屉展示流水，发起对账后展示一致性结果', async () => {
    vi.mocked(reconcilePayOrder).mockResolvedValue(reconcileResult);
    renderPage(<OrderList />, PAY_PERMS);

    fireEvent.click((await screen.findAllByRole('button', { name: /^详\s*情$/ }))[0]);
    expect(await screen.findByText('支付单详情：PAY20261028001')).toBeInTheDocument();
    await waitFor(() => {
      expect(listPayTransactions).toHaveBeenCalledWith('1');
    });
    expect(screen.getByText('PT-1')).toBeInTheDocument();
    expect(screen.getByText('成功')).toBeInTheDocument();

    fireEvent.click(screen.getByText('对账'));
    fireEvent.click(await screen.findByRole('button', { name: '发起对账' }));

    await waitFor(() => {
      expect(reconcilePayOrder).toHaveBeenCalledWith('1');
    });
    expect(await screen.findByText('一致')).toBeInTheDocument();
    // 本地状态与渠道状态均为 CREATED
    expect(screen.getAllByText('CREATED')).toHaveLength(2);
  });

  it('新增支付单：金额以 number 提交', async () => {
    vi.mocked(createPayOrder).mockResolvedValue({
      ...createdOrder,
      id: '3',
      orderNo: 'PAY20261028003',
    });
    renderPage(<OrderList />, PAY_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: '新增支付单' }));
    // 用弹窗内独有文案确认弹窗已开（标题与按钮同名）
    await screen.findByText('业务唯一，创建后不可修改');
    const modal = document.querySelector('.ant-modal') as HTMLElement;

    fireEvent.change(within(modal).getByLabelText('支付单号'), {
      target: { value: 'PAY20261028003' },
    });
    fireEvent.change(within(modal).getByLabelText('支付金额'), { target: { value: '9.9' } });
    fireEvent.click(within(modal).getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => {
      expect(createPayOrder).toHaveBeenCalledWith(
        expect.objectContaining({ orderNo: 'PAY20261028003', amount: 9.9, currency: 'CNY' }),
      );
    });
  });
});
