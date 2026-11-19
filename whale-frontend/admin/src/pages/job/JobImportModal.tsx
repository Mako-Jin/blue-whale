// 任务导入弹窗：本地解析导出文件 → 选择冲突策略 → 提交并按后端结果展示失败明细。

import { Alert, App, Button, Modal, Radio, Space, Typography, Upload } from 'antd';
import { useEffect, useState } from 'react';
import {
  importJobs,
  type JobImportFailureVO,
  type JobImportResultVO,
  type JobTransferDependency,
  type JobTransferJob,
} from '../../api/job';

interface JobImportModalProps {
  open: boolean;
  onCancel: () => void;
  /** 导入完成后回调，用于刷新任务列表 */
  onImported: () => void;
}

interface ParsedExport {
  version?: string;
  jobs: JobTransferJob[];
  dependencies: JobTransferDependency[];
}

const CONFLICT_OPTIONS = [
  { value: 'SKIP', label: 'SKIP（已存在则跳过，默认）' },
  { value: 'OVERWRITE', label: 'OVERWRITE（已存在则覆盖）' },
];

/** 失败明细最多展示条数，其余折叠为提示 */
const MAX_FAILURE_DISPLAY = 5;

export function JobImportModal({ open, onCancel, onImported }: JobImportModalProps) {
  const { message } = App.useApp();
  const [fileName, setFileName] = useState<string | null>(null);
  const [parsed, setParsed] = useState<ParsedExport | null>(null);
  const [conflictStrategy, setConflictStrategy] = useState<'SKIP' | 'OVERWRITE'>('SKIP');
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<JobImportResultVO | null>(null);

  // 每次打开重置，避免上次的文件与结果残留
  useEffect(() => {
    if (open) {
      setFileName(null);
      setParsed(null);
      setConflictStrategy('SKIP');
      setImporting(false);
      setResult(null);
    }
  }, [open]);

  const handleFile = async (file: File) => {
    try {
      const text = await file.text();
      const payload = JSON.parse(text) as Partial<ParsedExport>;
      if (!Array.isArray(payload.jobs)) {
        throw new Error('文件缺少 jobs 数组');
      }
      setParsed({
        version: payload.version,
        jobs: payload.jobs,
        dependencies: Array.isArray(payload.dependencies) ? payload.dependencies : [],
      });
      setFileName(file.name);
      setResult(null);
    } catch (error) {
      setParsed(null);
      setFileName(null);
      message.error(error instanceof Error ? `文件解析失败：${error.message}` : '文件解析失败');
    }
    // 阻止 antd 自动上传，文件内容已在本地读取
    return false;
  };

  const handleImport = async () => {
    if (!parsed) {
      return;
    }
    setImporting(true);
    try {
      const outcome = await importJobs({
        conflictStrategy,
        jobs: parsed.jobs,
        dependencies: parsed.dependencies,
      });
      setResult(outcome);
      onImported();
      if (outcome.failures.length === 0) {
        message.success('导入完成');
      } else {
        message.warning(`导入完成，${outcome.failures.length} 条失败`);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导入失败');
    } finally {
      setImporting(false);
    }
  };

  return (
    <Modal
      open={open}
      title="导入任务"
      onCancel={onCancel}
      destroyOnHidden
      footer={
        result ? (
          <Button onClick={onCancel}>关闭</Button>
        ) : (
          <Space>
            <Button onClick={onCancel}>取消</Button>
            <Button type="primary" loading={importing} disabled={!parsed} onClick={() => void handleImport()}>
              开始导入
            </Button>
          </Space>
        )
      }
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Upload.Dragger
          accept=".json,application/json"
          maxCount={1}
          showUploadList={false}
          beforeUpload={(file) => {
            void handleFile(file);
            return false;
          }}
        >
          <Typography.Text strong>选择导出的 JSON 文件</Typography.Text>
          <br />
          <Typography.Text type="secondary">
            可直接使用「导出选中 / 导出全部」生成的文件
          </Typography.Text>
        </Upload.Dragger>

        {parsed && (
          <Typography.Text type="secondary">
            {fileName}：任务 {parsed.jobs.length} 条，依赖 {parsed.dependencies.length} 条
            {parsed.version ? `，版本 ${parsed.version}` : ''}
          </Typography.Text>
        )}

        {!result && (
          <Radio.Group
            value={conflictStrategy}
            onChange={(event) => setConflictStrategy(event.target.value)}
            options={CONFLICT_OPTIONS}
          />
        )}

        {result && (
          <Alert
            type={result.failures.length === 0 ? 'success' : 'warning'}
            showIcon
            message={`新建 ${result.createdCount}，更新 ${result.updatedCount}，跳过 ${result.skippedCount}，依赖 ${result.dependencyCount} 条`}
            description={
              result.failures.length > 0 ? (
                <FailureList failures={result.failures} />
              ) : undefined
            }
          />
        )}
      </Space>
    </Modal>
  );
}

function FailureList({ failures }: { failures: JobImportFailureVO[] }) {
  const visible = failures.slice(0, MAX_FAILURE_DISPLAY);
  return (
    <div>
      {visible.map((failure) => (
        <div key={`${failure.target}-${failure.reason}`}>
          {failure.target}：{failure.reason}
        </div>
      ))}
      {failures.length > visible.length && (
        <Typography.Text type="secondary">
          其余 {failures.length - visible.length} 条失败未展示
        </Typography.Text>
      )}
    </div>
  );
}
