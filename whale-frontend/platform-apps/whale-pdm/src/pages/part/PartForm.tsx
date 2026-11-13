// 零件主档新增/编辑弹窗
import { useEffect, useState } from 'react';
import { Col, Form, Input, Modal, Row, Select, message } from 'antd';
import {
  getCategoryTree,
  savePart,
  type PartCategory,
  type PartType,
  type PdmPart,
  type SourceType,
} from '../../api/part';

/** 零件类型选项 */
const PART_TYPE_OPTIONS: { value: PartType; label: string }[] = [
  { value: 'PRODUCT', label: '成品' },
  { value: 'ASSEMBLY', label: '部件' },
  { value: 'PART', label: '零件' },
  { value: 'RAW_MATERIAL', label: '原材料' },
];

/** 来源类型选项 */
const SOURCE_TYPE_OPTIONS: { value: SourceType; label: string }[] = [
  { value: 'STANDARD', label: '标准件' },
  { value: 'COMMON', label: '通用件' },
  { value: 'UNIQUE', label: '唯一件' },
];

/** 表单值 */
interface PartFormValues {
  code: string;
  name: string;
  partType?: PartType;
  sourceType?: SourceType;
  material?: string;
  spec?: string;
  uom?: string;
  categoryCode?: string;
  erpItemCode?: string;
  status: 'ENABLED' | 'DISABLED';
  remark?: string;
}

interface PartFormProps {
  /** 弹窗可见性 */
  open: boolean;
  /** 编辑对象，为空表示新增 */
  initial: PdmPart | null;
  /** 取消回调 */
  onCancel: () => void;
  /** 保存成功回调 */
  onSaved: () => void;
}

/**
 * 将分类树摊平为下拉选项。
 *
 * @param nodes 分类树
 * @param depth 层级
 * @returns 选项
 */
function flattenCategories(nodes: PartCategory[], depth = 0): { value: string; label: string }[] {
  return nodes.flatMap((node) => [
    { value: node.code, label: `${'　'.repeat(depth)}${node.name}（${node.code}）` },
    ...flattenCategories(node.children ?? [], depth + 1),
  ]);
}

export default function PartForm({ open, initial, onCancel, onSaved }: PartFormProps) {
  const [form] = Form.useForm<PartFormValues>();
  const [categories, setCategories] = useState<{ value: string; label: string }[]>([]);

  useEffect(() => {
    if (!open) return;
    if (initial) {
      form.setFieldsValue(initial);
    } else {
      form.resetFields();
    }
    void getCategoryTree()
      .then((tree) => setCategories(flattenCategories(tree)))
      .catch(() => setCategories([]));
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    await savePart(values, initial?.id);
    message.success('保存成功');
    form.resetFields();
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑零件' : '新增零件'}
      width={720}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ partType: 'PART', sourceType: 'UNIQUE', uom: 'PCS', status: 'ENABLED' }}
      >
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="code"
              label="零件编码"
              rules={[{ required: true, message: '请输入零件编码' }]}
            >
              <Input placeholder="如 P-1001" disabled={Boolean(initial)} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="name"
              label="零件名称"
              rules={[{ required: true, message: '请输入零件名称' }]}
            >
              <Input placeholder="请输入零件名称" />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="partType" label="零件类型">
              <Select options={PART_TYPE_OPTIONS} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="sourceType" label="来源类型">
              <Select options={SOURCE_TYPE_OPTIONS} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="status" label="状态">
              <Select
                options={[
                  { value: 'ENABLED', label: '启用' },
                  { value: 'DISABLED', label: '停用' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="material" label="材质">
              <Input placeholder="如 45#" />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="spec" label="规格">
              <Input placeholder="如 Φ20×100" />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="uom" label="计量单位">
              <Input placeholder="PCS" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="categoryCode" label="物料分类">
              <Select allowClear showSearch options={categories} placeholder="请选择分类" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="erpItemCode" label="ERP 物料编码">
              <Input placeholder="选填，用于 ERP 物料映射" />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item name="remark" label="备注">
              <Input.TextArea rows={2} placeholder="选填" />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  );
}
