// BOM 结构编辑器：树表展示、行增改删、视图派生、版本对比、发布与导出
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
  deriveBom,
  diffBoms,
  exportBom,
  getBom,
  listBomReleases,
  listBoms,
  listParts,
  releaseBom,
  updateBomLine,
  type BomDeriveInput,
  type BomDiff,
  type BomDiffLine,
  type BomLine,
  type BomRelease,
  type BomStatus,
  type BomTreeNode,
  type BomView,
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

/** BOM 视图文案 */
const VIEW_LABEL: Record<BomView, string> = {
  EBOM: '设计 BOM',
  PBOM: '工艺 BOM',
  MBOM: '制造 BOM',
};

/** 对比变更文案与颜色 */
const CHANGE_CONFIG: Record<string, { label: string; color: string }> = {
  ADDED: { label: '新增', color: 'green' },
  REMOVED: { label: '删除', color: 'red' },
  QTY_CHANGED: { label: '用量变更', color: 'orange' },
  LOSS_CHANGED: { label: '损耗变更', color: 'orange' },
  UOM_CHANGED: { label: '单位变更', color: 'orange' },
  PART_CHANGED: { label: '零件变更', color: 'purple' },
};

/** 派生表单值 */
interface DeriveFormValues {
  view: 'PBOM' | 'MBOM';
  bomNo?: string;
  remark?: string;
}

/**
 * 触发浏览器保存 Blob。
 *
 * @param blob     文件内容
 * @param filename 文件名
 */
function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

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

  const [deriveOpen, setDeriveOpen] = useState(false);
  const [deriving, setDeriving] = useState(false);
  const [deriveForm] = Form.useForm<DeriveFormValues>();

  const [diffOpen, setDiffOpen] = useState(false);
  const [diffLoading, setDiffLoading] = useState(false);
  const [diffResult, setDiffResult] = useState<BomDiff | null>(null);
  const [diffOptions, setDiffOptions] = useState<PdmBom[]>([]);
  const [targetBomId, setTargetBomId] = useState<string>('');

  const canEdit = hasPermission('sys:pdm:bom:edit');
  const canAdd = hasPermission('sys:pdm:bom:add');
  const canRelease = hasPermission('sys:pdm:bom:release');
  const canExport = hasPermission('sys:pdm:bom:export');

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

  const handleDerive = async () => {
    const values = await deriveForm.validateFields();
    setDeriving(true);
    try {
      const derived = await deriveBom(bomId, values as BomDeriveInput);
      setBom(derived);
      setDeriveOpen(false);
      message.success(`已派生 ${VIEW_LABEL[derived.view]} ${derived.versionNo}`);
      onChanged();
    } finally {
      setDeriving(false);
    }
  };

  const openDiff = async () => {
    setDiffOpen(true);
    setDiffResult(null);
    setTargetBomId('');
    if (!bom) {
      return;
    }
    try {
      const page = await listBoms({
        current: 1,
        size: 200,
        productCode: bom.productCode,
      });
      setDiffOptions(page.records.filter((item) => item.id !== bom.id));
    } catch {
      setDiffOptions([]);
    }
  };

  const handleDiff = async () => {
    if (!bom || !targetBomId) {
      message.warning('请选择对比目标');
      return;
    }
    setDiffLoading(true);
    try {
      setDiffResult(await diffBoms(bom.id, targetBomId));
    } finally {
      setDiffLoading(false);
    }
  };

  const handleExport = async () => {
    const result = await exportBom(bomId);
    saveBlob(result.blob, result.filename || `${bom?.bomNo ?? 'BOM'}.xlsx`);
    message.success('导出成功');
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
          {canExport ? <Button onClick={() => void handleExport()}>导出 Excel</Button> : null}
          <Button onClick={() => void openDiff()}>对比</Button>
          {canAdd && bom?.view === 'EBOM' ? (
            <Button
              onClick={() => {
                deriveForm.resetFields();
                setDeriveOpen(true);
              }}
            >
              派生视图
            </Button>
          ) : null}
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
            <Descriptions.Item label="视图">{VIEW_LABEL[bom.view] ?? bom.view}</Descriptions.Item>
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

      <Modal
        open={deriveOpen}
        title="派生 BOM 视图"
        onOk={() => void handleDerive()}
        confirmLoading={deriving}
        onCancel={() => setDeriveOpen(false)}
        destroyOnClose
      >
        <Form form={deriveForm} layout="vertical" initialValues={{ view: 'PBOM' }}>
          <Form.Item
            name="view"
            label="目标视图"
            rules={[{ required: true, message: '请选择目标视图' }]}
          >
            <Select
              options={[
                { value: 'PBOM', label: '工艺 BOM（PBOM）' },
                { value: 'MBOM', label: '制造 BOM（MBOM）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="bomNo" label="BOM 编号">
            <Input placeholder="留空按源编号 + 视图生成" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={diffOpen}
        title="BOM 对比"
        width={900}
        footer={null}
        onCancel={() => setDiffOpen(false)}
        destroyOnClose
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Space wrap>
            <Select
              style={{ minWidth: 340 }}
              placeholder="选择同产品的对比目标"
              value={targetBomId || undefined}
              onChange={setTargetBomId}
              options={diffOptions.map((item) => ({
                value: item.id,
                label: `${item.bomNo} · ${VIEW_LABEL[item.view]} · ${item.versionNo} · ${
                  STATUS_CONFIG[item.status]?.label ?? item.status
                }`,
              }))}
            />
            <Button type="primary" loading={diffLoading} onClick={() => void handleDiff()}>
              开始对比
            </Button>
          </Space>
          {diffResult ? (
            <>
              <Descriptions size="small" column={4} bordered>
                <Descriptions.Item label="基线">
                  {`${diffResult.baseBomNo} · ${diffResult.baseVersionNo}`}
                </Descriptions.Item>
                <Descriptions.Item label="目标">
                  {`${diffResult.targetBomNo} · ${diffResult.targetVersionNo}`}
                </Descriptions.Item>
                <Descriptions.Item label="新增 / 删除">
                  {`${diffResult.added} / ${diffResult.removed}`}
                </Descriptions.Item>
                <Descriptions.Item label="变更">{diffResult.changed}</Descriptions.Item>
              </Descriptions>
              <Table<BomDiffLine>
                size="small"
                rowKey={(record) => `${record.path}-${record.changeType}-${record.field ?? ''}`}
                dataSource={diffResult.lines}
                pagination={false}
                scroll={{ y: 320 }}
                columns={[
                  { title: '路径', dataIndex: 'path', width: 80 },
                  {
                    title: '变更',
                    dataIndex: 'changeType',
                    width: 100,
                    render: (value: string) => {
                      const config = CHANGE_CONFIG[value] ?? { label: value, color: 'default' };
                      return <Tag color={config.color}>{config.label}</Tag>;
                    },
                  },
                  { title: '零件', dataIndex: 'partNo', width: 160 },
                  { title: '字段', dataIndex: 'field', width: 100 },
                  { title: '基线值', dataIndex: 'baseValue', width: 120 },
                  { title: '目标值', dataIndex: 'targetValue', width: 120 },
                ]}
              />
            </>
          ) : null}
        </Space>
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
