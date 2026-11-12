// 网关访问名单页组件测试：白名单列表渲染 / 黑名单 Tab 权限裁剪 / 新增表单路径校验
import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import GatewayAccessList from './AccessList';
import {
  createGatewayWhitelist,
  listGatewayBlacklist,
  listGatewayWhitelist,
  type GatewayAccessRuleVO,
} from '../../api/gatewayAccess';
import { renderPage } from '../../test/test-utils';

vi.mock('../../api/gatewayAccess', () => ({
  listGatewayWhitelist: vi.fn(),
  createGatewayWhitelist: vi.fn(),
  updateGatewayWhitelist: vi.fn(),
  deleteGatewayWhitelist: vi.fn(),
  listGatewayBlacklist: vi.fn(),
  createGatewayBlacklist: vi.fn(),
  updateGatewayBlacklist: vi.fn(),
  deleteGatewayBlacklist: vi.fn(),
}));

const WHITELIST_PERMS = [
  'sys:gateway:whitelist:list',
  'sys:gateway:whitelist:add',
  'sys:gateway:whitelist:edit',
  'sys:gateway:whitelist:delete',
];

const ALL_PERMS = [
  ...WHITELIST_PERMS,
  'sys:gateway:blacklist:list',
  'sys:gateway:blacklist:add',
  'sys:gateway:blacklist:edit',
  'sys:gateway:blacklist:delete',
];

const whitelistRow: GatewayAccessRuleVO = {
  id: '1',
  pathPattern: '/auth/v1/login',
  httpMethod: '*',
  scope: 'PLATFORM',
  source: 'MIGRATION',
  remark: '登录',
  status: 1,
  sortNo: 1,
  updateTime: '2026-11-12 10:00:00',
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listGatewayWhitelist).mockResolvedValue([whitelistRow]);
  vi.mocked(listGatewayBlacklist).mockResolvedValue([]);
});

describe('GatewayAccessList 网关访问名单页', () => {
  it('渲染白名单路径、方法与来源标签', async () => {
    renderPage(<GatewayAccessList />, ALL_PERMS);

    expect(await screen.findByText('/auth/v1/login')).toBeInTheDocument();
    expect(screen.getByText('迁移')).toBeInTheDocument();
    expect(screen.getByText('登录')).toBeInTheDocument();
    expect(listGatewayWhitelist).toHaveBeenCalled();
  });

  it('仅白名单权限时不展示黑名单 Tab', async () => {
    renderPage(<GatewayAccessList />, WHITELIST_PERMS);

    await screen.findByText('/auth/v1/login');
    expect(screen.queryByRole('tab', { name: '黑名单' })).not.toBeInTheDocument();
  });

  it('新增表单校验路径必须以 / 开头', async () => {
    renderPage(<GatewayAccessList />, WHITELIST_PERMS);

    await screen.findByText('/auth/v1/login');
    fireEvent.click(screen.getByRole('button', { name: '新增白名单' }));

    const pathInput = await screen.findByLabelText('路径');
    fireEvent.change(pathInput, { target: { value: 'music/stream' } });
    fireEvent.click(screen.getByRole('button', { name: /确\s*定/ }));

    expect(await screen.findByText('路径必须以 / 开头')).toBeInTheDocument();
    expect(createGatewayWhitelist).not.toHaveBeenCalled();
  });
});
