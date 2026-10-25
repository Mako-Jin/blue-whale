// whale-agent 契约层：问答 SSE、会话、评价、索引运维
// 后端响应均为 { code, message, data } 信封（SSE 除外），Id 一律 string 避免雪花精度丢失

import { del, get, post, streamSse, type PageResult } from './client';

/** 检索范围键（与后端 AgentConversationService.normalizeScope 白名单一致） */
export type ScopeKey = 'code' | 'doc' | 'sql' | 'config' | 'git' | 'runtime' | 'file' | 'archive';

/** 范围开关展示配置（左栏） */
export const SCOPE_OPTIONS: ReadonlyArray<{ key: ScopeKey; label: string }> = [
  { key: 'code', label: '代码' },
  { key: 'doc', label: '文档' },
  { key: 'sql', label: '数据库' },
  { key: 'config', label: '配置' },
  { key: 'git', label: 'Git' },
  { key: 'runtime', label: '运行时' },
  { key: 'file', label: '业务文件' },
  { key: 'archive', label: '档案' },
];

/** 引用（回答中的 [S1]…[Sn]） */
export interface Citation {
  sourceId: string;
  kind: 'fact' | 'chunk' | string;
  path: string;
  title?: string;
  startLine?: number;
  endLine?: number;
  snippet?: string;
  factType?: string;
  factKey?: string;
  score?: number;
  /** 文件服务文件ID（业务文件/档案知识块，可空） */
  fileId?: string;
  /** 文件下载地址（有文件ID时后端生成） */
  downloadUrl?: string;
}

/** 会话摘要 */
export interface Conversation {
  id: string;
  title: string;
  scope?: string;
  createTime?: string;
  updateTime?: string;
}

/** 会话消息（含引用，用于证据面板回放） */
export interface Message {
  id: string;
  role: 'user' | 'assistant' | string;
  content: string;
  citations?: Citation[];
  tokens?: number;
  createTime?: string;
}

/** SSE meta 事件：流开始前的会话/意图/证据清单 */
export interface ChatMeta {
  conversationId: string;
  intent: 'STRUCTURED' | 'SEMANTIC' | 'MIXED' | string;
  degraded: boolean;
  citations?: Citation[];
}

/** SSE tool 事件：单步工具调用轨迹（自主编排每执行一次工具推送一条） */
export interface ToolTrace {
  /** 轮次 */
  step: number;
  /** 工具名 */
  name: string;
  /** 参数 JSON（后端已截断，仅用于展示） */
  arguments?: string;
  /** 是否成功 */
  success: boolean;
  /** 耗时（毫秒） */
  elapsedMillis: number;
  /** 新增证据条数 */
  evidenceCount: number;
  /** 错误信息（成功时为 null） */
  error?: string | null;
}

/** SSE done 事件：回答结束，返回落库消息ID与统计 */
export interface ChatDone {
  conversationId: string;
  messageId: string;
  tokens: number;
  degraded: boolean;
  elapsedMillis: number;
  citations?: Citation[];
}

/** 索引状态 */
export interface IndexStatus {
  status: 'READY' | 'BUILDING' | 'FAILED' | 'EMPTY' | string;
  indexVersion?: string;
  fileCount?: number;
  chunkCount?: number;
  lastBuildAt?: string;
  buildMillis?: number;
  indexDir?: string;
}

/** 提问请求 */
export interface ChatRequest {
  /** 会话ID（字符串；空表示新建会话，后端 Long 自动强转） */
  conversationId?: string;
  question: string;
  /** 检索范围（逗号分隔；空表示全部） */
  scope?: string;
}

/** SSE 回调集合 */
export interface ChatStreamHandlers {
  onMeta?: (meta: ChatMeta) => void;
  onTool?: (trace: ToolTrace) => void;
  onDelta?: (text: string) => void;
  onDone?: (done: ChatDone) => void;
  onError?: (message: string) => void;
}

/** 由 ScopeKey 数组拼出后端 scope 参数（全选等价于空，即全部） */
export function joinScope(keys: ScopeKey[]): string | undefined {
  if (keys.length === 0 || keys.length === SCOPE_OPTIONS.length) {
    return undefined;
  }
  return keys.join(',');
}

/**
 * 发起问答（SSE 流式）。
 *
 * @param request 提问请求
 * @param handlers 事件回调
 * @param signal 中止信号（「停止」按钮）
 */
export async function streamChat(
  request: ChatRequest,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  await streamSse(
    '/agent/chat',
    request,
    (event, data) => {
      switch (event) {
        case 'meta':
          handlers.onMeta?.(data as ChatMeta);
          break;
        case 'tool':
          handlers.onTool?.(data as ToolTrace);
          break;
        case 'delta': {
          const text = (data as { text?: string })?.text ?? '';
          if (text) {
            handlers.onDelta?.(text);
          }
          break;
        }
        case 'done':
          handlers.onDone?.(data as ChatDone);
          break;
        case 'error':
          handlers.onError?.((data as { message?: string })?.message ?? '问答执行失败');
          break;
        default:
          break;
      }
    },
    signal,
  );
}

/** 我的会话分页 */
export function pageConversations(current = 1, size = 50): Promise<PageResult<Conversation>> {
  return get<PageResult<Conversation>>('/agent/conversation/page', { current, size });
}

/** 会话消息（仅本人） */
export function listMessages(conversationId: string): Promise<Message[]> {
  return get<Message[]>(`/agent/conversation/${conversationId}/messages`);
}

/** 删除会话（仅本人） */
export function deleteConversation(conversationId: string): Promise<void> {
  return del<void>(`/agent/conversation/${conversationId}`);
}

/** 回答评价：1 有用 / -1 无用 */
export function sendFeedback(messageId: string, rating: 1 | -1, comment?: string): Promise<void> {
  return post<void>('/agent/feedback', { messageId, rating, comment });
}

/** 索引状态（登录即可） */
export function getIndexStatus(): Promise<IndexStatus> {
  return get<IndexStatus>('/agent/index/status');
}

/** 触发全量重建（需 sys:agent:index:rebuild）；返回是否受理 */
export function rebuildIndex(): Promise<boolean> {
  return post<boolean>('/agent/index/rebuild');
}
