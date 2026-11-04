// 零件库：左侧物料分类树 + 右侧零件主档表格
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tree,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  deleteCategory,
  deletePart,
  getCategoryTree,
  listParts,
  listWhereUsed,
  saveCategory,
  type BomStatus,
  type PartCategory,
  type PartType,
  type PdmPart,
  type PartWhereUsed,
  type SourceType,
} from '../../api/part';
import { hasPermission } from '../../utils/permission';
import PartForm from './PartForm';

/** 零件类型文案 */
const PART_TYPE_LABEL: Record<PartType, string> = {
  PRODUCT: '成品',
  ASSEMBLY: '部件',
  PART: '零件',
  RAW_MATERIAL: '原材料',
};

/** 来源类型文案与颜色 */
const SOURCE_LABEL: Record<SourceType, { text: string; color: string }> = {
  STANDARD: { text: '标准件', color: 'blue' },
  COMMON: { text: '通用件', color: 'green' },
  UNIQUE: { text: '唯一件', color: 'default' },
};

/** BOM 状态颜色 */
const BOM_STATUS_COLOR: Record<BomStatus, string> = {
  DRAFT: 'default',
  RELEASED: 'green',
  OBSOLETE: 'red',
};

interface Filters {
  code?: string;
  name?: string;
  partType?: PartType;
  sourceType?: SourceType;
  status?: 'ENABLED' | 'DISABLED';
}

/**
 * 将分类树转为 antd Tree 数据。
 *
 * @param nodes 分类树
 * @returns Tree 节点
 */
function toTreeData(nodes: PartCategory[]): DataNode[] {
  return nodes.map((node) => ({
    key: node.id,
    title: `${node.name}（${node.code}）`,
    children: toTreeData(node.children ?? []),
  }));
}

/**
 * 在分类树中按 ID 查找节点。
 *
 * @param nodes 分类树
 * @param id 分类ID
 * @returns 分类节点或 null
 */
function findCategory(nodes: PartCategory[], id: string): PartCategory | null {
  for (const node of nodes) {
    if (node.id === id) {
      return node;
    }
    const found = findCategory(node.children ?? [], id);
    if (found) {
      return found;
    }
  }
  return null;
}

export default function PartLibrary() {
  const [filters, setFilters] = useState<Filters>({});
  const [categoryCode, setCategoryCode] = useState<string>();
  const [rows, setRows] = useState<PdmPart[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const [categories, setCategories] = useState<PartCategory[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<PartCategory | null>(null);
  const [categoryModalOpen, setCategoryModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<PartCategory | null>(null);
  const [categoryParentId, setCategoryParentId] = useState<string>('0');
  const [categoryForm] = Form.useForm<{ code: string; name: string; sortNo?: number; remark?: string }>();

  const [formOpen, setFormOpen] = useState(false);
  const [editingPart, setEditingPart] = useState<PdmPart | null>(null);
  const [whereUsedOpen, setWhereUsedOpen] = useState(false);
  const [whereUsedRows, setWhereUsedRows] = useState<PartWhereUsed[]>([]);
  const [whereUsedPart, setWhereUsedPart] = useState<PdmPart | null>(null);

  const canAdd = hasPermission('sys:pdm:part:add');
  const canEdit = hasPermission('sys:pdm:part:edit');
  const canDelete = hasPermission('sys:pdm:part:delete');
  const canManageCategory = hasPermission('sys:pdm:part:category:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listParts({ current, size, categoryCode, ...filters });
      setRows(page.records);
      setTotal(page.total);
    } finally {
      setLoading(false);
    }
  }, [current, size, categoryCode, filters]);

  const loadCategories = useCallback(async () => {
    try {
      setCategories(await getCategoryTree());
    } catch {
      setCategories([]);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  useEffect(() => {
    void loadCategories();
  }, [loadCategories, refreshKey]);

  const treeData = useMemo(() => toTreeData(categories), [categories]);

  const columns: TableProps<PdmPart>['columns'] = [
    { title: '零件编码', dataIndex: 'code', key: 'code', width: 140 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '类型',
      dataIndex: 'partType',
      key: 'partType',
      width: 100,
      render: (value: PartType) => <Tag color="geekblue">{PART_TYPE_LABEL[value] ?? value}</Tag>,
    },
    {
      title: '来源',
      dataIndex: 'sourceType',
      key: 'sourceType',
      width: 100,
      render: (value: SourceType) => {
        const config = SOURCE_LABEL[value];
        return config ? <Tag color={config.color}>{config.text}</Tag> : value;
      },
    },
    { title: '材质', dataIndex: 'material', key: 'material', width: 100 },
    { title: '规格', dataIndex: 'spec', key: 'spec', width: 120 },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (value: PdmPart['status']) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size={0}>
          {canEdit ? (
            <Button
              type="link"
              onClick={() => {
                setEditingPart(record);
                setFormOpen(true);
              }}
            >
              编辑
            </Button>
          ) : null}
          <Button
            type="link"
            onClick={() => {
              setWhereUsedPart(record);
              void listWhereUsed(record.id).then((list) => {
                setWhereUsedRows(list);
                setWhereUsedOpen(true);
              });
            }}
          >
            使用处
          </Button>
          {canDelete ? (
            <Popconfirm
              title="确认删除该零件？被 BOM 引用时将被拒绝。"
              onConfirm={async () => {
                await deletePart(record.id);
                message.success('删除成功');
                setRefreshKey((key) => key + 1);
              }}
            >
              <Button type="link" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const submitCategory = async () => {
    const values = await categoryForm.validateFields();
    await saveCategory(
      { ...values, parentId: editingCategory?.parentId ?? categoryParentId },
      editingCategory?.id,
    );
    message.success('保存成功');
    setCategoryModalOpen(false);
    setRefreshKey((key) => key + 1);
  };

  return (
    <Row gutter={16}>
      <Col flex="280px">
        <Card
          size="small"
          title="物料分类"
          extra={
            canManageCategory ? (
              <Space size={4}>
                <Button
                  size="small"
                  onClick={() => {
                    setEditingCategory(null);
                    setCategoryParentId('0');
                    categoryForm.resetFields();
                    setCategoryModalOpen(true);
                  }}
                >
                  新增根分类
                </Button>
              </Space>
            ) : null
          }
        >
          <Tree
            treeData={treeData}
            defaultExpandAll
            selectedKeys={selectedCategory ? [selectedCategory.id] : []}
            onSelect={(keys) => {
              const selectedId = keys[0] ? String(keys[0]) : null;
              const selected = selectedId ? findCategory(categories, selectedId) : null;
              setSelectedCategory(selected);
              setCurrent(1);
              setCategoryCode(selected?.code);
            }}
          />
          {canManageCategory && selectedCategory ? (
            <Space style={{ marginTop: 8 }}>
              <Button
                size="small"
                onClick={() => {
                  setEditingCategory(selectedCategory);
                  setCategoryParentId(selectedCategory.parentId);
                  categoryForm.setFieldsValue({
                    code: selectedCategory.code,
                    name: selectedCategory.name,
                    sortNo: selectedCategory.sortNo,
                    remark: selectedCategory.remark,
                  });
                  setCategoryModalOpen(true);
                }}
              >
                编辑分类
              </Button>
              <Popconfirm
                title="确认删除该分类？存在子分类或被零件引用时将被拒绝。"
                onConfirm={async () => {
                  await deleteCategory(selectedCategory.id);
                  message.success('删除成功');
                  setSelectedCategory(null);
                  setCategoryCode(undefined);
                  setRefreshKey((key) => key + 1);
                }}
              >
                <Button size="small" danger>
                  删除分类
                </Button>
              </Popconfirm>
            </Space>
          ) : null}
          {canManageCategory && categories.length > 0 ? (
            <Space style={{ marginTop: 12 }} wrap>
              {categories.map((root) => (
                <Button
                  key={root.id}
                  size="small"
                  onClick={() => {
                    setEditingCategory(null);
                    setCategoryParentId(root.id);
                    categoryForm.resetFields();
                    setCategoryModalOpen(true);
                  }}
                >
                  + {root.name}
                </Button>
              ))}
            </Space>
          ) : null}
          {categoryCode ? (
            <Button size="small" type="link" onClick={() => setCategoryCode(undefined)}>
              清除分类筛选
            </Button>
          ) : null}
        </Card>
      </Col>
      <Col flex="auto">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Space wrap>
            <Input
              allowClear
              placeholder="零件编码"
              style={{ width: 160 }}
              onChange={(event) => setFilters((prev) => ({ ...prev, code: event.target.value }))}
            />
            <Input
              allowClear
              placeholder="零件名称"
              style={{ width: 160 }}
              onChange={(event) => setFilters((prev) => ({ ...prev, name: event.target.value }))}
            />
            <Select
              allowClear
              placeholder="类型"
              style={{ width: 120 }}
              options={Object.entries(PART_TYPE_LABEL).map(([value, label]) => ({ value, label }))}
              onChange={(value) => setFilters((prev) => ({ ...prev, partType: value as PartType }))}
            />
            <Select
              allowClear
              placeholder="来源"
              style={{ width: 120 }}
              options={Object.entries(SOURCE_LABEL).map(([value, config]) => ({
                value,
                label: config.text,
              }))}
              onChange={(value) => setFilters((prev) => ({ ...prev, sourceType: value as SourceType }))}
            />
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 100 }}
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '停用' },
              ]}
              onChange={(value) => setFilters((prev) => ({ ...prev, status: value }))}
            />
            <Button type="primary" onClick={() => setCurrent(1)}>
              查询
            </Button>
            {canAdd ? (
              <Button
                type="primary"
                onClick={() => {
                  setEditingPart(null);
                  setFormOpen(true);
                }}
              >
                新增零件
              </Button>
            ) : null}
          </Space>
          <Table<PdmPart>
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={rows}
            pagination={{
              current,
              pageSize: size,
              total,
              showSizeChanger: true,
              onChange: (page, pageSize) => {
                setCurrent(page);
                setSize(pageSize);
              },
            }}
          />
        </Space>
      </Col>

      <PartForm
        open={formOpen}
        initial={editingPart}
        onCancel={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false);
          setRefreshKey((key) => key + 1);
        }}
      />

      <Modal
        open={whereUsedOpen}
        title={whereUsedPart ? `使用处：${whereUsedPart.code}` : '使用处'}
        footer={null}
        onCancel={() => setWhereUsedOpen(false)}
        width={720}
      >
        <Table<PartWhereUsed>
          rowKey={(record) => `${record.bomId}-${record.parentPartNo ?? ''}`}
          dataSource={whereUsedRows}
          pagination={false}
          columns={[
            { title: 'BOM 编号', dataIndex: 'bomNo' },
            { title: '版本', dataIndex: 'versionNo', width: 90 },
            { title: '视图', dataIndex: 'view', width: 90 },
            { title: '产品编码', dataIndex: 'productCode' },
            { title: '父件', dataIndex: 'parentPartNo' },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (value: BomStatus) => <Tag color={BOM_STATUS_COLOR[value]}>{value}</Tag>,
            },
          ]}
        />
      </Modal>

      <Modal
        open={categoryModalOpen}
        title={editingCategory ? '编辑物料分类' : '新增物料分类'}
        onOk={submitCategory}
        onCancel={() => setCategoryModalOpen(false)}
        destroyOnClose
      >
        <Form form={categoryForm} layout="vertical">
          <Form.Item name="code" label="分类编码" rules={[{ required: true, message: '请输入分类编码' }]}>
            <Input placeholder="如 BOLT" />
          </Form.Item>
          <Form.Item name="name" label="分类名称" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input placeholder="如 螺栓" />
          </Form.Item>
          <Form.Item name="sortNo" label="排序号">
            <Input type="number" placeholder="数字越小越靠前" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="选填" />
          </Form.Item>
        </Form>
        <Descriptions size="small" column={1}>
          <Descriptions.Item label="上级分类">
            {categoryParentId === '0' ? '根分类' : categoryParentId}
          </Descriptions.Item>
        </Descriptions>
      </Modal>
    </Row>
  );
}
