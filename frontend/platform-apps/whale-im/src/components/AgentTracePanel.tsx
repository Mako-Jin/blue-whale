// 机器人消息的工具轨迹与引用：折叠懒加载 GET /im/agent-runs/{id}

import { useEffect, useState } from 'react';
import { App, Button, Collapse, Empty, Space, Spin, Tag, Typography } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { createLogger } from '@bluewhale/whale-common-tools';
import { imApi, type AgentRunDetail, type CitationItem } from '../api/im';

const logger = createLogger('whale-im › trace');

const { Text } = Typography;

interface AgentTracePanelProps {
  runId?: string;
  /** 历史消息按消息ID懒加载运行ID */
  resolveRunId?: () => Promise<string | undefined>;
}

/** 引用条目：展示编号/标题/路径，路径可复制 */
function CitationRow({ citation }: { citation: CitationItem }) {
  const { message } = App.useApp();
  const copy = async () => {
    const path = citation.path ?? '';
    if (!path) {
      return;
    }
    try {
      await navigator.clipboard.writeText(path);
      message.success('路径已复制');
    } catch (error) {
      logger.warn('复制路径失败', error);
      message.warning('当前浏览器不支持自动复制');
    }
  };

  return (
    <Space size={8} style={{ width: '100%', justifyContent: 'space-between' }}>
      <Space size={6}>
        <Tag color="blue">[{citation.sourceId ?? '?'}]</Tag>
        <Text>{citation.title ?? citation.kind ?? '引用'}</Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {citation.path ?? ''}
        </Text>
      </Space>
      {citation.path && (
        <Button size="small" type="text" icon={<CopyOutlined />} onClick={() => { void copy(); }} />
      )}
    </Space>
  );
}

function TraceBody({
  loading,
  detail,
  failed,
}: {
  loading: boolean;
  detail: AgentRunDetail | null;
  failed: boolean;
}) {
  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 12 }}>
        <Spin size="small" />
      </div>
    );
  }
  if (failed || !detail) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="轨迹加载失败" />;
  }
  return (
    <Space direction="vertical" size={10} style={{ width: '100%' }}>
      <Space size={8} wrap>
        <Tag color="geekblue">{detail.status}</Tag>
        <Text type="secondary" style={{ fontSize: 12 }}>
          耗时 {detail.durationMs ?? 0} ms
        </Text>
      </Space>
      <div>
        <Text strong style={{ fontSize: 12 }}>
          工具调用（{detail.tools?.length ?? 0}）
        </Text>
        {detail.tools?.length ? (
          detail.tools.map((tool, index) => (
            <div key={`tool-${index}`} style={{ marginTop: 6 }}>
              <Space size={6} wrap>
                <Tag color={tool.success === false ? 'red' : 'green'}>
                  {tool.name ?? 'tool'}
                </Tag>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  第 {tool.step ?? index + 1} 步
                </Text>
              </Space>
              {tool.arguments && <pre className="im-card-summary">{tool.arguments}</pre>}
            </div>
          ))
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            本次未调用工具
          </Text>
        )}
      </div>
      <div>
        <Text strong style={{ fontSize: 12 }}>
          引用（{detail.citations?.length ?? 0}）
        </Text>
        {detail.citations?.length ? (
          detail.citations.map((citation, index) => (
            <CitationRow key={`cite-${index}`} citation={citation} />
          ))
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            无引用
          </Text>
        )}
      </div>
    </Space>
  );
}

export default function AgentTracePanel({ runId, resolveRunId }: AgentTracePanelProps) {
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const [detail, setDetail] = useState<AgentRunDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (openKeys.length === 0 || detail || loading) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    const load = async (): Promise<AgentRunDetail> => {
      const id = runId ?? (await resolveRunId?.());
      if (!id) {
        throw new Error('缺少运行记录ID');
      }
      return imApi.getRun(id);
    };
    load()
      .then((result) => {
        if (!cancelled) {
          setDetail(result);
        }
      })
      .catch((error) => {
        logger.warn('轨迹加载失败', error);
        if (!cancelled) {
          setFailed(true);
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
  }, [openKeys, detail, loading, resolveRunId, runId]);

  return (
    <Collapse
      size="small"
      activeKey={openKeys}
      onChange={(keys) => setOpenKeys(keys as string[])}
      items={[
        {
          key: 'trace',
          label: '工具轨迹',
          children: <TraceBody loading={loading} detail={detail} failed={failed} />,
        },
      ]}
    />
  );
}
