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
import { useI18n } from '../../i18n';

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

/** 失败明细最多展示条数，其余折叠为提示 */
const MAX_FAILURE_DISPLAY = 5;

export function JobImportModal({ open, onCancel, onImported }: JobImportModalProps) {
  const { message } = App.useApp();
  const { t } = useI18n();
  const [fileName, setFileName] = useState<string | null>(null);
  const [parsed, setParsed] = useState<ParsedExport | null>(null);
  const [conflictStrategy, setConflictStrategy] = useState<'SKIP' | 'OVERWRITE'>('SKIP');
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<JobImportResultVO | null>(null);

  const conflictOptions = [
    { value: 'SKIP', label: t('job.import.conflict.skip') },
    { value: 'OVERWRITE', label: t('job.import.conflict.overwrite') },
  ];

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
        throw new Error(t('job.import.error.missingJobs'));
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
      message.error(
        error instanceof Error
          ? t('job.import.error.parseFailedWithReason', { reason: error.message })
          : t('job.import.error.parseFailed'),
      );
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
        message.success(t('job.import.msg.done'));
      } else {
        message.warning(t('job.import.msg.doneWithFailures', { count: outcome.failures.length }));
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('job.import.error.importFailed'));
    } finally {
      setImporting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={t('job.import.title')}
      onCancel={onCancel}
      destroyOnHidden
      footer={
        result ? (
          <Button onClick={onCancel}>{t('job.import.action.close')}</Button>
        ) : (
          <Space>
            <Button onClick={onCancel}>{t('job.import.action.cancel')}</Button>
            <Button type="primary" loading={importing} disabled={!parsed} onClick={() => void handleImport()}>
              {t('job.import.action.start')}
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
          <Typography.Text strong>{t('job.import.uploadTitle')}</Typography.Text>
          <br />
          <Typography.Text type="secondary">{t('job.import.uploadHint')}</Typography.Text>
        </Upload.Dragger>

        {parsed && (
          <Typography.Text type="secondary">
            {t('job.import.summary', {
              file: fileName ?? '',
              jobs: parsed.jobs.length,
              dependencies: parsed.dependencies.length,
            })}
            {parsed.version ? t('job.import.summaryVersion', { version: parsed.version }) : ''}
          </Typography.Text>
        )}

        {!result && (
          <Radio.Group
            value={conflictStrategy}
            onChange={(event) => setConflictStrategy(event.target.value)}
            options={conflictOptions}
          />
        )}

        {result && (
          <Alert
            type={result.failures.length === 0 ? 'success' : 'warning'}
            showIcon
            message={t('job.import.resultSummary', {
              created: result.createdCount,
              updated: result.updatedCount,
              skipped: result.skippedCount,
              dependencies: result.dependencyCount,
            })}
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
  const { t } = useI18n();
  const visible = failures.slice(0, MAX_FAILURE_DISPLAY);
  return (
    <div>
      {visible.map((failure) => (
        <div key={`${failure.target}-${failure.reason}`}>
          {t('job.import.failureItem', { target: failure.target, reason: failure.reason })}
        </div>
      ))}
      {failures.length > visible.length && (
        <Typography.Text type="secondary">
          {t('job.import.failureRest', { count: failures.length - visible.length })}
        </Typography.Text>
      )}
    </div>
  );
}
