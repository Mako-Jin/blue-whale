// 商城类目：树形列表、按上级新增、编辑、删除
//
// 删除与改上级都由后端判定（有子类目 / 有商品即拒绝），前端不做"猜测式"禁用，
// 避免出现"按钮能点但后端必拒"的两套真相。
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  createCategory,
  fetchCategoryTree,
  removeCategory,
  updateCategory,
  type CategoryNode,
  type CategorySavePayload,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  ENABLE_STATUS_COLOR,
  ENABLE_STATUS_LABEL,
  ENABLE_STATUS_OPTIONS,
} from '../../utils/dict';

/** 上级类目下拉选项（含层级缩进，根级为「无（顶级类目）」） */
interface ParentOption {
  value: number;
  label: string;
}

/**
 * 读取错误文案（后端 Result.message 已可直接给用户看）。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

/**
 * 拍平类目树为上级下拉选项。
 *
 * @param nodes 类目树
 * @param excludeId 需要排除的节点（编辑时不能把自己或自己的后代挂到自己下面）
 * @param depth 当前深度
 * @returns 选项列表
 */
function flattenOptions(
  nodes: CategoryNode[],
  excludeId?: number,
  depth = 0,
): ParentOption[] {
  const options: ParentOption[] = [];
  nodes.forEach((node) => {
    if (node.id === excludeId) {
      return;
    }
    options.push({ value: node.id, label: `${'　'.repeat(depth)}${node.categoryName}` });
    if (node.children && node.children.length > 0) {
      options.push(...flattenOptions(node.children, excludeId, depth + 1));
    }
  });
  return options;
}

export default function CategoryPage() {
  const [tree, setTree] = useState<CategoryNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<CategoryNode | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<CategorySavePayload>();

  const canManage = hasPermission('sys:mall:category:manage');

  /** 拉取类目树 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchCategoryTree({
        keyword: keyword.trim() || undefined,
        status,
      });
      setTree(data);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 编辑时可选上级：排除自己（后端还会校验子孙与层级，前端只做第一层防呆） */
  const parentOptions = useMemo(
    () => [{ value: 0, label: '无（顶级类目）' }, ...flattenOptions(tree, editing?.id)],
    [tree, editing],
  );

  /** 打开新增弹窗：parentId 为空表示顶级 */
  const openCreate = (parentId?: number) => {
    setEditing(null);
    form.setFieldsValue({
      parentId: parentId ?? 0,
      categoryName: '',
      sortNo: 0,
      status: 'ACTIVE',
      remark: '',
    });
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (node: CategoryNode) => {
    setEditing(node);
    form.setFieldsValue({
      parentId: node.parentId,
      categoryName: node.categoryName,
      sortNo: node.sortNo,
      status: node.status,
      remark: '',
    });
    setModalOpen(true);
  };

  /** 提交表单 */
  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload: CategorySavePayload = {
        ...values,
        parentId: values.parentId ?? 0,
      };
      if (editing) {
        await updateCategory(editing.id, payload);
        message.success('类目已更新');
      } else {
        await createCategory(payload);
        message.success('类目已创建');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除类目 */
  const handleRemove = async (node: CategoryNode) => {
    try {
      await removeCategory(node.id);
      message.success('类目已删除');
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<CategoryNode>['columns'] = [
    {
      title: '类目名称',
      dataIndex: 'categoryName',
      render: (value: string) => <Typography.Text strong>{value}</Typography.Text>,
    },
    {
      title: '层级',
      dataIndex: 'categoryLevel',
      width: 90,
      render: (value: number) => `L${value}`,
    },
    { title: '排序', dataIndex: 'sortNo', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={ENABLE_STATUS_COLOR[value]}>{ENABLE_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      width: 230,
      render: (_, node) => (
        <Space size="small">
          {canManage && (
            <Button type="link" size="small" onClick={() => openCreate(node.id)}>
              新增子级
            </Button>
          )}
          {canManage && (
            <Button type="link" size="small" onClick={() => openEdit(node)}>
              编辑
            </Button>
          )}
          {canManage && (
            <Popconfirm
              title="删除类目"
              description="有子类目或仍在用的商品时会被拒绝"
              onConfirm={() => handleRemove(node)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="商城类目"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="类目名称"
            style={{ width: 200 }}
            onSearch={(value) => setKeyword(value)}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            options={ENABLE_STATUS_OPTIONS}
            value={status}
            onChange={(value) => setStatus(value)}
          />
          {canManage && (
            <Button type="primary" onClick={() => openCreate()}>
              新建顶级类目
            </Button>
          )}
        </Space>
      }
    >
      <Table<CategoryNode>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={tree}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
      />

      <Modal
        title={editing ? '编辑类目' : '新建类目'}
        open={modalOpen}
        confirmLoading={submitting}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item label="上级类目" name="parentId">
            <Select options={parentOptions} />
          </Form.Item>
          <Form.Item
            label="类目名称"
            name="categoryName"
            rules={[{ required: true, message: '请填写类目名称' }]}
          >
            <Input maxLength={64} placeholder="如：数码 / 耳机" />
          </Form.Item>
          <Form.Item label="排序" name="sortNo">
            <InputNumber min={0} max={9999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Select options={ENABLE_STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
