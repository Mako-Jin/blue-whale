// 场景管理页：场景 CRUD（草稿→发布⇄停用）+ 发布快照（M3）+ 进入查看器入口
//
// 发布需填发布说明（可空），发布时后端把场景整包冻结进 twin_snapshot；
// 快照抽屉支持查看历史版本与回滚（场景须为草稿/停用态）。
// 实体编辑在 3D 查看器页完成（树形面板），本页只管场景元数据与生命周期。

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  sceneApi,
  snapshotApi,
  type SceneSaveRequest,
  type SceneVO,
  type SnapshotVO,
} from '../api/twin';

/** 状态 → 标签（颜色对齐 IoT 产品页口径） */
const STATUS_TAG: Record<SceneVO['status'], { label: string; color: string }> = {
  draft: { label: '草稿', color: 'default' },
  published: { label: '已发布', color: 'success' },
  disabled: { label: '已停用', color: 'warning' },
};

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
    { title: '更新时间', dataIndex: 'updateTime', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 320,
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
          <Popconfirm title="确认删除该场景？" onConfirm={() => void remove(scene)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
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
    </div>
  );
}
