/* ==========================================================================
 * 城民问话面板
 *
 * 靠近 NPC 按 F（或按 T 唤小鲸宝）打开：左侧是玩家问的，右侧是城民答的。
 * 纯 DOM，不碰 three 主循环；流式文本按帧追加（服务端 delta 本身就是网络中速，
 * 再叠一层 requestAnimationFrame 节流只会更抖）。
 *
 * 三种状态必须都能看出区别，否则用户分不清「在查」和「坏了」：
 *   · 在线思考中：状态行显示「正在检索知识库…」+ 呼吸点
 *   · 服务端降级：气泡下方标一行「模型不可用，以上为资料摘要」
 *   · 未接入（无 token / 缺权限 / 网关没起）：徽标变灰，回答回落本地台词并说明原因
 * ========================================================================== */

import type { BrainError, BrainPersona, WorldBrain } from './brain';

export interface NpcChatPersona extends BrainPersona {
  /** 开口第一句（本地台词，任何人设都应自带，保证离线也有内容） */
  greeting?: string;
  /** 离线兜底台词池 */
  lines?: string[];
}

/** 面板顶部的一条委托卡（由场景层的任务系统算好，这里只负责画） */
export interface NpcChatQuestCard {
  id: string;
  title: string;
  /** offer = 可接（还没接）；active = 进行中（只显示进度）；ready = 可回禀 */
  state: 'offer' | 'active' | 'ready';
  /** 正文：可接时是他托付的那几句，进行中是还差什么，可交时是"可以复命了" */
  text: string;
  /** 按钮文案；不给就只显示不可点的进度条 */
  action?: string;
  /** 进度（已完成/总数），进行中与可交时显示 */
  done?: number;
  total?: number;
}

export interface NpcChatOptions {
  brain: WorldBrain;
  /** 面板开/关：场景层据此暂停该 NPC 巡逻、停止本地搭话 */
  onOpenChange?: (open: boolean, persona: NpcChatPersona | null) => void;
  /** 取该 NPC 身上的委托卡；不给则面板不显示委托区 */
  quests?: (npcId: string) => NpcChatQuestCard[];
  /** 点了委托按钮；action 与卡片的 action 一致 */
  onQuestAction?: (npcId: string, questId: string, action: string) => void;
}

export interface NpcChatHandle {
  open: (persona: NpcChatPersona) => void;
  close: () => void;
  isOpen: () => boolean;
  /** 当前对话对象 id */
  personaId: () => string | null;
  /** 代玩家发一句（冒烟/调试用；与输入框提交同一路径） */
  ask: (text: string) => void;
  /** 重画委托区：接取/交付之后状态变了，必须当场刷新，否则按钮还挂着旧状态 */
  refreshQuests: () => void;
  /** 以当前 NPC 的口吻补一句（交付后接一句收尾话） */
  say: (text: string) => void;
  destroy: () => void;
}

const CLOSE_LABEL = '离开';

/** 超过这个字数就折叠，否则 3000 字的检索摘要会把整块屏幕占满 */
const LONG_ANSWER = 620;

/**
 * 迷你 Markdown：只认 **粗体**、`代码`、`- 列表`、`# 标题`。
 * 先转义再套标签（不引第三方库，也不给 innerHTML 留任何用户可控的标签）。
 */
function renderRich(raw: string): string {
  const esc = (s: string) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const inline = (s: string) =>
    esc(s)
      .replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>')
      .replace(/`([^`]+)`/g, '<code>$1</code>');
  return raw
    .split('\n')
    .map((line) => {
      // 表格：检索摘要里大量是 Markdown 表格，小气泡里做不了真表格，
      // 但至少把 `|---|` 分隔行吞掉、每行压成「单元格 · 单元格」
      if (/^\s*\|/.test(line)) {
        if (/^\s*\|[\s:|-]+\|\s*$/.test(line)) {
          return '';
        }
        const cells = line
          .split('|')
          .slice(1, -1)
          .map((c) => c.trim())
          .filter(Boolean);
        return cells.length > 0
          ? `<div class="bww-chat-li">${inline(cells.join(' · '))}</div>`
          : '';
      }
      const bullet = /^\s*[-*+]\s+(.*)$/.exec(line);
      if (bullet) {
        return `<div class="bww-chat-li">${inline(bullet[1])}</div>`;
      }
      const heading = /^#{1,6}\s+(.*)$/.exec(line);
      if (heading) {
        return `<div class="bww-chat-h">${inline(heading[1])}</div>`;
      }
      return inline(line)
        ? `<div class="bww-chat-p">${inline(line)}</div>`
        : '<div class="bww-chat-gap"></div>';
    })
    .join('');
}

export function mountNpcChat(container: HTMLElement, options: NpcChatOptions): NpcChatHandle {
  const { brain } = options;
  container.innerHTML = `
    <div class="bww-chat-inner">
      <div class="bww-chat-head">
        <div class="bww-chat-who">
          <h3 class="bww-chat-name">—</h3>
          <span class="bww-chat-role">—</span>
        </div>
        <div class="bww-chat-badge" data-state="wait">···</div>
        <button type="button" class="bww-chat-close" title="${CLOSE_LABEL}（Esc）">✕</button>
      </div>
      <div class="bww-chat-quest"></div>
      <div class="bww-chat-log"></div>
      <div class="bww-chat-state"></div>
      <form class="bww-chat-form">
        <input class="bww-chat-input" type="text" maxlength="200" autocomplete="off" placeholder="问一句…" />
        <button type="submit" class="bww-chat-send">问</button>
      </form>
      <div class="bww-chat-foot">Enter 发问 · Esc 离开 · 他会去库里查，不是随口答</div>
    </div>`;

  const $ = <T extends Element>(sel: string): T => {
    const el = container.querySelector(sel);
    if (!el) {
      throw new Error(`npcchat: 缺少元素 ${sel}`);
    }
    return el as T;
  };

  const nameEl = $<HTMLElement>('.bww-chat-name');
  const roleEl = $<HTMLElement>('.bww-chat-role');
  const badgeEl = $<HTMLElement>('.bww-chat-badge');
  const questEl = $<HTMLElement>('.bww-chat-quest');
  const logEl = $<HTMLElement>('.bww-chat-log');
  const stateEl = $<HTMLElement>('.bww-chat-state');
  const formEl = $<HTMLFormElement>('.bww-chat-form');
  const inputEl = $<HTMLInputElement>('.bww-chat-input');
  const sendEl = $<HTMLButtonElement>('.bww-chat-send');
  const closeEl = $<HTMLButtonElement>('.bww-chat-close');

  let persona: NpcChatPersona | null = null;
  let opened = false;
  let busy = false;
  let abort: AbortController | null = null;
  let destroyed = false;
  /** 已展示过的说明文案（去重，见 note()） */
  const noteSeen = new Set<string>();
  /** 用函数取值：TS 会把 `brain.mode` 的联合类型按前一次判断收窄，导致"降级后再判断永远为假" */
  const offline = () => brain.mode === 'offline';

  const setBadge = () => {
    if (offline()) {
      badgeEl.dataset.state = 'off';
      badgeEl.textContent = 'AI 未接入';
      badgeEl.title = brain.reason || '未接入';
    } else {
      badgeEl.dataset.state = 'on';
      badgeEl.textContent = '已接入知识中枢';
      badgeEl.title = 'whale-agent · 可查业务数据';
    }
  };

  const scrollToEnd = () => {
    logEl.scrollTop = logEl.scrollHeight;
  };

  const setState = (text: string) => {
    stateEl.textContent = text;
    stateEl.classList.toggle('bww-show', Boolean(text));
  };

  const pushBubble = (who: 'npc' | 'me', text = ''): HTMLElement => {
    const bubble = document.createElement('div');
    bubble.className = `bww-chat-msg is-${who}`;
    const body = document.createElement('div');
    body.className = 'bww-chat-body';
    body.textContent = text;
    bubble.appendChild(body);
    logEl.appendChild(bubble);
    scrollToEnd();
    return body;
  };

  const note = (text: string) => {
    // 同一句说明只留一条：追问三轮就刷三行"模型不可用"会把对话本身挤没
    if (noteSeen.has(text)) {
      return;
    }
    noteSeen.add(text);
    const line = document.createElement('div');
    line.className = 'bww-chat-note';
    line.textContent = text;
    logEl.appendChild(line);
    scrollToEnd();
  };

  /* ---- 委托区：接取 / 进行中 / 可回禀 ----
   * 卡片全部由场景层的任务系统提供，这里只画不判：
   * 「能不能交」是状态机的事，面板照着显示就好，免得两处判断渐渐不一致。 */
  const escText = (s: string) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

  const renderQuests = () => {
    if (!options.quests || !persona) {
      questEl.innerHTML = '';
      questEl.classList.remove('bww-show');
      return;
    }
    const cards = options.quests(persona.id);
    if (cards.length === 0) {
      questEl.innerHTML = '';
      questEl.classList.remove('bww-show');
      return;
    }
    questEl.innerHTML = cards
      .map((card) => {
        const total = card.total ?? 0;
        const done = card.done ?? 0;
        // 进度条只在真的有几步时出现；可接的委托还不知道要跑几趟，画进度条是假的
        const bar =
          card.state !== 'offer' && total > 0
            ? `<div class="bww-quest-bar"><i style="width:${Math.round((done / total) * 100)}%"></i></div>`
            : '';
        const tag = card.state === 'offer' ? '委托' : card.state === 'ready' ? '可回禀' : '进行中';
        const btn = card.action
          ? `<button type="button" class="bww-quest-act" data-quest="${escText(card.id)}" data-quest-action="${escText(card.action)}">${escText(card.action)}</button>`
          : '';
        return `<div class="bww-quest-card is-${card.state}">
          <div class="bww-quest-top"><b>${escText(card.title)}</b><i class="bww-quest-tag">${tag}</i></div>
          <div class="bww-quest-text">${escText(card.text)}</div>
          ${bar}
          ${btn}
        </div>`;
      })
      .join('');
    questEl.classList.add('bww-show');
    for (const btn of questEl.querySelectorAll<HTMLButtonElement>('[data-quest]')) {
      btn.addEventListener('click', () => {
        const npcId = persona?.id;
        const questId = btn.dataset.quest;
        const action = btn.dataset.questAction;
        if (!npcId || !questId || !action) {
          return;
        }
        options.onQuestAction?.(npcId, questId, action);
        renderQuests();
      });
    }
  };

  const failText = (error: unknown): string => {
    const kind = (error as BrainError | null)?.kind;
    const message = error instanceof Error ? error.message : String(error);
    if (kind === 'denied') {
      return '（主公，账房的门我没钥匙——当前账号没有问数的权限。）';
    }
    if (kind === 'timeout') {
      return '（这一趟跑得太久了，怕是路上出了岔子，主公稍后再问？）';
    }
    if (kind === 'offline') {
      return '（眼下还连不上中枢，我先按老规矩回话。）';
    }
    return `（中枢那头出了岔子：${message}）`;
  };

  /** 离线兜底：从台词池挑一句，保证"问了总有回应" */
  const fallbackLine = (): string => {
    const lines = persona?.lines ?? [];
    if (lines.length === 0) {
      return '（……他想了想，没说什么。）';
    }
    const seen = Number(container.dataset.fallbackIndex ?? '0');
    container.dataset.fallbackIndex = String(seen + 1);
    return lines[seen % lines.length];
  };

  async function ask(raw: string) {
    const text = raw.trim();
    if (!persona || !text || busy) {
      return;
    }
    inputEl.value = '';
    pushBubble('me', text);

    // 未接入：不浪费一次必然 403 的请求，直接本地台词 + 一次性说明
    if (offline()) {
      busy = true;
      setState('本地台词 · 未接中枢');
      await new Promise((resolve) => setTimeout(resolve, 240));
      if (destroyed) {
        return;
      }
      pushBubble('npc', fallbackLine());
      note(`接入问数需要登录且具备 ask 权限（${brain.reason || '未登录'}）。`);
      setState('');
      busy = false;
      return;
    }

    busy = true;
    sendEl.disabled = true;
    setState('正在思忖…');
    const body = pushBubble('npc', '');
    body.classList.add('is-pending');
    abort = new AbortController();
    try {
      const reply = await brain.ask({
        persona,
        question: text,
        signal: abort.signal,
        onTool: (label) => {
          setState(`正在${label}…`);
        },
        onDelta: (piece) => {
          body.textContent += piece;
          body.classList.remove('is-pending');
          setState('正在回话…');
          scrollToEnd();
        },
      });
      body.classList.remove('is-pending');
      // 流式期间是纯文本（半截的 ** 会闪），收尾再统一渲染成富文本
      if (reply.text.length > LONG_ANSWER) {
        body.innerHTML = renderRich(reply.text);
        body.classList.add('is-rich', 'is-long');
        const more = document.createElement('button');
        more.type = 'button';
        more.className = 'bww-chat-more';
        more.textContent = '展开全文';
        more.addEventListener('click', () => {
          const expanded = body.classList.toggle('is-expanded');
          more.textContent = expanded ? '收起' : '展开全文';
        });
        body.parentElement?.appendChild(more);
      } else {
        body.innerHTML = renderRich(reply.text);
        body.classList.add('is-rich');
      }
      scrollToEnd();
      if (reply.degraded) {
        note('模型暂时不可用，以上为知识中枢检索到的资料摘要。');
      }
      setState('');
    } catch (error) {
      const kind = (error as BrainError | null)?.kind;
      if (kind === 'aborted') {
        body.parentElement?.remove();
      } else {
        body.textContent = failText(error);
        body.classList.remove('is-pending');
        setState('');
        if (offline()) {
          setBadge();
          note(`中枢连接已降级（${brain.reason}），此后面板走本地台词。`);
        }
      }
    } finally {
      abort = null;
      busy = false;
      sendEl.disabled = false;
      if (opened) {
        inputEl.focus();
      }
    }
  }

  formEl.addEventListener('submit', (event) => {
    event.preventDefault();
    void ask(inputEl.value);
  });
  closeEl.addEventListener('click', () => {
    close();
  });

  function open(next: NpcChatPersona) {
    if (destroyed) {
      return;
    }
    // 换人先掐掉上一条未完成的问答，避免串到新对象的气泡里
    abort?.abort();
    persona = next;
    container.dataset.fallbackIndex = '0';
    noteSeen.clear();
    logEl.innerHTML = '';
    nameEl.textContent = next.name;
    roleEl.textContent = next.place ? `${next.role} · ${next.place}` : next.role;
    inputEl.placeholder = `问${next.name}一句…`;
    setBadge();
    container.classList.add('bww-open');
    opened = true;
    if (next.greeting) {
      pushBubble('npc', next.greeting);
    }
    renderQuests();
    if (offline()) {
      note(`AI 未接入（${brain.reason || '未登录'}）：先按本地台词应答。`);
    }
    options.onOpenChange?.(true, next);
    setTimeout(() => {
      if (opened) {
        inputEl.focus();
      }
    }, 60);
  }

  function close() {
    if (!opened) {
      return;
    }
    abort?.abort();
    opened = false;
    container.classList.remove('bww-open');
    // 输入框还留着焦点的话，后续按键（T 唤小鲸宝、WASD 走路）会被当"打字"吞掉
    if (document.activeElement === inputEl) {
      inputEl.blur();
    }
    options.onOpenChange?.(false, persona);
    persona = null;
    setState('');
  }

  return {
    open,
    close,
    isOpen: () => opened,
    personaId: () => persona?.id ?? null,
    ask: (text: string) => {
      void ask(text);
    },
    refreshQuests: renderQuests,
    say: (text: string) => {
      if (opened && text) {
        pushBubble('npc', text);
      }
    },
    destroy() {
      destroyed = true;
      abort?.abort();
      opened = false;
      container.classList.remove('bww-open');
      container.innerHTML = '';
    },
  };
}
