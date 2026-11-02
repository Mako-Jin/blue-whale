// SupplierList 页面级单测：真渲染 antd Table，仅 mock API 层
// 权限走 utils/permission（读基座 localStorage），用例内直接注入 whale_platform_user
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SupplierList from './SupplierList';
import type { ErpSupplier } from '../api/erp';

vi.mock('../api/erp', () => ({
  listErpSuppliers: vi.fn(),
  deleteErpSupplier: vi.fn(),
}));

import { deleteErpSupplier, listErpSuppliers } from '../api/erp';

const mockedList = vi.mocked(listErpSuppliers);
const mockedDelete = vi.mocked(deleteErpSupplier);

const suppliers: ErpSupplier[] = [
  {
    id: '1',
    code: 'SUP-001',
    name: '华东金属材料厂',
    category: 'MATERIAL',
    contactName: '张三',
    contactPhone: '13800000001',
    status: 'ENABLED',
    rating: 'A',
  },
  {
    id: '2',
    code: 'SUP-002',
    name: '南方物流公司',
    category: 'LOGISTICS',
    status: 'DISABLED',
  },
];

/** 注入基座用户缓存（权限码来自 whale_platform_user） */
function grantPermissions(permissions: string[]) {
  window.localStorage.setItem('whale_platform_user', JSON.stringify({ permissions }));
}

function renderList(refreshKey = 0) {
  const onCreate = vi.fn();
  const onEdit = vi.fn();
  const view = render(<SupplierList refreshKey={refreshKey} onCreate={onCreate} onEdit={onEdit} />);
  return { ...view, onCreate, onEdit };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedList.mockResolvedValue(suppliers);
  mockedDelete.mockResolvedValue(undefined);
});

describe('SupplierList', () => {
  it('渲染列表：编码/名称/分类/联系人/评级/状态文案映射', async () => {
    grantPermissions([]);
    renderList();

    expect(await screen.findByText('SUP-001')).toBeInTheDocument();
    expect(screen.getByText('华东金属材料厂')).toBeInTheDocument();
    expect(screen.getByText('物料')).toBeInTheDocument();
    expect(screen.getByText('物流')).toBeInTheDocument();
    expect(screen.getByText('张三')).toBeInTheDocument();
    expect(screen.getByText('13800000001')).toBeInTheDocument();
    expect(screen.getByText('A')).toBeInTheDocument();
    expect(screen.getByText('启用')).toBeInTheDocument();
    expect(screen.getByText('禁用')).toBeInTheDocument();
    expect(mockedList).toHaveBeenCalledTimes(1);
  });

  it('无权限：不渲染新增按钮，操作列为空', async () => {
    grantPermissions([]);
    renderList();

    await screen.findByText('SUP-001');
    expect(screen.queryByText('新增供应商')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^编\s*辑$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /删\s*除/ })).not.toBeInTheDocument();
  });

  it('有权限：新增按钮触发 onCreate，编辑按钮携带行数据触发 onEdit', async () => {
    grantPermissions(['sys:erp:supplier:add', 'sys:erp:supplier:edit', 'sys:erp:supplier:delete']);
    const { onCreate, onEdit } = renderList();

    fireEvent.click(await screen.findByRole('button', { name: '新增供应商' }));
    expect(onCreate).toHaveBeenCalledTimes(1);

    // antd 两字按钮自动插空格（「编 辑」），断言用正则
    const editButtons = await screen.findAllByRole('button', { name: /^编\s*辑$/ });
    expect(editButtons).toHaveLength(2);
    fireEvent.click(editButtons[0]);
    expect(onEdit).toHaveBeenCalledWith(suppliers[0]);
  });

  it('Popconfirm 删除：确认后调 deleteErpSupplier 并重新加载列表', async () => {
    grantPermissions(['sys:erp:supplier:delete']);
    renderList();

    // 两行数据各有一个删除按钮，取第一行
    const deleteButtons = await screen.findAllByRole('button', { name: /删\s*除/ });
    fireEvent.click(deleteButtons[0]);

    // Popconfirm 气泡内确认按钮（okText 也是「删除」，限定气泡内避免歧义）
    const popconfirm = await screen.findByText('确认删除该供应商？');
    const popover = popconfirm.closest('.ant-popover') as HTMLElement;
    fireEvent.click(within(popover).getByRole('button', { name: /^删\s*除$/ }));

    await waitFor(() => expect(mockedDelete).toHaveBeenCalledWith('1'));
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
  });

  it('refreshKey 递增触发列表重载', async () => {
    grantPermissions([]);
    const { rerender } = renderList(0);
    await screen.findByText('SUP-001');

    rerender(<SupplierList refreshKey={1} onCreate={vi.fn()} onEdit={vi.fn()} />);
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
  });
});
