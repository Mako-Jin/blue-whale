import { useCallback, useEffect, useState } from 'react';
import type {
  AccessResource,
  AccessRole,
  AccessRoleMember,
  AccessRoleMemberPage,
  AccessRolePage,
  AccessRoleResource,
  AccessRoleSaveRequest,
  AccessUser,
  AccessUserGroup,
} from 'common-api';

import { api } from '../../api';
import { usePermissionState } from '../../auth/hooks';
import { Dialog } from '../../components/Dialog';
import { PermissionButton } from '../../components/PermissionButton';
import { describeError } from '../../errors';
import { formatStatus, formatTime, formatWindow, parseDateTimeLocal } from '../../format';
import { PERM } from '../../permission-codes';

const PAGE_SIZE = 10;
const MEMBER_PAGE_SIZE = 10;

/**
 * 成员类型的下拉项。
 *
 * <p>**默认不选**，而不是默认 `USER`：后端的入参契约明确"必填、不给默认值"，
 * 前端的下拉框必须与它同形 —— 默认选中会把"没选"变成"悄悄授给一个同号用户"，
 * 这正是后端刻意不设默认值要避免的事。
 */
const MEMBER_TYPES = [
  { value: '', label: '（请选择）' },
  { value: 'USER', label: '用户' },
  { value: 'GROUP', label: '用户组' },
] as const;

/** 选项列表一次拉多少。资源 / 用户 / 用户组都是百量级（被权限码总量封顶） */
const OPTION_SIZE = 200;

type RoleForm = {
  roleCode: string;
  roleName: string;
  category: string;
  dataScope: string;
  status: string;
  isDefault: boolean;
  sortNo: string;
  description: string;
};

const EMPTY_FORM: RoleForm = {
  roleCode: '',
  roleName: '',
  category: '',
  dataScope: 'ALL',
  status: 'ENABLED',
  isDefault: false,
  sortNo: '0',
  description: '',
};

function toForm(role: AccessRole): RoleForm {
  return {
    roleCode: role.roleCode ?? '',
    roleName: role.roleName ?? '',
    category: role.category ?? '',
    dataScope: role.dataScope ?? 'ALL',
    status: role.status ?? 'ENABLED',
    isDefault: role.isDefault === 1,
    sortNo: String(role.sortNo ?? 0),
    description: role.description ?? '',
  };
}

function toBody(form: RoleForm): AccessRoleSaveRequest {
  return {
    // 角色编码建后不可改（后端 update 忽略它），但契约里必填，所以照样带上
    roleCode: form.roleCode.trim(),
    roleName: form.roleName.trim(),
    category: form.category.trim() || undefined,
    dataScope: form.dataScope,
    status: form.status,
    // 布尔转 0/1：契约里是 Integer，而"不传"在更新时表示**不改这一列**
    // （后端按字段是否为 null 决定要不要 SET），所以取消勾选必须显式传 0，
    // 传 undefined 会让"取消默认角色"静默地不生效
    isDefault: form.isDefault ? 1 : 0,
    sortNo: Number(form.sortNo) || 0,
    description: form.description.trim() || undefined,
  };
}

/**
 * 角色管理页 —— 「谁能做什么」的第二段，也是**授权真正挂载的地方**。
 *
 * <p>角色下挂两样东西，各自一个对话框：
 * <ul>
 *   <li><b>资源授权</b>（`/roles/{id}/resources`）—— 这个角色能做什么；</li>
 *   <li><b>成员</b>（`/roles/{id}/members`）—— 谁持有这个角色（用户 / 用户组）。</li>
 * </ul>
 * 两者都是"改角色"，因此都复用 <code>{PERM.ROLE_UPDATE}</code>：授角色给人本质上
 * 就是改这个角色的持有者。但服务端的**审计动作是分开的**
 * （`auth.role.grant_member` / `revoke_member`），所以"谁把人提上来了"依然查得到。
 *
 * <p><b>表单只摆出"配了真的会生效"的字段</b>，这条界线比它看起来重要：
 * <ul>
 *   <li><b>数据范围</b>（`dataScope`）已参与求值（用户页的「数据范围反查」可见结果），
 *       但本仓尚无业务数据表 —— 目前只有审计查询按它收窄（非 ALL 即「仅本人」），
 *       DEPT 系列算出的组织集合还没有查询消费它。这四档照常提供：它们**各自都是真的**，
 *       只是"有没有落点"取决于数据模型，而不是取决于这一页；</li>
 *   <li><b>`CUSTOM` 不提供</b>：它要解析后端的 `filter_json`，而本仓未实现、
 *       本页也没有填写自定义条件的输入框 —— 摆出来就是"选了也配不了、配了也不生效"；</li>
 *   <li><b>继承策略</b>（`inheritStrategy`）、**`filterJson`** 不进表单：只存不用的字段
 *       不给它配的样子，否则管理员配完以为生效、排查时才发现从来没被读过。
 *       界面上标出来，比让人配了之后以为生效更诚实；</li>
 *   <li><b>`isDefault` 现在进来了</b> —— 它此前就列在上一行里。它不是被"放开"的，
 *       而是**先有了消费方**：建号时会按它自动授予（ADR-0049），
 *       于是"配了真的会生效"这条界线自己把它纳了进来。
 *       顺序是**先实现、后摆出来**；反过来做，它就是下一条 `CUSTOM`。</li>
 * </ul>
 */
export function RolesPage() {
  const permission = usePermissionState();

  const [page, setPage] = useState<AccessRolePage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const [editing, setEditing] = useState<AccessRole | null>(null);
  const [creating, setCreating] = useState(false);
  const [removing, setRemoving] = useState<AccessRole | null>(null);
  const [granting, setGranting] = useState<AccessRole | null>(null);
  const [membering, setMembering] = useState<AccessRole | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const reload = useCallback(() => setTick((value) => value + 1), []);

  useEffect(() => {
    if (!permission.ready) return undefined;

    let alive = true;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const result = await api.access.roles.page({
          page: pageNo,
          size: PAGE_SIZE,
          keyword: keyword || undefined,
        });
        if (alive) setPage(result);
      } catch (cause) {
        if (!alive) return;
        setPage(null);
        setError(describeError(cause));
      } finally {
        if (alive) setLoading(false);
      }
    };

    void load();

    return () => {
      alive = false;
    };
  }, [permission.ready, pageNo, keyword, tick]);

  const closeDialog = (): void => {
    setCreating(false);
    setEditing(null);
  };

  const submitForm = async (form: RoleForm, target: AccessRole | null): Promise<void> => {
    setBusy(true);
    setDialogError(null);
    try {
      const body = toBody(form);
      if (target) {
        await api.access.roles.update(target.id as number, body);
        setNotice(`已保存角色「${body.roleCode}」`);
      } else {
        await api.access.roles.create(body);
        setNotice(`已新建角色「${body.roleCode}」`);
      }
      closeDialog();
      reload();
    } catch (cause) {
      setDialogError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const confirmRemove = async (): Promise<void> => {
    if (!removing) return;
    setBusy(true);
    setDialogError(null);
    try {
      await api.access.roles.remove(removing.id as number);
      setNotice(`已删除角色「${removing.roleCode}」`);
      setRemoving(null);
      reload();
    } catch (cause) {
      setDialogError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const records = page?.records ?? [];

  return (
    <section className="panel" data-testid="roles-panel">
      <header className="panel__head">
        <div>
          <h2 className="panel__title">角色</h2>
          <p className="panel__hint">
            角色编码租户内唯一、<strong>建后不可修改</strong>。 操作入口按权限显隐：新建需要{' '}
            <code>{PERM.ROLE_CREATE}</code>， 编辑 / 授权 / 挂成员需要{' '}
            <code>{PERM.ROLE_UPDATE}</code>，删除需要 <code>{PERM.ROLE_DELETE}</code>。
          </p>
        </div>
        <div className="panel__actions">
          <PermissionButton
            permission={PERM.ROLE_CREATE}
            variant="primary"
            className="roles__create"
            onClick={() => {
              setDialogError(null);
              setCreating(true);
            }}
          >
            新建角色
          </PermissionButton>
        </div>
      </header>

      <form
        className="toolbar"
        onSubmit={(event) => {
          event.preventDefault();
          setPageNo(1);
          setKeyword(keywordInput.trim());
        }}
      >
        <input
          className="input roles__keyword"
          name="keyword"
          placeholder="按角色编码 / 名称搜索"
          value={keywordInput}
          onChange={(event) => setKeywordInput(event.target.value)}
        />
        <button type="submit" className="btn">
          搜索
        </button>
        <button
          type="button"
          className="btn"
          onClick={() => {
            setKeywordInput('');
            setKeyword('');
            setPageNo(1);
            reload();
          }}
        >
          重置
        </button>
        <span className="toolbar__spacer" />
        <span className="toolbar__meta" data-testid="roles-total">
          共 {page?.total ?? 0} 条 · 第 {page?.page ?? pageNo} / {page?.pages ?? 0} 页
        </span>
      </form>

      {notice ? (
        <p className="banner banner--ok" data-testid="roles-notice">
          {notice}
        </p>
      ) : null}

      {error ? (
        <p className="banner banner--error" data-testid="roles-error">
          {error}
        </p>
      ) : null}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>编码</th>
              <th>名称</th>
              <th>分类</th>
              <th>状态</th>
              <th>更新时间</th>
              <th className="table__ops">操作</th>
            </tr>
          </thead>
          <tbody>
            {records.length === 0 ? (
              <tr>
                <td className="table__empty" colSpan={7}>
                  {loading ? '加载中…' : '没有数据'}
                </td>
              </tr>
            ) : (
              records.map((role) => (
                <tr key={String(role.id)} data-testid={`role-row-${String(role.roleCode)}`}>
                  <td className="mono">{role.id}</td>
                  <td className="mono">{role.roleCode}</td>
                  <td>{role.roleName ?? '—'}</td>
                  <td>{role.category ?? '—'}</td>
                  <td>
                    <span className={`tag tag--${role.status === 'ENABLED' ? 'on' : 'off'}`}>
                      {formatStatus(role.status)}
                    </span>
                  </td>
                  <td className="mono">{formatTime(role.updateTime)}</td>
                  <td className="table__ops">
                    <PermissionButton
                      permission={PERM.ROLE_UPDATE}
                      className="roles__grants"
                      onClick={() => {
                        setDialogError(null);
                        setGranting(role);
                      }}
                    >
                      资源授权
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.ROLE_UPDATE}
                      className="roles__members"
                      onClick={() => {
                        setDialogError(null);
                        setMembering(role);
                      }}
                    >
                      成员
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.ROLE_UPDATE}
                      className="roles__edit"
                      onClick={() => {
                        setDialogError(null);
                        setEditing(role);
                      }}
                    >
                      编辑
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.ROLE_DELETE}
                      variant="danger"
                      className="roles__remove"
                      onClick={() => {
                        setDialogError(null);
                        setRemoving(role);
                      }}
                    >
                      删除
                    </PermissionButton>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="pager">
        <button
          type="button"
          className="btn"
          disabled={pageNo <= 1}
          onClick={() => setPageNo((value) => Math.max(1, value - 1))}
        >
          上一页
        </button>
        <button
          type="button"
          className="btn"
          disabled={pageNo >= (page?.pages ?? 0)}
          onClick={() => setPageNo((value) => value + 1)}
        >
          下一页
        </button>
      </div>

      {creating || editing ? (
        <RoleFormDialog
          key={editing ? `edit-${editing.id}` : 'create'}
          initial={editing}
          busy={busy}
          error={dialogError}
          onClose={closeDialog}
          onSubmit={(form) => submitForm(form, editing)}
        />
      ) : null}

      {granting ? (
        <RoleGrantsDialog role={granting} onClose={() => setGranting(null)} onChanged={reload} />
      ) : null}

      {membering ? (
        <RoleMembersDialog role={membering} onClose={() => setMembering(null)} onChanged={reload} />
      ) : null}

      {removing ? (
        <Dialog
          title="删除角色"
          onClose={() => setRemoving(null)}
          footer={
            <>
              <button type="button" className="btn" onClick={() => setRemoving(null)}>
                取消
              </button>
              <button
                type="button"
                className="btn btn--danger roles__remove-confirm"
                disabled={busy}
                onClick={confirmRemove}
              >
                确认删除
              </button>
            </>
          }
        >
          <p>
            将删除角色 <strong>{removing.roleCode}</strong>（ID {removing.id}）。
          </p>
          <p className="form__tip">
            系统内置角色（40917）、有子角色（40913）、仍被成员持有（40916）时会被拒绝。
            <strong>这个角色自己的资源授权会随删除一并撤销</strong> ——
            成员清空之后那些授权不再影响任何人，留着只会让下次复活它时旧权限悄悄回来。
          </p>
          {dialogError ? <p className="banner banner--error">{dialogError}</p> : null}
        </Dialog>
      ) : null}
    </section>
  );
}

/** 新建 / 编辑共用一个表单；`initial` 为空即新建 */
function RoleFormDialog({
  initial,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  initial: AccessRole | null;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (form: RoleForm) => void;
}) {
  const [form, setForm] = useState<RoleForm>(() => (initial ? toForm(initial) : EMPTY_FORM));
  const update = (patch: Partial<RoleForm>): void => setForm((prev) => ({ ...prev, ...patch }));

  return (
    <Dialog
      title={initial ? `编辑角色 · ${initial.roleCode}` : '新建角色'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
          <button
            type="submit"
            form="role-form"
            className="btn btn--primary roles__submit"
            disabled={busy}
          >
            {busy ? '提交中…' : '保存'}
          </button>
        </>
      }
    >
      <form
        id="role-form"
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit(form);
        }}
      >
        <label className="form__row">
          <span className="form__label">角色编码</span>
          <input
            className="input"
            name="roleCode"
            placeholder="DEPT_MANAGER"
            value={form.roleCode}
            disabled={Boolean(initial)}
            onChange={(event) => update({ roleCode: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">角色名称</span>
          <input
            className="input"
            name="roleName"
            value={form.roleName}
            onChange={(event) => update({ roleName: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">分类</span>
          <input
            className="input"
            name="category"
            placeholder="SYSTEM / BUSINESS / CUSTOM"
            value={form.category}
            // 系统内置角色不可改分类（改分类 = 先降级再删的绕过），由服务端拦
            onChange={(event) => update({ category: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">状态</span>
          <select
            className="input"
            name="status"
            value={form.status}
            onChange={(event) => update({ status: event.target.value })}
          >
            <option value="ENABLED">启用</option>
            <option value="DISABLED">停用</option>
          </select>
        </label>
        {/*
         * 这一行是 `div` 而不是本页其余各行的 `label`：其余各行是"点标签 = 聚焦输入框"，
         * 勾选框却是"点标签 = 改变取值"。让整行可点意味着点到"默认角色"这四个字
         * 就会把值改掉，而管理员可能只是想看看它是什么。把 label 收进勾选框自己那一截，
         * 只有"勾选框 + 说明文字"可点。
         */}
        <div className="form__row">
          <span className="form__label">默认角色</span>
          <label className="form__check">
            <input
              type="checkbox"
              name="isDefault"
              checked={form.isDefault}
              onChange={(event) => update({ isDefault: event.target.checked })}
            />
            <span>本租户新建的账号自动授予这个角色</span>
          </label>
        </div>
        <label className="form__row">
          <span className="form__label">数据范围</span>
          <select
            className="input"
            name="dataScope"
            value={form.dataScope}
            onChange={(event) => update({ dataScope: event.target.value })}
          >
            {/*
             * 候选档位 = `DataScope` 枚举里**真正能被求值**的那些。
             *
             * `CUSTOM` 刻意不在其中：它要解析后端 `filter_json` 里的表达式，而本仓未实现，
             * 本页也没有填写自定义条件的输入框 —— 摆出来就是一个"选了也配不了、
             * 配了也不生效"的假开关。上一轮修菜单权限时对三维环境的页面码做过同一个判断。
             *
             * 但它仍是**合法的线上取值**（枚举里有、库里有），而且本页曾经提供过它，
             * 所以已存在的角色可能正是 CUSTOM。把当前值作为一条只读选项渲染出来，
             * 好过让下拉显示空白 —— 那会让管理员以为"数据范围丢了"，
             * 实际只是它不再可配置。改成别的档之后就再也选不回来，这是有意的。
             */}
            {form.dataScope === 'CUSTOM' ? (
              <option value="CUSTOM">CUSTOM 自定义（历史值，已不再支持配置）</option>
            ) : null}
            <option value="ALL">ALL 全部</option>
            <option value="DEPT">DEPT 本部门</option>
            <option value="DEPT_AND_CHILD">DEPT_AND_CHILD 本部门及下级</option>
            <option value="SELF">SELF 仅本人</option>
          </select>
        </label>
        <label className="form__row">
          <span className="form__label">排序号</span>
          <input
            className="input"
            name="sortNo"
            type="number"
            value={form.sortNo}
            onChange={(event) => update({ sortNo: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">描述</span>
          <input
            className="input"
            name="description"
            value={form.description}
            onChange={(event) => update({ description: event.target.value })}
          />
        </label>
        {/*
         * 这一段（以及本页其余多行提示）的行尾一律带 `<br />`，看起来多余。
         * 它防的是 JSX 的一条渲染规则：**同一段文本里的换行会被折成一个空格**，
         * 于是中文句子里会凭空多出词间 / 标点后的空格，而 prettier 还会按 printWidth
         * 重新折行、把这些空格挪到别处去（`0048` 踩过一次）。
         * 元素隔在中间就没事 —— 紧贴标签的、含换行的空白会被 JSX 丢掉。
         * 所以规矩是"每一行都要有东西隔开"，而不是"写完记得看一眼"。
         */}
        <p className="form__tip">
          数据范围<strong>已参与求值</strong>，但目前只有审计查询按它收窄
          <br />（<strong>非 ALL 即「仅本人」</strong>）—— DEPT 系列算出的组织集合还没有查询消费它。
          <br />
          角色继承仍未实现，因此这里不提供继承策略：只存不用的字段不摆出可配的样子。
          <br />
          默认角色<strong>已经生效</strong>（ADR-0049）：它此前也是"只存不用"，
          <br />
          是先补上了消费方（建号时按它自动挂成员）才摆出来的。
          <br />
          它只影响<strong>之后</strong>新建的账号，已建账号不被追溯；
          <br />
          角色若已停用，自动授予会跳过它 —— 建号仍会成功，
          <br />
          但响应里会如实说明"一个角色都没挂上"，不会静默。
        </p>
        {error ? <p className="banner banner--error">{error}</p> : null}
      </form>
    </Dialog>
  );
}

/** 角色的资源授权：列已授权、授新的、撤销旧的 */
function RoleGrantsDialog({
  role,
  onClose,
  onChanged,
}: {
  role: AccessRole;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [grants, setGrants] = useState<AccessRoleResource[]>([]);
  const [options, setOptions] = useState<AccessResource[]>([]);
  const [resourceId, setResourceId] = useState('');
  const [effect, setEffect] = useState('ALLOW');
  const [priority, setPriority] = useState('0');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let alive = true;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        // 资源选项取平铺列表而不是树：授权要的是 resourceId，平铺一次给全，不必前端组装层级
        const [list, resourcePage] = await Promise.all([
          api.access.roles.listGrants(role.id as number),
          api.access.resources.page({ page: 1, size: OPTION_SIZE }),
        ]);
        if (!alive) return;
        setGrants(list);
        setOptions(resourcePage.records ?? []);
      } catch (cause) {
        if (alive) setError(describeError(cause));
      } finally {
        if (alive) setLoading(false);
      }
    };
    void load();
    return () => {
      alive = false;
    };
  }, [role.id, tick]);

  const submit = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await api.access.roles.grant(role.id as number, {
        resourceId: Number(resourceId),
        effect,
        priority: Number(priority) || 0,
        effectiveFrom: parseDateTimeLocal(from),
        effectiveTo: parseDateTimeLocal(to),
      });
      setResourceId('');
      setFrom('');
      setTo('');
      setTick((value) => value + 1);
      onChanged();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (resource: AccessRoleResource): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await api.access.roles.revoke(role.id as number, { resourceId: resource.resourceId ?? -1 });
      setTick((value) => value + 1);
      onChanged();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      title={`资源授权 · ${role.roleCode}`}
      onClose={onClose}
      footer={
        <button type="button" className="btn" onClick={onClose}>
          关闭
        </button>
      }
    >
      {error ? <p className="banner banner--error">{error}</p> : null}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>资源标识</th>
              <th>效果</th>
              <th>生效区间</th>
              <th className="table__ops">操作</th>
            </tr>
          </thead>
          <tbody>
            {grants.length === 0 ? (
              <tr>
                <td className="table__empty" colSpan={4}>
                  {loading ? '加载中…' : '尚未授予任何资源'}
                </td>
              </tr>
            ) : (
              grants.map((grant) => (
                <tr key={String(grant.grantId)}>
                  <td className="mono">
                    {grant.resourceCode}
                    {/* 不参与准入的资源标出来：授了菜单 ≠ 授权了接口 */}
                    {grant.requestScoped === false ? (
                      <span className="tag tag--off">不参与准入</span>
                    ) : null}
                  </td>
                  <td>
                    <span className={`tag tag--${grant.effect === 'DENY' ? 'off' : 'on'}`}>
                      {grant.effect === 'DENY' ? '拒绝' : '允许'}
                    </span>
                  </td>
                  <td className="mono">
                    {formatWindow(grant.effectiveFrom, grant.effectiveTo)}
                    {grant.effectiveNow === false ? (
                      <span className="tag tag--off">已失效</span>
                    ) : null}
                  </td>
                  <td className="table__ops">
                    <PermissionButton
                      permission={PERM.ROLE_UPDATE}
                      variant="danger"
                      className="roles__revoke-grant"
                      onClick={() => void revoke(grant)}
                    >
                      撤销
                    </PermissionButton>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <label className="form__row">
          <span className="form__label">资源</span>
          <select
            className="input roles__grant-resource"
            name="resourceId"
            value={resourceId}
            onChange={(event) => setResourceId(event.target.value)}
          >
            <option value="">（请选择）</option>
            {options.map((resource) => (
              <option key={String(resource.id)} value={String(resource.id)}>
                {resource.resourceCode}
              </option>
            ))}
          </select>
        </label>
        <label className="form__row">
          <span className="form__label">效果</span>
          <select
            className="input"
            name="effect"
            value={effect}
            onChange={(event) => setEffect(event.target.value)}
          >
            <option value="ALLOW">ALLOW 允许</option>
            <option value="DENY">DENY 显式拒绝（优先）</option>
          </select>
        </label>
        <label className="form__row">
          <span className="form__label">优先级</span>
          <input
            className="input"
            name="priority"
            type="number"
            value={priority}
            onChange={(event) => setPriority(event.target.value)}
          />
        </label>
        <label className="form__row">
          <span className="form__label">生效起</span>
          <input
            className="input"
            name="effectiveFrom"
            type="datetime-local"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </label>
        <label className="form__row">
          <span className="form__label">生效止</span>
          <input
            className="input"
            name="effectiveTo"
            type="datetime-local"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </label>
        <p className="form__tip">
          生效区间留空即"立即生效 / 长期有效"。到期的授权不需要定时任务清理 —— 判定 SQL
          每次都按当前时刻过滤。
        </p>
        <div className="form__actions">
          <button
            type="submit"
            className="btn btn--primary roles__grant-submit"
            disabled={busy || !resourceId}
          >
            {busy ? '提交中…' : '授予'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

/** 角色的成员：列成员、加成员、解除 */
function RoleMembersDialog({
  role,
  onClose,
  onChanged,
}: {
  role: AccessRole;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [page, setPage] = useState<AccessRoleMemberPage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [memberType, setMemberType] = useState('');
  const [boundType, setBoundType] = useState('');
  const [memberId, setMemberId] = useState('');
  const [users, setUsers] = useState<AccessUser[]>([]);
  const [groups, setGroups] = useState<AccessUserGroup[]>([]);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let alive = true;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const result = await api.access.roles.pageMembers(role.id as number, {
          page: pageNo,
          size: MEMBER_PAGE_SIZE,
          memberType: memberType || undefined,
        });
        if (alive) setPage(result);
      } catch (cause) {
        if (alive) setError(describeError(cause));
      } finally {
        if (alive) setLoading(false);
      }
    };
    void load();
    return () => {
      alive = false;
    };
  }, [role.id, pageNo, memberType, tick]);

  /*
   * 候选成员按选中的类型懒加载。
   *
   * 不一次把用户与用户组都拉下来：这两张表都可能是这个租户里最大的两张表，
   * 而"授成员"是一个低频动作。只在真的要选的时候拉，并且只拉出现在选项里的那一种。
   */
  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        if (boundType === 'USER' && users.length === 0) {
          const result = await api.access.users.page({ page: 1, size: OPTION_SIZE });
          if (alive) setUsers(result.records ?? []);
        }
        if (boundType === 'GROUP' && groups.length === 0) {
          const result = await api.access.userGroups.page({ page: 1, size: OPTION_SIZE });
          if (alive) setGroups(result.records ?? []);
        }
      } catch (cause) {
        if (alive) setError(describeError(cause));
      }
    };
    void load();
    return () => {
      alive = false;
    };
  }, [boundType, users.length, groups.length]);

  const submit = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await api.access.roles.bindMember(role.id as number, {
        memberType: boundType,
        memberId: Number(memberId),
        effectiveFrom: parseDateTimeLocal(from),
        effectiveTo: parseDateTimeLocal(to),
      });
      setBoundType('');
      setMemberId('');
      setFrom('');
      setTo('');
      setTick((value) => value + 1);
      onChanged();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (member: AccessRoleMember): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await api.access.roles.revokeMember(role.id as number, {
        memberType: member.memberType ?? '',
        memberId: member.memberId ?? -1,
      });
      setTick((value) => value + 1);
      onChanged();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const records = page?.records ?? [];

  return (
    <Dialog
      title={`成员 · ${role.roleCode}`}
      onClose={onClose}
      footer={
        <button type="button" className="btn" onClick={onClose}>
          关闭
        </button>
      }
    >
      {error ? <p className="banner banner--error">{error}</p> : null}

      <div className="toolbar">
        <label className="form__row">
          <span className="form__label">类型</span>
          <select
            className="input roles__member-filter"
            name="memberFilter"
            value={memberType}
            onChange={(event) => {
              setPageNo(1);
              setMemberType(event.target.value);
            }}
          >
            <option value="">全部</option>
            <option value="USER">用户</option>
            <option value="GROUP">用户组</option>
          </select>
        </label>
        <span className="toolbar__spacer" />
        <span className="toolbar__meta">
          共 {page?.total ?? 0} 条 · 第 {page?.page ?? pageNo} / {page?.pages ?? 0} 页
        </span>
      </div>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>类型</th>
              <th>成员</th>
              <th>生效区间</th>
              <th className="table__ops">操作</th>
            </tr>
          </thead>
          <tbody>
            {records.length === 0 ? (
              <tr>
                <td className="table__empty" colSpan={4}>
                  {loading ? '加载中…' : '没有成员'}
                </td>
              </tr>
            ) : (
              records.map((member) => (
                <tr key={String(member.grantId)}>
                  <td>{member.memberTypeLabel ?? member.memberType ?? '—'}</td>
                  <td>
                    {member.memberName ?? '（成员已不存在）'}
                    <span className="mono"> #{member.memberId}</span>
                  </td>
                  <td className="mono">
                    {formatWindow(member.effectiveFrom, member.effectiveTo)}
                    {member.effectiveNow === false ? (
                      <span className="tag tag--off">已失效</span>
                    ) : null}
                  </td>
                  <td className="table__ops">
                    <PermissionButton
                      permission={PERM.ROLE_UPDATE}
                      variant="danger"
                      className="roles__revoke-member"
                      onClick={() => void revoke(member)}
                    >
                      解除
                    </PermissionButton>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="pager">
        <button
          type="button"
          className="btn"
          disabled={pageNo <= 1}
          onClick={() => setPageNo((value) => Math.max(1, value - 1))}
        >
          上一页
        </button>
        <button
          type="button"
          className="btn"
          disabled={pageNo >= (page?.pages ?? 0)}
          onClick={() => setPageNo((value) => value + 1)}
        >
          下一页
        </button>
      </div>

      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <label className="form__row">
          <span className="form__label">成员类型</span>
          <select
            className="input roles__member-type"
            name="memberType"
            value={boundType}
            onChange={(event) => {
              setBoundType(event.target.value);
              setMemberId('');
            }}
          >
            {MEMBER_TYPES.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <label className="form__row">
          <span className="form__label">成员</span>
          <select
            className="input roles__member-id"
            name="memberId"
            value={memberId}
            disabled={!boundType}
            onChange={(event) => setMemberId(event.target.value)}
          >
            <option value="">（请先选择类型）</option>
            {boundType === 'USER'
              ? users.map((user) => (
                  <option key={String(user.id)} value={String(user.id)}>
                    {user.displayName || user.username}（#{user.id}）
                  </option>
                ))
              : groups.map((group) => (
                  <option key={String(group.id)} value={String(group.id)}>
                    {group.groupName}（#{group.id}）
                  </option>
                ))}
          </select>
        </label>
        <label className="form__row">
          <span className="form__label">生效起</span>
          <input
            className="input"
            name="effectiveFrom"
            type="datetime-local"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </label>
        <label className="form__row">
          <span className="form__label">生效止</span>
          <input
            className="input"
            name="effectiveTo"
            type="datetime-local"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </label>
        <p className="form__tip">
          成员必须与角色<strong>同租户</strong>：跨租户的关系在判定里永远匹配不上，等于静默不可达。
          类型与成员重复授予时改的是时间窗，不会堆出第二行。
        </p>
        <div className="form__actions">
          <button
            type="submit"
            className="btn btn--primary roles__member-submit"
            disabled={busy || !boundType || !memberId}
          >
            {busy ? '提交中…' : '授予成员'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
