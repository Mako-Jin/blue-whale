// 流程定义页的弹窗：新建 / 编辑 / 校验结果 / BPMN 导入。

import {
  Button,
  Descriptions,
  Form,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  type TableProps,
} from 'antd';
import type { ValidationIssue } from '../../api/workflow';
import type { BpmnFormValues, CreateFormValues, EditFormValues, IssueRow } from './constants';
import type { DefinitionListController } from './useDefinitionList';

const { Paragraph, Text } = Typography;

interface DefinitionDialogsProps {
  controller: DefinitionListController;
}

export default function DefinitionDialogs({ controller }: DefinitionDialogsProps) {
  const {
    createOpen,
    submitting,
    createForm,
    closeCreate,
    handleCreate,
    editing,
    editForm,
    closeEdit,
    handleUpdate,
    validateOpen,
    validating,
    validateResult,
    issueRows,
    closeValidate,
    bpmnOpen,
    bpmnSubmitting,
    bpmnForm,
    closeBpmn,
    handleBpmnFile,
    handleBpmnImport,
  } = controller;

  const issueColumns: TableProps<IssueRow>['columns'] = [
    {
      title: '级别',
      dataIndex: 'level',
      width: 100,
      render: (level: ValidationIssue['level']) =>
        level === 'ERROR' ? <Tag color="red">错误</Tag> : <Tag color="orange">警告</Tag>,
    },
    { title: '校验码', dataIndex: 'code', width: 240 },
    { title: '说明', dataIndex: 'message' },
    {
      title: '关联',
      dataIndex: 'refKey',
      width: 140,
      render: (refKey: string | null | undefined) => refKey ?? '-',
    },
  ];

  return (
    <>
      <Modal
        open={createOpen}
        title="新建流程定义"
        destroyOnClose
        confirmLoading={submitting}
        onOk={() => void handleCreate()}
        onCancel={closeCreate}
      >
        <Form<CreateFormValues> form={createForm} layout="vertical">
          <Form.Item
            name="code"
            label="流程编码"
            rules={[{ required: true, message: '请输入流程编码' }]}
          >
            <Input placeholder="如 leave_apply" />
          </Form.Item>
          <Form.Item
            name="name"
            label="流程名称"
            rules={[{ required: true, message: '请输入流程名称' }]}
          >
            <Input placeholder="如 请假申请" />
          </Form.Item>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            将创建含单个开始节点的最小定义，创建后可跳转设计器继续绘制。
          </Paragraph>
        </Form>
      </Modal>

      <Modal
        open={editing !== null}
        title="编辑流程定义"
        destroyOnClose
        confirmLoading={submitting}
        onOk={() => void handleUpdate()}
        onCancel={closeEdit}
      >
        <Form<EditFormValues> form={editForm} layout="vertical">
          <Form.Item
            name="code"
            label="流程编码"
            rules={[{ required: true, message: '请输入流程编码' }]}
          >
            <Input placeholder="如 leave_apply" />
          </Form.Item>
          <Form.Item
            name="name"
            label="流程名称"
            rules={[{ required: true, message: '请输入流程名称' }]}
          >
            <Input placeholder="如 请假申请" />
          </Form.Item>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            仅修改编码与名称，流程结构（definitionJson）保持原值不变。
          </Paragraph>
        </Form>
      </Modal>

      <Modal
        open={validateOpen}
        title="校验结果"
        loading={validating}
        footer={<Button onClick={closeValidate}>关闭</Button>}
        width={720}
        onCancel={closeValidate}
      >
        {validateResult ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="是否合法">
                {validateResult.valid ? (
                  <Tag color="green">合法</Tag>
                ) : (
                  <Tag color="red">不合法</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="模式">{validateResult.mode}</Descriptions.Item>
              <Descriptions.Item label="节点数">{validateResult.nodeCount}</Descriptions.Item>
              <Descriptions.Item label="流转数">{validateResult.flowCount}</Descriptions.Item>
            </Descriptions>
            {issueRows.length === 0 ? (
              <Text type="secondary">无错误与警告</Text>
            ) : (
              <Table<IssueRow>
                rowKey="rowKey"
                size="small"
                columns={issueColumns}
                dataSource={issueRows}
                pagination={false}
              />
            )}
          </Space>
        ) : (
          <Text type="secondary">校验中…</Text>
        )}
      </Modal>

      <Modal
        open={bpmnOpen}
        title="BPMN 导入"
        destroyOnClose
        confirmLoading={bpmnSubmitting}
        onOk={() => void handleBpmnImport()}
        onCancel={closeBpmn}
      >
        <Form<BpmnFormValues> form={bpmnForm} layout="vertical">
          <Form.Item
            name="code"
            label="流程编码"
            rules={[{ required: true, message: '请输入流程编码' }]}
          >
            <Input placeholder="如 leave_apply" />
          </Form.Item>
          <Form.Item
            name="name"
            label="流程名称"
            rules={[{ required: true, message: '请输入流程名称' }]}
          >
            <Input placeholder="如 请假申请" />
          </Form.Item>
          <Form.Item label="BPMN 文件">
            <Upload accept=".bpmn,.xml" showUploadList={false} beforeUpload={handleBpmnFile}>
              <Button>选择 .bpmn / .xml 文件</Button>
            </Upload>
          </Form.Item>
          <Form.Item
            name="bpmnXml"
            label="BPMN XML"
            rules={[{ required: true, message: '请粘贴或上传 BPMN XML' }]}
          >
            <Input.TextArea rows={8} placeholder="粘贴 BPMN 2.0 XML 内容" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
