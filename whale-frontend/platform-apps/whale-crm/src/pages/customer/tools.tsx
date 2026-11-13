// CRM 客户工具（M1-4 合并 / M1-9 CSV 导入导出）
//
// 合并：预览 -> 冲突确认 -> 执行；导入：预检（不落库）-> 执行；导出：CSV 脱敏下载。
import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Form,
  Input,
  InputNumber,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { hasPermission } from '../../utils/permission';
import {
  executeImport,
  exportCustomers,
  mergeCustomer,
  previewImport,
  previewMerge,
  type CustomerImportPreview,
  type CustomerImportResult,
  type CustomerMergePreview,
} from '../../api/customerTools';

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/**
 * 下载文本文件。
 *
 * @param content 文件内容
 * @param fileName 文件名
 */
function downloadText(content: string, fileName: string): void {
  const blob = new Blob(['\uFEFF', content], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

/** 客户工具页 */
export default function CustomerToolsPage() {
  const [mergeForm] = Form.useForm();
  const [mergePreview, setMergePreview] = useState<CustomerMergePreview | null>(null);
  const [merging, setMerging] = useState(false);
  const [csv, setCsv] = useState('');
  const [importPreview, setImportPreview] = useState<CustomerImportPreview | null>(null);
  const [importResult, setImportResult] = useState<CustomerImportResult | null>(null);

  const handlePreviewMerge = async () => {
    try {
      const values = await mergeForm.validateFields();
      setMergePreview(await previewMerge(values));
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const handleMerge = async () => {
    try {
      const values = await mergeForm.validateFields();
      setMerging(true);
      const result = await mergeCustomer({
        ...values,
        confirmPrimaryContactConflict: true,
      });
      const rewritten =
        result.references && Object.keys(result.references).length > 0
          ? Object.entries(result.references)
              .map(([key, count]) => `${key} ${count}`)
              .join('、')
          : '无';
      message.success(`合并完成：${rewritten}`);
      setMergePreview(null);
      mergeForm.resetFields();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setMerging(false);
    }
  };

  const handlePreviewImport = async () => {
    try {
      if (!csv.trim()) {
        message.warning('请先粘贴 CSV 内容');
        return;
      }
      setImportPreview(await previewImport(csv));
      setImportResult(null);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleImport = async () => {
    try {
      if (!csv.trim()) {
        message.warning('请先粘贴 CSV 内容');
        return;
      }
      const result = await executeImport(csv);
      setImportResult(result);
      message.success(
        `导入完成：新增 ${result.created}，跳过 ${result.skipped}，失败 ${result.failed}`,
      );
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleExport = async () => {
    try {
      const content = await exportCustomers();
      downloadText(content, `crm-customer-${new Date().toISOString().slice(0, 10)}.csv`);
      message.success('导出已开始');
    } catch (error) {
      message.error(errorText(error));
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ display: 'flex' }}>
      <Card title="客户合并（M1-4）">
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="合并会把源客户的联系人、地址、画像、跟进及商机/合同/订单/回款/工单等引用迁移到目标客户，源客户置为 MERGED 且只读；操作不可撤销，请先预览。"
        />
        <Form form={mergeForm} layout="inline" style={{ rowGap: 12 }}>
          <Form.Item
            name="sourceId"
            label="源客户 ID"
            rules={[{ required: true, message: '请输入源客户 ID' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="targetId"
            label="目标客户 ID"
            rules={[{ required: true, message: '请输入目标客户 ID' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="operatorNote" label="备注">
            <Input maxLength={200} style={{ width: 240 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button
                onClick={() => void handlePreviewMerge()}
                disabled={!hasPermission('sys:crm:customer:merge')}
              >
                预览
              </Button>
              <Button
                type="primary"
                danger
                loading={merging}
                disabled={!mergePreview || !hasPermission('sys:crm:customer:merge')}
                onClick={() => void handleMerge()}
              >
                确认合并
              </Button>
            </Space>
          </Form.Item>
        </Form>

        {mergePreview && (
          <>
            <Divider />
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="源客户">
                {mergePreview.sourceName || '-'}（{mergePreview.sourceCode || '-'}）
              </Descriptions.Item>
              <Descriptions.Item label="目标客户">
                {mergePreview.targetName || '-'}（{mergePreview.targetCode || '-'}）
              </Descriptions.Item>
              <Descriptions.Item label="源状态">
                {mergePreview.sourceStatus || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="目标状态">
                {mergePreview.targetStatus || '-'}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Text strong style={{ display: 'block', margin: '16px 0 8px' }}>
              引用数量
            </Typography.Text>
            <Table
              size="small"
              rowKey="key"
              pagination={false}
              dataSource={Object.entries(mergePreview.references).map(([key, value]) => ({
                key,
                value,
              }))}
              columns={[
                { title: '引用对象', dataIndex: 'key' },
                { title: '数量', dataIndex: 'value', width: 120 },
              ]}
            />
            {mergePreview.warnings.length > 0 && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 16 }}
                message={`需要注意：${mergePreview.warnings.join('；')}`}
              />
            )}
          </>
        )}
      </Card>

      <Card title="CSV 客户导入（M1-9）">
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="预检不落库；执行仅创建校验通过且无硬冲突的行。表头支持中文/英文别名，必填「客户名称」。重复导入会因硬冲突自动跳过。"
        />
        <Input.TextArea
          rows={8}
          value={csv}
          onChange={(event) => setCsv(event.target.value)}
          placeholder="客户名称,简称,行业,来源,等级,官网,税号,省,市,区,地址,负责人ID,标签,手机,邮箱"
        />
        <Space style={{ marginTop: 12 }}>
          <Button
            onClick={() => void handlePreviewImport()}
            disabled={!hasPermission('sys:crm:customer:import')}
          >
            预检
          </Button>
          <Button
            type="primary"
            onClick={() => void handleImport()}
            disabled={!hasPermission('sys:crm:customer:import')}
          >
            执行导入
          </Button>
        </Space>

        {importPreview && (
          <>
            <Divider />
            <Space size={24} style={{ marginBottom: 12 }}>
              <Typography.Text>总行数：{importPreview.total}</Typography.Text>
              <Typography.Text type="success">可导入：{importPreview.valid}</Typography.Text>
              <Typography.Text type="warning">重复：{importPreview.duplicate}</Typography.Text>
              <Typography.Text type="danger">非法：{importPreview.invalid}</Typography.Text>
            </Space>
            <Table
              size="small"
              rowKey="rowNo"
              pagination={{ pageSize: 10 }}
              dataSource={importPreview.rows}
              columns={[
                { title: '行号', dataIndex: 'rowNo', width: 80 },
                {
                  title: '客户名称',
                  dataIndex: 'name',
                  width: 220,
                  render: (value?: string) => value || '-',
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 110,
                  render: (value: string) => (
                    <Tag
                      color={value === 'OK' ? 'success' : value === 'DUPLICATE' ? 'orange' : 'red'}
                    >
                      {value === 'OK' ? '可导入' : value === 'DUPLICATE' ? '重复' : '非法'}
                    </Tag>
                  ),
                },
                { title: '原因', dataIndex: 'reason', render: (value?: string) => value || '-' },
              ]}
            />
          </>
        )}
        {importResult && (
          <Alert
            style={{ marginTop: 16 }}
            type="success"
            showIcon
            message={`执行结果：新增 ${importResult.created}，跳过 ${importResult.skipped}，失败 ${importResult.failed}`}
          />
        )}
      </Card>

      <Card title="CSV 客户导出（M1-9）">
        <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
          默认脱敏手机号、邮箱与税号；导出受数据范围与单次上限约束，并记录审计（不含敏感明文）。
        </Typography.Text>
        <Button
          type="primary"
          onClick={() => void handleExport()}
          disabled={!hasPermission('sys:crm:customer:export')}
        >
          导出 CSV
        </Button>
      </Card>
    </Space>
  );
}
