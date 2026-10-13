import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 资源 / 角色 / 用户组三个管理页的行为断言。
 *
 * <p>与 `app.test.tsx` 同一套替身策略：**接口客户端换掉、`AccessPermissions` 保留真身** ——
 * 页面里的权限标识来自它，换掉就等于把"标识拼错"这类问题一起藏起来。
 *
 * <p>这里刻意只断言**跨端约定**层面的东西（哪个按钮由哪个标识控制、
 * 哪个限制是"刻意不做"而不是"还没做"），不复述渲染细节：
 * 后者改一次布局就要修一次测试，而它并不能证明什么。
 */
const mocks = vi.hoisted(() => ({
  userPage: vi.fn(),
  userRoleSources: vi.fn(),
  userOrgs: vi.fn(),
  userDataScope: vi.fn(),
  tenantPage: vi.fn(),
  resourcePage: vi.fn(),
  resourceTree: vi.fn(),
  resourceCreate: vi.fn(),
  rolePage: vi.fn(),
  roleGrants: vi.fn(),
  roleGrant: vi.fn(),
  roleMembers: vi.fn(),
  roleBindMember: vi.fn(),
  groupPage: vi.fn(),
  groupMembers: vi.fn(),
  groupBindMember: vi.fn(),
  orgTree: vi.fn(),
  orgDetail: vi.fn(),
  orgCreate: vi.fn(),
  orgUpdate: vi.fn(),
  orgMove: vi.fn(),
  orgRemove: vi.fn(),
  orgMembers: vi.fn(),
  orgBindMember: vi.fn(),
  orgSetPrimary: vi.fn(),
  orgRevokeMember: vi.fn(),
}));

vi.mock('common-api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('common-api')>();
  return {
    ...actual,
    createApiClient: () => ({
      http: {},
      auth: {},
      access: {
        users: {
          page: mocks.userPage,
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          resetPassword: vi.fn(),
          remove: vi.fn(),
          roleSources: mocks.userRoleSources,
          organizations: mocks.userOrgs,
          dataScope: mocks.userDataScope,
        },
        tenants: {
          page: mocks.tenantPage,
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          remove: vi.fn(),
        },
        resources: {
          page: mocks.resourcePage,
          tree: mocks.resourceTree,
          detail: vi.fn(),
          create: mocks.resourceCreate,
          update: vi.fn(),
          remove: vi.fn(),
        },
        roles: {
          page: mocks.rolePage,
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          remove: vi.fn(),
          listGrants: mocks.roleGrants,
          grant: mocks.roleGrant,
          revoke: vi.fn(),
          pageMembers: mocks.roleMembers,
          bindMember: mocks.roleBindMember,
          revokeMember: vi.fn(),
        },
        userGroups: {
          page: mocks.groupPage,
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          remove: vi.fn(),
          pageMembers: mocks.groupMembers,
          bindMember: mocks.groupBindMember,
          revokeMember: vi.fn(),
        },
        organizations: {
          page: vi.fn(),
          tree: mocks.orgTree,
          detail: mocks.orgDetail,
          create: mocks.orgCreate,
          update: mocks.orgUpdate,
          move: mocks.orgMove,
          remove: mocks.orgRemove,
          pageMembers: mocks.orgMembers,
          bindMember: mocks.orgBindMember,
          setPrimaryPosition: mocks.orgSetPrimary,
          revokeMember: mocks.orgRevokeMember,
        },
      },
    }),
  };
});

import { AdminViews } from 'common-api';

import App from '../src/App';
import { getStandaloneSession, initializeAuth } from '../src/auth';
import { connectPermissionSource } from '../src/auth/permissions';

/**
 * 管理员的完整权限集合 = 读写权限 **加** 六个标签页菜单码。
 *
 * <p>两类码的用途不同：菜单码决定标签页出不出来，读写权限决定点进去之后
 * 能看到哪些按钮。只给前者会得到一个"进得去但全是只读"的界面，
 * 只给后者会得到一个连标签页都没有的界面 —— 两种都不算管理员。
 */
const ADMIN = [
  'auth:user:read',
  'auth:resource:read',
  'auth:resource:create',
  'auth:resource:delete',
  'auth:role:read',
  'auth:role:update',
  'auth:user_group:read',
  'auth:user_group:update',
  'auth:org:read',
  'auth:org:create',
  'auth:org:update',
  'auth:org:delete',
  ...Object.values(AdminViews),
];

const USER_ROW = { id: 7, username: 'admin', displayName: '系统管理员', status: 'ENABLED' };
const ROLE_ROW = { id: 3, roleCode: 'SUPER_ADMIN', roleName: '超级管理员', status: 'ENABLED' };
const GROUP_ROW = { id: 5, groupCode: 'FIN_GROUP', groupName: '财务部', isEnabled: 1 };

const TREE = [
  {
    id: 1,
    resourceCode: 'auth',
    resourceName: '权限',
    resourceType: 'MODULE',
    requestScoped: false,
    isEnabled: 1,
    children: [
      {
        id: 2,
        resourceCode: 'auth:user:create',
        resourceName: '新建用户',
        resourceType: 'API',
        requestScoped: true,
        parentCode: 'auth',
        isEnabled: 1,
      },
    ],
  },
];

/**
 * 组织树替身：三层，且第二层有一个**停用**的兄弟节点。
 *
 * <p>形状与真实响应一致 —— `children` 递归、层级 / 路径 / 全称都由后端派生。
 * 停用那个是用来断言"状态照原样显示"的。
 */
const ORG_TREE = [
  {
    id: 1,
    orgCode: 'HQ',
    orgName: '蓝鲸集团',
    orgLevel: 1,
    codePath: '/',
    parentId: null,
    status: 'ENABLED',
    sortNo: 0,
    children: [
      {
        id: 2,
        orgCode: 'RD',
        orgName: '研发部',
        orgLevel: 2,
        codePath: '/1/',
        parentId: 1,
        status: 'ENABLED',
        sortNo: 0,
        children: [
          {
            id: 4,
            orgCode: 'RD_FE',
            orgName: '前端组',
            orgLevel: 3,
            codePath: '/1/2/',
            parentId: 2,
            status: 'ENABLED',
            sortNo: 0,
          },
        ],
      },
      {
        id: 3,
        orgCode: 'OPS',
        orgName: '运维部',
        orgLevel: 2,
        codePath: '/1/',
        parentId: 1,
        status: 'DISABLED',
        sortNo: 0,
      },
    ],
  },
];

const ORG_ROW = { records: [], total: 0, page: 1, size: 10, pages: 0 };

function signIn(permissions: string[]): void {
  getStandaloneSession().set({
    user: { userId: 7, username: 'tester', roles: ['SUPER_ADMIN'], permissions },
    tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
  });
}

let disconnect: () => void;

describe('权限管理后台 · 资源 / 角色 / 用户组', () => {
  beforeEach(() => {
    localStorage.clear();
    getStandaloneSession().clear();
    initializeAuth();
    disconnect = connectPermissionSource();

    for (const mock of Object.values(mocks)) mock.mockReset();

    mocks.userPage.mockResolvedValue({
      records: [USER_ROW],
      total: 1,
      page: 1,
      size: 10,
      pages: 1,
    });
    mocks.tenantPage.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 });
    mocks.resourceTree.mockResolvedValue(TREE);
    mocks.resourcePage.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 });
    mocks.rolePage.mockResolvedValue({
      records: [ROLE_ROW],
      total: 1,
      page: 1,
      size: 10,
      pages: 1,
    });
    mocks.roleGrants.mockResolvedValue([]);
    mocks.roleMembers.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 });
    mocks.groupPage.mockResolvedValue({
      records: [GROUP_ROW],
      total: 1,
      page: 1,
      size: 10,
      pages: 1,
    });
    mocks.groupMembers.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 });
    mocks.orgTree.mockResolvedValue(ORG_TREE);
    mocks.orgMembers.mockResolvedValue(ORG_ROW);
    mocks.userOrgs.mockResolvedValue([]);
    mocks.userDataScope.mockResolvedValue({});
  });

  it('资源页：树按 parentCode 的层级渲染，且不参与准入的节点被标出来', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('资源'));

    expect(await screen.findByTestId('resource-node-auth')).toBeInTheDocument();
    expect(screen.getByTestId('resource-node-auth:user:create')).toBeInTheDocument();
    // 派生字段来自后端（`requestScoped`），前端不重算 —— 重算就是第二份真相
    expect(screen.getAllByText('不参与准入').length).toBeGreaterThan(0);
  });

  it('资源页：只读账号看不到新建入口，但树照常可见', async () => {
    signIn(['auth:resource:read', AdminViews.RESOURCE]);
    render(<App />);

    /*
     * 只授了「资源」一个菜单码 ⇒ 默认选中的「用户」被过滤掉，回落到第一个可见标签页。
     * 这一句顺带钉住了"过滤不只发生在导航上，也发生在内容区" ——
     * 否则被过滤的标签页仍会被渲染出来，导航过滤就成了摆设。
     */
    expect(await screen.findByTestId('resource-node-auth')).toBeInTheDocument();
    expect(screen.queryByTestId('admin-tab-users')).not.toBeInTheDocument();

    // 看得见 —— 否则就分不清是"没权限"还是"没数据"
    expect(screen.queryByText('新建资源')).not.toBeInTheDocument();
    expect(screen.queryByText('加子资源')).not.toBeInTheDocument();
  });

  it('资源页：标识形状不对时提交按钮直接不可用（少一次注定被拒的往返）', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('资源'));
    await screen.findByTestId('resource-node-auth');
    fireEvent.click(screen.getByText('新建资源'));

    const submit = screen.getByRole('button', { name: '保存' });
    fireEvent.change(screen.getByRole('textbox', { name: /资源标识/ }), {
      target: { value: 'Auth:User:Create' },
    });
    expect(submit).toBeDisabled();

    fireEvent.change(screen.getByRole('textbox', { name: /资源标识/ }), {
      target: { value: 'auth:user:create' },
    });
    expect(submit).not.toBeDisabled();
  });

  it('角色页：资源授权对话框列出已授权，未选资源时不可提交', async () => {
    mocks.roleGrants.mockResolvedValue([
      {
        grantId: 11,
        resourceId: 2,
        resourceCode: 'auth:user:create',
        effect: 'ALLOW',
        requestScoped: true,
        effectiveNow: true,
      },
    ]);
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('角色'));
    await screen.findByTestId('role-row-SUPER_ADMIN');
    fireEvent.click(screen.getByText('资源授权'));

    expect(await screen.findByText('资源授权 · SUPER_ADMIN')).toBeInTheDocument();
    expect(await screen.findByText('auth:user:create')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '授予' })).toBeDisabled();
  });

  it('角色页：成员类型默认不选 —— 不给默认值，避免把"没选"变成"授给同号用户"', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('角色'));
    await screen.findByTestId('role-row-SUPER_ADMIN');
    fireEvent.click(screen.getByText('成员'));

    expect(await screen.findByText('成员 · SUPER_ADMIN')).toBeInTheDocument();

    const typeSelect = screen.getByRole('combobox', { name: /成员类型/ });
    expect((typeSelect as HTMLSelectElement).value).toBe('');
    expect(screen.getByRole('button', { name: '授予成员' })).toBeDisabled();

    // 选类型 → 成员下拉才有候选；选成员 → 提交才可用
    fireEvent.change(typeSelect, { target: { value: 'USER' } });
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /^成员$/ })).not.toBeDisabled();
    });
    fireEvent.change(screen.getByRole('combobox', { name: /^成员$/ }), {
      target: { value: '7' },
    });
    mocks.roleBindMember.mockResolvedValue({ grantId: 1, memberType: 'USER', memberId: 7 });
    fireEvent.click(screen.getByRole('button', { name: '授予成员' }));

    await waitFor(() => {
      expect(mocks.roleBindMember).toHaveBeenCalledWith(3, {
        memberType: 'USER',
        memberId: 7,
        effectiveFrom: undefined,
        effectiveTo: undefined,
      });
    });
  });

  it('角色页：数据范围下拉不提供 CUSTOM —— 它要 filter_json，而本仓未实现解析、界面也无处填写', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('角色'));
    await screen.findByTestId('role-row-SUPER_ADMIN');
    fireEvent.click(screen.getByText('编辑'));

    const select = await screen.findByRole('combobox', { name: /数据范围/ });
    const values = within(select)
      .getAllByRole('option')
      .map((option) => (option as HTMLOptionElement).value);

    // 四档**都真的能求值**：ALL / SELF 自足，DEPT 系列靠主岗解析出组织集合。
    // CUSTOM 不在此列 —— 摆出来就是"选了也配不了、配了也不生效"的假开关，
    // 与菜单权限那次对三维环境页面码的判断同源。删掉它就再也不该被加回来。
    expect(values).toEqual(['ALL', 'DEPT', 'DEPT_AND_CHILD', 'SELF']);
  });

  it('角色页：存量角色若是 CUSTOM，下拉把它作为历史值显示，而不是显示成空白', async () => {
    mocks.rolePage.mockResolvedValue({
      records: [{ ...ROLE_ROW, dataScope: 'CUSTOM' }],
      total: 1,
      page: 1,
      size: 10,
      pages: 1,
    });
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('角色'));
    await screen.findByTestId('role-row-SUPER_ADMIN');
    fireEvent.click(screen.getByText('编辑'));

    const select = (await screen.findByRole('combobox', {
      name: /数据范围/,
    })) as HTMLSelectElement;

    // 关键是不能是空白 —— 那会让管理员以为"数据范围丢了"，
    // 而实际只是它不再可配置（枚举里仍有它，后端也照原样存）。
    expect(select.value).toBe('CUSTOM');
    expect(within(select).getByRole('option', { name: /CUSTOM/ })).toBeInTheDocument();
  });

  it('用户组页：成员表单没有"类型"下拉 —— 用户组只接受 USER，不提供能选错的开关', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('用户组'));
    await screen.findByTestId('user-group-row-FIN_GROUP');
    fireEvent.click(screen.getByText('成员'));

    expect(await screen.findByText('组成员 · FIN_GROUP')).toBeInTheDocument();
    // "成员类型"这个标签只在角色那边存在（那边 USER / GROUP 都合法）
    expect(screen.queryByText('成员类型')).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox', { name: /成员$/ }), { target: { value: '7' } });
    mocks.groupBindMember.mockResolvedValue({ grantId: 1, memberType: 'USER', memberId: 7 });
    fireEvent.click(screen.getByRole('button', { name: '加入用户组' }));

    await waitFor(() => {
      expect(mocks.groupBindMember).toHaveBeenCalledWith(5, {
        memberType: 'USER',
        memberId: 7,
        effectiveFrom: undefined,
        effectiveTo: undefined,
      });
    });
  });

  it('反查：只读账号也能查角色来源，且停用的角色照原样返回', async () => {
    mocks.userRoleSources.mockResolvedValue([
      {
        grantId: 21,
        source: 'DIRECT',
        sourceLabel: '直接授予',
        roleId: 9,
        roleCode: 'TEMP_REVIEWER',
        roleName: '临时评审',
        roleStatus: 'DISABLED',
        effectiveNow: true,
      },
    ]);
    // 只有用户读权限 —— 反查归 `auth:user:read`，因此这个按钮必须出现
    signIn(['auth:user:read', AdminViews.USER]);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    expect(screen.getByText('角色来源')).toBeInTheDocument();
    fireEvent.click(screen.getByText('角色来源'));

    expect(await screen.findByTestId('role-source-TEMP_REVIEWER')).toBeInTheDocument();
    // 「停用」正是"为什么没有权限"的答案，过滤掉它就等于把答案删了
    expect(screen.getByText('停用')).toBeInTheDocument();
  });

  it('组织页：树按 children 递归渲染，层级与停用状态照原样取自后端', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('组织'));

    // 三层都在树上 —— 层级是后端派生的，前端不按 parentId 重算一遍
    expect(await screen.findByTestId('org-node-HQ')).toBeInTheDocument();
    expect(screen.getByTestId('org-node-RD')).toBeInTheDocument();
    expect(screen.getByTestId('org-node-RD_FE')).toBeInTheDocument();
    expect(screen.getByTestId('org-node-OPS')).toBeInTheDocument();

    // 层级标签直接渲染 orgLevel，不推算
    expect(screen.getByText('L3')).toBeInTheDocument();
    // 停用的兄弟节点照常显示，只多一个标签
    expect(screen.getByText('停用')).toBeInTheDocument();
  });

  it('组织页：只读账号看不到任何写入口，但整棵树照常可见', async () => {
    signIn(['auth:org:read', AdminViews.ORGANIZATION]);
    render(<App />);

    // 只授了「组织」一个菜单码 ⇒ 默认选中的「用户」被过滤掉，回落到这一个可见标签页
    await screen.findByTestId('org-node-HQ');

    // 看得见结构 —— 否则分不清是"没权限"还是"没数据"
    expect(screen.getByTestId('org-node-RD_FE')).toBeInTheDocument();

    // 十个写入口（每行五个 × 两行，加顶部一个）一个都不该出现
    expect(screen.queryByText('新建顶级组织')).not.toBeInTheDocument();
    expect(screen.queryByText('加下级')).not.toBeInTheDocument();
    expect(screen.queryByText('编辑')).not.toBeInTheDocument();
    expect(screen.queryByText('移动')).not.toBeInTheDocument();
    expect(screen.queryByText('成员')).not.toBeInTheDocument();
    expect(screen.queryByText('删除')).not.toBeInTheDocument();
  });

  it('组织页：移动的候选里剔除了自身与全部后代，且走独立接口而不是 update', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('组织'));
    await screen.findByTestId('org-node-RD');

    // 第二棵树（研发部）那一行的「移动」。
    // 用操作区自己的 testid 定位：`org-node-RD` 是**递归嵌套**的 li，
    // 它连同前端组那一行一起匹配，直接 within 会撞到多个「移动」按钮。
    fireEvent.click(within(screen.getByTestId('org-ops-RD')).getByText('移动'));

    expect(await screen.findByText('移动组织 · 研发部')).toBeInTheDocument();

    const select = screen.getByRole('combobox', { name: /新的上级组织/ }) as HTMLSelectElement;
    /*
     * 候选按**主键**断言，不看文字 —— 选项名里带缩进占位符，比文字更脆。
     *
     * 期望是 `['', '1', '3']`：空值（提升为顶级）、HQ(#1)、运维部(#3)。
     * 研发部自己(#2)与它的下级前端组(#4)都不在候选里 —— 那些目标必然被拒(40913)，
     * 界面这层只是不让正常操作的人选到一个注定报错的选项。
     */
    expect(Array.from(select.options).map((option) => option.value)).toEqual(['', '1', '3']);

    // 选一个真的合法的目标（运维部），断言它打到 `/parent` 这条独立路径
    mocks.orgMove.mockResolvedValue({ id: 2, orgCode: 'RD', orgName: '研发部' });
    fireEvent.change(select, { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: '确认移动' }));

    await waitFor(() => {
      expect(mocks.orgMove).toHaveBeenCalledWith(2, { parentId: 3 });
    });
    // 移动**没有**走 update：两者在服务端做的事不同，审计动作也不同
    expect(mocks.orgUpdate).not.toHaveBeenCalled();
  });

  it('组织页：编辑先回查详情 —— 树节点里没有的字段不能靠空值提交', async () => {
    /*
     * 这条断言钉的是一个具体的坑：树节点不带 `orgType` / `managerId` / `description` /
     * 三个联系方式。若直接拿树节点填表单，"我只改了个名字"会把描述与联系方式一起清掉，
     * 而且不报错。
     */
    mocks.orgDetail.mockResolvedValue({
      id: 4,
      orgCode: 'RD_FE',
      orgName: '前端组',
      parentId: 2,
      orgType: 'TEAM',
      sortNo: 0,
      status: 'ENABLED',
      description: '来自详情接口',
      contactName: '张三',
    });
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('组织'));
    await screen.findByTestId('org-node-RD_FE');
    fireEvent.click(within(screen.getByTestId('org-ops-RD_FE')).getByText('编辑'));

    expect(await screen.findByText('编辑组织 · RD_FE')).toBeInTheDocument();
    await waitFor(() => {
      expect(mocks.orgDetail).toHaveBeenCalledWith(4);
    });

    // 树里没有的两个字段，值来自详情
    expect(screen.getByRole('textbox', { name: /描述/ })).toHaveValue('来自详情接口');
    expect(screen.getByRole('textbox', { name: /组织类型/ })).toHaveValue('TEAM');
    // 编辑态下上级是只读的：改层级归「移动」，这里不放一个必然被拒的开关
    expect(screen.getByRole('textbox', { name: /上级组织/ })).toHaveAttribute('readonly');
  });

  it('组织页：挂成员时"是否主岗"缺省是不指定，设主岗走独立接口', async () => {
    mocks.orgMembers.mockResolvedValue({
      records: [
        {
          grantId: 31,
          memberId: 7,
          memberName: '系统管理员',
          primaryPosition: true,
          positionName: '负责人',
          effectiveNow: true,
        },
      ],
      total: 1,
      page: 1,
      size: 10,
      pages: 1,
    });
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('组织'));
    await screen.findByTestId('org-node-HQ');
    fireEvent.click(within(screen.getByTestId('org-ops-HQ')).getByText('成员'));

    expect(await screen.findByText('组织成员 · 蓝鲸集团')).toBeInTheDocument();
    expect(await screen.findByTestId('org-member-7')).toBeInTheDocument();
    expect(screen.getByTestId('org-primary-flag')).toBeInTheDocument();

    /*
     * 「是否主岗」默认必须是**不指定**（空值）。
     *
     * 服务端对"不传"的定义是：没有别的主岗则自动设为主岗，已有主岗则保持不变。
     * 若这里给一个默认勾选的"是"，就把这个有信息量的默认值压成了二选一；
     * 若默认给"否"，则会把一个人已有的主岗降掉 —— 静默搬动了他的数据范围。
     */
    const primaryChoice = screen.getByRole('combobox', {
      name: /是否主岗/,
    }) as HTMLSelectElement;
    expect(primaryChoice.value).toBe('');

    // 挂成员：没选人时提交不可用；选人后按"不指定"提交
    const memberSelect = screen.getByRole('combobox', { name: /成员/ });
    const submit = screen.getByRole('button', { name: '挂到组织下' });
    expect(submit).toBeDisabled();

    fireEvent.change(memberSelect, { target: { value: '7' } });
    mocks.orgBindMember.mockResolvedValue({ grantId: 32, memberId: 7 });
    fireEvent.click(submit);

    await waitFor(() => {
      expect(mocks.orgBindMember).toHaveBeenCalledWith(1, {
        memberId: 7,
        isPrimaryPosition: undefined,
        positionName: undefined,
        effectiveFrom: undefined,
        effectiveTo: undefined,
      });
    });

    // 「设主岗」是独立入口：已挂进来的人要改主岗只能用这个动作，它有自己的审计动作
    mocks.orgSetPrimary.mockResolvedValue({ grantId: 31, memberId: 7 });
    fireEvent.click(screen.getByText('重设主岗'));
    await waitFor(() => {
      expect(mocks.orgSetPrimary).toHaveBeenCalledWith(1, { userId: 7 });
    });
  });

  it('用户页数据范围：把 null 与空集合显示成两句不同的话，且 precise=false 写明兜底方向', async () => {
    /*
     * `visibleOrgIds` 的 `null` 与 `[]` 是两件事：
     * null = 这一档根本不按组织划定（ALL / SELF / CUSTOM）；
     * []   = 按组织划定了，但一个也没有（例如主岗组织被停用）。
     * 都渲染成"（无）"会让"配了范围但没生效"看起来像"本来就没有范围"。
     */
    mocks.userDataScope.mockResolvedValue({
      scopes: ['DEPT_AND_CHILD'],
      scope: 'DEPT_AND_CHILD',
      scopeLabel: '本部门及下级',
      roleCodes: ['DEPT_MANAGER'],
      allData: false,
      precise: false,
      visibleOrgIds: [],
      note: '该用户没有有效主岗，已按「仅本人」处理',
    });
    signIn(['auth:user:read', AdminViews.USER]);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('数据范围'));

    expect(await screen.findByTestId('user-scope-facts')).toBeInTheDocument();
    expect(screen.getByTestId('user-scope-value')).toHaveTextContent('本部门及下级');
    expect(screen.getByTestId('user-scope-precise')).toHaveTextContent('不可以');
    // 空集合要有自己的说法，不能与 null 混用
    expect(screen.getByTestId('user-scope-visible-orgs')).toHaveTextContent('空集合');
    expect(screen.getByTestId('user-scope-note')).toHaveTextContent('没有有效主岗');
  });
});

/**
 * 先 `cleanup()` 再断开订阅 —— `afterEach` 逆序执行，
 * 而 testing-library 的自动清理是 import 时注册的，会**后于**这个回调跑；
 * 那时组件还在树上，断开触发的发布会落在 act 之外，产出一堆指向错误位置的警告。
 */
afterEach(() => {
  cleanup();
  disconnect();
});
