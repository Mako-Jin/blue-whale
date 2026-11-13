/**
 * 小鲸宝带路导览 —— 新玩家第一次进城，别让他自己在环路上乱转。
 *
 * <p>做成"纯状态机 + 一份站点表"，不碰 three.js、不碰 DOM：路线怎么走、什么时候算到站、
 * 说完这句话下一站是哪，全在这里决定；{@code city.ts} 只负责把鲸鱼飞过去、把台词说出来。
 * 这样这套逻辑可以在没有任何渲染的情况下被推演，也不用为了改一句讲解词去动 2000 行的场景文件。</p>
 *
 * <p>三个体验判断：</p>
 * <ul>
 *   <li><b>先绕八方宫署，最后进数枢殿</b>：八殿是并列的业务入口，按环形顺序走一遍最省路；
 *       数枢殿是"看完所有数据再回到总账"的收尾，放最后才有意义。</li>
 *   <li><b>到站才算数</b>：讲解的不是"鲸鱼飞到哪儿"，而是"主公走到哪儿"。鲸鱼先飞过去等，
 *       玩家没跟上就一直等——导览的价值在于人真的站在殿前看那块牌坊。</li>
 *   <li><b>玩家掉队就把鲸鱼叫回来</b>：鲸鱼卡在 70 米外自己等着，等于把人丢了；
 *       所以超出这个距离就先回到玩家身边，导览状态不前进也不丢失。</li>
 * </ul>
 */

/** 导览的一站 */
export interface GuideStop {
  /** 与 city-layout 的 nodeKey 一致，方便和牌坊、传送门对上 */
  nodeKey: string;
  /** 殿名（HUD 与台词都用它） */
  name: string;
  /** 讲解词：一句话说清这座殿管什么 */
  line: string;
  x: number;
  z: number;
}

/** 到站半径（米）：走到这个范围内就算"到了" */
const ARRIVE_RADIUS = 13;

/** 掉队阈值（米）：玩家离鲸鱼超过这个距离，鲸鱼先回来 */
const MAX_DRIFT = 70;

/** 鲸鱼回到玩家身边时停在玩家前方多远 */
const REGROUP_GAP = 7;

/** 导览进度（供 HUD 与 e2e 断言） */
export interface GuideStatus {
  active: boolean;
  /** 当前第几站（从 0 开始；done 后等于 total） */
  index: number;
  total: number;
  /** 当前站名；未开始时为空串 */
  name: string;
  /** 当前站讲解词 */
  line: string;
  /** 当前目标坐标；未开始时为玩家自身坐标 */
  targetX: number;
  targetZ: number;
  /** 是否走完 */
  done: boolean;
}

/** 每帧推演的结果 */
export interface GuideTick {
  /** 鲸鱼这一帧该飞向哪里；null 表示没有导览（交回跟随逻辑） */
  target: { x: number; z: number } | null;
  /** 本帧产生的事件：到站 / 走完。同一帧最多一个 */
  signal: null | { kind: 'arrive' | 'done'; stop: GuideStop; index: number };
  status: GuideStatus;
}

export interface GuideHandle {
  /** 开始导览，返回开场白；重复调用不重来 */
  start: () => string;
  /** 中止导览（保留进度以便下次重来） */
  stop: () => void;
  /** 是否导览中 */
  isActive: () => boolean;
  /**
   * 推演一帧。
   *
   * @param playerX 玩家 x
   * @param playerZ 玩家 z
   * @param whaleX  鲸鱼当前 x（判掉队用）
   * @param whaleZ  鲸鱼当前 z
   */
  update: (playerX: number, playerZ: number, whaleX: number, whaleZ: number) => GuideTick;
  /** 只读进度，不推进 */
  status: () => GuideStatus;
}

/**
 * 创建导览控制器。
 *
 * @param stops 站点表（顺序即路线顺序）
 */
export function createGuide(stops: GuideStop[]): GuideHandle {
  let index = 0;
  let active = false;
  let done = false;

  const current = (): GuideStop | null => stops[index] ?? null;

  const status = (): GuideStatus => {
    const stop = active ? current() : null;
    return {
      active,
      index,
      total: stops.length,
      name: stop?.name ?? '',
      line: stop?.line ?? '',
      targetX: stop?.x ?? 0,
      targetZ: stop?.z ?? 0,
      done,
    };
  };

  return {
    start() {
      if (active) {
        return current()?.line ?? '';
      }
      index = 0;
      done = false;
      active = stops.length > 0;
      return active ? `主公随我来。先绕八方宫署看一圈，最后进数枢殿看全城的账。` : '';
    },

    stop() {
      active = false;
    },

    isActive: () => active,

    update(playerX, playerZ, whaleX, whaleZ) {
      if (!active) {
        return { target: null, signal: null, status: status() };
      }

      const stop = current();
      if (!stop) {
        active = false;
        done = true;
        return { target: null, signal: null, status: status() };
      }

      // 到站判定：以玩家位置为准，而不是鲸鱼飞到没有
      const reach = Math.hypot(playerX - stop.x, playerZ - stop.z);
      if (reach <= ARRIVE_RADIUS) {
        const arrivedIndex = index;
        index += 1;
        const finished = index >= stops.length;
        if (finished) {
          active = false;
          done = true;
        }
        return {
          target: finished ? null : { x: stops[index].x, z: stops[index].z },
          signal: {
            kind: finished ? 'done' : 'arrive',
            stop,
            index: arrivedIndex,
          },
          status: status(),
        };
      }

      // 掉队就把鲸鱼叫回来：先站在玩家前方，等玩家自己走过去
      const drift = Math.hypot(playerX - whaleX, playerZ - whaleZ);
      if (drift > MAX_DRIFT) {
        const towards = Math.atan2(stop.x - playerX, stop.z - playerZ);
        return {
          target: {
            x: playerX + Math.sin(towards) * REGROUP_GAP,
            z: playerZ + Math.cos(towards) * REGROUP_GAP,
          },
          signal: null,
          status: status(),
        };
      }

      return { target: { x: stop.x, z: stop.z }, signal: null, status: status() };
    },

    status,
  };
}

/**
 * 八方宫署的讲解词，按 nodeKey 索引。
 *
 * <p>每条都对齐殿里真实在跑的东西（对应牌坊上的取数探针），
 * 不写"宏伟壮丽"这类放在哪座殿都成立、也就等于什么都没说的话。</p>
 */
export const GUIDE_LINES: Record<string, string> = {
  'hubu-hall': '这里是户部，管家底：营收、订单、库存与预警，都从这儿出。',
  'gongbu-hall': '工部管营造：产线达成率、故障设备、生产例外，都记在工部的册子上。',
  'libu-hall': '吏部管人与公文：待办、通告、会议，件件都要过吏部。',
  'libu-ritual-hall': '礼部管宾朋：新客、商机、目标达成，迎来送往都记在这儿。',
  'bingbu-hall': '兵部管哨探：全城的告警与设备心跳，兵部最先知道。',
  'hanlin-academy': '翰林院是典籍库：平台的文档与知识，都收在这一阁里。',
  'xingtan-hall': '杏坛讲学：学员、课程、完成率，学得怎么样一目了然。',
  'shibosi-hall': '市舶司管买卖：订单、成交额、商城货品，都从这道门进出。',
  'shushu-hall': '到数枢殿了。八方数据最后都汇到这儿——主公以后就在这儿看全城。',
};

/**
 * 兜底讲解词：新加宫殿但还没写讲解时不要紧，别让导览卡在一句空话上。
 *
 * @param name 殿名
 * @param sub  副标题（如「ERP · 企业资源」）
 */
export function fallbackLine(name: string, sub: string): string {
  const brief = sub.split('·').pop()?.trim();
  return brief ? `这里是${name}，管${brief}。` : `这里是${name}。`;
}
