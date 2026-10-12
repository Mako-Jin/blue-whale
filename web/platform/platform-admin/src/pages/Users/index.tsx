import { useCallback, useEffect, useState } from 'react';
import type { AccessUser, AccessUserPage, AccessUserSaveRequest } from 'common-api';

import { api } from '../../api';
import { usePermissionState } from '../../auth/hooks';
import { Dialog } from '../../components/Dialog';
import { PermissionButton } from '../../components/PermissionButton';
import { describeError } from '../../errors';
import { formatStatus, formatTime } from '../../format';
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
        const result = await api().access.users.page({
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
        await api().access.users.update(target.id as number, body);
        setNotice(`已保存用户「${form.username}」`);
        setEditing(null);
      } else {
        await api().access.users.create(body);
        setNotice(`已新建用户「${form.username}」`);
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
      await api().access.users.resetPassword(resetting.id as number, { password });
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
      await api().access.users.remove(removing.id as number);
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
    </section>
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
