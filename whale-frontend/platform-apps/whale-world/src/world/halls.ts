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
 * 已接入契约的服务（标杆：户部 / ERP；S5：数枢殿总控、枢密殿、度支殿、藏书殿、
 * 时辰殿、政事殿、考功殿、通政殿、起居殿；S6：工部坊 / MES、吏部坊 / OA、礼部坊 / CRM、
 * 兵部坊 / IoT、杏坛坊 / LMS）。
 *
 * <p>路径与 2D 后台同前缀（REQ-ARCH-003：网关不剥前缀），权限码复用 2D 同一码，
 * 不为 3D 单独发一套授权。</p>
 */
export const OVERVIEW_HALLS: OverviewHallSpec[] = [
  { nodeKey: 'hubu-hall', path: '/erp/overview', permission: 'sys:erp:bi:cockpit:list' },
  /* 工部坊（S6/T6.4）：今日产量、达成率、未关闭异常、故障设备。
     权限码复用 2D 综合看板的 `sys:mes:dashboard:view`——能在后台看车间看板的人，
     才该在城里看到"今天这条线到底跑成什么样"。四个域（生产/质量/设备/人员）
     由 MES 内部综合看板聚合，城里不重新拼口径。 */
  { nodeKey: 'gongbu-hall', path: '/mes/overview', permission: 'sys:mes:dashboard:view' },
  /* 吏部坊（S6/T6.7）：在册人数、今日缺卡/迟到、本月考勤异常。
     权限码复用 2D 考勤日报页的 `sys:oa:attendance:daily:list`——能在后台看考勤日报的人，
     才该在城里看到"今天谁没来"。人事档案（在职人数）是另一个模块的口径，
     provider 会在缺 `sys:oa:hr:profile:list` 时把那一块牌整块裁掉，不靠前端隐藏。 */
  { nodeKey: 'libu-hall', path: '/oa/overview', permission: 'sys:oa:attendance:daily:list' },
  /* 礼部坊（S6/T6.10）：本月赢单、目标达成、加权管道、风险商机。
     权限码复用 2D 销售看板的 `sys:crm:analytics:dashboard`——能在后台看销售看板的人，
     才该在城里看到"离目标还差多少"。**这一坊的数是按人算的**：CRM 的
     `CrmActor#allDataScope()` 按角色判（管理员看全租户，其余只看本人），
     所以概览上下文除了权限码还必须带角色码，否则管理员站在坊前只会看到一片 0。 */
  { nodeKey: 'libu-ritual-hall', path: '/crm/overview', permission: 'sys:crm:analytics:dashboard' },
  /* 兵部坊（S6/T6.13）：设备总数、在线率、近 24h 上报、待回指令。
     权限码复用 2D 设备管理的 `sys:iot:device`——能管设备的人，才该在城里看到
     "哪些哨探哑了"。**这一坊不接告警中心**：告警（whale-alert）是户部那套经营预警的
     口径，兵部看的是设备心跳与遥测断流，两条链路搅在一起会让同一件事被两座坊各报一次。 */
  { nodeKey: 'bingbu-hall', path: '/iot/overview', permission: 'sys:iot:device' },
  /* 杏坛坊（S6/T6.16）：在册学员、学习覆盖、学习完成、考试通过。
     权限码复用 2D 学习看板的 `sys:lms:analytics:view`——能在后台看学习看板的人，
     才该在城里看到"这一城的人有没有在长本事"。**这一屏的两个比率分母不同**：
     覆盖率对在职人数、完成率对已学人数，由 provider 在牌面提示语里写明，
     前端不做二次换算。 */
  { nodeKey: 'xingtan-hall', path: '/lms/overview', permission: 'sys:lms:analytics:view' },
  /* 将作坊（S6/T6.19）：在册物料、生效 BOM、未关闭变更、图纸文档。
     权限码复用 2D 物料台账的 `sys:pdm:part:list`——能在后台看物料的人，
     才该在城里看到"这一城要造什么、图纸上改到第几版"。四本账的"未关闭"判定
     （驳回与取消不算欠账）在后端统计服务一处定义，前端不做二次换算。 */
  { nodeKey: 'jiangzuo-bureau', path: '/pdm/overview', permission: 'sys:pdm:part:list' },
  /* 营造坊（S6/T6.22）：在建项目、活跃迭代、未完成工作项、逾期未完成。
     权限码复用 2D 项目台账的 `sys:pm:project:list`——能在后台看项目的人，
     才该在城里看到"工程推进到哪了"。未完成的判定跟状态分类
     （TODO / IN_PROGRESS / DONE）走，由 provider 在口径说明里写明，
     前端不做二次换算。 */
  { nodeKey: 'yingzao-bureau', path: '/pm/overview', permission: 'sys:pm:project:list' },
  { nodeKey: 'mengfu-hall', path: '/contract/overview', permission: 'sys:contract:list' },
  { nodeKey: 'xuanfu-court', path: '/party/overview', permission: 'sys:party:metric:list' },
  /* 数枢殿总控（S5/T5.1）：服务端把在册各殿的概览汇成一屏。
     **不设权限码**——总控的可见范围就是"你在各殿能看到的范围"，
     缺凭据的殿在总控里显示为「无凭据」行，而不是整块 403。
     另给一个权限码只会让"没权限"和"没这条接口"变成同一个 403。 */
  { nodeKey: 'shushu-hall', path: '/monitor/overview' },
  /* 枢密殿（S5/T5.2）：账号与会话。权限码与 2D 登录历史页同一码，
     所以"在后台能看登录历史"的人在城里也能看——两处口径不会分叉。 */
  { nodeKey: 'shumi-hall', path: '/auth/overview', permission: 'sys:login:history:list' },
  /* 度支殿（S5/T5.3）：收款、待支付、退款。权限码复用 2D 支付单管理的
     `sys:pay:list`——能在后台看支付单的人，就能在城里看这一屏。 */
  { nodeKey: 'duzhi-hall', path: '/pay/overview', permission: 'sys:pay:list' },
  /* 藏书殿（S5/T5.4）：文件总量、占用空间、回收站。权限码复用 2D 文件列表的
     `sys:file:list`；非管理员本来就只看得见自己上传的文件，3D 沿用同一口子，
     不在这里额外放宽。 */
  { nodeKey: 'cangshu-hall', path: '/file/overview', permission: 'sys:file:list' },
  /* 时辰殿（S5/T5.5）：任务是否还在按点跑、今天跑了多少次、失败几次。
     权限码复用 2D 定时任务页的 `sys:job:list`——能在后台启停任务的人，
     才该在城里看到"哪个任务静默了"。 */
  { nodeKey: 'shichen-hall', path: '/job/overview', permission: 'sys:job:list' },
  /* 政事殿（S5/T5.6）：在途审批、今日办结、待办积压、卡住的流程。
     权限码复用 2D 流程实例页的 `sys:workflow:instance:list`——能看到全租户实例的人，
     才该在城里看到"哪条审批链断了"。 */
  { nodeKey: 'zhengshi-hall', path: '/workflow/overview', permission: 'sys:workflow:instance:list' },
  /* 考功殿（S5/T5.7）：表单还在被填吗、今天录了几条、多少条卡在审批、
     主表与物理表有没有对不上的数据。权限码复用 2D 表单定义页的 `sys:form:list`——
     能维护表单定义的人，才该在城里看到"哪张表没人填了、哪批数据对不上"。 */
  { nodeKey: 'kaogong-hall', path: '/form/overview', permission: 'sys:form:list' },
  /* 通政殿（S5/T5.8）：今天送出多少封信、送得到吗、有没有卡在半路、有没有被退回。
     权限码复用 2D 消息记录页的 `sys:message:record:list`——能在后台看投递记录的人，
     才该在城里看到"哪些消息根本没发出去"。 */
  { nodeKey: 'tongzheng-hall', path: '/message/overview', permission: 'sys:message:record:list' },
  /* 起居殿（S5/T5.9）：今日留痕多少、有没有失败，以及两件"安静的坏事"——
     审计留痕断流（最近一条距今多久）与归档作业陈旧。权限码复用 2D 审计日志页的
     `sys:audit:log:list`——只有审计管理员该在城里看到"哪段时间没留下证据"。 */
  { nodeKey: 'qiju-hall', path: '/audit/overview', permission: 'sys:audit:log:list' },
];

/** 采集器选项：与 metrics 共用同一套令牌/权限来源 */
export interface HallOverviewOptions extends Omit<OverviewFeedOptions, 'halls' | 'onChange'> {
  /** 城内宫殿列表，用于取殿名与副标题兜底 */
  palaces: PalaceSpec[];
  /**
   * 主殿（数枢殿）：它不在 {@link PalaceSpec} 列表里（那是十二坊），
   * 但它的厅同样走契约接口，名字与副标题得单独给，否则厅匾上会显示节点 key。
   */
  mainHall?: { nodeKey: string; name: string; sub: string };
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
  const labelOf = (nodeKey: string): { name: string; sub: string } => {
    const palace = options.palaces.find((item) => item.nodeKey === nodeKey);
    if (palace) {
      return { name: palace.name, sub: palace.sub };
    }
    if (options.mainHall && options.mainHall.nodeKey === nodeKey) {
      return { name: options.mainHall.name, sub: options.mainHall.sub };
    }
    return { name: nodeKey, sub: nodeKey };
  };

  const halls = new Map<string, HallData>();
  const feed = createOverviewFeed({
    halls: OVERVIEW_HALLS,
    getToken: options.getToken,
    getPermissions: options.getPermissions,
    timeoutMillis: options.timeoutMillis,
    intervalMillis: options.intervalMillis,
    onChange: (results) => {
      results.forEach((result, nodeKey) => {
        const label = labelOf(nodeKey);
        halls.set(nodeKey, toHallData(result, label.name, label.sub));
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
