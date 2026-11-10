// 物料列表页（List.tsx）页面级单测
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import List from './List';
import { listErpItems, type ErpItem } from '../api/erp';

vi.mock('../api/erp', () => ({
  listErpItems: vi.fn(),
}));

const mockedList = vi.mocked(listErpItems);

const items: ErpItem[] = [
  { id: '1', code: 'ERP-001', name: '六角螺栓', status: 'ENABLED', remark: 'M8' },
  { id: '2', code: 'ERP-002', name: '密封垫片', status: 'DISABLED' },
];

function grantPermissions(permissions: string[]) {
  window.localStorage.setItem('whale_platform_user', JSON.stringify({ permissions }));
}

function renderList(refreshKey = 0) {
  const onCreate = vi.fn();
  const onEdit = vi.fn();
  const view = render(<List refreshKey={refreshKey} onCreate={onCreate} onEdit={onEdit} />);
  return { ...view, onCreate, onEdit };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedList.mockResolvedValue(items);
});

describe('List（物料列表）', () => {
  it('渲染列表：编码/名称/状态/备注', async () => {
    grantPermissions([]);
    renderList();

    expect(await screen.findByText('ERP-001')).toBeInTheDocument();
    expect(screen.getByText('六角螺栓')).toBeInTheDocument();
    expect(screen.getByText('ERP-002')).toBeInTheDocument();
    expect(screen.getByText('启用')).toBeInTheDocument();
    expect(screen.getByText('禁用')).toBeInTheDocument();
    expect(screen.getByText('M8')).toBeInTheDocument();
    expect(mockedList).toHaveBeenCalledTimes(1);
  });

  it('无权限：不渲染新增与编辑按钮', async () => {
    grantPermissions([]);
    renderList();

    await screen.findByText('ERP-001');
    expect(screen.queryByRole('button', { name: /^新\s*增$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^编\s*辑$/ })).not.toBeInTheDocument();
  });

  it('有权限：新增触发 onCreate，编辑携带行数据触发 onEdit', async () => {
    grantPermissions(['sys:erp:item:add', 'sys:erp:item:edit']);
    const { onCreate, onEdit } = renderList();

    // antd 两字按钮自动插空格（「新 增」「编 辑」），断言用正则；两行数据产生两个编辑按钮
    fireEvent.click(await screen.findByRole('button', { name: /^新\s*增$/ }));
    expect(onCreate).toHaveBeenCalledTimes(1);

    const editButtons = screen.getAllByRole('button', { name: /^编\s*辑$/ });
    expect(editButtons).toHaveLength(2);
    fireEvent.click(editButtons[0]);
    expect(onEdit).toHaveBeenCalledWith(items[0]);
  });

  it('refreshKey 递增触发列表重载', async () => {
    grantPermissions([]);
    const { rerender } = renderList(0);
    await screen.findByText('ERP-001');

    rerender(<List refreshKey={1} onCreate={vi.fn()} onEdit={vi.fn()} />);
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
  });
});
