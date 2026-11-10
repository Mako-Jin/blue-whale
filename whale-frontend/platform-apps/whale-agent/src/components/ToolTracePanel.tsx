// 工具轨迹：展示助手回答过程中自主编排的每次工具调用（步骤/耗时/证据/状态）
// 历史消息没有轨迹数据时由调用方不渲染本组件

import { useEffect, useState } from 'react';
import { Collapse, Spin, Tag } from 'antd';
import type { ToolTrace } from '../api/agent';

interface ToolTracePanelProps {
  /** 本次回答的工具调用轨迹（按 SSE 推送顺序） */
  traces: ToolTrace[];
  /** 是否仍在流式执行（自动展开并显示执行中） */
  streaming?: boolean;
}

export default function ToolTracePanel({ traces, streaming }: ToolTracePanelProps) {
  // 首次出现轨迹时若仍在流式，直接展开；流式期间保持自动展开
  const [open, setOpen] = useState(Boolean(streaming));

  useEffect(() => {
    if (streaming) {
      setOpen(true);
    }
  }, [streaming]);

  return (
    <Collapse
      className="agent-tool-traces"
      size="small"
      activeKey={open ? ['traces'] : []}
      onChange={(keys) => setOpen(keys.length > 0)}
      items={[
        {
          key: 'traces',
          label: (
            <span className="agent-tool-traces-label">
              工具调用 {traces.length} 次
              {streaming && (
                <>
                  <Spin size="small" />
                  <span className="agent-tool-traces-running">执行中</span>
                </>
              )}
            </span>
          ),
          children: (
            <div className="agent-tool-trace-list">
              {traces.map((trace, index) => (
                <div key={`${trace.step}-${index}`} className="agent-tool-trace-row">
                  <span className="agent-tool-trace-step">#{trace.step}</span>
                  <Tag color="blue">{trace.name}</Tag>
                  <span>{trace.elapsedMillis} ms</span>
                  <span>证据 {trace.evidenceCount}</span>
                  {trace.success ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>}
                  {!trace.success && trace.error && (
                    <span className="agent-tool-trace-error">{trace.error}</span>
                  )}
                </div>
              ))}
            </div>
          ),
        },
      ]}
    />
  );
}
