// 业务配置管理页组件测试：列表渲染 / 变更历史抽屉 / 回滚 / 编辑弹窗键锁定
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ItemList from './ItemList';
import {
  listConfigItemHistory,
  pageConfigItems,
  rollbackConfigItem,
  updateConfigItem,
  type ConfigItemHistoryVO,
  type ConfigItemVO,
} from '../../api/config';
import { pageResult, renderPage } from '../../test/test-utils';

vi.mock('../../api/config', () => ({
  pageConfigItems: vi.fn(),
  createConfigItem: vi.fn(),
  updateConfigItem: vi.fn(),
  deleteConfigItem: vi.fn(),
  listConfigItemHistory: vi.fn(),
  rollbackConfigItem: vi.fn(),
}));

const CONFIG_PERMS = [
  'sys:config:add',
  'sys:config:edit',
  'sys:config:delete',
  'sys:config:rollback',
];

const item: ConfigItemVO = {
  id: '1',
  configKey: 'order.pay.timeout',
  configValue: '30',
  valueType: 'NUMBER',
  description: '支付超时分钟数',
  version: 3,
  status: 1,
  updateTime: '2026-11-01 10:00:00',
};

const historyV3: ConfigItemHistoryVO = {
  id: 'h3',
  itemId: '1',
  configKey: 'order.pay.timeout',
  operation: 'UPDATE',
  oldValue: '20',
  newValue: '30',
  version: 3,
  operator: 'admin',
  createTime: '2026-11-01 10:00:00',
};

const historyV1: ConfigItemHistoryVO = {
  id: 'h1',
  itemId: '1',
  configKey: 'order.pay.timeout',
  operation: 'CREATE',
  oldValue: null,
  newValue: '10',
  version: 1,
  operator: 'admin',
  createTime: '2026-10-30 09:00:00',
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(pageConfigItems).mockResolvedValue(pageResult([item]));
  vi.mocked(listConfigItemHistory).mockResolvedValue([historyV3, historyV1]);
});

describe('ItemList 业务配置页', () => {
  it('渲染列表：键、值、类型标签、版本与状态', async () => {
    renderPage(<ItemList />, CONFIG_PERMS);

    expect(await screen.findByText('order.pay.timeout')).toBeInTheDocument();
    expect(screen.getByText('30')).toBeInTheDocument();
    expect(screen.getByText('NUMBER')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('启用')).toBeInTheDocument();
    expect(screen.getByText('业务配置项')).toBeInTheDocument();
  });

  it('变更历史抽屉：操作标签、变更对照、当前版本标记与可回滚行', async () => {
    renderPage(<ItemList />, CONFIG_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /历\s*史/ }));
    expect(await screen.findByText('变更历史：order.pay.timeout')).toBeInTheDocument();
    await waitFor(() => {
      expect(listConfigItemHistory).toHaveBeenCalledWith('1');
    });

    // v3：UPDATE 标签 + 变更对照 + 当前版本标记（不可回滚）
    expect(screen.getByText('更新')).toBeInTheDocument();
    expect(screen.getByText('20 → 30')).toBeInTheDocument();
    expect(screen.getByText('当前')).toBeInTheDocument();
    // v1：CREATE 标签 + 空值对照 + 可回滚按钮
    expect(screen.getByText('创建')).toBeInTheDocument();
    expect(screen.getByText('∅ → 10')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /回\s*滚/ })).toBeInTheDocument();
  });

  it('回滚到历史版本后产生新版本并刷新历史', async () => {
    const rolledBack: ConfigItemVO = { ...item, version: 4 };
    vi.mocked(rollbackConfigItem).mockResolvedValue(rolledBack);
    renderPage(<ItemList />, CONFIG_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /历\s*史/ }));
    fireEvent.click(await screen.findByRole('button', { name: /回\s*滚/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(rollbackConfigItem).toHaveBeenCalledWith('1', 1);
    });
    // 回滚后重新拉取历史（初次加载 + 回滚刷新 = 2 次）并刷新列表
    await waitFor(() => {
      expect(listConfigItemHistory).toHaveBeenCalledTimes(2);
    });
    await waitFor(() => {
      expect(pageConfigItems).toHaveBeenCalledTimes(2);
    });
  });

  it('编辑弹窗回填且配置键锁定，保存携带新值', async () => {
    vi.mocked(updateConfigItem).mockResolvedValue({ ...item, configValue: '60' });
    renderPage(<ItemList />, CONFIG_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    expect(await screen.findByText('编辑配置：order.pay.timeout')).toBeInTheDocument();
    const keyInput = screen.getByLabelText('配置键') as HTMLInputElement;
    expect(keyInput.value).toBe('order.pay.timeout');
    expect(keyInput).toBeDisabled();

    fireEvent.change(screen.getByLabelText('配置值'), { target: { value: '60' } });
    fireEvent.click(screen.getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => {
      expect(updateConfigItem).toHaveBeenCalledWith(
        '1',
        expect.objectContaining({ configValue: '60' }),
      );
    });
  });

  it('无权限时隐藏编辑/删除，历史始终可见', async () => {
    renderPage(<ItemList />, []);

    await screen.findByText('order.pay.timeout');
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /删\s*除/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增配置' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /历\s*史/ })).toBeInTheDocument();
  });
});
