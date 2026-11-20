/**
 * 厅配置（S4/T4.6）：哪些殿走契约接口、走哪个路径、要什么权限码。
 *
 * <p>这份数组是"接入一个殿"的全部成本：加一行就能让某座殿改用
 * `/<服务>/overview`，不改渲染、不改 3D 场景、不改 city-layout。
 * 没登记在这里的殿继续用 S3 的 metrics 采集（历史口径），两条路并存、
 * 逐殿替换——这正是总纲里"一对多，先通一个再复制"的意思。</p>
 */

import type { PalaceSpec } from './layout';
import type { HallData } from './datahall';
import { createOverviewFeed, toHallData, type OverviewFeedOptions, type OverviewHallSpec } from './overview';

/**
 * 已接入契约的服务（标杆：户部 / ERP）。
 *
 * <p>路径与 2D 后台同前缀（REQ-ARCH-003：网关不剥前缀），权限码复用
 * `sys:erp:bi:cockpit:list`，不为 3D 单独发一套授权。</p>
 */
export const OVERVIEW_HALLS: OverviewHallSpec[] = [
  { nodeKey: 'hubu-hall', path: '/erp/overview', permission: 'sys:erp:bi:cockpit:list' },
];

/** 采集器选项：与 metrics 共用同一套令牌/权限来源 */
export interface HallOverviewOptions extends Omit<OverviewFeedOptions, 'halls' | 'onChange'> {
  /** 城内宫殿列表，用于取殿名与副标题兜底 */
  palaces: PalaceSpec[];
  /** 厅数据变化回调（Map 原地更新） */
  onChange: (halls: Map<string, HallData>) => void;
}

/** 采集器句柄（透出 refresh/start/stop，便于与进出厅调度对齐） */
export interface HallOverviewHandle {
  readonly halls: Map<string, HallData>;
  refresh: () => Promise<void>;
  start: () => void;
  stop: () => void;
  isRunning: () => boolean;
  dispose: () => void;
}

/**
 * 创建"契约概览 → 厅数据"的采集器。
 *
 * <p>空配置时返回 {@code null}：调用方不必为"一个殿都没接"写分支，
 * 也让 e2e 在没后端的环境里完全不发这路请求。</p>
 *
 * @param options 令牌、权限、宫殿列表与回调
 * @returns 采集器句柄；无配置时 {@code null}
 */
export function createHallOverview(options: HallOverviewOptions): HallOverviewHandle | null {
  if (OVERVIEW_HALLS.length === 0) {
    return null;
  }
  const palaceOf = (nodeKey: string): PalaceSpec | undefined =>
    options.palaces.find((palace) => palace.nodeKey === nodeKey);

  const halls = new Map<string, HallData>();
  const feed = createOverviewFeed({
    halls: OVERVIEW_HALLS,
    getToken: options.getToken,
    getPermissions: options.getPermissions,
    timeoutMillis: options.timeoutMillis,
    intervalMillis: options.intervalMillis,
    onChange: (results) => {
      results.forEach((result, nodeKey) => {
        const palace = palaceOf(nodeKey);
        halls.set(nodeKey, toHallData(result, palace?.name ?? nodeKey, palace?.sub ?? nodeKey));
      });
      options.onChange(halls);
    },
  });

  return {
    get halls() {
      return halls;
    },
    refresh: () => feed.refresh(),
    start: () => feed.start(),
    stop: () => feed.stop(),
    isRunning: () => feed.isRunning(),
    dispose: () => feed.dispose(),
  };
}
