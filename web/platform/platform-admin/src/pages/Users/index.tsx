import { useCallback, useEffect, useState } from 'react';
import type {
  AccessUser,
  AccessUserDataScope,
  AccessUserOrg,
  AccessUserPage,
  AccessUserRoleSource,
  AccessUserSaveRequest,
} from 'common-api';

import { api } from '../../api';
import { usePermissionState } from '../../auth/hooks';
import { Dialog } from '../../components/Dialog';
import { PermissionButton } from '../../components/PermissionButton';
import { describeError } from '../../errors';
import { formatStatus, formatTime, formatWindow } from '../../format';
import { PERM } from '../../permission-codes';

const PAGE_SIZE = 10;

type UserForm = {
  username: string;
  displayName: string;
  email: string;
  phone: string;
  password: string;
  status: string;
};

const EMPTY_FORM: UserForm = {
  username: '',
  displayName: '',
  email: '',
  phone: '',
  password: '',
  status: 'ENABLED',
};

function toForm(user: AccessUser): UserForm {
  return {
    username: user.username ?? '',
    displayName: user.displayName ?? '',
    email: user.email ?? '',
    phone: user.phone ?? '',
    // 修改时留空表示不改口令，因此编辑态**不回填**口令
    password: '',
    status: user.status ?? 'ENABLED',
  };
}

/**
 * 用户管理页 —— 按钮级权限的演示载体。
 *
 * <p>同一个页面、同一段代码，管理员看到完整的操作条，只读账号（审计员）只看得到列表。
 * 差异**不出现在代码里**，只出现在权限集合里 —— 这正是"判定单点、结果下发"的效果。
 */
export function UsersPage() {
  const permission = usePermissionState();

  const [page, setPage] = useState<AccessUserPage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const [editing, setEditing] = useState<AccessUser | null>(null);
  const [creating, setCreating] = useState(false);
  const [resetting, setResetting] = useState<AccessUser | null>(null);
  const [removing, setRemoving] = useState<AccessUser | null>(null);
  const [sourceOf, setSourceOf] = useState<AccessUser | null>(null);
  const [orgsOf, setOrgsOf] = useState<AccessUser | null>(null);
  const [scopeOf, setScopeOf] = useState<AccessUser | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const reload = useCallback(() => setTick((value) => value + 1), []);

  /*
   * 权限未就绪时不发请求。
   *
   * 不是为了省一次请求：独立运行时"会话还没建立"就等于没有令牌，
   * 这时候发出去必然 401，而 401 会触发一次刷新 —— 本该由启动流程做的事
   * 被一个页面 effect 抢了先。等 `ready` 再拉，时序就只有一个来源。
   */
  useEffect(() => {
    if (!permission.ready) return undefined;

    let alive = true;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const result = await api.access.users.page({
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

  const submitForm = async (form: UserForm, target: AccessUser | null): Promise<void> => {
    setBusy(true);
    setDialogError(null);
    try {
      const body: AccessUserSaveRequest = {
        username: form.username,
        displayName: form.displayName || undefined,
        email: form.email || undefined,
        phone: form.phone || undefined,
        status: form.status,
        // 新增必填、修改留空表示不改 —— 契约与后端服务端的判断一致
        password: form.password || (target ? undefined : ''),
      };
      if (target) {
        await api.access.users.update(target.id as number, body);
        setNotice(`已保存用户「${form.username}」`);
        setEditing(null);
      } else {
        await api.access.users.create(body);
        /*
         * 建号这条路的通知必须带上**下一步**，它不能只报"已新建"。
         *
         * 新账号**默认一个菜单码都没有**（ADR-0031 决策 2：默认不可见优于默认可见），
         * 而"建号"与"能用"之间因此隔着一步授权。只报"已新建用户「x」"的话，
         * 管理员会认为工作已经完成 —— 直到那个人登录进来说"里面是空的"才回头查，
         * 而那时现场已经不在管理员手上了。
         *
         * 同一页的重置通知就是按这个形状写的（"…该用户下次登录须改密"）：
         * 动作 + 它的后果。这里补的是后果 + 去哪处理。
         */
        setNotice(
          `已新建用户「${form.username}」。它默认没有任何菜单码 —— 到「角色」页授角色并把该账号挂为成员，否则它登录后看不到任何入口。`,
        );
        setCreating(false);
      }
      reload();
    } catch (cause) {
      setDialogError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const submitReset = async (password: string): Promise<void> => {
    if (!resetting) return;
    setBusy(true);
    setDialogError(null);
    try {
      await api.access.users.resetPassword(resetting.id as number, { password });
      setNotice(`已重置「${resetting.username}」的口令，该用户下次登录须改密`);
      setResetting(null);
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
      await api.access.users.remove(removing.id as number);
      setNotice(`已删除用户「${removing.username}」`);
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
    <section className="panel" data-testid="users-panel">
      <header className="panel__head">
        <div>
          <h2 className="panel__title">用户</h2>
          <p className="panel__hint">
            操作入口按权限标识显隐：新建需要 <code>{PERM.USER_CREATE}</code>，编辑/重置口令需要{' '}
            <code>{PERM.USER_UPDATE}</code>，删除需要 <code>{PERM.USER_DELETE}</code>。
          </p>
        </div>
        <div className="panel__actions">
          <PermissionButton
            permission={PERM.USER_CREATE}
            variant="primary"
            className="users__create"
            onClick={() => {
              setDialogError(null);
              setCreating(true);
            }}
          >
            新建用户
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
          className="input users__keyword"
          name="keyword"
          placeholder="按登录名 / 姓名 / 邮箱搜索"
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
        <span className="toolbar__meta" data-testid="users-total">
          共 {page?.total ?? 0} 条 · 第 {page?.page ?? pageNo} / {page?.pages ?? 0} 页
        </span>
      </form>

      {notice ? (
        <p className="banner banner--ok" data-testid="users-notice">
          {notice}
        </p>
      ) : null}

      {error ? (
        <p className="banner banner--error" data-testid="users-error">
          {error}
        </p>
      ) : null}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>登录名</th>
              <th>姓名</th>
              <th>邮箱</th>
              <th>状态</th>
              <th>最近登录</th>
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
              records.map((user) => (
                <tr key={String(user.id)} data-testid={`user-row-${String(user.username)}`}>
                  <td className="mono">{user.id}</td>
                  <td className="mono">{user.username}</td>
                  <td>{user.displayName ?? '—'}</td>
                  <td>{user.email ?? '—'}</td>
                  <td>
                    <span className={`tag tag--${user.status === 'ENABLED' ? 'on' : 'off'}`}>
                      {formatStatus(user.status)}
                    </span>
                  </td>
                  <td className="mono">{formatTime(user.lastLoginAt)}</td>
                  <td className="table__ops">
                    {/*
                      反查按钮归 `auth:user:read`（与列表、详情同一类权限），
                      因此它出现在**每一行**，包括只读账号那一行 —— 这正是有意的：
                      审计员能查"某人的权限从哪来"，却不能碰用户组配置。
                    */}
                    <PermissionButton
                      permission={PERM.USER_READ}
                      className="users__sources"
                      onClick={() => setSourceOf(user)}
                    >
                      角色来源
                    </PermissionButton>
                    {/*
                      组织归属与数据范围两个入口**都归 `auth:user:read`**（后端如此），
                      因此它们也与"角色来源"一样出现在每一行。

                      这条权限划分有一个可验证的后果，值得写在这里：组织**树**归
                      `auth:org:read`、个人归属归 `auth:user:read`，两者不互相蕴含 ——
                      只有 org:read 的人能改组织结构，却看不到"某个人在哪些部门"；
                      只有 user:read 的人能反查个人归属，却看不到树本身。
                      于是本页的这两个按钮与「组织」标签页是**两个独立的权限面**，
                      不是同一块功能的两个入口。
                    */}
                    <PermissionButton
                      permission={PERM.USER_READ}
                      className="users__orgs"
                      onClick={() => setOrgsOf(user)}
                    >
                      组织归属
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.USER_READ}
                      className="users__scope"
                      onClick={() => setScopeOf(user)}
                    >
                      数据范围
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.USER_UPDATE}
                      className="users__edit"
                      onClick={() => {
                        setDialogError(null);
                        setEditing(user);
                      }}
                    >
                      编辑
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.USER_UPDATE}
                      className="users__reset"
                      onClick={() => {
                        setDialogError(null);
                        setResetting(user);
                      }}
                    >
                      重置口令
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.USER_DELETE}
                      variant="danger"
                      className="users__remove"
                      onClick={() => {
                        setDialogError(null);
                        setRemoving(user);
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
          // 总页数由服务端算好（取整方式属业务约定），契约里它是可选字段，因此兜 0
          disabled={pageNo >= (page?.pages ?? 0)}
          onClick={() => setPageNo((value) => value + 1)}
        >
          下一页
        </button>
      </div>

      {creating || editing ? (
        <UserFormDialog
          key={editing ? `edit-${editing.id}` : 'create'}
          initial={editing}
          busy={busy}
          error={dialogError}
          onClose={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSubmit={(form) => submitForm(form, editing)}
        />
      ) : null}

      {resetting ? (
        <ResetPasswordDialog
          user={resetting}
          busy={busy}
          error={dialogError}
          onClose={() => setResetting(null)}
          onSubmit={submitReset}
        />
      ) : null}

      {removing ? (
        <Dialog
          title="删除用户"
          onClose={() => setRemoving(null)}
          footer={
            <>
              <button type="button" className="btn" onClick={() => setRemoving(null)}>
                取消
              </button>
              <button
                type="button"
                className="btn btn--danger users__remove-confirm"
                disabled={busy}
                onClick={confirmRemove}
              >
                确认删除
              </button>
            </>
          }
        >
          <p>
            将删除用户 <strong>{removing.username}</strong>（ID {removing.id}）。
          </p>
          {dialogError ? <p className="banner banner--error">{dialogError}</p> : null}
        </Dialog>
      ) : null}

      {sourceOf ? <RoleSourcesDialog user={sourceOf} onClose={() => setSourceOf(null)} /> : null}
      {orgsOf ? <UserOrgsDialog user={orgsOf} onClose={() => setOrgsOf(null)} /> : null}
      {scopeOf ? <UserDataScopeDialog user={scopeOf} onClose={() => setScopeOf(null)} /> : null}
    </section>
  );
}

/**
 * 用户的组织归属 —— 回答"他在哪些部门"。
 *
 * <p>与「角色来源」同一形状：**逐条、带状态、不过滤**。停用的组织、已过期的归属
 * 照原样返回 —— 排查"他配了本部门范围，为什么还是只看得到自己"时，
 * 答案通常就在 `orgStatus` 与 `effectiveNow` 这两列里（后端 `UserController`
 * 对这两个字段的注释说的就是这句话）。
 *
 * <p>本对话框是**只读**的：加人与移人都挂在 `/api/auth/orgs/{id}/members` 下，
 * 也就是「组织」标签页。这里不提供写入口，因为在"某个人"的主语下写成员关系，
 * 会让人看不出这次操作改的是哪个组织的成员集合。
 */
function UserOrgsDialog({ user, onClose }: { user: AccessUser; onClose: () => void }) {
  const [orgs, setOrgs] = useState<AccessUserOrg[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const list = await api.access.users.organizations(user.id as number);
        if (alive) setOrgs(list);
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
  }, [user.id]);

  return (
    <Dialog
      title={`组织归属 · ${user.username}`}
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
              <th>组织</th>
              <th>全称</th>
              <th>组织状态</th>
              <th>岗位</th>
              <th>生效区间</th>
            </tr>
          </thead>
          <tbody>
            {orgs.length === 0 ? (
              <tr>
                <td className="table__empty" colSpan={5}>
                  {loading ? '加载中…' : '没有任何组织归属'}
                </td>
              </tr>
            ) : (
              orgs.map((org) => (
                <tr key={String(org.grantId)} data-testid={`user-org-${String(org.orgCode)}`}>
                  <td className="mono">
                    {org.orgCode}
                    <span className="tree__name">{org.orgName ?? ''}</span>
                    {/*
                      主岗是 DEPT 系列**唯一**的取值来源，因此它必须与其它归属区分开：
                      一个用户可以在多个组织下有归属，但只有主岗那一条决定数据范围。
                    */}
                    {org.primaryPosition ? (
                      <span className="tag tag--on" data-testid="user-org-primary">
                        主岗
                      </span>
                    ) : null}
                  </td>
                  <td>{org.fullName ?? '—'}</td>
                  <td>
                    <span className={`tag tag--${org.orgStatus === 'ENABLED' ? 'on' : 'off'}`}>
                      {formatStatus(org.orgStatus)}
                    </span>
                  </td>
                  <td>
                    {org.positionName ?? '—'}
                    {org.relationType ? (
                      <span className="tree__name">{org.relationType}</span>
                    ) : null}
                  </td>
                  <td className="mono">
                    {formatWindow(org.effectiveFrom, org.effectiveTo)}
                    {org.effectiveNow === false ? (
                      <span className="tag tag--off">已失效</span>
                    ) : null}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="form__tip">
        <strong>停用的组织与已过期的归属照原样返回</strong>：它们恰恰是"为什么数据范围
        算不出来"的答案。判定侧只看此刻有效的那条主岗，两边刻意不合并。
      </p>
    </Dialog>
  );
}

/**
 * 数据范围反查 —— 回答"按现在这套配置，最终算出来是什么"。
 *
 * <p><b>它自 ADR-0025 起就有后端、前端刻意没接。</b>当时的理由很具体：
 * `DEPT` / `DEPT_AND_CHILD` 因为没有组织树可用，对每个用户都只能是
 * `precise = false`，挂出来等于给一个恒定的"无法精确求值"标签，
 * 什么也说明不了。组织树落地之后这个理由消失了 —— 现在它真的能区分
 * "档位不支持"与"这个人没有有效主岗"。
 *
 * <p>三件事必须分开显示，因为它们是最容易被混为一谈的：
 * <ol>
 *   <li><b>档位</b>（配置里有什么、最宽是哪一档、来自哪些角色）；</li>
 *   <li><b>能否精确求值</b>（`precise`）—— 为 false 时已接入的查询保守地按"仅本人"处理，
 *       这是**往窄兜底**，不是越权；</li>
 *   <li><b>可见组织集合</b>（`visibleOrgIds`）—— 注意 `null` 与 `[]` 是两件事，
 *       前者是"这一档根本不按组织划定"，后者是"划定了，但一个也没有"。</li>
 * </ol>
 */
function UserDataScopeDialog({ user, onClose }: { user: AccessUser; onClose: () => void }) {
  const [scope, setScope] = useState<AccessUserDataScope | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const result = await api.access.users.dataScope(user.id as number);
        if (alive) setScope(result);
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
  }, [user.id]);

  return (
    <Dialog
      title={`数据范围 · ${user.username}`}
      onClose={onClose}
      footer={
        <button type="button" className="btn" onClick={onClose}>
          关闭
        </button>
      }
    >
      {error ? <p className="banner banner--error">{error}</p> : null}
      {loading ? <p className="table__empty">加载中…</p> : null}

      {scope ? (
        <>
          <dl className="facts" data-testid="user-scope-facts">
            <dt>合并档位</dt>
            <dd>
              <span className="tag tag--on" data-testid="user-scope-value">
                {scope.scopeLabel ?? scope.scope ?? '（无）'}
              </span>
              {scope.scope ? <code className="mono"> {scope.scope}</code> : null}
              {scope.allData ? <span className="tag tag--off">放行整个租户</span> : null}
            </dd>

            <dt>配置里的档位</dt>
            <dd className="mono">
              {scope.scopes?.length ? scope.scopes.join(' · ') : '（没有任何角色带数据范围）'}
            </dd>

            <dt>来自哪些角色</dt>
            <dd className="mono">{scope.roleCodes?.length ? scope.roleCodes.join(' · ') : '—'}</dd>

            <dt>能否精确求值</dt>
            <dd>
              {/*
                这个标签是整页里最容易被误读的一处，所以两种状态都写了说明文字，
                而不是只显示 true / false。
              */}
              <span
                className={`tag tag--${scope.precise ? 'on' : 'off'}`}
                data-testid="user-scope-precise"
              >
                {scope.precise ? '可以' : '不可以'}
              </span>
              {scope.precise ? null : (
                <span className="tree__name">
                  已接入的查询保守地按「仅本人」处理 —— 比实际范围更窄，是安全的一侧
                </span>
              )}
            </dd>

            <dt>主岗组织</dt>
            <dd>
              {scope.primaryOrgId == null ? (
                <span data-testid="user-scope-primary-org">—</span>
              ) : (
                <span data-testid="user-scope-primary-org">
                  #{scope.primaryOrgId}
                  <span className="tree__name">
                    {scope.primaryOrgFullName ?? scope.primaryOrgName ?? ''}
                  </span>
                </span>
              )}
            </dd>

            <dt>可见组织集合</dt>
            <dd className="mono" data-testid="user-scope-visible-orgs">
              {/*
                `null` 与 `[]` 必须显示成两句不同的话。

                null = "这一档不是按组织划定的"（ALL / SELF / CUSTOM 都没有组织维度）；
                []   = "按组织划定了，但集合是空的"（例如主岗组织被停用）。
                把它们都渲染成"（无）"，会让"配了范围但没生效"看起来像"本来就没有范围"。
              */}
              {scope.visibleOrgIds == null
                ? '—（这一档不是按组织划定的）'
                : scope.visibleOrgIds.length === 0
                  ? '空集合（按组织划定，但一个也没有）'
                  : scope.visibleOrgIds.map((id) => `#${id}`).join(' ')}
            </dd>

            {scope.note ? (
              <>
                <dt>说明</dt>
                <dd data-testid="user-scope-note">{scope.note}</dd>
              </>
            ) : null}
          </dl>

          <p className="form__tip">
            <strong>这是求值结果，不是配置。</strong>要回答"这个范围是谁给的"请看
            「角色来源」，要回答"最终算成了什么"才看这里 —— 配置侧允许自相矛盾，
            求值结果不会，而"为什么没生效"的答案往往正是两者之间的差异。
          </p>
          <p className="form__tip">
            它目前只**求值并下发**：本仓还没有业务数据表，因此"按它过滤业务数据"
            没有落点。看到范围正确不等于某个查询真的在用它 —— 有没有落点，
            取决于消费方有没有把这个结果接进自己的查询条件。
          </p>
        </>
      ) : null}
    </Dialog>
  );
}

/**
 * 角色来源反查 —— 回答"这个人的权限是从哪来的"。
 *
 * <p>它与"当前权限集合"是两件事：判定侧给的是**此刻有效**的权限码（去重、过滤），
 * 这里给的是**配置里有哪些路径**（逐条、带来源）。
 * 因此**停用的角色 / 停用的用户组 / 已过期的授权照原样返回** ——
 * 一行"角色已停用"正是"为什么没有权限"的答案，把它过滤掉等于把答案删了。
 *
 * <p>经用户组授予的路径实际是**两条边**（角色↔组、组↔用户），各有自己的生效区间，
 * 所以这里分两列显示，而不是揉成一个"是否有效"。
 */
function RoleSourcesDialog({ user, onClose }: { user: AccessUser; onClose: () => void }) {
  const [sources, setSources] = useState<AccessUserRoleSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const list = await api.access.users.roleSources(user.id as number);
        if (alive) setSources(list);
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
  }, [user.id]);

  return (
    <Dialog
      title={`角色来源 · ${user.username}`}
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
              <th>来源</th>
              <th>角色</th>
              <th>角色状态</th>
              <th>来源用户组</th>
              <th>授权区间</th>
            </tr>
          </thead>
          <tbody>
            {sources.length === 0 ? (
              <tr>
                <td className="table__empty" colSpan={5}>
                  {loading ? '加载中…' : '没有任何角色来源'}
                </td>
              </tr>
            ) : (
              sources.map((source) => (
                <tr
                  key={String(source.grantId)}
                  data-testid={`role-source-${String(source.roleCode)}`}
                >
                  <td>
                    <span className={`tag tag--${source.source === 'GROUP' ? 'off' : 'on'}`}>
                      {source.sourceLabel ?? source.source ?? '—'}
                    </span>
                  </td>
                  <td className="mono">
                    {source.roleCode}
                    <span className="tree__name">{source.roleName ?? ''}</span>
                  </td>
                  <td>
                    <span className={`tag tag--${source.roleStatus === 'ENABLED' ? 'on' : 'off'}`}>
                      {formatStatus(source.roleStatus)}
                    </span>
                  </td>
                  <td>
                    {source.groupName ?? '—'}
                    {source.groupEnabled === 0 ? (
                      <span className="tag tag--off">组已停用</span>
                    ) : null}
                  </td>
                  <td className="mono">
                    {formatWindow(source.effectiveFrom, source.effectiveTo)}
                    {source.effectiveNow === false ? (
                      <span className="tag tag--off">已失效</span>
                    ) : null}
                    {/*
                      `source = DIRECT` 时第二条边**不存在**（不是"长期有效"），
                      因此整块不渲染，而不是显示成"长期有效"。
                    */}
                    {source.groupMemberEffectiveFrom != null ||
                    source.groupMemberEffectiveTo != null ? (
                      <>
                        <br />
                        组内：
                        {formatWindow(
                          source.groupMemberEffectiveFrom,
                          source.groupMemberEffectiveTo,
                        )}
                        {source.groupMemberEffectiveNow === false ? (
                          <span className="tag tag--off">组内已失效</span>
                        ) : null}
                      </>
                    ) : null}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="form__tip">
        <strong>停用与过期照原样返回</strong>：它们是"为什么没有权限"的答案。
        判定侧只认此刻有效的权限码，两边刻意不合并 —— 合并会让这份反查退化成权限集合的副本。
      </p>
    </Dialog>
  );
}

/** 新建 / 编辑共用一个表单；`initial` 为空即新建 */
function UserFormDialog({
  initial,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  initial: AccessUser | null;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (form: UserForm) => void;
}) {
  const [form, setForm] = useState<UserForm>(() => (initial ? toForm(initial) : EMPTY_FORM));
  const update = (patch: Partial<UserForm>): void => setForm((prev) => ({ ...prev, ...patch }));

  return (
    <Dialog
      title={initial ? `编辑用户 · ${initial.username}` : '新建用户'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
          <button
            type="submit"
            form="user-form"
            className="btn btn--primary users__submit"
            disabled={busy}
          >
            {busy ? '提交中…' : '保存'}
          </button>
        </>
      }
    >
      <form
        id="user-form"
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit(form);
        }}
      >
        <label className="form__row">
          <span className="form__label">登录名</span>
          <input
            className="input"
            name="username"
            value={form.username}
            // 登录名是登录凭据的一部分，后端 update 也不接受它
            disabled={Boolean(initial)}
            onChange={(event) => update({ username: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">姓名</span>
          <input
            className="input"
            name="displayName"
            value={form.displayName}
            onChange={(event) => update({ displayName: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">邮箱</span>
          <input
            className="input"
            name="email"
            value={form.email}
            onChange={(event) => update({ email: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">手机号</span>
          <input
            className="input"
            name="phone"
            value={form.phone}
            onChange={(event) => update({ phone: event.target.value })}
          />
        </label>
        {!initial ? (
          <label className="form__row">
            <span className="form__label">初始口令</span>
            <input
              className="input"
              name="password"
              type="password"
              value={form.password}
              onChange={(event) => update({ password: event.target.value })}
            />
          </label>
        ) : null}
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
        {error ? <p className="banner banner--error">{error}</p> : null}
      </form>
    </Dialog>
  );
}

function ResetPasswordDialog({
  user,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  user: AccessUser;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (password: string) => void;
}) {
  const [password, setPassword] = useState('');

  return (
    <Dialog
      title={`重置口令 · ${user.username}`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
          <button
            type="submit"
            form="reset-form"
            className="btn btn--primary users__reset-submit"
            disabled={busy}
          >
            {busy ? '提交中…' : '重置'}
          </button>
        </>
      }
    >
      <form
        id="reset-form"
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit(password);
        }}
      >
        <label className="form__row">
          <span className="form__label">新口令</span>
          <input
            className="input"
            name="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <p className="form__tip">重置后该用户下次登录须修改口令，同时解除锁定。</p>
        {error ? <p className="banner banner--error">{error}</p> : null}
      </form>
    </Dialog>
  );
}
