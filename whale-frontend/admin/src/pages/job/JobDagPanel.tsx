// 依赖拓扑页签：按分组加载 DAG、展示节点/边统计，并提供整图环检测。

import { Alert, App, Button, Card, Input, Space, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import {
  getJobGraph,
  validateJobGraph,
  type JobGraphValidateVO,
  type JobGraphVO,
} from '../../api/job';
import { DagGraph } from './DagGraph';

export function JobDagPanel() {
  const { message } = App.useApp();
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
          message.error(error instanceof Error ? error.message : '拓扑图加载失败');
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
  }, [appliedGroup, message]);

  const handleValidate = async () => {
    setValidating(true);
    try {
      const result = await validateJobGraph();
      setValidation(result);
      if (result.acyclic) {
        message.success('依赖图无环');
      } else {
        message.warning('检测到循环依赖，已在图中标红');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '环检测失败');
    } finally {
      setValidating(false);
    }
  };

  return (
    <Card title="依赖拓扑">
      <Space wrap style={{ marginBottom: 12 }}>
        <Input
          allowClear
          placeholder="分组（留空为全部分组）"
          value={jobGroup}
          onChange={(event) => setJobGroup(event.target.value)}
          onPressEnter={() => setAppliedGroup(jobGroup.trim() || undefined)}
          style={{ width: 220 }}
        />
        <Button onClick={() => setAppliedGroup(jobGroup.trim() || undefined)}>查询</Button>
        <Button loading={validating} onClick={() => void handleValidate()}>
          校验环
        </Button>
        <Typography.Text type="secondary">
          共 {graph.nodes.length} 个任务，{graph.edges.length} 条依赖
        </Typography.Text>
      </Space>
      {validation && (
        <Alert
          style={{ marginBottom: 12 }}
          type={validation.acyclic ? 'success' : 'error'}
          showIcon
          message={validation.acyclic ? '依赖图无环，可安全保存依赖' : '检测到循环依赖'}
          description={
            validation.acyclic
              ? undefined
              : `环路：${validation.cycleJobIds.map((id) => `#${id}`).join(' → ')}`
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
