// 目录新增/编辑弹窗：名称、上级目录、类型与排序

import { useEffect } from 'react';
import { Form, Input, InputNumber, Modal, Select, message } from 'antd';
import { createFolder, updateFolder, type DocFolder, type FolderInput } from '../../api/document';
import type { FolderOption } from './utils';

/** 目录类型选项 */
const FOLDER_TYPE_OPTIONS = [
  { value: 'PRODUCT', label: '产品图纸' },
  { value: 'PROJECT', label: '项目资料' },
  { value: 'DOC_TYPE', label: '技术文档' },
  { value: 'CUSTOM', label: '自定义' },
];

interface FolderModalProps {
  open: boolean;
  /** 编辑对象，为空表示新增 */
  initial: DocFolder | null;
  /** 新增时的默认上级目录（0 表示根） */
  defaultParentId?: number;
  /** 可选上级目录（编辑时排除自身及其子孙） */
  parentOptions: FolderOption[];
  onCancel: () => void;
  onSaved: () => void;
}

export default function FolderModal({
  open,
  initial,
  defaultParentId,
  parentOptions,
  onCancel,
  onSaved,
}: FolderModalProps) {
  const [form] = Form.useForm<FolderInput>();

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        parentId: initial.parentId,
        name: initial.name,
        folderType: initial.folderType,
        sortNo: initial.sortNo,
        remark: initial.remark,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ parentId: defaultParentId ?? 0, folderType: 'CUSTOM', sortNo: 0 });
    }
  }, [open, initial, defaultParentId, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    if (initial) {
      await updateFolder(initial.id, values);
      message.success('目录已更新');
    } else {
      await createFolder(values);
      message.success('目录已创建');
    }
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑目录' : '新建目录'}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item name="parentId" label="上级目录">
          <Select
            options={[{ value: 0, label: '根目录' }, ...parentOptions]}
            placeholder="默认根目录"
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="目录名称"
          rules={[{ required: true, message: '请输入目录名称' }]}
        >
          <Input placeholder="如 主机装配图" maxLength={128} />
        </Form.Item>
        <Form.Item name="folderType" label="目录类型">
          <Select options={FOLDER_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="sortNo" label="排序号">
          <InputNumber min={0} max={9999} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} placeholder="选填" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
