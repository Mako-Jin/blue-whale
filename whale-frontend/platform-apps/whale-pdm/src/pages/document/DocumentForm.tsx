// 图文档新增/编辑弹窗：目录、编号、标题、类型、关联零件与说明

import { useEffect } from 'react';
import { Form, Input, Modal, Select, message } from 'antd';
import {
  createDocument,
  updateDocument,
  type DocumentInput,
  type DocType,
  type PdmDocument,
} from '../../api/document';
import { DOC_TYPE_OPTIONS, type FolderOption } from './utils';

interface DocumentFormProps {
  open: boolean;
  /** 编辑对象，为空表示新增 */
  initial: PdmDocument | null;
  /** 目录下拉选项 */
  folderOptions: FolderOption[];
  /** 新增时的默认目录 */
  defaultFolderId?: number;
  onCancel: () => void;
  onSaved: () => void;
}

export default function DocumentForm({
  open,
  initial,
  folderOptions,
  defaultFolderId,
  onCancel,
  onSaved,
}: DocumentFormProps) {
  const [form] = Form.useForm<DocumentInput>();

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        folderId: initial.folderId,
        docNo: initial.docNo,
        title: initial.title,
        docType: initial.docType,
        partNo: initial.partNo,
        description: initial.description,
        tags: initial.tags,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ folderId: defaultFolderId, docType: 'DRAWING' });
    }
  }, [open, initial, defaultFolderId, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    if (initial) {
      await updateDocument(initial.id, values);
      message.success('图文档已更新');
    } else {
      await createDocument(values);
      message.success('图文档已创建');
    }
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑图文档' : '新建图文档'}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
      width={560}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="folderId"
          label="所属目录"
          rules={[{ required: true, message: '请选择所属目录' }]}
        >
          <Select
            options={folderOptions}
            placeholder="请选择目录"
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>
        <Form.Item name="docNo" label="文档编号">
          <Input placeholder="留空则自动生成，如 DOC-20261104-000001" disabled={Boolean(initial)} />
        </Form.Item>
        <Form.Item
          name="title"
          label="文档标题"
          rules={[{ required: true, message: '请输入文档标题' }]}
        >
          <Input placeholder="如 主机外壳装配图" maxLength={255} />
        </Form.Item>
        <Form.Item
          name="docType"
          label="文档类型"
          rules={[{ required: true, message: '请选择文档类型' }]}
        >
          <Select<DocType> options={DOC_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="partNo" label="关联零件号">
          <Input placeholder="选填，如 PART-001" maxLength={64} />
        </Form.Item>
        <Form.Item name="tags" label="标签">
          <Input placeholder="选填，逗号分隔，如 钣金,外壳" maxLength={255} />
        </Form.Item>
        <Form.Item name="description" label="说明">
          <Input.TextArea rows={3} placeholder="选填" maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
