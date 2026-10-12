import { useCallback, useEffect, useState } from 'react';
import type {
  AccessUser,
  AccessUserGroup,
  AccessUserGroupMember,
  AccessUserGroupMemberPage,
  AccessUserGroupPage,
  AccessUserGroupSaveRequest,
} from 'common-api';

import { api } from '../../api';
import { usePermissionState } from '../../auth/hooks';
import { Dialog } from '../../components/Dialog';
import { PermissionButton } from '../../components/PermissionButton';
import { describeError } from '../../errors';
import { formatTime, formatWindow, parseDateTimeLocal } from '../../format';
import { PERM } from '../../permission-codes';

const PAGE_SIZE = 10;
const MEMBER_PAGE_SIZE = 10;
const OPTION_SIZE = 200;

type GroupForm = {
  groupCode: string;
  groupName: string;
  sortNo: string;
  isEnabled: string;
  description: string;
};

const EMPTY_FORM: GroupForm = {
  groupCode: '',
  groupName: '',
  sortNo: '0',
  isEnabled: '1',
  description: '',
};

function toForm(group: AccessUserGroup): GroupForm {
  return {
    groupCode: group.groupCode ?? '',
    groupName: group.groupName ?? '',
    sortNo: String(group.sortNo ?? 0),
    isEnabled: String(group.isEnabled ?? 1),
    description: group.description ?? '',
  };
}

function toBody(form: GroupForm): AccessUserGroupSaveRequest {
  return {
    // 编码建后不可改（后端 update 忽略），契约里必填所以照样带
    groupCode: form.groupCode.trim(),
    groupName: form.groupName.trim(),
    sortNo: Number(form.sortNo) || 0,
    isEnabled: form.isEnabled === '0' ? 0 : 1,
    description: form.description.trim() || undefined,
  };
}

/**
 * 用户组管理页 —— 「谁能做什么」的最后一段：**把角色批量授给一群人**。
 *
 * <p>这一页刻意只做**静态组**：
 * <ul>
 *   <li><b>动态组（`DYNAMIC`）不开放</b> —— 动态成员规则求值没实现，接受了它等于给一个永远算不出成员的组；</li>
 *   <li><b>`filterJson` / `orgScopeJson` 连输入框都没有</b> —— 接受一个没人读的字段，
 *       最坏后果是填的人<strong>以为它生效了</strong>；</li>
 *   <li><b>成员只能是用户，不能是组</b> —— 判定链路只跳一层，允许嵌套会造出
 *       "成员列表里看得见、判定里不参与"的关系。</li>
 * </ul>
 * 这些限制不是"还没做"，而是**刻意不让做**：与其给出一个看起来能配、实际不生效的开关，
 * 不如让它根本不存在。
 */
export function UserGroupsPage() {
  const permission = usePermissionState();

  const [page, setPage] = useState<AccessUserGroupPage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const [editing, setEditing] = useState<AccessUserGroup | null>(null);
  const [creating, setCreating] = useState(false);
  const [removing, setRemoving] = useState<AccessUserGroup | null>(null);
  const [membering, setMembering] = useState<AccessUserGroup | null>(null);
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
        const result = await api().access.userGroups.page({
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

  const submitForm = async (form: GroupForm, target: AccessUserGroup | null): Promise<void> => {
    setBusy(true);
    setDialogError(null);
    try {
      const body = toBody(form);
      if (target) {
        await api().access.userGroups.update(target.id as number, body);
        setNotice(`已保存用户组「${body.groupCode}」`);
      } else {
        await api().access.userGroups.create(body);
        setNotice(`已新建用户组「${body.groupCode}」`);
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
      await api().access.userGroups.remove(removing.id as number);
      setNotice(`已删除用户组「${removing.groupCode}」`);
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
    <section className="panel" data-testid="user-groups-panel">
      <header className="panel__head">
        <div>
          <h2 className="panel__title">用户组</h2>
          <p className="panel__hint">
            把角色批量授给一群人：<strong>用户 → 用户组 → 角色 → 资源</strong>。 编码租户内唯一、
            <strong>建后不可修改</strong>。操作入口按权限显隐：新建需要{' '}
            <code>{PERM.USER_GROUP_CREATE}</code>，编辑 / 挂成员需要{' '}
            <code>{PERM.USER_GROUP_UPDATE}</code>，删除需要 <code>{PERM.USER_GROUP_DELETE}</code>。
          </p>
        </div>
        <div className="panel__actions">
          <PermissionButton
            permission={PERM.USER_GROUP_CREATE}
            variant="primary"
            className="user-groups__create"
            onClick={() => {
              setDialogError(null);
              setCreating(true);
            }}
          >
            新建用户组
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
          className="input user-groups__keyword"
          name="keyword"
          placeholder="按用户组编码 / 名称搜索"
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
        <span className="toolbar__meta" data-testid="user-groups-total">
          共 {page?.total ?? 0} 条 · 第 {page?.page ?? pageNo} / {page?.pages ?? 0} 页
        </span>
      </form>

      {notice ? (
        <p className="banner banner--ok" data-testid="user-groups-notice">
          {notice}
        </p>
      ) : null}

      {error ? (
        <p className="banner banner--error" data-testid="user-groups-error">
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
              records.map((group) => (
                <tr
                  key={String(group.id)}
                  data-testid={`user-group-row-${String(group.groupCode)}`}
                >
                  <td className="mono">{group.id}</td>
                  <td className="mono">{group.groupCode}</td>
                  <td>{group.groupName ?? '—'}</td>
                  <td>
                    <span className="tag tag--on">{group.category ?? 'STATIC'}</span>
                  </td>
                  <td>
                    <span className={`tag tag--${group.isEnabled === 0 ? 'off' : 'on'}`}>
                      {group.isEnabled === 0 ? '停用' : '启用'}
                    </span>
                  </td>
                  <td className="mono">{formatTime(group.updateTime)}</td>
                  <td className="table__ops">
                    <PermissionButton
                      permission={PERM.USER_GROUP_UPDATE}
                      className="user-groups__members"
                      onClick={() => {
                        setDialogError(null);
                        setMembering(group);
                      }}
                    >
                      成员
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.USER_GROUP_UPDATE}
                      className="user-groups__edit"
                      onClick={() => {
                        setDialogError(null);
                        setEditing(group);
                      }}
                    >
                      编辑
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.USER_GROUP_DELETE}
                      variant="danger"
                      className="user-groups__remove"
                      onClick={() => {
                        setDialogError(null);
                        setRemoving(group);
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
        <GroupFormDialog
          key={editing ? `edit-${editing.id}` : 'create'}
          initial={editing}
          busy={busy}
          error={dialogError}
          onClose={closeDialog}
          onSubmit={(form) => submitForm(form, editing)}
        />
      ) : null}

      {membering ? (
        <GroupMembersDialog
          group={membering}
          onClose={() => setMembering(null)}
          onChanged={reload}
        />
      ) : null}

      {removing ? (
        <Dialog
          title="删除用户组"
          onClose={() => setRemoving(null)}
          footer={
            <>
              <button type="button" className="btn" onClick={() => setRemoving(null)}>
                取消
              </button>
              <button
                type="button"
                className="btn btn--danger user-groups__remove-confirm"
                disabled={busy}
                onClick={confirmRemove}
              >
                确认删除
              </button>
            </>
          }
        >
          <p>
            将删除用户组 <strong>{removing.groupCode}</strong>（ID {removing.id}）。
          </p>
          <p className="form__tip">
            仍包含成员（40918）或有子组（40913）时会被拒绝。
            <strong>这个组自己持有的角色会随删除一并撤销</strong> ——
            成员清空之后那些授权不再影响任何人，留着只会让下次复活它时旧权限悄悄回来。
          </p>
          {dialogError ? <p className="banner banner--error">{dialogError}</p> : null}
        </Dialog>
      ) : null}
    </section>
  );
}

/** 新建 / 编辑共用一个表单；`initial` 为空即新建 */
function GroupFormDialog({
  initial,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  initial: AccessUserGroup | null;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (form: GroupForm) => void;
}) {
  const [form, setForm] = useState<GroupForm>(() => (initial ? toForm(initial) : EMPTY_FORM));
  const update = (patch: Partial<GroupForm>): void => setForm((prev) => ({ ...prev, ...patch }));

  return (
    <Dialog
      title={initial ? `编辑用户组 · ${initial.groupCode}` : '新建用户组'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
          <button
            type="submit"
            form="user-group-form"
            className="btn btn--primary user-groups__submit"
            disabled={busy}
          >
            {busy ? '提交中…' : '保存'}
          </button>
        </>
      }
    >
      <form
        id="user-group-form"
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit(form);
        }}
      >
        <label className="form__row">
          <span className="form__label">用户组编码</span>
          <input
            className="input"
            name="groupCode"
            placeholder="FIN_GROUP"
            value={form.groupCode}
            disabled={Boolean(initial)}
            onChange={(event) => update({ groupCode: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">名称</span>
          <input
            className="input"
            name="groupName"
            value={form.groupName}
            onChange={(event) => update({ groupName: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">分组方式</span>
          <input className="input" name="category" value="STATIC（只支持静态组）" disabled />
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
          <span className="form__label">状态</span>
          <select
            className="input"
            name="isEnabled"
            value={form.isEnabled}
            onChange={(event) => update({ isEnabled: event.target.value })}
          >
            <option value="1">启用</option>
            <option value="0">停用</option>
          </select>
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
        <p className="form__tip">
          <strong>停用的用户组不再产生权限</strong> —— 组里每个人经由它拿到的权限会一并失效，
          成员关系本身保留。
        </p>
        {error ? <p className="banner banner--error">{error}</p> : null}
      </form>
    </Dialog>
  );
}

/** 组成员：列成员、加成员（只能是用户）、移出 */
function GroupMembersDialog({
  group,
  onClose,
  onChanged,
}: {
  group: AccessUserGroup;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [page, setPage] = useState<AccessUserGroupMemberPage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [users, setUsers] = useState<AccessUser[]>([]);
  const [memberId, setMemberId] = useState('');
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
        const [memberPage, userPage] = await Promise.all([
          api().access.userGroups.pageMembers(group.id as number, {
            page: pageNo,
            size: MEMBER_PAGE_SIZE,
          }),
          api().access.users.page({ page: 1, size: OPTION_SIZE }),
        ]);
        if (!alive) return;
        setPage(memberPage);
        setUsers(userPage.records ?? []);
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
  }, [group.id, pageNo, tick]);

  const submit = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      // `memberType` 显式写死 USER：这是唯一被接受的取值，不给用户一个可以选错的开关
      await api().access.userGroups.bindMember(group.id as number, {
        memberType: 'USER',
        memberId: Number(memberId),
        effectiveFrom: parseDateTimeLocal(from),
        effectiveTo: parseDateTimeLocal(to),
      });
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

  const revoke = async (member: AccessUserGroupMember): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await api().access.userGroups.revokeMember(group.id as number, {
        memberType: member.memberType ?? 'USER',
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
      title={`组成员 · ${group.groupCode}`}
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
              <th>成员</th>
              <th>生效区间</th>
              <th className="table__ops">操作</th>
            </tr>
          </thead>
          <tbody>
            {records.length === 0 ? (
              <tr>
                <td className="table__empty" colSpan={3}>
                  {loading ? '加载中…' : '组里还没有成员'}
                </td>
              </tr>
            ) : (
              records.map((member) => (
                <tr key={String(member.grantId)}>
                  <td>
                    {member.memberName ?? '（用户已不存在）'}
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
                      permission={PERM.USER_GROUP_UPDATE}
                      variant="danger"
                      className="user-groups__revoke-member"
                      onClick={() => void revoke(member)}
                    >
                      移出
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
          <span className="form__label">成员</span>
          <select
            className="input user-groups__member-id"
            name="memberId"
            value={memberId}
            onChange={(event) => setMemberId(event.target.value)}
          >
            <option value="">（请选择用户）</option>
            {users.map((user) => (
              <option key={String(user.id)} value={String(user.id)}>
                {user.displayName || user.username}（#{user.id}）
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
          成员类型固定为<strong>用户</strong>：组的嵌套在判定链路里只跳一层，因此这里不提供
          "把组加进组"的选项 —— 而不是提供了一个不生效的。
        </p>
        <div className="form__actions">
          <button
            type="submit"
            className="btn btn--primary user-groups__member-submit"
            disabled={busy || !memberId}
          >
            {busy ? '提交中…' : '加入用户组'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
