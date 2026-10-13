import { useCallback, useEffect, useMemo, useState } from 'react';
import type {
  AccessResource,
  AccessResourcePage,
  AccessResourceSaveRequest,
  AccessResourceTreeNode,
} from 'common-api';

import { api } from '../../api';
import { usePermissionState } from '../../auth/hooks';
import { Dialog } from '../../components/Dialog';
import { PermissionButton } from '../../components/PermissionButton';
import { describeError } from '../../errors';
import { formatTime } from '../../format';
import { PERM } from '../../permission-codes';

const PAGE_SIZE = 10;

/**
 * 资源类型。顺序与后端枚举一致，**不要按字母排** ——
 * 它是"粗到细"的层级（系统 → 服务 → 模块 → 界面 → 接口 → 数据），
 * 下拉框照这个顺序读起来才有意义。
 */
const RESOURCE_TYPES = [
  'SYSTEM',
  'SERVER',
  'SERVICE',
  'MODULE',
  'MENU',
  'PAGE',
  'API',
  'BUTTON',
  'DATA',
] as const;

/**
 * 资源标识的形状提示。
 *
 * <p>它与后端 `AuthTaxonomyTest` 的断言同源（`域:资源:动作`，三段小写）。
 * 前端不做校验 —— 校验的唯一归属是服务端，这里写出来只是为了让填的人少踩一次。
 */
const CODE_PLACEHOLDER = 'auth:user:create';
const CODE_PATTERN = /^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$/;

type ResourceForm = {
  resourceCode: string;
  resourceName: string;
  resourceType: string;
  resourceCategory: string;
  parentCode: string;
  requestUrl: string;
  requestMethod: string;
  icon: string;
  sortNo: string;
  /** 0/1 三态字段在表单里用字符串：`undefined` 与 `0` 在 select 上分不出来 */
  isEnabled: string;
  isWhitelist: string;
  isDeprecated: string;
  description: string;
};

const EMPTY_FORM: ResourceForm = {
  resourceCode: '',
  resourceName: '',
  resourceType: 'API',
  resourceCategory: '',
  parentCode: '',
  requestUrl: '',
  requestMethod: '',
  icon: '',
  sortNo: '0',
  isEnabled: '1',
  isWhitelist: '0',
  isDeprecated: '0',
  description: '',
};

function toForm(resource: AccessResource): ResourceForm {
  return {
    resourceCode: resource.resourceCode ?? '',
    resourceName: resource.resourceName ?? '',
    resourceType: resource.resourceType ?? 'API',
    resourceCategory: resource.resourceCategory ?? '',
    parentCode: resource.parentCode ?? '',
    requestUrl: resource.requestUrl ?? '',
    requestMethod: resource.requestMethod ?? '',
    icon: resource.icon ?? '',
    sortNo: String(resource.sortNo ?? 0),
    isEnabled: String(resource.isEnabled ?? 1),
    isWhitelist: String(resource.isWhitelist ?? 0),
    isDeprecated: String(resource.isDeprecated ?? 0),
    description: resource.description ?? '',
  };
}

/** 把"是 / 否"这种二元开关拼成数字串；认不得的值给兜底，不抛 */
function flagOf(value: string, fallback: string): number {
  return value === '1' || value === '0' ? Number(value) : Number(fallback);
}

function toBody(form: ResourceForm): AccessResourceSaveRequest {
  return {
    // `resourceCode` 在修改时不生效（后端明确拒绝改标识），但仍然要带 —— 契约里它必填
    resourceCode: form.resourceCode.trim(),
    resourceName: form.resourceName.trim(),
    resourceType: form.resourceType,
    resourceCategory: form.resourceCategory.trim() || undefined,
    parentCode: form.parentCode.trim() || undefined,
    requestUrl: form.requestUrl.trim() || undefined,
    requestMethod: form.requestMethod.trim() || undefined,
    icon: form.icon.trim() || undefined,
    sortNo: Number(form.sortNo) || 0,
    isEnabled: flagOf(form.isEnabled, '1'),
    isWhitelist: flagOf(form.isWhitelist, '0'),
    isDeprecated: flagOf(form.isDeprecated, '0'),
    description: form.description.trim() || undefined,
  };
}

type FlatNode = { node: AccessResourceTreeNode; depth: number };

/**
 * 把资源树压平，用于"父资源"下拉框。
 *
 * <p>压平时带上深度用来缩进。父资源选项必须来自**同一棵树**：
 * 资源用 `parentCode`（标识）而不是主键建关系，随手填一个不存在的标识会得到一个孤儿节点。
 */
function flatten(nodes: AccessResourceTreeNode[], depth = 0): FlatNode[] {
  const out: FlatNode[] = [];
  for (const node of nodes) {
    out.push({ node, depth });
    if (node.children?.length) out.push(...flatten(node.children, depth + 1));
  }
  return out;
}

/**
 * 资源目录页 —— 「谁能做什么」的第一段。
 *
 * <p>资源就是**权限码的登记表**：既有 `auth:user:create` 这类接口权限，
 * 将来也会有 `MENU` / `PAGE` 这类只影响前端可见性的条目。
 * 因此这一页要显式展示 `requestScoped`（是否参与接口准入），
 * 它是 `resourceType` 的派生值 —— 让人看清"可见性不是安全边界"。
 *
 * <p>两种视图刻意都保留：树看结构（`parentCode` 组装出来的层级），
 * 列表看检索（按类型 / 关键字 / 启用状态过滤，带分页）。两者是同一份数据的两个切面，
 * 用同一个 CRUD 状态机驱动，不各写一套。
 */
export function ResourcesPage() {
  const permission = usePermissionState();

  const [view, setView] = useState<'tree' | 'list'>('tree');
  const [tree, setTree] = useState<AccessResourceTreeNode[]>([]);
  const [page, setPage] = useState<AccessResourcePage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const [editing, setEditing] = useState<AccessResource | null>(null);
  const [creatingParent, setCreatingParent] = useState<string | null>(null);
  const [removing, setRemoving] = useState<AccessResourceTreeNode | AccessResource | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const creating = creatingParent !== null;

  const reload = useCallback(() => setTick((value) => value + 1), []);

  /*
   * 权限未就绪时不发请求 —— 与用户页同一个理由：
   * 独立运行时"会话还没建立"等于没有令牌，这时候发出去必然 401，
   * 而 401 会触发一次刷新，把启动流程该做的事抢到页面 effect 里来。
   */
  useEffect(() => {
    if (!permission.ready) return undefined;

    let alive = true;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        // 树始终加载：它既是树视图的数据源，也是"父资源"下拉框的选项来源
        const nodes = await api.access.resources.tree();
        if (alive) setTree(nodes);

        if (view === 'list') {
          const result = await api.access.resources.page({
            page: pageNo,
            size: PAGE_SIZE,
            keyword: keyword || undefined,
            resourceType: typeFilter || undefined,
          });
          if (alive) setPage(result);
        }
      } catch (cause) {
        if (!alive) return;
        if (view === 'list') setPage(null);
        setTree([]);
        setError(describeError(cause));
      } finally {
        if (alive) setLoading(false);
      }
    };

    void load();

    return () => {
      alive = false;
    };
  }, [permission.ready, view, pageNo, keyword, typeFilter, tick]);

  const parentOptions = useMemo(() => flatten(tree), [tree]);

  const closeDialog = (): void => {
    setCreatingParent(null);
    setEditing(null);
  };

  const submitForm = async (form: ResourceForm, target: AccessResource | null): Promise<void> => {
    setBusy(true);
    setDialogError(null);
    try {
      const body = toBody(form);
      if (target) {
        await api.access.resources.update(target.id as number, body);
        setNotice(`已保存资源「${body.resourceCode}」`);
      } else {
        await api.access.resources.create(body);
        setNotice(`已新建资源「${body.resourceCode}」`);
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
      await api.access.resources.remove(removing.id as number);
      setNotice(`已删除资源「${removing.resourceCode}」`);
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
    <section className="panel" data-testid="resources-panel">
      <header className="panel__head">
        <div>
          <h2 className="panel__title">资源目录</h2>
          <p className="panel__hint">
            资源标识就是权限码（<code>{CODE_PLACEHOLDER}</code>，三段小写冒号分隔），
            <strong>建后不可修改</strong> —— 改了它，已经授出去的授权会静默失效。
            操作入口按权限显隐：新建需要 <code>{PERM.RESOURCE_CREATE}</code>， 编辑需要{' '}
            <code>{PERM.RESOURCE_UPDATE}</code>，删除需要 <code>{PERM.RESOURCE_DELETE}</code>。
          </p>
        </div>
        <div className="panel__actions">
          <PermissionButton
            permission={PERM.RESOURCE_CREATE}
            variant="primary"
            className="resources__create"
            onClick={() => {
              setDialogError(null);
              setCreatingParent('');
            }}
          >
            新建资源
          </PermissionButton>
        </div>
      </header>

      <div className="toolbar">
        <div className="tabs tabs--inline" role="tablist" aria-label="资源视图">
          <button
            type="button"
            role="tab"
            aria-selected={view === 'tree'}
            className={`tabs__item${view === 'tree' ? ' tabs__item--active' : ''}`}
            data-testid="resources-view-tree"
            onClick={() => setView('tree')}
          >
            树形
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={view === 'list'}
            className={`tabs__item${view === 'list' ? ' tabs__item--active' : ''}`}
            data-testid="resources-view-list"
            onClick={() => setView('list')}
          >
            列表
          </button>
        </div>

        {view === 'list' ? (
          <>
            <form
              className="toolbar"
              onSubmit={(event) => {
                event.preventDefault();
                setPageNo(1);
                setKeyword(keywordInput.trim());
              }}
            >
              <input
                className="input resources__keyword"
                name="keyword"
                placeholder="按资源标识 / 名称搜索"
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
              />
              <select
                className="input resources__type"
                name="resourceType"
                value={typeFilter}
                onChange={(event) => {
                  setPageNo(1);
                  setTypeFilter(event.target.value);
                }}
              >
                <option value="">全部类型</option>
                {RESOURCE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
              <button type="submit" className="btn">
                搜索
              </button>
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setKeywordInput('');
                  setKeyword('');
                  setTypeFilter('');
                  setPageNo(1);
                  reload();
                }}
              >
                重置
              </button>
            </form>
            <span className="toolbar__spacer" />
            <span className="toolbar__meta" data-testid="resources-total">
              共 {page?.total ?? 0} 条 · 第 {page?.page ?? pageNo} / {page?.pages ?? 0} 页
            </span>
          </>
        ) : (
          <>
            <span className="toolbar__spacer" />
            <span className="toolbar__meta" data-testid="resources-total">
              {loading ? '加载中…' : `共 ${parentOptions.length} 个节点`}
            </span>
          </>
        )}
      </div>

      {notice ? (
        <p className="banner banner--ok" data-testid="resources-notice">
          {notice}
        </p>
      ) : null}

      {error ? (
        <p className="banner banner--error" data-testid="resources-error">
          {error}
        </p>
      ) : null}

      {view === 'tree' ? (
        <div className="tree" data-testid="resources-tree">
          {tree.length === 0 ? (
            <p className="table__empty">{loading ? '加载中…' : '没有数据'}</p>
          ) : (
            <ul className="tree__list">
              {tree.map((node) => (
                <ResourceBranch
                  key={node.resourceCode}
                  node={node}
                  onCreateChild={(parentCode) => {
                    setDialogError(null);
                    setCreatingParent(parentCode);
                  }}
                  onEdit={(resource) => {
                    setDialogError(null);
                    setEditing(resource);
                  }}
                  onRemove={setRemoving}
                />
              ))}
            </ul>
          )}
        </div>
      ) : (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>标识</th>
                  <th>名称</th>
                  <th>类型</th>
                  <th>父资源</th>
                  <th>准入</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th className="table__ops">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.length === 0 ? (
                  <tr>
                    <td className="table__empty" colSpan={8}>
                      {loading ? '加载中…' : '没有数据'}
                    </td>
                  </tr>
                ) : (
                  records.map((resource) => (
                    <tr
                      key={String(resource.id)}
                      data-testid={`resource-row-${String(resource.resourceCode)}`}
                    >
                      <td className="mono">{resource.resourceCode}</td>
                      <td>{resource.resourceName ?? '—'}</td>
                      <td>
                        <span className="tag tag--on">{resource.resourceType ?? '—'}</span>
                      </td>
                      <td className="mono">{resource.parentCode ?? '—'}</td>
                      <td>
                        {/* 准入与否由后端从类型派生，前端不重算 —— 重算就是第二份真相 */}
                        <span className={`tag tag--${resource.requestScoped ? 'on' : 'off'}`}>
                          {resource.requestScoped ? '参与' : '不参与'}
                        </span>
                      </td>
                      <td>
                        <span className={`tag tag--${resource.isEnabled === 1 ? 'on' : 'off'}`}>
                          {resource.isEnabled === 1 ? '启用' : '停用'}
                        </span>
                      </td>
                      <td className="mono">{formatTime(resource.updateTime)}</td>
                      <td className="table__ops">
                        <PermissionButton
                          permission={PERM.RESOURCE_UPDATE}
                          className="resources__edit"
                          onClick={() => {
                            setDialogError(null);
                            setEditing(resource);
                          }}
                        >
                          编辑
                        </PermissionButton>
                        <PermissionButton
                          permission={PERM.RESOURCE_DELETE}
                          variant="danger"
                          className="resources__remove"
                          onClick={() => {
                            setDialogError(null);
                            setRemoving(resource);
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
        </>
      )}

      {creating || editing ? (
        <ResourceFormDialog
          key={editing ? `edit-${editing.id}` : `create-${creatingParent}`}
          initial={editing}
          parentOptions={parentOptions}
          defaultParentCode={creatingParent ?? ''}
          busy={busy}
          error={dialogError}
          onClose={closeDialog}
          onSubmit={(form) => submitForm(form, editing)}
        />
      ) : null}

      {removing ? (
        <Dialog
          title="删除资源"
          onClose={() => setRemoving(null)}
          footer={
            <>
              <button type="button" className="btn" onClick={() => setRemoving(null)}>
                取消
              </button>
              <button
                type="button"
                className="btn btn--danger resources__remove-confirm"
                disabled={busy}
                onClick={confirmRemove}
              >
                确认删除
              </button>
            </>
          }
        >
          <p>
            将删除资源 <strong>{removing.resourceCode}</strong>（ID {removing.id}）。
          </p>
          <p className="form__tip">
            有子资源（40913）或已被角色引用（40915）时会被拒绝 —— 先解除引用再删。
            删除是逻辑删除，同一标识后续可以重新建（服务端会恢复这一行）。
          </p>
          {dialogError ? <p className="banner banner--error">{dialogError}</p> : null}
        </Dialog>
      ) : null}
    </section>
  );
}

/** 树的一个分支。递归渲染，子节点在自己的 `ul` 里 */
function ResourceBranch({
  node,
  onCreateChild,
  onEdit,
  onRemove,
}: {
  node: AccessResourceTreeNode;
  onCreateChild: (parentCode: string) => void;
  onEdit: (resource: AccessResource) => void;
  onRemove: (node: AccessResourceTreeNode) => void;
}) {
  const children = node.children ?? [];

  return (
    <li className="tree__node" data-testid={`resource-node-${String(node.resourceCode)}`}>
      <div className="tree__row">
        <span className="tree__code mono">{node.resourceCode}</span>
        <span className="tree__name">{node.resourceName ?? '—'}</span>
        <span className="tag tag--on">{node.resourceType ?? '—'}</span>
        {node.requestScoped ? null : (
          // 不参与准入的类型（MENU / PAGE / DATA）特别标一下：
          // "授了它 ≠ 能调接口"是这一页最容易被误解的地方
          <span className="tag tag--off">不参与准入</span>
        )}
        {node.isEnabled === 1 ? null : <span className="tag tag--off">停用</span>}
        <span className="tree__ops">
          <PermissionButton
            permission={PERM.RESOURCE_CREATE}
            className="resources__create-child"
            onClick={() => onCreateChild(node.resourceCode ?? '')}
          >
            加子资源
          </PermissionButton>
          <PermissionButton
            permission={PERM.RESOURCE_UPDATE}
            className="resources__edit"
            onClick={() =>
              onEdit({
                id: node.id,
                resourceCode: node.resourceCode,
                resourceName: node.resourceName,
                resourceType: node.resourceType,
                parentCode: node.parentCode,
                icon: node.icon,
                sortNo: node.sortNo,
                isEnabled: node.isEnabled,
              })
            }
          >
            编辑
          </PermissionButton>
          <PermissionButton
            permission={PERM.RESOURCE_DELETE}
            variant="danger"
            className="resources__remove"
            onClick={() => onRemove(node)}
          >
            删除
          </PermissionButton>
        </span>
      </div>
      {children.length > 0 ? (
        <ul className="tree__list">
          {children.map((child) => (
            <ResourceBranch
              key={child.resourceCode}
              node={child}
              onCreateChild={onCreateChild}
              onEdit={onEdit}
              onRemove={onRemove}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
}

/** 新建 / 编辑共用一个表单；`initial` 为空即新建 */
function ResourceFormDialog({
  initial,
  parentOptions,
  defaultParentCode,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  initial: AccessResource | null;
  parentOptions: FlatNode[];
  defaultParentCode: string;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (form: ResourceForm) => void;
}) {
  const [form, setForm] = useState<ResourceForm>(() =>
    initial ? toForm(initial) : { ...EMPTY_FORM, parentCode: defaultParentCode },
  );
  const update = (patch: Partial<ResourceForm>): void => setForm((prev) => ({ ...prev, ...patch }));

  const codeLooksWrong = form.resourceCode.length > 0 && !CODE_PATTERN.test(form.resourceCode);

  return (
    <Dialog
      title={initial ? `编辑资源 · ${initial.resourceCode}` : '新建资源'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
          <button
            type="submit"
            form="resource-form"
            className="btn btn--primary resources__submit"
            // 形状不对时先不给提交：校验的归属是服务端，这里只是省一次往返
            disabled={busy || codeLooksWrong}
          >
            {busy ? '提交中…' : '保存'}
          </button>
        </>
      }
    >
      <form
        id="resource-form"
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit(form);
        }}
      >
        <label className="form__row">
          <span className="form__label">资源标识</span>
          <input
            className="input"
            name="resourceCode"
            placeholder={CODE_PLACEHOLDER}
            value={form.resourceCode}
            // 标识是权限码本身，建后不可改 —— 后端 update 也不接受它
            disabled={Boolean(initial)}
            onChange={(event) => update({ resourceCode: event.target.value })}
          />
        </label>
        {codeLooksWrong ? (
          <p className="form__tip form__tip--warn">
            标识须为「域:资源:动作」三段小写，如 {CODE_PLACEHOLDER}
          </p>
        ) : null}
        <label className="form__row">
          <span className="form__label">名称</span>
          <input
            className="input"
            name="resourceName"
            value={form.resourceName}
            onChange={(event) => update({ resourceName: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">类型</span>
          <select
            className="input"
            name="resourceType"
            value={form.resourceType}
            onChange={(event) => update({ resourceType: event.target.value })}
          >
            {RESOURCE_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </label>
        <label className="form__row">
          <span className="form__label">父资源</span>
          <select
            className="input"
            name="parentCode"
            value={form.parentCode}
            onChange={(event) => update({ parentCode: event.target.value })}
          >
            <option value="">（顶级）</option>
            {parentOptions
              // 不能把自己当父资源 —— 服务端也会拒（40913 成环），这里提前挡掉
              .filter((item) => item.node.resourceCode !== form.resourceCode)
              .map((item) => (
                <option key={item.node.resourceCode} value={item.node.resourceCode}>
                  {'　'.repeat(item.depth)}
                  {item.node.resourceCode}
                </option>
              ))}
          </select>
        </label>
        <label className="form__row">
          <span className="form__label">业务分类</span>
          <input
            className="input"
            name="resourceCategory"
            value={form.resourceCategory}
            onChange={(event) => update({ resourceCategory: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">请求地址</span>
          <input
            className="input"
            name="requestUrl"
            placeholder="/api/auth/users"
            value={form.requestUrl}
            onChange={(event) => update({ requestUrl: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">请求方法</span>
          <input
            className="input"
            name="requestMethod"
            placeholder="GET / POST"
            value={form.requestMethod}
            onChange={(event) => update({ requestMethod: event.target.value })}
          />
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
          <span className="form__label">启用</span>
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
          <span className="form__label">免鉴权</span>
          <select
            className="input"
            name="isWhitelist"
            value={form.isWhitelist}
            onChange={(event) => update({ isWhitelist: event.target.value })}
          >
            <option value="0">否</option>
            <option value="1">是（白名单）</option>
          </select>
        </label>
        <p className="form__tip">请求地址与免鉴权仅供评审参考，不参与运行时判定。</p>
        <label className="form__row">
          <span className="form__label">描述</span>
          <input
            className="input"
            name="description"
            value={form.description}
            onChange={(event) => update({ description: event.target.value })}
          />
        </label>
        {error ? <p className="banner banner--error">{error}</p> : null}
      </form>
    </Dialog>
  );
}
