import { BatchChannel } from 'common-tools/report';
import type { BatchChannelOptions, BatchChannelState } from 'common-tools/report';
import type { ClientLogItem, ClientLogReportBody, ClientLogReportResult } from 'common-api';

/**
 * 日志上报器。
 *
 * <p><b>它现在只是一个薄封装。</b>批量 / 有界 / 退避 / 卸载兜底这套机制已经下沉到
 * `common-tools/report` 的 {@link BatchChannel} —— 行为事件管道需要逐条相同的特性，
 * 各写一份的结果是两份实现立刻开始漂移。
 *
 * <p>本文件剩下的唯一职责，是把"日志"这件事告诉通用通道：请求体长什么样
 * （`app` / `release` / `sessionId` / `logs` 信封），以及怎么从返回值里读出计数。
 * 这正是通用通道刻意不接受的那部分 —— 它不认识日志，也不认识事件。
 */

/** 上报通道的对外状态。与通用通道一致，沿用旧名以免调用方到处改 */
export type ReporterState = BatchChannelState;

export interface LogReporterOptions extends Omit<
  BatchChannelOptions<ClientLogItem, ClientLogReportBody, ClientLogReportResult>,
  'buildBody' | 'send' | 'resolveCounts'
> {
  app: string;
  release: string;
  sessionId: string;
  send: (body: ClientLogReportBody) => Promise<ClientLogReportResult>;
}

export class LogReporter extends BatchChannel<
  ClientLogItem,
  ClientLogReportBody,
  ClientLogReportResult
> {
  constructor(options: LogReporterOptions) {
    const { app, release, sessionId, send, ...rest } = options;
    super({
      ...rest,
      // 批级信封：同一批日志必然同源，逐条重复携带 app / sessionId 是纯粹的浪费
      buildBody: (logs) => ({ app, release, sessionId, logs }),
      send,
      // 后端会逐条校验并回报 accepted / rejected，据此把"部分接受"和"整批失败"分开统计
      resolveCounts: (result, batch) => ({
        accepted: result?.accepted ?? batch.length,
        rejected: result?.rejected ?? 0,
      }),
    });
  }
}
