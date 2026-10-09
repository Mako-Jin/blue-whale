import type { ClientLogItem, ClientLogReportBody, ClientLogReportResult } from 'common-api';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LogReporter } from '../src/reporter';
import type { LogReporterOptions } from '../src/reporter';

function item(index: number): ClientLogItem {
  return {
    level: 'info',
    namespace: 'app › test',
    timestamp: 1_700_000_000_000 + index,
    message: `第 ${index} 条`,
  };
}

function ok(accepted: number): ClientLogReportResult {
  return { accepted, rejected: 0 };
}

/** 轮询等待条件成立。上报是"尽力而为"的异步过程，没有可 await 的句柄，只能等状态 */
async function until(condition: () => boolean, timeoutMs = 500): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (!condition()) {
    if (Date.now() > deadline) throw new Error('等待超时');
    await new Promise((resolve) => setTimeout(resolve, 1));
  }
}

function createReporter(overrides: Partial<LogReporterOptions> = {}) {
  // 默认替身也要带上真实签名：否则 mockImplementation 传参时会因"目标签名不接受参数"而类型不匹配
  const send = vi.fn(async (_body: ClientLogReportBody): Promise<ClientLogReportResult> => ok(0));
  const reporter = new LogReporter({
    app: 'platform-log-console',
    release: '0.1.0',
    sessionId: 's-test',
    send,
    batchSize: 2,
    flushIntervalMs: 20,
    ...overrides,
  });
  return { reporter, send };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('LogReporter', () => {
  it('未 start 时不缓冲：避免页面还没打开就开始攒日志', () => {
    const { reporter } = createReporter();
    reporter.push(item(1));
    expect(reporter.getState().pending).toBe(0);
  });

  it('攒够一批立即上报，不等到定时器', async () => {
    const { reporter, send } = createReporter({ flushIntervalMs: 10_000 });
    send.mockImplementation(async (body) => ok(body.logs.length));
    reporter.start();

    reporter.push(item(1));
    expect(send).not.toHaveBeenCalled();

    reporter.push(item(2));
    await until(() => send.mock.calls.length === 1);

    expect(reporter.getState()).toMatchObject({ sent: 2, pending: 0, failedBatches: 0 });
    reporter.stop();
  });

  it('到时间未攒满也会上报', async () => {
    const { reporter, send } = createReporter({ batchSize: 100, flushIntervalMs: 15 });
    send.mockImplementation(async (body) => ok(body.logs.length));
    reporter.start();

    reporter.push(item(1));
    await until(() => send.mock.calls.length === 1);
    expect(reporter.getState().sent).toBe(1);

    reporter.stop();
  });

  it('缓冲有上限，超出丢弃最旧的并计入 dropped', () => {
    const { reporter } = createReporter({ batchSize: 100, maxBuffer: 3 });
    reporter.start();

    for (let i = 1; i <= 5; i += 1) reporter.push(item(i));

    const state = reporter.getState();
    expect(state.pending).toBe(3);
    expect(state.dropped).toBe(2);

    reporter.stop();
  });

  it('上报失败不抛异常，而是记状态并指数退避', async () => {
    const { reporter, send } = createReporter({
      batchSize: 100,
      flushIntervalMs: 1000,
      maxBackoffMs: 5000,
    });
    send.mockRejectedValue(new Error('后端不可达'));
    reporter.start();
    reporter.push(item(1));

    // 第一次失败
    await expect(reporter.flush()).resolves.toBeUndefined();
    expect(reporter.getState()).toMatchObject({
      failedBatches: 1,
      lastError: '后端不可达',
      backoffMs: 2000,
    });

    // 第二次失败：退避翻倍
    await reporter.flush();
    expect(reporter.getState().backoffMs).toBe(4000);

    // 第三次失败：退避封顶，不会无限增长
    await reporter.flush();
    expect(reporter.getState().backoffMs).toBe(5000);

    // 失败的那批要放回缓冲重试，不能凭空消失
    expect(reporter.getState().pending).toBe(1);

    reporter.stop();
  });

  it('恢复成功后清空退避', async () => {
    const { reporter, send } = createReporter({ batchSize: 100, flushIntervalMs: 1000 });
    send.mockRejectedValueOnce(new Error('抖动'));
    reporter.start();
    reporter.push(item(1));

    await reporter.flush();
    expect(reporter.getState().backoffMs).toBeGreaterThan(0);

    send.mockImplementation(async (body) => ok(body.logs.length));
    await reporter.flush();

    expect(reporter.getState()).toMatchObject({ sent: 1, backoffMs: 0, lastError: undefined });
    reporter.stop();
  });

  it('被拒条目计入 rejected，与整批失败分开统计', async () => {
    const { reporter, send } = createReporter({ batchSize: 100 });
    send.mockImplementation(async () => ({ accepted: 1, rejected: 1 }));
    reporter.start();
    reporter.push(item(1));
    reporter.push(item(2));

    await reporter.flush();

    expect(reporter.getState()).toMatchObject({ sent: 1, rejected: 1, failedBatches: 0 });
    reporter.stop();
  });

  it('stop 会补发剩余日志，不能因为关闭开关就丢掉', async () => {
    const { reporter, send } = createReporter({ batchSize: 100, flushIntervalMs: 10_000 });
    send.mockImplementation(async (body) => ok(body.logs.length));
    reporter.start();
    reporter.push(item(1));
    expect(reporter.getState().pending).toBe(1);

    reporter.stop();
    await until(() => reporter.getState().sent === 1);
    expect(reporter.getState().pending).toBe(0);
  });
});
