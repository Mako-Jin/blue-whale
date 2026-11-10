// 文档模板管理页（M3）：列表、创建、编辑、删除与从模板建页

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  TreeSelect,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined, FileAddOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { hasPermission } from '../api/client';
import { pageApi } from '../api/page';
import { spaceApi } from '../api/space';
import { templateApi, type TemplateSavePayload } from '../api/template';
import type { DocJson, DocPageTree, DocSpace, DocTemplate } from '../api/types';

const { Text } = Typography;

/** 模板表单值 */
interface TemplateFormValues {
  spaceId: number;
  name: string;
  description?: string;
  icon?: string;
  content: string;
}

/** 应用模板表单值 */
interface ApplyFormValues {
  spaceId: number;
  parentId: number;
  title?: string;
}

/** 树选择节点 */
interface ParentOption {
  title: string;
  value: number;
  children?: ParentOption[];
}

/** 纯文本转 TipTap 段落 JSON */
function textToDoc(text: string): DocJson {
  const lines = text.split('\n');
  return {
    type: 'doc',
    content: lines.map((line) => ({
      type: 'paragraph',
      content: line ? [{ type: 'text', text: line }] : [],
    })),
  };
}

/** 从 TipTap JSON 粗略提取纯文本（用于回填编辑框） */
function docToText(contentJson?: string | null): string {
  if (!contentJson) {
    return '';
  }
  try {
    const doc = JSON.parse(contentJson) as { content?: Array<Record<string, unknown>> };
    const lines: string[] = [];
    for (const node of doc.content ?? []) {
      const inner = node.content as Array<{ text?: string }> | undefined;
      lines.push((inner ?? []).map((item) => item.text ?? '').join(''));
    }
    return lines.join('\n');
  } catch {
    return '';
  }
}

/** 页面树转父节点选项 */
function toParentOptions(nodes: DocPageTree[]): ParentOption[] {
  return nodes.map((node) => ({
    title: `${node.pageType === 'FOLDER' ? '📁' : '📄'} ${node.title}`,
    value: node.id,
    children: toParentOptions(node.children ?? []),
  }));
}

export default function TemplatesPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<TemplateFormValues>();
  const [applyForm] = Form.useForm<ApplyFormValues>();
  const [templates, setTemplates] = useState<DocTemplate[]>([]);
  const [spaces, setSpaces] = useState<DocSpace[]>([]);
  const [filterSpace, setFilterSpace] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<DocTemplate | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [applying, setApplying] = useState<DocTemplate | null>(null);
  const [applyTree, setApplyTree] = useState<DocPageTree[]>([]);

  const canManage = hasPermission('sys:doc:template:manage');
  const canCreatePage = hasPermission('sys:doc:page:add');

  const spaceOptions = useMemo(
    () =>
      spaces.map((space) => ({ value: space.id, label: `${space.icon || '📄'} ${space.name}` })),
    [spaces],
  );

  const load = useCallback(
    async (spaceId?: number) => {
      setLoading(true);
      try {
        setTemplates(await templateApi.list(spaceId));
      } catch (error) {
        message.error((error as Error).message);
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    void (async () => {
      try {
        setSpaces(await spaceApi.my());
      } catch {
        setSpaces([]);
      }
      await load();
    })();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ spaceId: 0, content: '' });
    setEditorOpen(true);
  };

  const openEdit = (template: DocTemplate) => {
    setEditing(template);
    form.setFieldsValue({
      spaceId: template.spaceId,
      name: template.name,
      description: template.description ?? undefined,
      icon: template.icon ?? undefined,
      content: docToText(template.contentJson),
    });
    setEditorOpen(true);
  };

  const submitTemplate = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const payload: TemplateSavePayload = {
        spaceId: values.spaceId ?? 0,
        name: values.name.trim(),
        description: values.description,
        icon: values.icon,
        pageType: 'PAGE',
        contentJson: textToDoc(values.content ?? ''),
      };
      if (editing) {
        await templateApi.update(editing.id, payload);
        message.success('模板已更新');
      } else {
        await templateApi.create(payload);
        message.success('模板已创建');
      }
      setEditorOpen(false);
      await load(filterSpace);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (template: DocTemplate) => {
    try {
      await templateApi.remove(template.id);
      message.success('模板已删除');
      await load(filterSpace);
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const openApply = async (template: DocTemplate) => {
    setApplying(template);
    applyForm.resetFields();
    const defaultSpace = template.spaceId || spaces[0]?.id;
    applyForm.setFieldsValue({ spaceId: defaultSpace, parentId: 0, title: template.name });
    if (defaultSpace) {
      await loadTree(defaultSpace);
    }
  };

  const loadTree = async (spaceId: number) => {
    try {
      setApplyTree(await pageApi.tree(spaceId));
    } catch {
      setApplyTree([]);
    }
  };

  const submitApply = async () => {
    if (!applying) {
      return;
    }
    const values = await applyForm.validateFields();
    setSaving(true);
    try {
      const created = await templateApi.apply(applying.id, {
        spaceId: values.spaceId,
        parentId: values.parentId ?? 0,
        title: values.title,
      });
      message.success('页面已创建');
      setApplying(null);
      navigate(`/space/${created.spaceId}?pageId=${created.id}`);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      title="文档模板"
      extra={
        <Space>
          <Select
            allowClear
            placeholder="按空间筛选"
            value={filterSpace}
            options={spaceOptions}
            onChange={(value) => {
              setFilterSpace(value);
              void load(value);
            }}
            style={{ width: 220 }}
          />
          {canManage && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建模板
            </Button>
          )}
        </Space>
      }
    >
      <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
        模板用于快速创建结构一致的页面；租户级模板对所有空间可见。
      </Text>
      <Table<DocTemplate>
        rowKey="id"
        loading={loading}
        dataSource={templates}
        locale={{ emptyText: <Empty description="暂无模板" /> }}
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        columns={[
          {
            title: '名称',
            dataIndex: 'name',
            render: (value: string, record) => (
              <Space size={6}>
                <Text>{record.icon || '📄'}</Text>
                <Text strong>{value}</Text>
              </Space>
            ),
          },
          {
            title: '范围',
            dataIndex: 'spaceId',
            width: 180,
            render: (value: number) =>
              value ? (
                <Tag color="blue">
                  {spaces.find((item) => item.id === value)?.name ?? `空间 ${value}`}
                </Tag>
              ) : (
                <Tag>租户级</Tag>
              ),
          },
          {
            title: '说明',
            dataIndex: 'description',
            ellipsis: true,
            render: (value?: string | null) => value || '—',
          },
          {
            title: '更新时间',
            dataIndex: 'updateTime',
            width: 180,
            render: (value?: string | null) => value || '—',
          },
          {
            title: '操作',
            width: 200,
            render: (_, record) => (
              <Space size={4}>
                {canCreatePage && (
                  <Button
                    type="link"
                    icon={<FileAddOutlined />}
                    onClick={() => void openApply(record)}
                  >
                    使用
                  </Button>
                )}
                {canManage && (
                  <Button type="link" icon={<EditOutlined />} onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                )}
                {canManage && (
                  <Popconfirm
                    title="删除该模板？"
                    okText="删除"
                    onConfirm={() => void remove(record)}
                  >
                    <Button type="link" danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? '编辑模板' : '新建模板'}
        open={editorOpen}
        confirmLoading={saving}
        onOk={() => void submitTemplate()}
        onCancel={() => setEditorOpen(false)}
        destroyOnHidden
        width={640}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="spaceId" label="归属空间" initialValue={0}>
            <Select options={[{ value: 0, label: '租户级（所有空间可用）' }, ...spaceOptions]} />
          </Form.Item>
          <Form.Item
            name="name"
            label="模板名称"
            rules={[
              { required: true, message: '请输入模板名称' },
              { max: 100, message: '最多 100 字' },
            ]}
          >
            <Input placeholder="例如 需求评审记录" />
          </Form.Item>
          <Form.Item name="description" label="说明" rules={[{ max: 500, message: '最多 500 字' }]}>
            <Input placeholder="模板用途（可选）" />
          </Form.Item>
          <Form.Item name="icon" label="图标" rules={[{ max: 64, message: '最多 64 字' }]}>
            <Input placeholder="例如 📝（可选）" />
          </Form.Item>
          <Form.Item name="content" label="初始正文（逐行转为段落）">
            <Input.TextArea autoSize={{ minRows: 6, maxRows: 14 }} placeholder="模板初始内容" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`使用模板「${applying?.name ?? ''}」`}
        open={Boolean(applying)}
        confirmLoading={saving}
        onOk={() => void submitApply()}
        onCancel={() => setApplying(null)}
        destroyOnHidden
      >
        <Form form={applyForm} layout="vertical" preserve={false}>
          <Form.Item
            name="spaceId"
            label="目标空间"
            rules={[{ required: true, message: '请选择目标空间' }]}
          >
            <Select
              options={spaceOptions}
              placeholder="选择空间"
              onChange={(value: number) => {
                applyForm.setFieldValue('parentId', 0);
                void loadTree(value);
              }}
            />
          </Form.Item>
          <Form.Item name="parentId" label="父节点" initialValue={0}>
            <TreeSelect
              treeData={[{ title: '📚 根目录', value: 0, children: toParentOptions(applyTree) }]}
              treeDefaultExpandAll
              placeholder="默认根目录"
              allowClear
            />
          </Form.Item>
          <Form.Item name="title" label="页面标题" rules={[{ max: 200, message: '最多 200 字' }]}>
            <Input placeholder="默认使用模板名称" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
