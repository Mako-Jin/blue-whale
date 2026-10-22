// BPMN 2.0 导入弹窗：code / name 默认取当前定义元信息，提交后跳转到新定义。

import { App, Input, Modal, Space } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { workflowApi, type Id } from '../../../api/workflow';
import { toMessage } from '../errors';

interface BpmnImportModalProps {
  open: boolean;
  /** 打开时预填的流程编码 */
  defaultCode: string;
  /** 打开时预填的流程名称 */
  defaultName: string;
  onClose: () => void;
  onImported: (id: Id) => void;
}

export default function BpmnImportModal({
  open,
  defaultCode,
  defaultName,
  onClose,
  onImported,
}: BpmnImportModalProps) {
  const { message } = App.useApp();
  const [xml, setXml] = useState('');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 只在打开时预填 code / name（打开期间页面元信息变化不覆盖用户输入）
  const defaultsRef = useRef({ code: defaultCode, name: defaultName });
  useEffect(() => {
    defaultsRef.current = { code: defaultCode, name: defaultName };
  });
  useEffect(() => {
    if (open) {
      setCode(defaultsRef.current.code);
      setName(defaultsRef.current.name);
      setXml('');
    }
  }, [open]);

  const handleImport = async () => {
    if (!code.trim() || !name.trim()) {
      message.warning('请填写流程编码与名称');
      return;
    }
    if (!xml.trim()) {
      message.warning('请粘贴 BPMN XML');
      return;
    }
    setSubmitting(true);
    try {
      const created = await workflowApi.definitionBpmnImport({
        code: code.trim(),
        name: name.trim(),
        bpmnXml: xml,
      });
      message.success('BPMN 导入成功');
      onClose();
      if (created?.id) {
        onImported(created.id);
      }
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="导入 BPMN"
      open={open}
      width={720}
      okText="导入"
      confirmLoading={submitting}
      onOk={() => void handleImport()}
      onCancel={onClose}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Input
          addonBefore="code"
          placeholder="流程编码"
          value={code}
          onChange={(event) => setCode(event.target.value)}
        />
        <Input
          addonBefore="name"
          placeholder="流程名称"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
        <Input.TextArea
          rows={12}
          placeholder="粘贴 BPMN 2.0 XML"
          value={xml}
          onChange={(event) => setXml(event.target.value)}
        />
      </Space>
    </Modal>
  );
}
