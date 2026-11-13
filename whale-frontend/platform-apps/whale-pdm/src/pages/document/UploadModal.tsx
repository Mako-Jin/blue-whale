// 上传 / 检入弹窗：选择文件并填写变更说明

import { useEffect, useState } from 'react';
import { Form, Input, Modal, Upload, message } from 'antd';
import type { UploadFile } from 'antd';
import { checkIn, uploadVersion, type PdmDocument } from '../../api/document';

interface UploadModalProps {
  open: boolean;
  /** 目标文档 */
  document: PdmDocument | null;
  /** 上传=生成新草稿；检入=释放检出锁并生成新草稿 */
  mode: 'upload' | 'checkin';
  onCancel: () => void;
  onDone: () => void;
}

export default function UploadModal({ open, document, mode, onCancel, onDone }: UploadModalProps) {
  const [form] = Form.useForm<{ changeNote?: string }>();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
      setFileList([]);
    }
  }, [open, form]);

  const handleOk = async () => {
    const file = fileList[0]?.originFileObj;
    if (!document || !file) {
      message.warning('请选择要上传的文件');
      return;
    }
    const { changeNote } = await form.validateFields();
    setSubmitting(true);
    try {
      if (mode === 'checkin') {
        await checkIn(document.id, file, changeNote);
        message.success('检入成功，已生成新草稿版本');
      } else {
        await uploadVersion(document.id, file, changeNote);
        message.success('上传成功，已生成新草稿版本');
      }
      onDone();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={mode === 'checkin' ? '检入图文档' : '上传新版本'}
      okText="提交"
      confirmLoading={submitting}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      {document ? (
        <div style={{ marginBottom: 12, color: '#666' }}>
          目标文档：{document.docNo} · {document.title}
        </div>
      ) : null}
      <Upload.Dragger
        multiple={false}
        maxCount={1}
        fileList={fileList}
        beforeUpload={() => false}
        onChange={({ fileList: next }) => setFileList(next)}
      >
        <p className="ant-upload-text">点击或拖拽文件到此区域</p>
        <p className="ant-upload-hint">提交后生成新的草稿版本，发布前不影响正式版本</p>
      </Upload.Dragger>
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item name="changeNote" label="变更说明">
          <Input.TextArea rows={2} placeholder="选填，如 修正尺寸标注" maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
