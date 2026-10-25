// 证据面板：按 [S1]…[Sn] 展示引用来源（路径/行号/片段），Git 提交与运行时事实给出跳转

import { Empty, Space, Tag, Tooltip } from 'antd';
import type { Citation } from '../api/agent';

/** Gitee 仓库地址（commit 引用跳转） */
const GITEE_REPO = 'https://gitee.com/StrongToStop/blue-whale';

interface EvidencePanelProps {
  citations: Citation[];
  /** 当前高亮引用（点击正文 [Sx]） */
  activeId: string | null;
  /** 消息时间等辅助信息（可选） */
  footer?: string;
}

/** 证据类型 → 中文标签 */
function kindLabel(citation: Citation): string {
  if (citation.kind === 'fact') {
    return citation.factType ? `事实 · ${citation.factType}` : '结构化事实';
  }
  if (citation.kind === 'archive') {
    return '档案';
  }
  if (citation.kind === 'file') {
    return '业务文件';
  }
  return '知识块';
}

/** 路径可点击时的外部链接（git:hash → Gitee commit；其余返回 null） */
function externalLink(citation: Citation): string | null {
  if (citation.path?.startsWith('git:')) {
    const hash = citation.path.slice(4).trim();
    return hash ? `${GITEE_REPO}/commit/${hash}` : null;
  }
  return null;
}

/** 行号展示（有起止行时显示区间） */
function lineLabel(citation: Citation): string {
  if (citation.startLine == null) {
    return '';
  }
  return citation.endLine != null && citation.endLine !== citation.startLine
    ? `L${citation.startLine}-${citation.endLine}`
    : `L${citation.startLine}`;
}

export default function EvidencePanel({ citations, activeId, footer }: EvidencePanelProps) {
  return (
    <div className="evidence-panel">
      <div className="evidence-title">
        <span>证据来源</span>
        <Tag>{citations.length}</Tag>
      </div>
      {citations.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本轮回答暂无引用" />
      ) : (
        <div className="evidence-list">
          {citations.map((citation) => {
            const link = externalLink(citation);
            return (
              <div
                key={citation.sourceId}
                id={`evidence-${citation.sourceId}`}
                className={`evidence-item${activeId === citation.sourceId ? ' evidence-item-active' : ''}`}
              >
                <Space size={6} wrap>
                  <Tag color="blue">{citation.sourceId}</Tag>
                  <span className="evidence-kind">{kindLabel(citation)}</span>
                  {citation.score != null && (
                    <Tooltip title="BM25 检索分数">
                      <span className="evidence-score">score {citation.score.toFixed(2)}</span>
                    </Tooltip>
                  )}
                </Space>
                <div className="evidence-heading">
                  {citation.title || citation.factKey || citation.path}
                </div>
                <div className="evidence-path">
                  {link ? (
                    <a href={link} target="_blank" rel="noreferrer">
                      {citation.factKey ?? citation.path}
                    </a>
                  ) : (
                    <span>{citation.path}</span>
                  )}
                  {lineLabel(citation) && (
                    <span className="evidence-lines">{lineLabel(citation)}</span>
                  )}
                  {citation.downloadUrl && (
                    <a href={citation.downloadUrl} target="_blank" rel="noreferrer">
                      下载
                    </a>
                  )}
                </div>
                {citation.snippet && <pre className="evidence-snippet">{citation.snippet}</pre>}
              </div>
            );
          })}
        </div>
      )}
      {footer && <div className="evidence-footer">{footer}</div>}
    </div>
  );
}
