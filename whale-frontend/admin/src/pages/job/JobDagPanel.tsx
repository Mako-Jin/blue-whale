// 依赖拓扑页签：按分组加载 DAG、展示节点/边统计，并提供整图环检测。

import { Alert, App, Button, Card, Input, Space, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import {
  getJobGraph,
  validateJobGraph,
  type JobGraphValidateVO,
  type JobGraphVO,
} from '../../api/job';
import { useI18n } from '../../i18n';
import { DagGraph } from './DagGraph';

export function JobDagPanel() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const [jobGroup, setJobGroup] = useState('');
  const [appliedGroup, setAppliedGroup] = useState<string | undefined>(undefined);
  const [graph, setGraph] = useState<JobGraphVO>({ nodes: [], edges: [] });
  const [loading, setLoading] = useState(false);
  const [validating, setValidating] = useState(false);
  const [validation, setValidation] = useState<JobGraphValidateVO | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getJobGraph(appliedGroup)
      .then((result) => {
        if (!cancelled) {
          setGraph(result);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          message.error(error instanceof Error ? error.message : t('job.dag.msg.loadFailed'));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [appliedGroup, message, t]);

  const handleValidate = async () => {
    setValidating(true);
    try {
      const result = await validateJobGraph();
      setValidation(result);
      if (result.acyclic) {
        message.success(t('job.dag.msg.acyclic'));
      } else {
        message.warning(t('job.dag.msg.cycleDetected'));
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('job.dag.msg.validateFailed'));
    } finally {
      setValidating(false);
    }
  };

  return (
    <Card title={t('job.dag.title')}>
      <Space wrap style={{ marginBottom: 12 }}>
        <Input
          allowClear
          placeholder={t('job.dag.filter.group')}
          value={jobGroup}
          onChange={(event) => setJobGroup(event.target.value)}
          onPressEnter={() => setAppliedGroup(jobGroup.trim() || undefined)}
          style={{ width: 220 }}
        />
        <Button onClick={() => setAppliedGroup(jobGroup.trim() || undefined)}>
          {t('job.dag.action.search')}
        </Button>
        <Button loading={validating} onClick={() => void handleValidate()}>
          {t('job.dag.action.validate')}
        </Button>
        <Typography.Text type="secondary">
          {t('job.dag.summary', { jobs: graph.nodes.length, edges: graph.edges.length })}
        </Typography.Text>
      </Space>
      {validation && (
        <Alert
          style={{ marginBottom: 12 }}
          type={validation.acyclic ? 'success' : 'error'}
          showIcon
          message={validation.acyclic ? t('job.dag.alert.acyclic') : t('job.dag.alert.cycle')}
          description={
            validation.acyclic
              ? undefined
              : t('job.dag.alert.cyclePath', {
                  path: validation.cycleJobIds.map((id) => `#${id}`).join(' → '),
                })
          }
        />
      )}
      <Spin spinning={loading}>
        <DagGraph
          nodes={graph.nodes}
          edges={graph.edges}
          cycleJobIds={validation?.acyclic === false ? validation.cycleJobIds : []}
        />
      </Spin>
    </Card>
  );
}
