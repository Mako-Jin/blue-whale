// 场景管理页：场景 CRUD（草稿→发布⇄停用）+ 发布快照（M3）+ 进入查看器入口
//
// 发布需填发布说明（可空），发布时后端把场景整包冻结进 twin_snapshot；
// 快照抽屉支持查看历史版本与回滚（场景须为草稿/停用态）。
// 实体编辑在 3D 查看器页完成（树形面板），本页只管场景元数据与生命周期。
//
// Wave 6.08 M2：工具栏挂「全局告警」角标（/twin/alerts/summary，30s 轮询），
// 抽屉拉跨场景未恢复列表（/twin/alerts/unresolved），条目带场景名可直接跳查看器，
// 也支持就地确认——不必先点进某个场景才知道平台在报警。

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  App,
  Badge,
  Button,
  Checkbox,
  Drawer,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Tooltip,
  TreeSelect,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  alertApi,
  alertOverviewApi,
  notifyApi,
  sceneApi,
  snapshotApi,
  type AlertDispatchStatus,
  type SceneSaveRequest,
  type SceneVO,
  type SnapshotVO,
  type TwinAlertDispatchVO,
  type TwinAlertNotifySettingsRequest,
  type TwinAlertNotifySettingsVO,
  type TwinAlertSummaryVO,
  type TwinAlertVO,
  type TwinNotifyDeptVO,
  type TwinNotifyRecipientVO,
} from '../api/twin';

/** 状态 → 标签（颜色对齐 IoT 产品页口径） */
const STATUS_TAG: Record<SceneVO['status'], { label: string; color: string }> = {
  draft: { label: '草稿', color: 'default' },
  published: { label: '已发布', color: 'success' },
  disabled: { label: '已停用', color: 'warning' },
};

/** 告警级别 → 展示文案与配色（与查看器告警中心同口径：橙预警 / 红严重） */
const ALERT_LEVEL_META: Record<string, { color: string; label: string }> = {
  critical: { color: 'red', label: '严重' },
  warn: { color: 'orange', label: '预警' },
  normal: { color: 'green', label: '恢复' },
};

/** 总览列表一次拉取上限（与后端钳制值一致，超出时抽屉给「仅展示最近 N 条」提示） */
const OVERVIEW_LIMIT = 200;

/** 角标统计轮询间隔（无订阅时告警靠无头通道落库，前端只需低频对齐） */
const SUMMARY_POLL_MS = 30_000;

/** TreeSelect 节点（antd 用 title/value，后端给的是 name/id） */
interface DeptTreeNode {
  title: string;
  value: string;
  children?: DeptTreeNode[];
}

/**
 * 部门树 → TreeSelect 数据（后端 children 为 null 表示叶子）。
 *
 * @param nodes 后端部门树
 * @returns TreeSelect 可消费的节点
 */
function toDeptTreeData(nodes: TwinNotifyDeptVO[]): DeptTreeNode[] {
  return nodes.map((node) => {
    const children = node.children && node.children.length > 0
      ? toDeptTreeData(node.children)
      : undefined;
    return { title: node.name, value: node.id, children };
  });
}

interface Props {
  onOpenViewer: (sceneId: string) => void;
}

/** 表单值（antd Form 包装，字段与 SceneSaveRequest 对齐） */
interface SceneFormValues {
  code: string;
  name: string;
  description?: string;
  sceneConfig?: string;
}

export default function ScenePage({ onOpenViewer }: Props) {
  const { message } = App.useApp();
  const [rows, setRows] = useState<SceneVO[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState({ current: 1, size: 20, keyword: '' });
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<SceneVO | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [form] = Form.useForm<SceneFormValues>();

  // ---- M3：发布说明 + 快照 ----
  const [publishTarget, setPublishTarget] = useState<SceneVO | null>(null);
  const [publishRemark, setPublishRemark] = useState('');
  const [snapshotScene, setSnapshotScene] = useState<SceneVO | null>(null);
  const [snapshots, setSnapshots] = useState<SnapshotVO[]>([]);
  const [snapshotDetail, setSnapshotDetail] = useState<SnapshotVO | null>(null);

  // ---- 6.08 M2：全局告警角标 + 跨场景总览抽屉 ----
  const [summary, setSummary] = useState<TwinAlertSummaryVO | null>(null);
  const [overviewOpen, setOverviewOpen] = useState(false);
  const [overviewRows, setOverviewRows] = useState<TwinAlertVO[]>([]);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [overviewLevel, setOverviewLevel] = useState<'all' | 'critical' | 'warn'>('all');
  const [overviewUnackedOnly, setOverviewUnackedOnly] = useState(false);
  /** 抽屉的场景限定（从某一行角标点进来时只看该场景） */
  const [overviewScene, setOverviewScene] = useState<{ id: string; name: string } | null>(null);

  // ---- 6.10 M2：场景通知设置 + 投递记录 ----
  const [notifyScene, setNotifyScene] = useState<SceneVO | null>(null);
  const [notifySettings, setNotifySettings] = useState<TwinAlertNotifySettingsVO | null>(null);
  const [notifyLoading, setNotifyLoading] = useState(false);
  const [notifySaving, setNotifySaving] = useState(false);
  /** 本场景关闭 webhook / 站内信 / 恢复通知（后端 false = 关闭，null = 跟随全局） */
  const [webhookOff, setWebhookOff] = useState(false);
  const [inappOff, setInappOff] = useState(false);
  const [recoveryOff, setRecoveryOff] = useState(false);
  /** 场景级门槛：跟随全局 或 仅严重 */
  const [minLevelOverride, setMinLevelOverride] = useState<'follow' | 'critical'>('follow');
  /** 场景级静默窗口（HH:mm；空 = 跟随全局，只叠加不覆盖） */
  const [quietStart, setQuietStart] = useState('');
  const [quietEnd, setQuietEnd] = useState('');
  /** 接收人（6.11 M2：从 auth 通讯录选人，不再手填用户ID） */
  const [recipientIds, setRecipientIds] = useState<string[]>([]);
  const [recipientOptions, setRecipientOptions] = useState<TwinNotifyRecipientVO[]>([]);
  const [recipientLoading, setRecipientLoading] = useState(false);
  /** 6.12：按部门筛人（空 = 不限部门）+ 匹配总人数（用于提示「共 N 人」） */
  const [recipientDeptId, setRecipientDeptId] = useState<string | undefined>(undefined);
  const [recipientTotal, setRecipientTotal] = useState(0);
  const [deptTree, setDeptTree] = useState<TwinNotifyDeptVO[]>([]);
  const [deptLoading, setDeptLoading] = useState(false);
  /** 选人搜索防抖句柄（每敲一个字就发请求会打爆 auth） */
  const recipientTimer = useRef<number | undefined>(undefined);
  const [dispatchRows, setDispatchRows] = useState<TwinAlertDispatchVO[]>([]);
  const [dispatchLoading, setDispatchLoading] = useState(false);
  const [dispatchStatus, setDispatchStatus] = useState<'all' | AlertDispatchStatus>('all');
  const [dispatchOpen, setDispatchOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await sceneApi.page({
        keyword: query.keyword || undefined,
        current: query.current,
        size: query.size,
      });
      setRows(page.records ?? []);
      setTotal(page.total ?? 0);
    } catch (error) {
      message.error(`加载场景失败：${String(error)}`);
    } finally {
      setLoading(false);
    }
  }, [message, query]);

  useEffect(() => {
    void load();
  }, [load]);

  /**
   * 静默重取当前页（不置 loading，轮询用）：失败保留上次数据。
   *
   * <p>行级告警角标随分页返回，告警恢复后角标需要跟着退，所以低频安静重取一次。</p>
   */
  const refreshQuiet = useCallback(async () => {
    try {
      const page = await sceneApi.page({
        keyword: query.keyword || undefined,
        current: query.current,
        size: query.size,
      });
      setRows(page.records ?? []);
      setTotal(page.total ?? 0);
    } catch {
      // 静默：角标刷新失败不影响当前列表
    }
  }, [query]);

  /**
   * 拉取平台级告警统计（角标数字）。
   *
   * @param silent 静默模式（轮询用；失败不改角标、不弹错误，避免后台刷屏）
   */
  const loadSummary = useCallback(
    async (silent = true) => {
      try {
        setSummary(await alertOverviewApi.summary());
      } catch (error) {
        if (!silent) {
          message.error(`加载告警统计失败：${String(error)}`);
        }
      }
    },
    [message],
  );

  // 角标：进页拉一次 + 低频轮询（告警由无头通道落库，不依赖是否有人开着查看器）
  // 行级角标随分页返回，轮询时静默重取一次列表，避免告警已恢复但角标还在
  useEffect(() => {
    void loadSummary();
    const timer = window.setInterval(() => {
      if (document.visibilityState !== 'visible') return;
      void loadSummary();
      void refreshQuiet();
    }, SUMMARY_POLL_MS);
    return () => window.clearInterval(timer);
  }, [loadSummary, refreshQuiet]);

  /** 拉取跨场景未恢复列表（抽屉打开/刷新/确认后调用） */
  const loadOverview = useCallback(async () => {
    setOverviewLoading(true);
    try {
      setOverviewRows(await alertOverviewApi.unresolved(OVERVIEW_LIMIT));
    } catch (error) {
      message.error(`加载全局告警失败：${String(error)}`);
    } finally {
      setOverviewLoading(false);
    }
  }, [message]);

  /**
   * 打开全局告警抽屉。
   *
   * @param scene 传入场景则只看该场景（行级角标点入），不传为全平台
   */
  const openOverview = (scene?: { id: string; name: string }) => {
    setOverviewScene(scene ?? null);
    setOverviewOpen(true);
    void loadOverview();
    void loadSummary();
  };

  /** 总览内就地确认：走场景维度确认接口（条目必带 sceneId） */
  const ackInOverview = async (item: TwinAlertVO) => {
    if (!item.sceneId) {
      message.warning('该告警缺少场景信息，请进入对应场景后确认');
      return;
    }
    try {
      await alertApi.ack(item.sceneId, item.id);
      message.success('告警已确认');
      void loadOverview();
      void loadSummary();
      void refreshQuiet();
    } catch (error) {
      message.error(`确认失败：${String(error)}`);
    }
  };

  /** 抽屉内本地过滤（场景 + 级别 + 未确认），不额外打接口 */
  const overviewView = useMemo(
    () =>
      overviewRows.filter(
        (item) =>
          (!overviewScene || item.sceneId === overviewScene.id) &&
          (overviewLevel === 'all' || item.level === overviewLevel) &&
          (!overviewUnackedOnly || !item.acked),
      ),
    [overviewRows, overviewScene, overviewLevel, overviewUnackedOnly],
  );

  /** 场景限定下有多少条告警没进本次加载（只在超出 200 条上限时非 0） */
  const overviewSceneMissing = useMemo(() => {
    if (!overviewScene) {
      return 0;
    }
    const row = rows.find((item) => item.id === overviewScene.id);
    if (!row) {
      return 0;
    }
    const loaded = overviewRows.filter((item) => item.sceneId === overviewScene.id).length;
    return Math.max((row.unresolvedAlerts ?? 0) - loaded, 0);
  }, [overviewScene, rows, overviewRows]);

  /**
   * 拉取接收人候选（远程搜索，失败静默：通讯录不可用时仍保留已选的人）。
   *
   * @param keyword 关键字（空 = 取前 20 人）
   * @param keep    需要保留显示的已选ID（旧配置里可能存在通讯录之外的人）
   * @param deptId  部门ID（可空 = 不限部门）
   */
  const loadRecipients = useCallback(
    async (keyword: string, keep: string[] = [], deptId?: string) => {
      setRecipientLoading(true);
      try {
        const page = await notifyApi.recipients({ keyword, deptId, size: 20 });
        setRecipientTotal(page.total);
        setRecipientOptions((prev) => {
          const merged = new Map<string, TwinNotifyRecipientVO>();
          [...prev, ...page.records].forEach((item) => merged.set(item.id, item));
          keep.forEach((id) => {
            if (!merged.has(id)) {
              merged.set(id, { id, name: `用户 ${id}` });
            }
          });
          return [...merged.values()];
        });
      } catch {
        // 静默：选人只是辅助，拉不到就让用户看到已有的选项
      } finally {
        setRecipientLoading(false);
      }
    },
    [],
  );

  /** 拉取部门树（按部门筛人用；拉不到就让「部门」保持为空，不影响按关键字搜） */
  const loadDepartments = useCallback(async () => {
    setDeptLoading(true);
    try {
      setDeptTree(await notifyApi.departments());
    } catch {
      // 静默：部门树只是筛选辅助
    } finally {
      setDeptLoading(false);
    }
  }, []);

  /**
   * 选人搜索（300ms 防抖）。
   *
   * @param keyword 输入的关键字
   */
  const searchRecipients = (keyword: string) => {
    if (recipientTimer.current) {
      window.clearTimeout(recipientTimer.current);
    }
    recipientTimer.current = window.setTimeout(() => {
      void loadRecipients(keyword, recipientIds, recipientDeptId);
    }, 300);
  };

  /**
   * 切换部门筛选：清掉防抖中的搜索，立刻按新部门重拉第一页。
   *
   * @param deptId 部门ID（undefined = 不限部门）
   */
  const changeRecipientDept = (deptId?: string) => {
    if (recipientTimer.current) {
      window.clearTimeout(recipientTimer.current);
    }
    setRecipientDeptId(deptId);
    void loadRecipients('', recipientIds, deptId);
  };

  /**
   * 打开场景通知设置抽屉（6.10 M2）：读回场景级配置并回填表单。
   *
   * @param scene 目标场景
   */
  const openNotify = async (scene: SceneVO) => {
    setNotifyScene(scene);
    setNotifySettings(null);
    setDispatchRows([]);
    setDispatchStatus('all');
    setNotifyLoading(true);
    try {
      const settings = await notifyApi.getSettings(scene.id);
      setNotifySettings(settings);
      setWebhookOff(settings.webhookEnabled === false);
      setInappOff(settings.inappEnabled === false);
      setRecoveryOff(settings.recoveryEnabled === false);
      setMinLevelOverride(settings.minLevel === 'critical' ? 'critical' : 'follow');
      setQuietStart(settings.quietStart ?? '');
      setQuietEnd(settings.quietEnd ?? '');
      setRecipientIds(settings.userIds);
      setRecipientDeptId(undefined);
      void loadRecipients('', settings.userIds);
      void loadDepartments();
    } catch (error) {
      message.error(`加载通知设置失败：${String(error)}`);
    } finally {
      setNotifyLoading(false);
    }
  };

  /** 保存场景通知设置：null 表示清除该项、回到跟随全局 */
  const saveNotify = async () => {
    if (!notifyScene) {
      return;
    }
    const start = quietStart.trim();
    const end = quietEnd.trim();
    const timePattern = /^([01]?\d|2[0-3]):[0-5]\d$/;
    if (start || end) {
      if (!start || !end) {
        message.error('静默时段需要同时填开始和结束（如 22:00 – 08:00）');
        return;
      }
      if (!timePattern.test(start) || !timePattern.test(end)) {
        message.error('时间格式应为 HH:mm，例如 22:00');
        return;
      }
    }
    const body: TwinAlertNotifySettingsRequest = {
      webhookEnabled: webhookOff ? false : null,
      inappEnabled: inappOff ? false : null,
      recoveryEnabled: recoveryOff ? false : null,
      minLevel: minLevelOverride === 'critical' ? 'critical' : null,
      quietStart: start || null,
      quietEnd: end || null,
      userIds: recipientIds.length > 0 ? recipientIds.map((id) => Number(id)) : null,
    };
    setNotifySaving(true);
    try {
      setNotifySettings(await notifyApi.saveSettings(notifyScene.id, body));
      message.success('通知设置已保存');
    } catch (error) {
      message.error(`保存失败：${String(error)}`);
    } finally {
      setNotifySaving(false);
    }
  };

  /**
   * 拉取当前场景的投递记录。
   *
   * @param status 结果过滤（all = 全部）
   */
  const loadDispatches = useCallback(
    async (status: 'all' | AlertDispatchStatus) => {
      const sceneId = notifyScene?.id;
      if (!sceneId) {
        return;
      }
      setDispatchLoading(true);
      try {
        setDispatchRows(
          await notifyApi.dispatches(sceneId, {
            status: status === 'all' ? undefined : status,
            limit: 100,
          }),
        );
      } catch (error) {
        message.error(`加载投递记录失败：${String(error)}`);
      } finally {
        setDispatchLoading(false);
      }
    },
    [message, notifyScene?.id],
  );

  /** 打开投递记录弹层（沿用当前抽屉里的场景） */
  const openDispatches = () => {
    setDispatchOpen(true);
    setDispatchStatus('all');
    void loadDispatches('all');
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setDrawerOpen(true);
  };

  const openEdit = (scene: SceneVO) => {
    setEditing(scene);
    form.setFieldsValue({
      code: scene.code,
      name: scene.name,
      description: scene.description ?? undefined,
      sceneConfig: scene.sceneConfig ?? undefined,
    });
    setDrawerOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const body: SceneSaveRequest = {
      code: values.code.trim(),
      name: values.name.trim(),
      description: values.description?.trim() || undefined,
      sceneConfig: values.sceneConfig?.trim() || null,
    };
    try {
      if (editing) {
        await sceneApi.update(editing.id, body);
        message.success('场景已更新');
      } else {
        await sceneApi.create(body);
        message.success('场景已创建（草稿态）');
      }
      setDrawerOpen(false);
      void load();
    } catch (error) {
      message.error(`保存失败：${String(error)}`);
    }
  };

  const toggleStatus = async (scene: SceneVO) => {
    try {
      if (scene.status === 'published') {
        await sceneApi.disable(scene.id);
        message.success('场景已停用');
        void load();
      } else {
        // 发布改走弹窗：支持填发布说明（后端冻结快照）
        setPublishTarget(scene);
        setPublishRemark('');
      }
    } catch (error) {
      message.error(`操作失败：${String(error)}`);
    }
  };

  /** 确认发布（M3：发布说明随发布冻结进快照） */
  const confirmPublish = async () => {
    if (!publishTarget) return;
    try {
      await sceneApi.publish(publishTarget.id, { remark: publishRemark.trim() || undefined });
      message.success(`场景已发布（v${publishTarget.version} 快照已冻结）`);
      setPublishTarget(null);
      setPublishRemark('');
      void load();
    } catch (error) {
      message.error(`发布失败：${String(error)}`);
    }
  };

  /** 打开快照抽屉（M3） */
  const openSnapshots = async (scene: SceneVO) => {
    setSnapshotScene(scene);
    setSnapshots([]);
    try {
      setSnapshots(await snapshotApi.list(scene.id));
    } catch (error) {
      message.error(`加载快照失败：${String(error)}`);
    }
  };

  /** 回滚到指定快照（场景须为草稿/停用态，后端把守） */
  const rollback = async (snapshot: SnapshotVO) => {
    if (!snapshotScene) return;
    try {
      await snapshotApi.rollback(snapshotScene.id, snapshot.id);
      message.success(`已回滚到 v${snapshot.version}（工作副本已重建，版本号已递增）`);
      setSnapshots(await snapshotApi.list(snapshotScene.id));
      void load();
    } catch (error) {
      message.error(`回滚失败：${String(error)}`);
    }
  };

  /** 查看快照整包 JSON（详情接口才携带） */
  const showSnapshotDetail = async (snapshot: SnapshotVO) => {
    if (!snapshotScene) return;
    try {
      setSnapshotDetail(await snapshotApi.get(snapshotScene.id, snapshot.id));
    } catch (error) {
      message.error(`加载快照详情失败：${String(error)}`);
    }
  };

  const remove = async (scene: SceneVO) => {
    try {
      await sceneApi.remove(scene.id);
      message.success('场景已删除');
      void load();
    } catch (error) {
      message.error(`删除失败：${String(error)}`);
    }
  };

  const columns: ColumnsType<SceneVO> = [
    { title: '编码', dataIndex: 'code', width: 160 },
    { title: '名称', dataIndex: 'name', width: 180 },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status: SceneVO['status']) => {
        const tag = STATUS_TAG[status] ?? { label: status, color: 'default' };
        return <Tag color={tag.color}>{tag.label}</Tag>;
      },
    },
    { title: '版本', dataIndex: 'version', width: 70 },
    { title: '实体数', dataIndex: 'entityCount', width: 80 },
    {
      // 行级告警角标（6.09）：随分页一次带回，点开抽屉只看本场景
      title: '告警',
      key: 'alerts',
      width: 110,
      render: (_, scene) => {
        const unresolved = scene.unresolvedAlerts ?? 0;
        if (unresolved === 0) {
          return <span className="twin-live-off">—</span>;
        }
        const critical = scene.criticalAlerts ?? 0;
        const unacked = scene.unackedAlerts ?? 0;
        return (
          <Tooltip title={`未恢复 ${unresolved}｜严重 ${critical}｜未确认 ${unacked}（点击查看）`}>
            <Tag
              color={critical > 0 ? 'red' : 'orange'}
              style={{ cursor: 'pointer', marginInlineEnd: 0 }}
              onClick={() => openOverview({ id: scene.id, name: scene.name })}
            >
              {unresolved}
              {critical > 0 ? ` · 严重 ${critical}` : ''}
            </Tag>
          </Tooltip>
        );
      },
    },
    { title: '更新时间', dataIndex: 'updateTime', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 370,
      render: (_, scene) => (
        <Space size="small">
          <Button size="small" type="link" onClick={() => onOpenViewer(scene.id)}>
            查看
          </Button>
          <Button size="small" type="link" disabled={scene.status === 'published'} onClick={() => openEdit(scene)}>
            编辑
          </Button>
          <Button size="small" type="link" onClick={() => void toggleStatus(scene)}>
            {scene.status === 'published' ? '停用' : '发布'}
          </Button>
          <Button size="small" type="link" onClick={() => void openSnapshots(scene)}>
            快照
          </Button>
          {/* 6.10 M2：通知设置与投递记录（运营配置，已发布场景也可改） */}
          <Button size="small" type="link" onClick={() => void openNotify(scene)}>
            通知
          </Button>
          <Popconfirm title="确认删除该场景？" onConfirm={() => void remove(scene)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  /** 投递记录列（结果/通道用标签，失败原因直接可读） */
  const dispatchColumns: ColumnsType<TwinAlertDispatchVO> = [
    { title: '时间', dataIndex: 'createTime', width: 170 },
    {
      title: '通道',
      dataIndex: 'channel',
      width: 90,
      render: (channel: TwinAlertDispatchVO['channel']) => (
        <Tag color={channel === 'INAPP' ? 'blue' : 'geekblue'}>
          {channel === 'INAPP' ? '站内信' : 'Webhook'}
        </Tag>
      ),
    },
    {
      title: '结果',
      dataIndex: 'status',
      width: 90,
      render: (status: TwinAlertDispatchVO['status']) => {
        const meta =
          status === 'SUCCESS'
            ? { color: 'green', label: '成功' }
            : status === 'FAILED'
              ? { color: 'red', label: '失败' }
              : { color: 'default', label: '跳过' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '级别',
      dataIndex: 'level',
      width: 80,
      render: (level: string | null) => {
        const meta = ALERT_LEVEL_META[level ?? ''] ?? { color: 'default', label: level ?? '-' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    { title: '点位', dataIndex: 'stateKey', width: 140, ellipsis: true },
    { title: '目标', dataIndex: 'target', ellipsis: true },
    {
      title: '失败原因',
      dataIndex: 'errorMsg',
      ellipsis: true,
      render: (text: string | null) => text ?? '—',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="搜索名称/编码"
          allowClear
          style={{ width: 260 }}
          onSearch={(keyword) => setQuery((prev) => ({ ...prev, keyword, current: 1 }))}
        />
        <Button type="primary" onClick={openCreate}>
          新建场景
        </Button>
        {/* 全局告警角标（6.08 M2）：数字来自平台级统计，点开跨场景总览 */}
        <Tooltip
          title={
            summary
              ? `未恢复 ${summary.unresolved}｜严重 ${summary.critical}｜预警 ${summary.warn}｜未确认 ${summary.unacked}`
              : '告警统计加载中'
          }
        >
          <Badge
            count={summary?.unresolved ?? 0}
            size="small"
            offset={[2, -2]}
            color={summary && summary.critical > 0 ? 'red' : 'orange'}
          >
            <Button danger={!!summary && summary.critical > 0} onClick={() => openOverview()}>
              全局告警
            </Button>
          </Badge>
        </Tooltip>
        {summary && summary.unresolved > 0 && (
          <span className="twin-live-off">
            严重 {summary.critical} · 预警 {summary.warn} · 未确认 {summary.unacked}
          </span>
        )}
      </Space>
      <Table<SceneVO>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          onChange: (current, size) => setQuery((prev) => ({ ...prev, current, size })),
        }}
      />
      <Drawer
        title={editing ? '编辑场景' : '新建场景'}
        width={480}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submit()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form<SceneFormValues> form={form} layout="vertical">
          <Form.Item
            name="code"
            label="编码"
            rules={[
              { required: true, message: '请输入场景编码' },
              { pattern: /^[a-z][a-z0-9-]{1,63}$/, message: '小写字母开头的字母/数字/中划线，2-64 位' },
            ]}
            extra={editing ? '编码创建后不可修改' : '租户内唯一，如 plant-demo'}
          >
            <Input disabled={!!editing} placeholder="plant-demo" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入场景名称' }]}>
            <Input placeholder="示范厂区" maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} maxLength={512} />
          </Form.Item>
          <Form.Item
            name="sceneConfig"
            label="场景配置 JSON"
            rules={[
              {
                validator: (_, value: string | undefined) => {
                  if (!value) return Promise.resolve();
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('不是合法的 JSON'));
                  }
                },
              },
            ]}
            extra='背景/光照/雾效等，如 {"backgroundColor":"#1a2b3c"}'
          >
            <Input.TextArea rows={4} placeholder='{"backgroundColor":"#1a2b3c"}' />
          </Form.Item>
        </Form>
      </Drawer>
      {/* 发布确认弹窗（M3：发布说明冻结进快照） */}
      <Modal
        title={publishTarget ? `发布场景：${publishTarget.name}（v${publishTarget.version}）` : '发布场景'}
        open={!!publishTarget}
        onOk={() => void confirmPublish()}
        okText="发布"
        onCancel={() => setPublishTarget(null)}
        destroyOnHidden
      >
        <Input.TextArea
          rows={3}
          maxLength={256}
          placeholder="发布说明（可空），如：首次发布 / 调整锅炉绑定阈值"
          value={publishRemark}
          onChange={(event) => setPublishRemark(event.target.value)}
        />
        <p style={{ marginTop: 8, color: '#999', marginBottom: 0 }}>
          发布后场景禁止修改（需先停用）；当前实体/绑定/标注整包冻结为版本快照，可在「快照」中回滚。
        </p>
      </Modal>
      {/* 快照抽屉（M3） */}
      <Drawer
        title={snapshotScene ? `发布快照：${snapshotScene.name}` : '发布快照'}
        width={560}
        open={!!snapshotScene}
        onClose={() => setSnapshotScene(null)}
      >
        {snapshots.length === 0 && <div style={{ color: '#999' }}>暂无快照（发布一次即生成）</div>}
        {snapshots.map((snapshot) => (
          <div key={snapshot.id} className="twin-live-item" style={{ marginBottom: 8 }}>
            <Tag color="blue">v{snapshot.version}</Tag>
            <span className="twin-live-code">{snapshot.remark || '（无说明）'}</span>
            <span className="twin-live-value">{snapshot.publishTime ?? ''}</span>
            <Space size="small" style={{ float: 'right' }}>
              <Button size="small" type="link" onClick={() => void showSnapshotDetail(snapshot)}>
                查看
              </Button>
              <Popconfirm
                title={`回滚到 v${snapshot.version}？`}
                description="当前实体/绑定/标注将被快照内容整包替换"
                onConfirm={() => void rollback(snapshot)}
              >
                <Button
                  size="small"
                  type="link"
                  disabled={snapshotScene?.status === 'published'}
                >
                  回滚
                </Button>
              </Popconfirm>
            </Space>
          </div>
        ))}
        {snapshotScene?.status === 'published' && (
          <p style={{ color: '#faad14' }}>场景已发布，回滚前请先停用。</p>
        )}
      </Drawer>
      {/* 快照整包 JSON 预览 */}
      <Modal
        title={snapshotDetail ? `快照 v${snapshotDetail.version} 整包 JSON` : '快照详情'}
        open={!!snapshotDetail}
        footer={null}
        onCancel={() => setSnapshotDetail(null)}
        width={720}
      >
        <pre
          style={{
            maxHeight: 420,
            overflow: 'auto',
            background: '#0f1a24',
            color: '#9fd8cf',
            padding: 12,
            borderRadius: 6,
            fontSize: 12,
          }}
        >
          {snapshotDetail?.snapshotJson
            ? JSON.stringify(JSON.parse(snapshotDetail.snapshotJson), null, 2)
            : ''}
        </pre>
      </Modal>
      {/* 全局告警总览抽屉（6.08 M2）：跨场景未恢复列表，可就地确认或跳查看器 */}
      <Drawer
        title={
          <Space>
            <span>全局告警（未恢复）</span>
            {summary && <Tag color={summary.critical > 0 ? 'red' : 'orange'}>{summary.unresolved}</Tag>}
          </Space>
        }
        width={560}
        open={overviewOpen}
        onClose={() => setOverviewOpen(false)}
        extra={
          <Button
            loading={overviewLoading}
            onClick={() => {
              void loadOverview();
              void loadSummary(false);
            }}
          >
            刷新
          </Button>
        }
      >
        <Space size={16} style={{ marginBottom: 12 }} wrap>
          <span>
            未恢复 <strong>{summary?.unresolved ?? 0}</strong>
          </span>
          <span style={{ color: '#e74c3c' }}>严重 {summary?.critical ?? 0}</span>
          <span style={{ color: '#fa8c16' }}>预警 {summary?.warn ?? 0}</span>
          <span>未确认 {summary?.unacked ?? 0}</span>
        </Space>
        {overviewScene && (
          <div style={{ marginBottom: 8 }}>
            <Tag color="geekblue" closable onClose={() => setOverviewScene(null)}>
              仅看：{overviewScene.name}
            </Tag>
          </div>
        )}
        <Space style={{ marginBottom: 12 }} wrap>
          <Radio.Group
            size="small"
            value={overviewLevel}
            optionType="button"
            buttonStyle="solid"
            options={[
              { label: '全部', value: 'all' },
              { label: '严重', value: 'critical' },
              { label: '预警', value: 'warn' },
            ]}
            onChange={(event) => setOverviewLevel(event.target.value as 'all' | 'critical' | 'warn')}
          />
          <Checkbox
            checked={overviewUnackedOnly}
            onChange={(event) => setOverviewUnackedOnly(event.target.checked)}
          >
            仅未确认
          </Checkbox>
        </Space>
        <List
          size="small"
          loading={overviewLoading}
          dataSource={overviewView}
          locale={{ emptyText: '暂无未恢复告警' }}
          renderItem={(item) => {
            const meta = ALERT_LEVEL_META[item.level] ?? ALERT_LEVEL_META.normal;
            return (
              <List.Item
                actions={[
                  ...(item.acked
                    ? []
                    : [
                        <Button
                          key="ack"
                          size="small"
                          type="link"
                          onClick={() => void ackInOverview(item)}
                        >
                          确认
                        </Button>,
                      ]),
                  ...(item.sceneId
                    ? [
                        <Button
                          key="open"
                          size="small"
                          type="link"
                          onClick={() => {
                            const target = item.sceneId;
                            if (target) {
                              setOverviewOpen(false);
                              onOpenViewer(target);
                            }
                          }}
                        >
                          查看
                        </Button>,
                      ]
                    : []),
                ]}
              >
                <div style={{ width: '100%' }}>
                  <Space wrap size={4}>
                    <Tag color={meta.color}>{meta.label}</Tag>
                    {item.acked && <Tag color="blue">已确认</Tag>}
                    <Tag color="geekblue">{item.sceneName ?? item.sceneCode ?? '未知场景'}</Tag>
                    <span style={{ fontWeight: 600 }}>
                      {item.entityCode ?? item.entityId ?? '-'}
                    </span>
                    <span>.{item.stateKey}</span>
                    <span>= {item.value ?? '-'}</span>
                    {item.thresholds?.length ? (
                      <span className="twin-live-off">阈值 [{item.thresholds.join(', ')}]</span>
                    ) : null}
                  </Space>
                  <div className="twin-live-off">
                    {item.createTime ?? '-'}
                    {item.acked
                      ? ` · 确认于 ${item.ackTime ?? '-'}${item.ackBy ? `（用户 ${item.ackBy}）` : ''}`
                      : ' · 待确认'}
                  </div>
                </div>
              </List.Item>
            );
          }}
        />
        {overviewRows.length >= OVERVIEW_LIMIT && (
          <p style={{ color: '#faad14', marginTop: 12, marginBottom: 0 }}>
            仅展示最近 {OVERVIEW_LIMIT} 条未恢复告警，剩余请进对应场景查看。
          </p>
        )}
        {overviewSceneMissing > 0 && (
          <p style={{ color: '#faad14', marginTop: 12, marginBottom: 0 }}>
            该场景还有 {overviewSceneMissing} 条未恢复告警不在本次加载范围内，请在查看器告警中心查看。
          </p>
        )}
      </Drawer>

      {/* 6.10 M2：场景级通知设置（通道开关 / 门槛 / 接收人） */}
      <Drawer
        title={notifyScene ? `通知设置 · ${notifyScene.name}` : '通知设置'}
        width={460}
        open={!!notifyScene}
        onClose={() => setNotifyScene(null)}
        extra={
          <Space>
            <Button onClick={openDispatches}>投递记录</Button>
            <Button type="primary" loading={notifySaving} onClick={() => void saveNotify()}>
              保存
            </Button>
          </Space>
        }
      >
        {notifyLoading || !notifySettings ? (
          <Spin />
        ) : (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <div className="twin-live-off">
              全局口径：Webhook {notifySettings.globalWebhookReady ? '已配置' : '未配置'}｜站内信{' '}
              {notifySettings.globalInappEnabled ? '已开启' : '未开启'}｜最低级别{' '}
              {notifySettings.globalMinLevel === 'critical' ? '仅严重' : '预警及以上'}｜恢复通知{' '}
              {notifySettings.globalRecoveryEnabled ? '已开启' : '已关闭'}｜静默{' '}
              {notifySettings.globalQuietStart && notifySettings.globalQuietEnd
                ? `${notifySettings.globalQuietStart}–${notifySettings.globalQuietEnd}`
                : '未启用'}
              <br />
              场景只能收紧（关通道 / 抬高门槛 / 加静默），全局未开启时这里设了什么都不会外发。
            </div>
            <div>
              <div style={{ marginBottom: 8 }}>本场景关闭 Webhook</div>
              <Space>
                <Switch checked={webhookOff} onChange={setWebhookOff} />
                <Tag color={notifySettings.effectiveWebhookEnabled ? 'green' : 'default'}>
                  {notifySettings.effectiveWebhookEnabled ? '当前会外发' : '当前不外发'}
                </Tag>
              </Space>
            </div>
            <div>
              <div style={{ marginBottom: 8 }}>本场景关闭站内信</div>
              <Space>
                <Switch checked={inappOff} onChange={setInappOff} />
                <Tag color={notifySettings.effectiveInappEnabled ? 'green' : 'default'}>
                  {notifySettings.effectiveInappEnabled ? '当前会外发' : '当前不外发'}
                </Tag>
              </Space>
            </div>
            <div>
              <div style={{ marginBottom: 8 }}>最低外发级别</div>
              <Radio.Group
                value={minLevelOverride}
                onChange={(event) => setMinLevelOverride(event.target.value)}
              >
                <Radio value="follow">跟随全局</Radio>
                <Radio value="critical">仅严重</Radio>
              </Radio.Group>
              <div className="twin-live-off" style={{ marginTop: 4 }}>
                实际生效：{notifySettings.effectiveMinLevel === 'critical' ? '仅严重' : '预警及以上'}
              </div>
            </div>
            <div>
              <div style={{ marginBottom: 8 }}>接收人</div>
              <TreeSelect
                style={{ width: '100%', marginBottom: 8 }}
                placeholder="先选部门缩小范围（可空 = 全公司）"
                value={recipientDeptId}
                onChange={changeRecipientDept}
                treeData={toDeptTreeData(deptTree)}
                loading={deptLoading}
                allowClear
                showSearch
                treeNodeFilterProp="title"
                treeDefaultExpandAll={false}
              />
              <Select
                mode="multiple"
                style={{ width: '100%' }}
                placeholder="搜索姓名 / 账号，留空则发给场景创建人"
                value={recipientIds}
                loading={recipientLoading}
                showSearch
                filterOption={false}
                onSearch={searchRecipients}
                onChange={(value: string[]) => setRecipientIds(value)}
                options={recipientOptions.map((item) => ({
                  value: item.id,
                  label: item.deptName ? `${item.name}（${item.deptName}）` : item.name,
                }))}
              />
              <div className="twin-live-off" style={{ marginTop: 4 }}>
                {notifySettings.fallbackToCreator
                  ? '当前：回退场景创建人'
                  : `当前：${notifySettings.userIds.length} 人`}
                {recipientTotal > 0
                  ? ` · 匹配 ${recipientTotal} 人，列表仅取前 20 条（继续输入关键字收窄）`
                  : ''}
              </div>
            </div>
            <div>
              <div style={{ marginBottom: 8 }}>本场景关闭恢复通知</div>
              <Space>
                <Switch checked={recoveryOff} onChange={setRecoveryOff} />
                <Tag color={notifySettings.effectiveRecoveryEnabled ? 'green' : 'default'}>
                  {notifySettings.effectiveRecoveryEnabled ? '恢复会通知' : '恢复不通知'}
                </Tag>
              </Space>
              <div className="twin-live-off" style={{ marginTop: 4 }}>
                只有当初真的通知过的告警才回「已恢复」，不会凭空打扰。
              </div>
            </div>
            <div>
              <div style={{ marginBottom: 8 }}>本场景静默时段（叠加在全局之上）</div>
              <Space>
                <Input
                  value={quietStart}
                  onChange={(event) => setQuietStart(event.target.value)}
                  placeholder="22:00"
                  style={{ width: 110 }}
                  allowClear
                />
                <span>至</span>
                <Input
                  value={quietEnd}
                  onChange={(event) => setQuietEnd(event.target.value)}
                  placeholder="08:00"
                  style={{ width: 110 }}
                  allowClear
                />
                {notifySettings.quietNow && <Tag color="blue">此刻静默中</Tag>}
              </Space>
              <div className="twin-live-off" style={{ marginTop: 4 }}>
                可跨零点；静默期只拦预警，
                {notifySettings.quietCriticalBypass ? '严重告警照常外发。' : '严重告警也会被拦（全局已关穿透）。'}
              </div>
            </div>
          </Space>
        )}
      </Drawer>

      {/* 6.10 M2：投递记录（twin_alert_dispatch 场景维度） */}
      <Modal
        title={notifyScene ? `投递记录 · ${notifyScene.name}` : '投递记录'}
        open={dispatchOpen}
        width={920}
        footer={null}
        onCancel={() => setDispatchOpen(false)}
      >
        <Space style={{ marginBottom: 12 }}>
          <Select<'all' | AlertDispatchStatus>
            value={dispatchStatus}
            style={{ width: 160 }}
            onChange={(value) => {
              setDispatchStatus(value);
              void loadDispatches(value);
            }}
            options={[
              { value: 'all', label: '全部结果' },
              { value: 'SUCCESS', label: '成功' },
              { value: 'FAILED', label: '失败' },
              { value: 'SKIPPED', label: '跳过' },
            ]}
          />
          <Button onClick={() => void loadDispatches(dispatchStatus)}>刷新</Button>
          <span className="twin-live-off">最多展示最近 100 条</span>
        </Space>
        <Table<TwinAlertDispatchVO>
          rowKey="id"
          size="small"
          loading={dispatchLoading}
          columns={dispatchColumns}
          dataSource={dispatchRows}
          pagination={{ pageSize: 10, showSizeChanger: false }}
        />
      </Modal>
    </div>
  );
}
