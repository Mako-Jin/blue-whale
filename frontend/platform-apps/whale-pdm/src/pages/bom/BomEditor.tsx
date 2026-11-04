// BOM 结构编辑器：树表展示、行增改删、发布确认、快照记录
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  addBomLine,
  deleteBomLine,
  getBom,
  listBomReleases,
  listParts,
  releaseBom,
  updateBomLine,
  type BomLine,
  type BomRelease,
  type BomStatus,
  type BomTreeNode,
  type PdmBom,
  type PdmPart,
} from '../../api/part';
import { hasPermission } from '../../utils/permission';

/** BOM 状态文案与颜色 */
const STATUS_CONFIG: Record<BomStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'blue' },
  RELEASED: { label: '已发布', color: 'green' },
  OBSOLETE: { label: '已失效', color: 'default' },
};

/** 可展开子件的父件类型 */
const ASSEMBLY_TYPES = new Set(['PRODUCT', 'ASSEMBLY']);

/** 树表行（children 为空时置空，避免叶子节点出现展开图标） */
interface BomRow extends Omit<BomTreeNode, 'children'> {
  children?: BomRow[];
}

/** 行表单值 */
interface LineFormValues {
  childPartId: string;
  lineNo?: number;
  qty: number;
  uom?: string;
  lossRate?: number;
  remark?: string;
}

interface BomEditorProps {
  /** 抽屉可见性 */
  open: boolean;
  /** BOM ID */
  bomId: string;
  /** 关闭回调 */
  onClose: () => void;
  /** 结构或状态变化后通知父页面刷新列表 */
  onChanged: () => void;
}

/**
 * 将 BOM 树转为树表数据。
 *
 * @param nodes BOM 树节点
 * @returns 树表行
 */
function toTableData(nodes: BomTreeNode[]): BomRow[] {
  return nodes.map((node) => ({
    ...node,
    children: node.children?.length ? toTableData(node.children) : undefined,
  }));
}

export default function BomEditor({ open, bomId, onClose, onChanged }: BomEditorProps) {
  const [bom, setBom] = useState<PdmBom | null>(null);
  const [loading, setLoading] = useState(false);

  const [parts, setParts] = useState<PdmPart[]>([]);
  const [lineModalOpen, setLineModalOpen] = useState(false);
  const [editingLine, setEditingLine] = useState<BomLine | null>(null);
  const [parentPartId, setParentPartId] = useState<string>('');
  const [parentPartNo, setParentPartNo] = useState<string>('');
  const [lineForm] = Form.useForm<LineFormValues>();

  const [releasesOpen, setReleasesOpen] = useState(false);
  const [releases, setReleases] = useState<BomRelease[]>([]);
  const [releasesLoading, setReleasesLoading] = useState(false);

  const canEdit = hasPermission('sys:pdm:bom:edit');
  const canRelease = hasPermission('sys:pdm:bom:release');

  const load = useCallback(async () => {
    if (!bomId) {
      setBom(null);
      return;
    }
    setLoading(true);
    try {
      setBom(await getBom(bomId));
    } finally {
      setLoading(false);
    }
  }, [bomId]);

  useEffect(() => {
    if (!open) return;
    void load();
    void listParts({ current: 1, size: 200, status: 'ENABLED' })
      .then((page) => setParts(page.records))
      .catch(() => setParts([]));
  }, [open, load]);

  const isDraft = bom?.status === 'DRAFT';
  const tableData = useMemo(() => toTableData(bom?.tree ?? []), [bom]);

  /**
   * 查询指定父件下已存在的子件（用于排除重复选项）。
   *
   * @param parentId 父件ID
   * @returns 已用子件ID集合
   */
  const usedChildIds = useMemo(() => {
    if (!bom || !parentPartId) {
      return new Set<string>();
    }
    return new Set(
      bom.lines.filter((line) => line.parentPartId === parentPartId).map((line) => line.childPartId),
    );
  }, [bom, parentPartId]);

  const childOptions = useMemo(
    () =>
      parts
        .filter((part) => part.id !== parentPartId && !usedChildIds.has(part.id))
        .map((part) => ({ value: part.id, label: `${part.code} - ${part.name}` })),
    [parts, parentPartId, usedChildIds],
  );

  const openAddLine = (node: BomRow) => {
    setEditingLine(null);
    setParentPartId(node.partId);
    setParentPartNo(node.partNo);
    setLineModalOpen(true);
    lineForm.resetFields();
    lineForm.setFieldsValue({ qty: 1, lossRate: 0, uom: node.uom ?? 'PCS' });
  };

  const openEditLine = (node: BomRow) => {
    const line = bom?.lines.find((item) => item.id === node.lineId);
    if (!line) {
      return;
    }
    setEditingLine(line);
    setParentPartId(line.parentPartId);
    setParentPartNo(line.parentPartNo ?? '');
    setLineModalOpen(true);
    lineForm.resetFields();
    lineForm.setFieldsValue({
      childPartId: line.childPartId,
      lineNo: line.lineNo,
      qty: line.qty,
      uom: line.uom,
      lossRate: line.lossRate,
      remark: line.remark,
    });
  };

  const submitLine = async () => {
    const values = await lineForm.validateFields();
    const payload = {
      parentPartId,
      childPartId: values.childPartId,
      lineNo: values.lineNo,
      qty: values.qty,
      uom: values.uom,
      lossRate: values.lossRate,
      remark: values.remark,
    };
    const updated = editingLine
      ? await updateBomLine(bomId, editingLine.id, payload)
      : await addBomLine(bomId, payload);
    setBom(updated);
    setLineModalOpen(false);
    message.success(editingLine ? '行已更新' : '子件已添加');
    onChanged();
  };

  const handleDeleteLine = async (lineId: string) => {
    const updated = await deleteBomLine(bomId, lineId);
    setBom(updated);
    message.success('行已删除');
    onChanged();
  };

  const handleRelease = async () => {
    const result = await releaseBom(bomId);
    message.success(`版本 ${result.versionNo} 已发布`);
    await load();
    onChanged();
  };

  const loadReleases = async () => {
    if (!bom) {
      return;
    }
    setReleasesLoading(true);
    try {
      setReleases(await listBomReleases(bom.bomNo));
    } finally {
      setReleasesLoading(false);
    }
  };

  const columns: TableProps<BomRow>['columns'] = [
    { title: '路径', dataIndex: 'path', key: 'path', width: 80 },
    { title: '零件编码', dataIndex: 'partNo', key: 'partNo', width: 160 },
    { title: '零件名称', dataIndex: 'partName', key: 'partName' },
    { title: '类型', dataIndex: 'partType', key: 'partType', width: 100 },
    { title: '行号', dataIndex: 'lineNo', key: 'lineNo', width: 80 },
    { title: '用量', dataIndex: 'qty', key: 'qty', width: 100 },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 80 },
    {
      title: '损耗率',
      dataIndex: 'lossRate',
      key: 'lossRate',
      width: 90,
      render: (value?: number) => (value === undefined || value === null ? '-' : `${value}%`),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size={0}>
          {isDraft && canEdit && ASSEMBLY_TYPES.has(record.partType) ? (
            <Button type="link" size="small" onClick={() => openAddLine(record)}>
              添加子件
            </Button>
          ) : null}
          {isDraft && canEdit && record.lineId ? (
            <Button type="link" size="small" onClick={() => openEditLine(record)}>
              编辑
            </Button>
          ) : null}
          {isDraft && canEdit && record.lineId ? (
            <Popconfirm
              title="确认删除该行？其下级结构将一并删除。"
              onConfirm={() => void handleDeleteLine(record.lineId as string)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      open={open}
      title={bom ? `BOM 结构 - ${bom.bomNo}` : 'BOM 结构'}
      width={1000}
      onClose={onClose}
      extra={
        <Space>
          <Button
            onClick={() => {
              setReleasesOpen(true);
              void loadReleases();
            }}
          >
            发布记录
          </Button>
          {canRelease && isDraft ? (
            <Popconfirm title="发布后结构不可修改，确认发布？" onConfirm={() => void handleRelease()}>
              <Button type="primary">发布</Button>
            </Popconfirm>
          ) : null}
        </Space>
      }
    >
      {bom ? (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Descriptions size="small" column={4} bordered>
            <Descriptions.Item label="BOM 编号">{bom.bomNo}</Descriptions.Item>
            <Descriptions.Item label="产品">{bom.productCode}</Descriptions.Item>
            <Descriptions.Item label="版本">{bom.versionNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={STATUS_CONFIG[bom.status]?.color ?? 'default'}>
                {STATUS_CONFIG[bom.status]?.label ?? bom.status}
              </Tag>
            </Descriptions.Item>
          </Descriptions>
          <Table<BomRow>
            rowKey="path"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={tableData}
            pagination={false}
            defaultExpandAllRows
            scroll={{ x: 900 }}
          />
        </Space>
      ) : (
        <Table loading={loading} columns={columns} dataSource={[]} pagination={false} />
      )}

      <Modal
        open={lineModalOpen}
        title={editingLine ? '编辑 BOM 行' : `添加子件 - ${parentPartNo}`}
        onOk={submitLine}
        onCancel={() => setLineModalOpen(false)}
        destroyOnClose
      >
        <Form form={lineForm} layout="vertical">
          <Form.Item
            name="childPartId"
            label="子件"
            rules={[{ required: true, message: '请选择子件' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              disabled={Boolean(editingLine)}
              placeholder="请选择启用的零件"
              options={childOptions}
            />
          </Form.Item>
          <Space size="middle" style={{ width: '100%' }}>
            <Form.Item
              name="qty"
              label="用量"
              rules={[{ required: true, message: '请输入用量' }]}
            >
              <InputNumber min={0.000001} step={1} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="uom" label="单位">
              <Input placeholder="PCS" style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="lossRate" label="损耗率（%）">
              <InputNumber min={0} max={100} step={0.1} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="lineNo" label="行号">
              <InputNumber min={1} style={{ width: 100 }} placeholder="自动" />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={releasesOpen}
        title="发布记录"
        width={720}
        onClose={() => setReleasesOpen(false)}
      >
        <Table<BomRelease>
          rowKey="id"
          size="small"
          loading={releasesLoading}
          dataSource={releases}
          pagination={false}
          columns={[
            { title: '版本', dataIndex: 'versionNo', width: 90 },
            { title: '视图', dataIndex: 'view', width: 90 },
            { title: '行数', dataIndex: 'lineCount', width: 80 },
            { title: '校验和', dataIndex: 'checksum', ellipsis: true },
            { title: '发布人', dataIndex: 'releasedBy', width: 100 },
            { title: '发布时间', dataIndex: 'releasedAt', width: 180 },
          ]}
        />
      </Drawer>
    </Drawer>
  );
}
