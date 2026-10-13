import { useCallback, useEffect, useMemo, useState } from 'react';
import type {
  AccessOrganization,
  AccessOrganizationMoveRequest,
  AccessOrgMember,
  AccessOrgMemberBindRequest,
  AccessOrgMemberPage,
  AccessOrganizationSaveRequest,
  AccessOrganizationTreeNode,
  AccessUser,
} from 'common-api';

import { api } from '../../api';
import { usePermissionState } from '../../auth/hooks';
import { Dialog } from '../../components/Dialog';
import { PermissionButton } from '../../components/PermissionButton';
import { describeError } from '../../errors';
import { formatWindow, parseDateTimeLocal } from '../../format';
import { PERM } from '../../permission-codes';

const MEMBER_PAGE_SIZE = 10;
const OPTION_SIZE = 200;

/** 组织编码的形状：字母开头，后跟字母 / 数字 / 下划线。与后端 `OrganizationSaveRequest` 的正则一致 */
const CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,63}$/;

type FlatNode = { node: AccessOrganizationTreeNode; depth: number };

/**
 * 把组织树压平，用于"上级组织"下拉框。
 *
 * <p>压平时带上深度用来缩进。上级选项必须来自**这棵树本身**，而不是重新拉一次列表 ——
 * 组织用主键建关系，随手填一个不存在的主键会得到一个孤儿节点：它在列表里查得到，
 * 却不属于任何一棵树（树接口压根不会返回它）。用同一棵树做选项，这类值就选不出来。
 */
function flatten(nodes: AccessOrganizationTreeNode[], depth = 0): FlatNode[] {
  const out: FlatNode[] = [];
  for (const node of nodes) {
    out.push({ node, depth });
    if (node.children?.length) out.push(...flatten(node.children, depth + 1));
  }
  return out;
}

/**
 * 收集一个节点自身与它的全部后代主键。
 *
 * <p>用于**移动时把非法目标从下拉框里去掉**。这是界面层的"防止一次注定被拒的往返"，
 * 与资源页在标识形状不对时直接禁用提交同类 —— 服务端照样独立判（返回 40913），
 * 前端这层只是不让正常操作的人选到一个必然报错的选项。
 *
 * <p>判据用的是树自身的 `children`，不是重新按 `codePath` 前缀算一遍：
 * 那等于把服务端的求值口径在前端抄一份，两份一旦分叉，"界面允许但后端拒绝"就会回来。
 */
function selfAndDescendantIds(node: AccessOrganizationTreeNode): number[] {
  const out: number[] = [];
  const walk = (item: AccessOrganizationTreeNode): void => {
    if (typeof item.id === 'number') out.push(item.id);
    for (const child of item.children ?? []) walk(child);
  };
  walk(node);
  return out;
}

type OrgForm = {
  orgCode: string;
  orgName: string;
  /** 上级组织主键的字符串形式，`''` 表示顶级。**只在新增时提交** */
  parentId: string;
  orgType: string;
  managerId: string;
  sortNo: string;
  status: string;
  description: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
};

const EMPTY_FORM: OrgForm = {
  orgCode: '',
  orgName: '',
  parentId: '',
  orgType: '',
  managerId: '',
  sortNo: '0',
  status: 'ENABLED',
  description: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
};

function toForm(org: AccessOrganization): OrgForm {
  return {
    orgCode: org.orgCode ?? '',
    orgName: org.orgName ?? '',
    parentId: org.parentId == null ? '' : String(org.parentId),
    orgType: org.orgType ?? '',
    managerId: org.managerId == null ? '' : String(org.managerId),
    sortNo: String(org.sortNo ?? 0),
    status: org.status ?? 'ENABLED',
    description: org.description ?? '',
    contactName: org.contactName ?? '',
    contactPhone: org.contactPhone ?? '',
    contactEmail: org.contactEmail ?? '',
  };
}

/**
 * 表单 → 请求体。
 *
 * <p><b>编辑路径上刻意不带 `parentId`。</b>后端只在"值非空且与原值不同"时拒绝它（40001），
 * 所以带上原值也能通过 —— 但那意味着界面在**鼓励**用户以为这里能改层级。
 * 干脆不传：改资料就是改资料，层级归「移动」。不传与传 `null` 在服务端是同一个意思
 * （"这次不改层级"），因此这个省略没有任何副作用。
 *
 * <p>`orgCode` 修改时**仍要回传**：它是 `@NotBlank`，与资源 / 角色 / 用户组同一形状 ——
 * 编码建后不可改，但契约要求必填，界面把它渲染成只读并原样带回。
 */
function toBody(form: OrgForm, isEdit: boolean): AccessOrganizationSaveRequest {
  const body: AccessOrganizationSaveRequest = {
    orgCode: form.orgCode.trim(),
    orgName: form.orgName.trim(),
    orgType: form.orgType.trim() || undefined,
    managerId: form.managerId ? Number(form.managerId) : undefined,
    sortNo: Number(form.sortNo) || 0,
    status: form.status,
    description: form.description.trim() || undefined,
    contactName: form.contactName.trim() || undefined,
    contactPhone: form.contactPhone.trim() || undefined,
    contactEmail: form.contactEmail.trim() || undefined,
  };
  if (!isEdit && form.parentId) body.parentId = Number(form.parentId);
  return body;
}

/**
 * 组织（部门）管理页 —— **数据权限的坐标系**。
 *
 * <h3>为什么这一页比其它管理页"重"</h3>
 *
 * 资源、角色、用户组改的是"谁能做什么"；组织改的是"**谁能看到哪些数据**"。
 * `DEPT` / `DEPT_AND_CHILD` 两档数据范围最终都要翻译成一组组织 id，而
 * `DEPT_AND_CHILD` 正是按 `codePath` 做前缀匹配求子树。于是：
 *
 * <ul>
 *   <li><b>凭编码 / 名称 / 全称找不出一条规则</b> —— 层级本身是规则的一部分；</li>
 *   <li><b>移动一个节点会改写整棵子树</b>的层级、路径与全称，子树上每个人的可见范围
 *       同时改变。因此它是**独立入口、独立审计动作**（`auth.org.move`），
 *       不能藏在"编辑资料"里（ADR-0030 第 3、4 条）；</li>
 *   <li><b>删除有守卫且没有级联</b>：有下级报 40913、有成员报 40919。这里刻意不做
 *       "先删子树"的一键操作 —— 那等于用一个按钮无声地撤销一批人的数据范围。</li>
 * </ul>
 *
 * <h3>为什么只有树视图，没有列表视图</h3>
 *
 * 资源页给了两个切面（树看结构、列表看检索），因为资源是扁平的目录、条目多。
 * 组织不同：**它本身就是一棵树，`parentId` 是唯一的关系**，而部门树是人手维护量级。
 * 契约里确实有分页接口（`GET /api/auth/orgs`，支持按编码 / 名称 / 全称搜索），
 * 但那是给 API 调用方用的；在这里再渲染一遍同样的行，只能得到一份
 * "按关键字筛过的平铺列表"—— 而它恰好丢掉了唯一的有效信息（层级）。
 * 与其做一个半吊子的第二视图，不如不做。
 *
 * <h3>界面上的分工</h3>
 *
 * 权限码只有 `auth:org:create / update / delete` 三个写权限，**移动与设主岗都归 `update`** ——
 * 与角色成员关系同一条理由（拆权限码会让已配好的角色在升级后静默丢能力）。
 * 但**入口是分开的**：它们的后果不同，审计动作也不同
 * （`auth.org.move` / `auth.org.set_primary_position`），所以不能合并成一个"编辑"。
 */
export function OrganizationsPage() {
  const permission = usePermissionState();

  const [tree, setTree] = useState<AccessOrganizationTreeNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const [editing, setEditing] = useState<AccessOrganization | null>(null);
  /** `''` = 在顶级新建；非空字符串 = 在该主键下新建。`null` 表示对话框未打开 */
  const [creatingParent, setCreatingParent] = useState<string | null>(null);
  const [moving, setMoving] = useState<AccessOrganizationTreeNode | null>(null);
  const [removing, setRemoving] = useState<AccessOrganizationTreeNode | null>(null);
  const [membering, setMembering] = useState<AccessOrganizationTreeNode | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const creating = creatingParent !== null;

  const reload = useCallback(() => setTick((value) => value + 1), []);

  // 权限未就绪时不发请求：独立运行时"会话还没建立"等于没有令牌，
  // 这时发出去必然 401，而 401 会触发一次刷新 —— 把启动流程该做的事抢到页面 effect 里来
  useEffect(() => {
    if (!permission.ready) return undefined;

    let alive = true;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const nodes = await api().access.organizations.tree();
        if (alive) setTree(nodes);
      } catch (cause) {
        if (!alive) return;
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
  }, [permission.ready, tick]);

  const options = useMemo(() => flatten(tree), [tree]);
  const total = options.length;

  const closeDialog = (): void => {
    setCreatingParent(null);
    setEditing(null);
  };

  /**
   * 打开编辑对话框 —— **必须先回查详情**。
   *
   * <p>树节点带的是 `id / orgCode / orgName / fullName / parentId / orgLevel / codePath /
   * status / managerName / sortNo`，**不含 `orgType` / `managerId` / `description` /
   * 三个联系方式**。直接拿树节点填表单，会把这几项渲染成空 —— 而保存时它们会以空值提交，
   * 于是"我只是改了个名字"会把描述与联系方式一起清掉。
   *
   * <p>因此这里的取值是：树提供结构与导航，**详情接口提供表单的初值**。
   * 两者都不能省：缺了树就没有层级，缺了详情就有静默丢字段的风险。
   */
  const openEdit = async (node: AccessOrganizationTreeNode): Promise<void> => {
    if (typeof node.id !== 'number') return;
    setDialogError(null);
    setError(null);
    setBusy(true);
    try {
      setEditing(await api().access.organizations.detail(node.id));
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const submitForm = async (form: OrgForm, target: AccessOrganization | null): Promise<void> => {
    setBusy(true);
    setDialogError(null);
    try {
      const body = toBody(form, Boolean(target));
      if (target) {
        await api().access.organizations.update(target.id as number, body);
        setNotice(`已保存组织「${body.orgCode}」`);
      } else {
        await api().access.organizations.create(body);
        setNotice(`已新建组织「${body.orgCode}」`);
      }
      closeDialog();
      reload();
    } catch (cause) {
      setDialogError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const submitMove = async (parentId: number | null): Promise<void> => {
    if (!moving) return;
    setBusy(true);
    setDialogError(null);
    try {
      // `parentId` 为 null 即"提升为顶级"：契约里 null 与省略同义，显式传 null 更好读
      const body: AccessOrganizationMoveRequest = { parentId: parentId ?? undefined };
      await api().access.organizations.move(moving.id as number, body);
      setNotice(
        parentId == null
          ? `已将「${moving.orgName}」提升为顶级`
          : `已移动「${moving.orgName}」，整棵子树的层级与可见范围已同步重写`,
      );
      setMoving(null);
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
      await api().access.organizations.remove(removing.id as number);
      setNotice(`已删除组织「${removing.orgName}」`);
      setRemoving(null);
      reload();
    } catch (cause) {
      setDialogError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel" data-testid="orgs-panel">
      <header className="panel__head">
        <div>
          <h2 className="panel__title">组织</h2>
          <p className="panel__hint">
            数据权限的坐标系：<strong>用户 → 主岗 → 组织 → DEPT 档位的可见范围</strong>。 层级由
            <strong>物化路径</strong>表达，编码租户内唯一且<strong>建后不可修改</strong>。
            操作入口按权限显隐：新建需要 <code>{PERM.ORG_CREATE}</code>，编辑 / 移动 / 挂成员需要{' '}
            <code>{PERM.ORG_UPDATE}</code>，删除需要 <code>{PERM.ORG_DELETE}</code>。
          </p>
        </div>
        <div className="panel__actions">
          <PermissionButton
            permission={PERM.ORG_CREATE}
            variant="primary"
            className="orgs__create"
            onClick={() => {
              setDialogError(null);
              setCreatingParent('');
            }}
          >
            新建顶级组织
          </PermissionButton>
        </div>
      </header>

      {notice ? (
        <p className="banner banner--ok" data-testid="orgs-notice">
          {notice}
        </p>
      ) : null}

      {error ? (
        <p className="banner banner--error" data-testid="orgs-error">
          {error}
        </p>
      ) : null}

      <div className="tree" data-testid="orgs-tree">
        {tree.length === 0 ? (
          <p className="table__empty" data-testid="orgs-empty">
            {loading ? '加载中…' : '还没有任何组织'}
          </p>
        ) : (
          <ul className="tree__list">
            {tree.map((node) => (
              <OrgBranch
                key={String(node.id)}
                node={node}
                onCreateChild={(parentId) => {
                  setDialogError(null);
                  setCreatingParent(parentId);
                }}
                onEdit={(target) => void openEdit(target)}
                onMove={(target) => {
                  setDialogError(null);
                  setMoving(target);
                }}
                onMembers={(target) => setMembering(target)}
                onRemove={(target) => {
                  setDialogError(null);
                  setRemoving(target);
                }}
              />
            ))}
          </ul>
        )}
      </div>

      <p className="toolbar__meta" data-testid="orgs-total">
        共 {total} 个组织 · 顶级 {tree.length} 个
      </p>

      {creating || editing ? (
        <OrgFormDialog
          key={editing ? `edit-${editing.id}` : `create-${creatingParent}`}
          initial={editing}
          parentOptions={options}
          defaultParentId={creatingParent ?? ''}
          busy={busy}
          error={dialogError}
          onClose={closeDialog}
          onSubmit={(form) => submitForm(form, editing)}
        />
      ) : null}

      {moving ? (
        <MoveDialog
          node={moving}
          parentOptions={options}
          busy={busy}
          error={dialogError}
          onClose={() => setMoving(null)}
          onSubmit={submitMove}
        />
      ) : null}

      {membering ? (
        <OrgMembersDialog org={membering} onClose={() => setMembering(null)} onChanged={reload} />
      ) : null}

      {removing ? (
        <Dialog
          title="删除组织"
          onClose={() => setRemoving(null)}
          footer={
            <>
              <button type="button" className="btn" onClick={() => setRemoving(null)}>
                取消
              </button>
              <button
                type="button"
                className="btn btn--danger orgs__remove-confirm"
                disabled={busy}
                onClick={confirmRemove}
              >
                确认删除
              </button>
            </>
          }
        >
          <p>
            将删除组织 <strong>{removing.orgName}</strong>（ID {removing.id}）。
          </p>
          <p className="form__tip">
            有下级时被拒（<code>40913</code>），有成员时被拒（<code>40919</code>）。
            <strong>没有级联删除</strong> —— 这两道守卫是刻意的：删一个中间节点等于
            无声地撤销一批人的数据范围，而它不会报错，只会让那些人的查询范围悄悄变窄。
          </p>
          {dialogError ? <p className="banner banner--error">{dialogError}</p> : null}
        </Dialog>
      ) : null}
    </section>
  );
}

/** 树的一个分支。递归渲染，子节点在自己的 `ul` 里 */
function OrgBranch({
  node,
  onCreateChild,
  onEdit,
  onMove,
  onMembers,
  onRemove,
}: {
  node: AccessOrganizationTreeNode;
  onCreateChild: (parentId: string) => void;
  onEdit: (node: AccessOrganizationTreeNode) => void;
  onMove: (node: AccessOrganizationTreeNode) => void;
  onMembers: (node: AccessOrganizationTreeNode) => void;
  onRemove: (node: AccessOrganizationTreeNode) => void;
}) {
  const children = node.children ?? [];
  const disabled = node.status !== 'ENABLED';

  return (
    <li className="tree__node" data-testid={`org-node-${String(node.orgCode)}`}>
      <div className="tree__row">
        <span className="tree__code mono">{node.orgCode}</span>
        <span className="tree__name">{node.orgName ?? '—'}</span>
        {/* 层级是派生值：显示它是为了让"移动会改层级"这件事可见，而不是为了让人输入 */}
        <span className="tag tag--on">L{node.orgLevel ?? '?'}</span>
        {node.managerName ? <span className="tree__name">负责人 {node.managerName}</span> : null}
        {disabled ? <span className="tag tag--off">停用</span> : null}
        <span className="tree__ops" data-testid={`org-ops-${String(node.orgCode)}`}>
          <PermissionButton
            permission={PERM.ORG_CREATE}
            className="orgs__create-child"
            onClick={() => onCreateChild(String(node.id))}
          >
            加下级
          </PermissionButton>
          <PermissionButton
            permission={PERM.ORG_UPDATE}
            className="orgs__edit"
            onClick={() => onEdit(node)}
          >
            编辑
          </PermissionButton>
          {/*
            移动是**独立入口**，不是编辑里的一项。
            它会重写整棵子树的 orgLevel / codePath / fullName，子树上每个人的
            DEPT_AND_CHILD 可见范围同时改变 —— 后果与"改个名字"不在一个量级，
            因此必须点两次、且对话框里明说。审计动作也是分开的（auth.org.move）。
          */}
          <PermissionButton
            permission={PERM.ORG_UPDATE}
            className="orgs__move"
            title="移动会改写整棵子树的层级与可见范围"
            onClick={() => onMove(node)}
          >
            移动
          </PermissionButton>
          <PermissionButton
            permission={PERM.ORG_UPDATE}
            className="orgs__members"
            onClick={() => onMembers(node)}
          >
            成员
          </PermissionButton>
          <PermissionButton
            permission={PERM.ORG_DELETE}
            variant="danger"
            className="orgs__remove"
            onClick={() => onRemove(node)}
          >
            删除
          </PermissionButton>
        </span>
      </div>
      {children.length > 0 ? (
        <ul className="tree__list">
          {children.map((child) => (
            <OrgBranch
              key={String(child.id)}
              node={child}
              onCreateChild={onCreateChild}
              onEdit={onEdit}
              onMove={onMove}
              onMembers={onMembers}
              onRemove={onRemove}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
}

/** 新建 / 编辑共用一个表单；`initial` 为空即新建 */
function OrgFormDialog({
  initial,
  parentOptions,
  defaultParentId,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  initial: AccessOrganization | null;
  parentOptions: FlatNode[];
  defaultParentId: string;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (form: OrgForm) => void;
}) {
  const [form, setForm] = useState<OrgForm>(() =>
    initial ? toForm(initial) : { ...EMPTY_FORM, parentId: defaultParentId },
  );
  const [users, setUsers] = useState<AccessUser[]>([]);

  const update = (patch: Partial<OrgForm>): void => setForm((prev) => ({ ...prev, ...patch }));

  /*
   * 用户列表**只在对话框打开时拉**，不跟组织树一起拉。
   *
   * 原因是两者没有关系：组织树每次进页面都要看，而用户列表只在"选负责人"时才需要，
   * 且它是个 200 条上限的候选集合。跟树一起拉等于让每次加载都付一次没人用的开销。
   */
  useEffect(() => {
    let alive = true;
    void (async () => {
      try {
        const result = await api().access.users.page({ page: 1, size: OPTION_SIZE });
        if (alive) setUsers(result.records ?? []);
      } catch {
        // 拉不到候选就让负责人保持为空：它是描述性字段，不该拖垮整个表单
        if (alive) setUsers([]);
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  const codeLooksWrong = form.orgCode.length > 0 && !CODE_PATTERN.test(form.orgCode);
  const nameMissing = form.orgName.trim().length === 0;
  // 编码与名称都齐才放行：少一次注定被服务端拒掉的往返
  const submitDisabled = busy || codeLooksWrong || nameMissing;

  const currentParent = initial?.parentId ?? null;
  const parentLabel =
    currentParent == null
      ? '（顶级）'
      : (parentOptions.find((item) => item.node.id === currentParent)?.node.orgName ??
        `#${currentParent}`);

  return (
    <Dialog
      title={initial ? `编辑组织 · ${initial.orgCode}` : '新建组织'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
          <button
            type="submit"
            form="org-form"
            className="btn btn--primary orgs__submit"
            disabled={submitDisabled}
          >
            {busy ? '提交中…' : '保存'}
          </button>
        </>
      }
    >
      <form
        id="org-form"
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          if (submitDisabled) return;
          onSubmit(form);
        }}
      >
        <label className="form__row">
          <span className="form__label">组织编码</span>
          <input
            className="input"
            name="orgCode"
            placeholder="RD_FE"
            value={form.orgCode}
            // 编码建后不可改（后端 update 不读它），但仍要回传：契约里它是必填
            readOnly={Boolean(initial)}
            onChange={(event) => update({ orgCode: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">名称</span>
          <input
            className="input"
            name="orgName"
            value={form.orgName}
            onChange={(event) => update({ orgName: event.target.value })}
          />
        </label>

        {initial ? (
          /*
           * 编辑态**没有上级选择器**，只显示当前值。
           *
           * 后端对"parentId 与原值不同"直接返回 40001，所以渲染一个可改的下拉框
           * 等于给一个必然报错的开关。层级改动归「移动」—— 那是一件不同的事，
           * 有自己的审计动作。
           */
          <label className="form__row">
            <span className="form__label">上级组织</span>
            <input className="input" name="parentIdReadonly" value={parentLabel} readOnly />
          </label>
        ) : (
          <label className="form__row">
            <span className="form__label">上级组织</span>
            <select
              className="input orgs__parent"
              name="parentId"
              value={form.parentId}
              onChange={(event) => update({ parentId: event.target.value })}
            >
              {/*
                选项里刻意**没有**"不选"这个模糊项：空值就是顶级，它是这个下拉框的
                第一个正常取值，不是一个"还没填"的中间态。
              */}
              <option value="">（顶级组织）</option>
              {parentOptions.map((item) => (
                <option key={String(item.node.id)} value={String(item.node.id)}>
                  {'　'.repeat(item.depth)}
                  {item.node.orgName ?? item.node.orgCode}（#{item.node.id}）
                </option>
              ))}
            </select>
          </label>
        )}

        <label className="form__row">
          <span className="form__label">组织类型</span>
          <input
            className="input"
            name="orgType"
            placeholder="DEPT"
            value={form.orgType}
            onChange={(event) => update({ orgType: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">负责人</span>
          <select
            className="input orgs__manager"
            name="managerId"
            value={form.managerId}
            onChange={(event) => update({ managerId: event.target.value })}
          >
            <option value="">（不指定）</option>
            {users.map((user) => (
              <option key={String(user.id)} value={String(user.id)}>
                {user.displayName || user.username}（#{user.id}）
              </option>
            ))}
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
          <span className="form__label">联系人</span>
          <input
            className="input"
            name="contactName"
            value={form.contactName}
            onChange={(event) => update({ contactName: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">联系电话</span>
          <input
            className="input"
            name="contactPhone"
            value={form.contactPhone}
            onChange={(event) => update({ contactPhone: event.target.value })}
          />
        </label>
        <label className="form__row">
          <span className="form__label">联系邮箱</span>
          <input
            className="input"
            name="contactEmail"
            value={form.contactEmail}
            onChange={(event) => update({ contactEmail: event.target.value })}
          />
        </label>

        <p className="form__tip">
          <strong>停用的组织不再能作为 `DEPT` 档位的范围目标</strong> —— 挂在这个组织下的人
          会退回"仅本人"。停用**不级联**，下级不受影响。
        </p>
        <p className="form__tip">
          组织类型<strong>只作展示，不参与任何判定</strong>，因此这里是自由文本而不是枚举：
          先把取值定下来的是使用方，不是我们。
        </p>
        <p className="form__tip">
          层级、物化路径与全称<strong>由服务端按上级派生</strong>，表单里没有这几项 ——
          改层级请用列表上的「移动」。
        </p>
        {codeLooksWrong ? (
          <p className="banner banner--error">
            组织编码须以字母开头，后跟字母 / 数字 / 下划线，最长 64 位。
          </p>
        ) : null}
        {error ? <p className="banner banner--error">{error}</p> : null}
      </form>
    </Dialog>
  );
}

/**
 * 移动节点 —— 这一页后果最大的一个动作。
 *
 * <p>它会把**整棵子树**的 `orgLevel` / `codePath` / `fullName` 一起重写（ADR-0030 第 4 条），
 * 而 `codePath` 正是 `DEPT_AND_CHILD` 的求值口径。因此这不是"换了个上级部门"：
 * 子树上每个人的可见数据范围会同时改变，且**两处都不报错**。
 * 对话框第一段就写这一句，不折叠、不放提示符里。
 *
 * <p>下拉框里剔除了自身与全部后代：那是一个必然被拒的目标（40913）。
 */
function MoveDialog({
  node,
  parentOptions,
  busy,
  error,
  onClose,
  onSubmit,
}: {
  node: AccessOrganizationTreeNode;
  parentOptions: FlatNode[];
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (parentId: number | null) => void;
}) {
  const excluded = useMemo(() => new Set(selfAndDescendantIds(node)), [node]);
  const candidates = parentOptions.filter((item) => !excluded.has(item.node.id as number));

  const [parentId, setParentId] = useState('');

  return (
    <Dialog
      title={`移动组织 · ${node.orgName}`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            取消
          </button>
          <button
            type="submit"
            form="org-move-form"
            className="btn btn--primary orgs__move-submit"
            disabled={busy}
          >
            {busy ? '提交中…' : '确认移动'}
          </button>
        </>
      }
    >
      <form
        id="org-move-form"
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit(parentId ? Number(parentId) : null);
        }}
      >
        <p className="form__tip">
          这是一次<strong>子树级</strong>的改动：层级、物化路径与全称会一起重写，
          <strong>子树上每个人的 `DEPT_AND_CHILD` 可见范围同时改变</strong>。
          它会留下独立的审计记录（<code>auth.org.move</code>）。
        </p>
        <label className="form__row">
          <span className="form__label">新的上级组织</span>
          <select
            className="input orgs__move-parent"
            name="parentId"
            value={parentId}
            onChange={(event) => setParentId(event.target.value)}
          >
            <option value="">（提升为顶级）</option>
            {candidates.map((item) => (
              <option key={String(item.node.id)} value={String(item.node.id)}>
                {'　'.repeat(item.depth)}
                {item.node.orgName ?? item.node.orgCode}（#{item.node.id}）
              </option>
            ))}
          </select>
        </label>
        <p className="form__tip">
          候选里<strong>不含自身与它的下级</strong>：那些目标必然被拒（<code>40913</code>，
          移动会造成环）。这只省一次注定失败的往返 —— 服务端照样独立判定。
        </p>
        <p className="form__tip">
          当前上级：{node.parentId == null ? '（顶级）' : `#${node.parentId}`} · 当前层级 L
          {node.orgLevel ?? '?'} · 路径 <code className="mono">{node.codePath}</code>
        </p>
        {error ? <p className="banner banner--error">{error}</p> : null}
      </form>
    </Dialog>
  );
}

/** 挂成员时的主岗选择：三态，缺省是"不指定" */
type PrimaryChoice = '' | '1' | '0';

type MemberForm = {
  memberId: string;
  isPrimaryPosition: PrimaryChoice;
  positionName: string;
  from: string;
  to: string;
};

const EMPTY_MEMBER_FORM: MemberForm = {
  memberId: '',
  isPrimaryPosition: '',
  positionName: '',
  from: '',
  to: '',
};

/**
 * 组织成员对话框 —— 列成员、挂成员、设主岗、移出。
 *
 * <h3>`memberId` 与 `userId` 是同一个东西，两个名字</h3>
 *
 * 列表里叫 `memberId`（"这个组织的成员"），路径参数里叫 `userId`（"这个人"）——
 * 这是契约的写法，照抄不加工。值得写在这里是因为：组织域的关系表只有用户，
 * 不存在角色那边"用户与用户组同号"的歧义，所以这里**没有 `memberType`**。
 *
 * <h3>为什么"是否主岗"是三态而不是复选框</h3>
 *
 * 服务端对"不传 `isPrimaryPosition`"的定义是：该用户当前没有主岗则自动设为主岗，
 * **已有主岗则保持不变**。这是个有信息量的默认值 —— 用一个默认勾选的复选框表达它，
 * 会把这个默认值压成一个二进制："不勾"读起来是"明确要求不要当主岗"，
 * 而那恰好是最容易误伤数据范围的一种读法（把已有主岗降掉）。
 *
 * <h3>「设为主岗」是独立按钮，不只是挂成员时的一个勾</h3>
 *
 * 两者的后果一样（都决定这个人的 `DEPT` 范围），但**审计动作不同**：
 * 挂成员记 `auth.org.bind_member`，设主岗记 `auth.org.set_primary_position`。
 * 已经挂进来的人要改主岗，只能用后者 —— 所以必须给它一个独立入口。
 */
function OrgMembersDialog({
  org,
  onClose,
  onChanged,
}: {
  org: AccessOrganizationTreeNode;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [page, setPage] = useState<AccessOrgMemberPage | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [users, setUsers] = useState<AccessUser[]>([]);
  const [form, setForm] = useState<MemberForm>(EMPTY_MEMBER_FORM);
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
          api().access.organizations.pageMembers(org.id as number, {
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
  }, [org.id, pageNo, tick]);

  const submit = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      const body: AccessOrgMemberBindRequest = {
        memberId: Number(form.memberId),
        // 三态原样透传：'' → undefined（走服务端的"自动 / 保持"规则），'1' / '0' → 明确的意图
        isPrimaryPosition:
          form.isPrimaryPosition === '' ? undefined : Number(form.isPrimaryPosition),
        positionName: form.positionName.trim() || undefined,
        effectiveFrom: parseDateTimeLocal(form.from),
        effectiveTo: parseDateTimeLocal(form.to),
      };
      await api().access.organizations.bindMember(org.id as number, body);
      setForm(EMPTY_MEMBER_FORM);
      setTick((value) => value + 1);
      onChanged();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const setPrimary = async (member: AccessOrgMember): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await api().access.organizations.setPrimaryPosition(org.id as number, {
        userId: member.memberId as number,
      });
      setTick((value) => value + 1);
      onChanged();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (member: AccessOrgMember): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await api().access.organizations.revokeMember(org.id as number, {
        userId: member.memberId as number,
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
      title={`组织成员 · ${org.orgName}`}
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
              <th>岗位</th>
              <th>生效区间</th>
              <th className="table__ops">操作</th>
            </tr>
          </thead>
          <tbody>
            {records.length === 0 ? (
              <tr>
                <td className="table__empty" colSpan={4}>
                  {loading ? '加载中…' : '这个组织下还没有成员'}
                </td>
              </tr>
            ) : (
              records.map((member) => (
                <tr
                  key={String(member.grantId)}
                  data-testid={`org-member-${String(member.memberId)}`}
                >
                  <td>
                    {member.memberName ?? '（用户已不存在）'}
                    <span className="mono"> #{member.memberId}</span>
                    {/*
                      主岗标记要显眼：它是 DEPT 档位**唯一**的取值来源。
                      每人每租户至多一个 —— 但那是服务层保证的（关系表没有唯一索引），
                      所以这里同时用 primaryOnly 之外的完整列表把它显示出来，
                      让人能亲眼看到"确实只有一个"。
                    */}
                    {member.primaryPosition ? (
                      <span className="tag tag--on" data-testid="org-primary-flag">
                        主岗
                      </span>
                    ) : null}
                  </td>
                  <td>
                    {member.positionName ?? '—'}
                    {member.relationType ? (
                      <span className="tree__name">{member.relationType}</span>
                    ) : null}
                  </td>
                  <td className="mono">
                    {formatWindow(member.effectiveFrom, member.effectiveTo)}
                    {member.effectiveNow === false ? (
                      // 过期的归属**不参与数据范围求值**，但关系保留 —— 所以显示成标签而不是隐藏
                      <span className="tag tag--off">已失效</span>
                    ) : null}
                  </td>
                  <td className="table__ops">
                    <PermissionButton
                      permission={PERM.ORG_UPDATE}
                      className="orgs__set-primary"
                      title="设为主岗会同时把该用户级别处的主岗降下来"
                      onClick={() => void setPrimary(member)}
                    >
                      {member.primaryPosition ? '重设主岗' : '设为主岗'}
                    </PermissionButton>
                    <PermissionButton
                      permission={PERM.ORG_UPDATE}
                      variant="danger"
                      className="orgs__revoke-member"
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
            className="input orgs__member-id"
            name="memberId"
            value={form.memberId}
            onChange={(event) => setForm((prev) => ({ ...prev, memberId: event.target.value }))}
          >
            {/* 不给默认值：与角色成员关系同一理由 —— 静默选中的那一行不是用户的意思 */}
            <option value="">（请选择用户）</option>
            {users.map((user) => (
              <option key={String(user.id)} value={String(user.id)}>
                {user.displayName || user.username}（#{user.id}）
              </option>
            ))}
          </select>
        </label>
        <label className="form__row">
          <span className="form__label">是否主岗</span>
          <select
            className="input orgs__primary-choice"
            name="isPrimaryPosition"
            value={form.isPrimaryPosition}
            onChange={(event) =>
              setForm((prev) => ({
                ...prev,
                isPrimaryPosition: event.target.value as PrimaryChoice,
              }))
            }
          >
            <option value="">不指定（按服务端规则）</option>
            <option value="1">是（设为主岗）</option>
            <option value="0">否（不要动主岗）</option>
          </select>
        </label>
        <label className="form__row">
          <span className="form__label">岗位名</span>
          <input
            className="input"
            name="positionName"
            value={form.positionName}
            onChange={(event) => setForm((prev) => ({ ...prev, positionName: event.target.value }))}
          />
        </label>
        <label className="form__row">
          <span className="form__label">生效起</span>
          <input
            className="input"
            name="effectiveFrom"
            type="datetime-local"
            value={form.from}
            onChange={(event) => setForm((prev) => ({ ...prev, from: event.target.value }))}
          />
        </label>
        <label className="form__row">
          <span className="form__label">生效止</span>
          <input
            className="input"
            name="effectiveTo"
            type="datetime-local"
            value={form.to}
            onChange={(event) => setForm((prev) => ({ ...prev, to: event.target.value }))}
          />
        </label>
        <p className="form__tip">
          <strong>「不指定」的含义不是"否"</strong>：该用户当前没有主岗时会自动设为主岗， 已有主岗则
          <strong>保持不变</strong> —— 服务端不会静默搬动一个人已有的数据范围。
        </p>
        <p className="form__tip">
          主岗决定 `DEPT` / `DEPT_AND_CHILD` 两档的可见范围，因此<strong>每人每租户至多一个</strong>
          ； 设为主岗时服务端会先把别处的主岗降下来。已过期的归属<strong>不参与求值</strong>
          （见上方标签）。
        </p>
        <div className="form__actions">
          <button
            type="submit"
            className="btn btn--primary orgs__member-submit"
            disabled={busy || !form.memberId}
          >
            {busy ? '提交中…' : '挂到组织下'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
