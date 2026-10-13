import { useCallback, useEffect, useState } from 'react';
import type { AccessTenant, AccessTenantPage, AccessTenantSaveRequest } from 'common-api';

import { api } from '../../api';
import { usePermissionState } from '../../auth/hooks';
import { Dialog } from '../../components/Dialog';
import { PermissionButton } from '../../components/PermissionButton';
import { describeError } from '../../errors';
import { formatStatus, formatTime } from '../../format';
import { PERM } from '../../permission-codes';

const PAGE_SIZE = 10;

type TenantForm = {
  tenantCode: string;
  tenantName: string;
  description: string;
  status: string;
};

const EMPTY_FORM: TenantForm = {
  tenantCode: '',
  tenantName: '',
  description: '',
  status: 'ENABLED',
};

/**
 * 租户管理页。
 *
 * <p>与用户页是同一套写法的第二份 —— 刻意不做成"通用表格组件"：
 * 两张表的字段、操作、约束都不同（租户编码不可改、有子租户时不可删），
 * 抽一层抽象省下的代码量远小于它带来的间接层。真正重复的只有显隐与错误提示，
 * 那两样已经各有一份实现（`PermissionButton` / `describeError`）。
 */
export function TenantsPage() {
  const permission = usePermissionState();

  const [page, setPage] = useState<AccessTenantPage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const [editing, setEditing] = useState<AccessTenant | null>(null);
  const [creating, setCreating] = useState(false);
  const [removing, setRemoving] = useState<AccessTenant | null>(null);
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
        const result = await api.access.tenants.page({
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

  const submitForm = async (form: TenantForm, target: AccessTenant | null): Promise<void> => {
    setBusy(true);
    setDialogError(null);
    try {
      const body: AccessTenantSaveRequest = {
        tenantCode: form.tenantCode,
        tenantName: form.tenantName,
        description: form.description || undefined,
        status: form.status,
      };
      if (target) {
        await api.access.tenants.update(target.id as number, body);
        setNotice(`已保存租户「${form.tenantName}」`);
        setEditing(null);
      } else {
        await api.access.tenants.create(body);
        setNotice(`已新建租户「${form.tenantName}」`);
        setCreating(false);
      }
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
      await api.access.tenants.remove(removing.id as number);
      setNotice(`已删除租户「${removing.tenantName}」`);
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
    <section className="panel" data-testid="tenants-panel">
      <header className="panel__head">
        <div>
          <h2 className="panel__title">租户</h2>
          <p className="panel__hint">
            同一个页面、同一段代码，操作入口由 <code>{PERM.TENANT_CREATE}</code> /{' '}
            <code>{PERM.TENANT_UPDATE}</code> / <code>{PERM.TENANT_DELETE}</code> 决定显隐。
          </p>
        </div>
        <div className="panel__actions">
          <PermissionButton
            permission={PERM.TENANT_CREATE}
            variant="primary"
            className="tenants__create"
            onClick={() => {
              setDialogError(null);
              setCreating(true);
            }}
          >
            新建租户
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
          className="input tenants__keyword"
          name="keyword"
          placeholder="按租户编码 / 名称搜索"
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
        <span className="toolbar__meta" data-testid="tenants-total">
          共 {page?.total ?? 0} 条 · 第 {page?.page ?? pageNo} / {page?.pages ?? 0} 页
        </span>
      </form>

      {notice ? (
        <p className="banner banner--ok" data-testid="tenants-notice">
          {notice}
        </p>
      ) : null}

      {error ? (
        <p className="banner banner--error" data-testid="tenants-error">
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
              <th>层级</th>
              <th>状态</th>
              <th>创建时间</th>
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
              records.map((tenant) => (
                <tr key={String(tenant.id)} data-testid={`tenant-row-${String(tenant.tenantCode)}`}>
                  <td className="mono">{tenant.id}</td>
                  <td className="mono">{tenant.tenantCode}</td>
                  <td>{tenant.tenantName}</td>
                  <td className="mono">{tenant.tenantLevel}</td>
                  <td>
                    <span className={`tag tag--${tenant.status === 'ENABLED' ? 'on' : 'off'}`}>
                      {formatStatus(tenant.status)}
                    </span>
                  </td>
                  <td className="mono">{formatTime(tenant.createTime)}</td>
                  <td className="table__ops">
                    <PermissionButton
                      permission={PERM.TENANT_UPDATE}
                      className="tenants__edit"
                      onClick={() => {
                        setDialogError(null);
                        setEditing(tenant);
                      }}
                    >
                      编辑
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.TENANT_DELETE}
                      variant="danger"
                      className="tenants__remove"
                      onClick={() => {
                        setDialogError(null);
                        setRemoving(tenant);
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
        <TenantFormDialog
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

      {removing ? (
        <Dialog
          title="删除租户"
          onClose={() => setRemoving(null)}
          footer={
            <>
              <button type="button" className="btn" onClick={() => setRemoving(null)}>
                取消
              </button>
              <button
                type="button"
                className="btn btn--danger tenants__remove-confirm"
                disabled={busy}
                onClick={confirmRemove}
              >
                确认删除
              </button>
            </>
          }
        >
          <p>
            将删除租户 <strong>{removing.tenantName}</strong>（{removing.tenantCode}）。
            存在子租户时服务端会拒绝。
          </p>
          {dialogError ? <p className="banner banner--error">{dialogError}</p> : null}
        </Dialog>
      ) : null}
    </section>
  );
}

function TenantFormDialog({
  initial,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  initial: AccessTenant | null;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (form: TenantForm) => void;
}) {
  const [form, setForm] = useState<TenantForm>(() =>
    initial
      ? {
          tenantCode: initial.tenantCode ?? '',
          tenantName: initial.tenantName ?? '',
          description: initial.description ?? '',
          status: initial.status ?? 'ENABLED',
        }
      : EMPTY_FORM,
  );
  const update = (patch: Partial<TenantForm>): void => setForm((prev) => ({ ...prev, ...patch }));

  return (
    <Dialog
      title={initial ? `编辑租户 · ${initial.tenantCode}` : '新建租户'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
          <button
            type="submit"
            form="tenant-form"
            className="btn btn--primary tenants__submit"
            disabled={busy}
          >
            {busy ? '提交中…' : '保存'}
          </button>
        </>
      }
    >
      <form
        id="tenant-form"
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit(form);
        }}
      >
        <label className="form__row">
          <span className="form__label">租户编码</span>
          <input
            className="input"
            name="tenantCode"
            value={form.tenantCode}
            // 编码是数据归属的一部分，后端 update 不接受它的变更
            disabled={Boolean(initial)}
            onChange={(event) => update({ tenantCode: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">租户名称</span>
          <input
            className="input"
            name="tenantName"
            value={form.tenantName}
            onChange={(event) => update({ tenantName: event.target.value })}
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
