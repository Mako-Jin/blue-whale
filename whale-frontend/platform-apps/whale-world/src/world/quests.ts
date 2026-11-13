/* ==========================================================================
 * 城民委托 —— 任务状态机
 *
 * 城市里已经有 NPC、有牌坊、有传送门，但没有"为什么走这一趟"。这一层补的是
 * 最简单的闭环：城民托你一件事 → 你去城里某处站一站 → 回来复命。
 *
 * <p>刻意不碰 three.js、不碰 DOM：任务怎么算达成、什么时候可交、存档怎么存，
 * 全在这里定；city.ts 只负责把位置喂进来、把提示画出去。这样状态机可以在没有
 * 渲染的环境下被推演（离线脚本、e2e），改一句委托台词也不必动两千行的场景文件。</p>
 *
 * <p>三条体验判断：</p>
 * <ul>
 *   <li><b>目标必须是"走到某处"，不是"打怪掉落"</b>：这座城没有战斗，也没有掉落物；
 *       接得上这座城的动作只有走路、看牌坊、进传送门。目标点全部落在真实可达的位置。</li>
 *   <li><b>只要在半径内就算到</b>，不要求踩中一个点。牌坊立在殿前，殿本身是碰撞体，
 *       把判定做成"精确落点"等于逼玩家往墙上撞。</li>
 *   <li><b>可交时不在原地自动结算</b>：必须回去找委托人再按一次 F。任务的重量在于
 *       "回来说这一声"，自动结算就变成了一张打勾清单。</li>
 * </ul>
 * ========================================================================== */

export type QuestState = 'available' | 'active' | 'ready' | 'done';

/** 一步目标：走到 (x,z) 的 radius 米内 */
export interface QuestStep {
  kind: 'visit';
  /** 目标世界坐标 */
  x: number;
  z: number;
  /** 判定半径（米）：牌坊/殿前用 10~13，门洞用 7，别做太紧 */
  radius: number;
  /** HUD 上显示的这一句（动词开头，带地点，不说"任务目标 1"） */
  label: string;
}

export interface QuestDef {
  id: string;
  title: string;
  /** 委托人（与 NPCS 的 id 一致） */
  giver: string;
  /** 接取时他说的话 */
  offer: string;
  /** 还没走完时再找他，他催你的一句 */
  remind: string;
  /** 都走完了找他，他开口的一句 */
  report: string;
  /** 交付后他收尾的一句 */
  done: string;
  /** 报酬（现在是口头凭据，等经济系统落地再换成实物） */
  reward: string;
  steps: QuestStep[];
}

/**
 * 三件委托，各占一块城。
 *
 * <p>坐标直接取自 city-layout：户部 (26, 62.8)、市舶司 (-26, 62.8)、礼部 (26, -62.8)、
 * 兵部 (-26, -62.8)、数枢殿 (0, -2)、三道门洞都在御道 x≈0 上（皇城 22 / 内城 48 / 外城 92）。</p>
 */
export const QUESTS: QuestDef[] = [
  {
    id: 'han-city-tour',
    title: '认得这城',
    giver: 'guard-han',
    offer:
      '主公初来，这城看着齐整，其实九座殿各管一摊事。先替我走三处：户部看钱粮、市舶司看买卖、礼部看仪典——回来我再跟主公说门道。',
    remind: '三处牌坊都还空着。主公右键点地就走过去了，不必一步步挪。',
    report: '三处都去过了？那主公再抬头看这城，就不是一堆房子了。',
    done: '这便对了。往后主公进城，不必我引路。',
    reward: '门吏的腰牌 · 过正门不必再验',
    steps: [
      { kind: 'visit', x: 26.02, z: 62.82, radius: 11, label: '去户部牌坊下看一眼钱粮' },
      { kind: 'visit', x: -26.02, z: 62.82, radius: 11, label: '去市舶司牌坊看市面上的买卖' },
      { kind: 'visit', x: 26.02, z: -62.82, radius: 11, label: '去礼部牌坊看仪典名册' },
    ],
  },
  {
    id: 'shen-ledger-check',
    title: '账要看三处',
    giver: 'clerk-shen',
    offer:
      '主公来得巧。总账与分账对不上时，最忌坐在屋里算。去数枢殿看一眼总数，再去兵部牌坊核一遍军械开销，回来我就能断。',
    remind: '总账与军械两处，主公还差几步。数目不会自己走过来。',
    report: '总数与分项都看过了？那便好办了。',
    done: '账上的窟窿，原在这里。主公这双眼，比我的算盘快。',
    reward: '户部的一枚算珠 · 往后数目不对可以先问我',
    steps: [
      { kind: 'visit', x: 0, z: -2, radius: 14, label: '去数枢殿前看一遍总账' },
      { kind: 'visit', x: -26.02, z: -62.82, radius: 11, label: '去兵部牌坊核军械开销' },
    ],
  },
  {
    id: 'wei-three-gates',
    title: '三道门',
    giver: 'guard-wei',
    offer:
      '宵禁的事，纸上说不清。主公替我走一趟三道门——皇城、内城、外城，各站一站，看有无灯火不熄。',
    remind: '三道门还差着。皇城那道在御道尽北，外城那道最远。',
    report: '三道门都看了？好，今夜我这班就交卸了。',
    done: '有劳主公。城里的灯，今夜总算都是该亮的。',
    reward: '侍卫的巡令牌 · 夜里行走无人拦',
    steps: [
      { kind: 'visit', x: 0, z: 22, radius: 8, label: '巡到皇城门洞' },
      { kind: 'visit', x: 0, z: 48, radius: 8, label: '巡到内城门洞' },
      { kind: 'visit', x: 0, z: 88, radius: 9, label: '巡到外城门洞' },
    ],
  },
];

const SAVE_KEY = 'bww.quests.v1';
const SAVE_VERSION = 1;

export interface QuestProgress {
  state: QuestState;
  /** 与 def.steps 等长；true = 已达成 */
  steps: boolean[];
}

/** 推进事件：完成一步 / 全部走完（可以交差了） */
export interface QuestEvent {
  kind: 'step' | 'ready';
  quest: QuestDef;
  stepIndex: number;
  label: string;
}

/** HUD / 面板要的一条视图数据 */
export interface QuestView {
  quest: QuestDef;
  progress: QuestProgress;
  /** 下一个未完成的目标；全部完成（可交）时为 null */
  nextStep: QuestStep | null;
  done: number;
  total: number;
}

export interface QuestSystem {
  defs: QuestDef[];
  stateOf: (id: string) => QuestState;
  /** 某委托人身上还挂着的任务（可接 + 进行中 + 可交），已交付的不再列 */
  forGiver: (npcId: string) => QuestDef[];
  /** 进行中与可交的任务（HUD 用） */
  active: () => QuestView[];
  accept: (id: string) => void;
  complete: (id: string) => void;
  /** 每帧喂玩家位置：返回本帧新达成的事件（可能多步同时满足） */
  tick: (x: number, z: number) => QuestEvent[];
  /** 清空进度（调试 / 重置用） */
  reset: () => void;
  /** 供 e2e 断言的状态快照 */
  snapshot: () => Array<{
    id: string;
    title: string;
    giver: string;
    state: QuestState;
    steps: boolean[];
  }>;
}

const emptyProgress = (): QuestProgress => ({ state: 'available', steps: [] });

function loadProgress(defs: QuestDef[]): Map<string, QuestProgress> {
  const out = new Map<string, QuestProgress>();
  for (const def of defs) {
    out.set(def.id, { state: 'available', steps: new Array(def.steps.length).fill(false) });
  }
  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(SAVE_KEY);
  } catch {
    // 隐私模式 / 禁用存储：不存档也能玩，只是刷新后从头开始
    return out;
  }
  if (!raw) {
    return out;
  }
  try {
    const parsed = JSON.parse(raw) as {
      v?: number;
      quests?: Record<string, { state?: string; steps?: boolean[] }>;
    };
    if (parsed.v !== SAVE_VERSION || !parsed.quests) {
      return out;
    }
    for (const def of defs) {
      const saved = parsed.quests[def.id];
      if (!saved) continue;
      const state = saved.state;
      const valid: QuestState[] =
        state === 'active' || state === 'ready' || state === 'done' ? [state] : [];
      if (valid.length === 0) continue;
      // 步骤数组必须补齐到当前定义长度：任务改过步数时（旧档 2 步、新档 3 步）
      // 直接沿用旧数组会让 HUD 永远显示"2/3"，补 false 才是正确语义
      const steps = new Array(def.steps.length).fill(false);
      for (let i = 0; i < Math.min(steps.length, saved.steps?.length ?? 0); i += 1) {
        steps[i] = Boolean(saved.steps?.[i]);
      }
      const progress = out.get(def.id);
      if (progress) {
        progress.state = valid[0];
        progress.steps = steps;
      }
    }
    return out;
  } catch {
    return out;
  }
}

export function createQuestSystem(defs: QuestDef[] = QUESTS): QuestSystem {
  const byId = new Map(defs.map((d) => [d.id, d]));
  const progress = loadProgress(defs);

  const save = (): void => {
    const quests: Record<string, { state: QuestState; steps: boolean[] }> = {};
    for (const [id, p] of progress) {
      if (p.state === 'available') continue;
      quests[id] = { state: p.state, steps: p.steps };
    }
    try {
      window.localStorage.setItem(SAVE_KEY, JSON.stringify({ v: SAVE_VERSION, quests }));
    } catch {
      // 写不进去就算了：本局的进度仍在内存里
    }
  };

  const stateOf = (id: string): QuestState => progress.get(id)?.state ?? 'available';

  /** 内部：某任务的视图 */
  const view = (def: QuestDef): QuestView => {
    const p = progress.get(def.id) ?? emptyProgress();
    const nextIndex = p.steps.findIndex((done) => !done);
    return {
      quest: def,
      progress: p,
      nextStep: nextIndex >= 0 ? def.steps[nextIndex] : null,
      done: p.steps.filter(Boolean).length,
      total: def.steps.length,
    };
  };

  return {
    defs,
    stateOf,
    forGiver(npcId) {
      return defs.filter((def) => def.giver === npcId && stateOf(def.id) !== 'done');
    },
    active() {
      return defs
        .filter((def) => {
          const s = stateOf(def.id);
          return s === 'active' || s === 'ready';
        })
        .map(view);
    },
    accept(id) {
      const def = byId.get(id);
      if (!def) return;
      const p = progress.get(id);
      if (!p || p.state !== 'available') return;
      p.state = 'active';
      save();
    },
    complete(id) {
      const def = byId.get(id);
      if (!def) return;
      const p = progress.get(id);
      // 只有"全部走完"的才能交，否则回禀一次就把没走的路跳过了
      if (!p || p.state !== 'ready') return;
      p.state = 'done';
      save();
    },
    tick(x, z) {
      const events: QuestEvent[] = [];
      for (const def of defs) {
        const p = progress.get(def.id);
        if (!p || p.state !== 'active') continue;
        let changed = false;
        for (let i = 0; i < def.steps.length; i += 1) {
          if (p.steps[i]) continue;
          const step = def.steps[i];
          if (Math.hypot(x - step.x, z - step.z) > step.radius) continue;
          p.steps[i] = true;
          changed = true;
          events.push({ kind: 'step', quest: def, stepIndex: i, label: step.label });
        }
        if (changed && p.steps.every(Boolean)) {
          p.state = 'ready';
          events.push({
            kind: 'ready',
            quest: def,
            stepIndex: def.steps.length - 1,
            label: def.report,
          });
        }
      }
      if (events.length > 0) {
        save();
      }
      return events;
    },
    reset() {
      for (const def of defs) {
        progress.set(def.id, {
          state: 'available',
          steps: new Array(def.steps.length).fill(false),
        });
      }
      try {
        window.localStorage.removeItem(SAVE_KEY);
      } catch {
        // 同上：清不掉也不影响本局
      }
    },
    snapshot() {
      return defs.map((def) => {
        const p = progress.get(def.id) ?? emptyProgress();
        return {
          id: def.id,
          title: def.title,
          giver: def.giver,
          state: p.state,
          steps: [...p.steps],
        };
      });
    },
  };
}
