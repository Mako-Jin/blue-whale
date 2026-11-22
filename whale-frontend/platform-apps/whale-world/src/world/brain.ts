/* ==========================================================================
 * 世界之脑 —— 城内 NPC 与小鲸宝接 whale-agent（P3）
 *
 * 「能聊天」升级为「能查业务数据」：城民不再是背台词的木偶，问户部主事
 * 「今天有几张异常工单」，他会真的去库里查。
 *
 * 传输层：`POST /agent/v1/chat`（whale-agent 8090，网关 `/agent` 不剥前缀）走 SSE，
 * 帧契约与 whale-frontend 的 whale-agent 子应用一致：
 *   meta  {conversationId,intent,degraded,citations}
 *   tool  {step,name,arguments,success,elapsedMillis,evidenceCount}
 *   delta {text}                       —— 增量文本，逐帧追加
 *   done  {conversationId,messageId,tokens,degraded,elapsedMillis}
 *   error {code,message}
 *
 * 三条硬约束（都是"宁可降级也不要白屏"）：
 *   1. 没有 token / 没有 sys:agent:chat / 401 / 403 → 直接判 offline，**不再发请求**；
 *      否则每个 NPC 每句问话都要吃一次 403，网关与 agent 日志会被刷穿。
 *   2. 判定 offline 后写入 reason，面板据此显示「AI 未接入，先听我本地台词」，
 *      而不是留一个永远转圈的「思索中」。
 *   3. 模型侧可能降级（服务端回 `模型暂时不可用` + 检索摘要，done.degraded=true）。
 *      这不算失败：照常显示，只在气泡上标一句提示。
 *
 * 走 `mode=direct` 而不是默认的自主编排：本机是 qwen2.5:1.5B，在工具轮次里会把
 * tool_calls 当正文吐出来，最终回答只剩一句「需要检索。」+ 一段 JSON。直答模式让
 * 后端「召回证据 → 单轮生成」，小模型也能成段作答。人设走独立的 `persona` 字段
 * （后端作为一条 system 消息注入），不再塞进问题文本——问题保持原样，检索不被污染。
 * ========================================================================== */

/** 能问话的人设：NPC 与宠物都实现它 */
export interface BrainPersona {
  id: string;
  name: string;
  role: string;
  /** 所在宫署/岗位，让回答带场景感（可缺省） */
  place?: string;
  /** 职责与能答的范围，写进首轮人设前缀 */
  brief: string;
}

export interface AskInput {
  persona: BrainPersona;
  question: string;
  /** 增量文本回调（上游自己做节流：这里按帧回调，一帧可能只有几个字） */
  onDelta?: (text: string) => void;
  /** 工具调用回调（如 search_knowledge / data_query），用于「正在查…」 */
  onTool?: (label: string) => void;
  /** 外部取消信号（面板关闭 / 场景销毁） */
  signal?: AbortSignal;
}

export interface BrainReply {
  text: string;
  /** 服务端降级：模型不可用，返回的是检索摘要 */
  degraded: boolean;
  conversationId: string;
}

export type BrainMode = 'online' | 'offline';

export interface WorldBrain {
  /** online：可问；offline：直接回本地台词 */
  readonly mode: BrainMode;
  /** offline 的人话原因（面板展示用） */
  readonly reason: string;
  ask: (input: AskInput) => Promise<BrainReply>;
  /** 忘掉某人设的会话（下次提问重新注人设） */
  reset: (personaId: string) => void;
  dispose: () => void;
}

export interface WorldBrainOptions {
  getToken?: () => string | null;
  /** 基座透传的权限码；缺省（undefined/空数组）时按「未知」处理，先试一次 */
  getPermissions?: () => string[] | null;
  /** 问答接口，默认 `/agent/v1/chat`（网关同源） */
  endpoint?: string;
  /** 单次问答整体超时（毫秒） */
  timeoutMillis?: number;
}

/** 问答权限码：缺失时后端必然 403，前端不必浪费一次请求 */
const CHAT_PERMISSION = 'sys:agent:chat';

const DEFAULT_ENDPOINT = '/agent/v1/chat';
/** 单次问答整体超时：本机 1.5B 在 CPU 上首问（含模型加载）常到 30–60 秒，
 *  卡 60 秒会把正常回答判成超时；后端 SSE emitter 上限是 180 秒，这里留足余量 */
const DEFAULT_TIMEOUT = 120_000;

/** 工具编码 → 中文动作，用于「正在查…」提示；未收录的按原样显示 */
const TOOL_LABELS: Record<string, string> = {
  search_knowledge: '检索知识库',
  data_query: '查业务数据',
  fact_get: '查指标',
  doc_read: '翻文档',
  doc_page: '翻文档',
  platform_op: '查平台状态',
  iot_device_state: '查设备',
  iot_device_history: '查设备历史',
  twin_scene_overview: '看孪生场景',
  sql: '查库',
  config: '查配置',
  git: '查代码',
  runtime: '查运行时',
  file: '翻文件',
  archive: '翻档案',
};

export class BrainError extends Error {
  /** offline：未接入；denied：无权限；http：服务端异常；timeout：超时；aborted：主动取消 */
  readonly kind: 'offline' | 'denied' | 'http' | 'timeout' | 'aborted';

  constructor(kind: BrainError['kind'], message: string) {
    super(message);
    this.name = 'BrainError';
    this.kind = kind;
  }
}

interface SseFrame {
  event: string;
  data: string;
}

/** 增量 SSE 解析：按空行切帧，`event:` 无空格（服务端就是 `event:meta`），`data:` 可能多行 */
function createFrameParser(onFrame: (frame: SseFrame) => void) {
  let buffer = '';
  return (chunk: string) => {
    buffer += chunk.replace(/\r\n?/g, '\n');
    let cut = buffer.indexOf('\n\n');
    while (cut >= 0) {
      const raw = buffer.slice(0, cut);
      buffer = buffer.slice(cut + 2);
      let event = 'message';
      const data: string[] = [];
      for (const line of raw.split('\n')) {
        if (line.startsWith('event:')) {
          event = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          data.push(line.slice(5).replace(/^ /, ''));
        }
      }
      if (data.length > 0) {
        onFrame({ event, data: data.join('\n') });
      }
      cut = buffer.indexOf('\n\n');
    }
  };
}

/** 宽松取字段：后端帧字段可能缺失或被序列化成字符串，读不到就回落 */
function pickString(source: Record<string, unknown>, key: string): string {
  const value = source[key];
  return typeof value === 'string' ? value : '';
}

function parseJson(raw: string): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/**
 * 人设文本（发给后端的 `persona` 字段，作为 system 消息注入）。
 *
 * 只写「我是谁、管什么」，不写「怎么答」——回答规则由后端系统提示统一约束，
 * 前端再叠一层措辞要求会把小模型的指令遵循挤爆（实测会退化成复述指令）。
 */
export function composePersona(persona: BrainPersona): string {
  const place = persona.place ? `，驻${persona.place}` : '';
  return [
    `蓝鲸城的${persona.role}「${persona.name}」${place}。职责范围：${persona.brief}`,
    // 知识库只有文档/代码/表结构，没有实时业务数据。不写这句，1.5B 会顺着问题的框架
    // 编出「今天没有异常工单」这种像模像样的假数（实测踩过）。
    '提醒：你手上只有平台的资料（文档、接口、配置、表结构），看不到实时业务数字；' +
      '被问到「今天有多少」「现在几张」这类，就说明要另去殿里查账，不要编。',
  ].join('\n');
}

/**
 * 兜底清洗：小模型偶尔把工具调用当正文吐出来（`需要检索。{"name":"search_knowledge",...}`）。
 * 剥掉这类 JSON 片段；剥完只剩「需要检索。」这类空话时判为无内容，交给上层回落本地台词。
 */
export function sanitizeAnswer(raw: string): string {
  const stripped = raw.replace(/\{[\s\S]{0,400}?"name"\s*:\s*"[a-z_]+"[\s\S]{0,400}?\}\s*/gi, '');
  const rest = stripped.replace(/^\s*需要检索[。.]?\s*/, '').trim();
  return rest.length >= 4 ? rest : '';
}

export function createWorldBrain(options: WorldBrainOptions = {}): WorldBrain {
  const endpoint = options.endpoint ?? DEFAULT_ENDPOINT;
  const timeoutMillis = options.timeoutMillis ?? DEFAULT_TIMEOUT;
  /** 人设 → 会话：同一 NPC 续聊带 conversationId，跨 NPC 不串味 */
  const sessions = new Map<string, string>();
  let mode: BrainMode = 'online';
  let reason = '';
  let disposed = false;
  const inflight = new Set<AbortController>();

  const goOffline = (why: string) => {
    if (mode === 'offline') {
      return;
    }
    mode = 'offline';
    reason = why;
  };

  const checkPermission = (): boolean => {
    const perms = options.getPermissions?.();
    // 未知（未注入 / 空数组）时不预判：留一次机会，真 403 再降级
    if (!perms || perms.length === 0) {
      return true;
    }
    return perms.includes(CHAT_PERMISSION);
  };

  /**
   * 惰性体检：不联网，只看「有没有票、有没有权限」。
   * 必须在创建时就生效——否则面板先显示「已接入」，等用户问第一句才变「未接入」，
   * 前 3 秒是假象（P1 传送门"开了但没内容"就是同类问题）。
   */
  const probeMode = (): BrainMode => {
    if (mode === 'offline') {
      return mode;
    }
    if (!options.getToken?.()) {
      goOffline('未登录（无访问令牌）');
      return mode;
    }
    if (!checkPermission()) {
      goOffline(`缺少权限 ${CHAT_PERMISSION}`);
    }
    return mode;
  };

  async function ask(input: AskInput): Promise<BrainReply> {
    if (disposed) {
      throw new BrainError('aborted', '世界已卸载');
    }
    if (probeMode() === 'offline') {
      throw new BrainError('offline', reason || 'AI 未接入');
    }
    const token = options.getToken?.() ?? null;
    if (!token) {
      // probeMode 已拦过，这里只做兜底（token 可能在两次读取间失效）
      goOffline('未登录（无访问令牌）');
      throw new BrainError('offline', reason);
    }

    const controller = new AbortController();
    inflight.add(controller);
    const onExternalAbort = () => controller.abort();
    input.signal?.addEventListener('abort', onExternalAbort, { once: true });
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, timeoutMillis);

    const conversationId = sessions.get(input.persona.id) ?? '';

    let text = '';
    let degraded = false;

    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          conversationId: conversationId || undefined,
          question: input.question,
          mode: 'direct',
          persona: composePersona(input.persona),
        }),
        signal: controller.signal,
        credentials: 'include',
      });

      if (response.status === 401) {
        goOffline('登录已失效（401）');
        throw new BrainError('denied', reason);
      }
      if (response.status === 403) {
        goOffline(`缺少权限 ${CHAT_PERMISSION}（403）`);
        throw new BrainError('denied', reason);
      }
      if (!response.ok || !response.body) {
        throw new BrainError('http', `问答服务异常 HTTP ${response.status}`);
      }

      const feed = createFrameParser((frame) => {
        const payload = parseJson(frame.data);
        if (!payload) {
          return;
        }
        if (frame.event === 'meta') {
          const id = pickString(payload, 'conversationId');
          if (id) {
            sessions.set(input.persona.id, id);
          }
          if (payload.degraded === true) {
            degraded = true;
          }
        } else if (frame.event === 'tool') {
          const name = pickString(payload, 'name');
          if (name) {
            input.onTool?.(TOOL_LABELS[name] ?? name);
          }
        } else if (frame.event === 'delta') {
          const piece = pickString(payload, 'text');
          if (piece) {
            text += piece;
            input.onDelta?.(piece);
          }
        } else if (frame.event === 'done') {
          const id = pickString(payload, 'conversationId');
          if (id) {
            sessions.set(input.persona.id, id);
          }
          if (payload.degraded === true) {
            degraded = true;
          }
        } else if (frame.event === 'error') {
          throw new BrainError('http', pickString(payload, 'message') || '问答中断');
        }
      });

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      for (;;) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }
        feed(decoder.decode(value, { stream: true }));
      }
      feed(decoder.decode());

      const answer = sanitizeAnswer(text);
      if (!answer) {
        // 2xx 但吐的都是"需要检索。{...}"这类半成品：当失败处理，
        // 让面板回落本地台词，别把工具调用当回答摆给玩家
        throw new BrainError('http', '模型未按要求作答');
      }
      return { text: answer, degraded, conversationId: sessions.get(input.persona.id) ?? '' };
    } catch (error) {
      const aborted = controller.signal.aborted;
      if (!aborted) {
        // 中途抛错（如 error 帧）时响应流还挂着，主动 abort 释放连接
        controller.abort();
      }
      if (error instanceof BrainError) {
        throw error;
      }
      if (aborted && timedOut) {
        throw new BrainError('timeout', `问答超时（${Math.round(timeoutMillis / 1000)} 秒）`);
      }
      if (aborted) {
        throw new BrainError('aborted', '已取消');
      }
      const message = error instanceof Error ? error.message : String(error);
      throw new BrainError('http', message);
    } finally {
      clearTimeout(timer);
      input.signal?.removeEventListener('abort', onExternalAbort);
      inflight.delete(controller);
    }
  }

  return {
    get mode() {
      return probeMode();
    },
    get reason() {
      return reason;
    },
    ask,
    reset(personaId) {
      sessions.delete(personaId);
    },
    dispose() {
      disposed = true;
      for (const controller of inflight) {
        controller.abort();
      }
      inflight.clear();
      sessions.clear();
    },
  };
}
