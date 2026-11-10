// 场景管理页：场景 CRUD（草稿→发布⇄停用）+ 进入查看器入口
//
// 实体编辑在 3D 查看器页完成（树形面板），本页只管场景元数据与生命周期。

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Space,
  Table,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { sceneApi, type SceneSaveRequest, type SceneVO } from '../api/twin';

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
      } else {
        await sceneApi.publish(scene.id);
        message.success('场景已发布');
      }
      void load();
    } catch (error) {
      message.error(`操作失败：${String(error)}`);
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
      width: 260,
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
    </div>
  );
}
