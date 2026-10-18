/**
 * Wave 5.14 无框架冒烟脚本（针对构建产物 dist/index.mjs 断言）。
 *
 * <p>不引入 vitest / jest 等测试基建；运行：`node scripts/smoke.mjs`（需先 build）。</p>
 */

import {
  chunkItems,
  initLogSdk,
  LogChannel,
  maskSensitive,
  maskText,
  maskUrl,
  shouldSample,
} from '../dist/index.mjs';

let passed = 0;

/**
 * 断言相等
 * @param {unknown} actual 实际值
 * @param {unknown} expected 期望值
 * @param {string} label 用例名
 */
function equal(actual, expected, label) {
  if (actual !== expected) {
    throw new Error(
      `[FAIL] ${label}：期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actual)}`,
    );
  }
  passed += 1;
}

/** 构造单通道配置（测试用，intervalMs=0 关闭定时器，手动 flush） */
function createChannel(transport, overrides = {}) {
  return new LogChannel({
    endpoint: '/audit/log/test/batch',
    size: 1000,
    intervalMs: 0,
    maxRetries: 2,
    backoffBaseMs: 1,
    sampleRate: 1,
    transport,
    buildRequest: (items) => ({ traceId: 't', userId: null, clientId: 'c', pageUrl: null, items }),
    onError: () => undefined,
    ...overrides,
  });
}

async function run() {
  // 1) 结构掩码
  const masked = maskSensitive({
    password: 'p@ss',
    token: 'abc',
    mobile: '13812345678',
    idCard: '11010119900307123X',
    bankCard: '6222021234567890123',
    email: 'alice@bluewhale.com',
    name: 'ok',
  });
  equal(masked.password, '******', '掩码-密码');
  equal(masked.token, '***', '掩码-令牌');
  equal(masked.mobile, '138****5678', '掩码-手机号');
  equal(masked.idCard, '110101********123X', '掩码-身份证');
  equal(masked.bankCard, '**** **** **** 0123', '掩码-银行卡');
  equal(masked.email, 'a***@bluewhale.com', '掩码-邮箱');
  equal(masked.name, 'ok', '掩码-普通字段保留');

  // 2) 嵌套 + 附加键
  const nested = maskSensitive(
    { user: { password: 'x' }, list: [{ secretValue: 'y' }] },
    { sensitiveKeys: ['secret_value'] },
  );
  equal(nested.user.password, '******', '掩码-嵌套');
  equal(nested.list[0].secretValue, '******', '掩码-附加键');

  // 3) 自由文本掩码
  equal(maskText('token=abc123'), 'token=******', '文本掩码-键值对');
  equal(maskText('Authorization: Bearer aa.bb.cc'), 'Authorization: Bearer ***', '文本掩码-Bearer');

  // 4) URL query 掩码
  equal(maskUrl('http://x/y?token=abc&name=ok'), 'http://x/y?token=***&name=ok', 'URL掩码-令牌');
  equal(maskUrl('http://x/y?phone=13812345678'), 'http://x/y?phone=138****5678', 'URL掩码-手机号');

  // 5) 分片 ≤50
  const many = Array.from({ length: 120 }, (_, i) => ({ timestamp: i }));
  const chunks = chunkItems(many, 50);
  equal(chunks.length, 3, '分片-数量');
  equal(chunks[0].length, 50, '分片-首片');
  equal(chunks[2].length, 20, '分片-末片');
  equal(
    chunks.every((c) => c.length <= 50),
    true,
    '分片-上限',
  );

  // 6) 采样
  equal(shouldSample(1), true, '采样-全采');
  equal(shouldSample(0), false, '采样-全不采');

  // 7) 去重 + 单次上报
  const sent = [];
  const okTransport = async (url, body) => {
    sent.push(body);
    return true;
  };
  const dedupeChannel = createChannel(okTransport);
  const dupItem = { logLevel: 'INFO', message: 'same', timestamp: 1 };
  dedupeChannel.push(dupItem);
  dedupeChannel.push({ ...dupItem, timestamp: 2 });
  dedupeChannel.push({ logLevel: 'INFO', message: 'other', timestamp: 3 });
  await dedupeChannel.flush();
  dedupeChannel.destroy();
  equal(sent.length, 1, '去重-上报次数');
  equal(sent[0].items.length, 2, '去重-条目数');

  // 8) 满批分片（60 条 → 50 + 10）
  sent.length = 0;
  const chunkChannel = createChannel(okTransport);
  for (let i = 0; i < 60; i += 1) {
    chunkChannel.push({ logLevel: 'INFO', message: `m${i}`, timestamp: i });
  }
  await chunkChannel.flush();
  chunkChannel.destroy();
  equal(sent.length, 2, '满批-请求数');
  equal(sent[0].items.length, 50, '满批-首片');
  equal(sent[1].items.length, 10, '满批-次片');

  // 9) 指数退避重试（首次失败，第二次成功）
  let attempts = 0;
  const flakyTransport = async () => {
    attempts += 1;
    return attempts >= 2;
  };
  const retryChannel = createChannel(flakyTransport);
  retryChannel.push({ logLevel: 'ERROR', message: 'retry-me', timestamp: 1 });
  await retryChannel.flush();
  retryChannel.destroy();
  equal(attempts, 2, '退避-重试次数');

  // 10) initLogSdk：disabled 为空操作
  const noop = initLogSdk({ enabled: false });
  equal(noop.config.enabled, false, 'initLogSdk-禁用开关');
  noop.track('x');
  noop.log('INFO', 'y');
  await noop.flush();
  noop.destroy();
  equal(true, true, 'initLogSdk-禁用不抛错');

  // 11) initLogSdk：console 采集 + 掩码 + 原输出保留
  const captured = [];
  const sdk = initLogSdk({
    console: { enabled: true, levels: ['DEBUG'] },
    behavior: { autoPageView: false, autoClick: false, autoSubmit: false },
    batch: { intervalMs: 0, size: 1000 },
    transport: async (url, body) => {
      captured.push(body);
      return true;
    },
  });
  console.debug('hello token=abc123');
  await sdk.flush();
  sdk.destroy();
  equal(captured.length, 1, 'sdk-console上报次数');
  equal(captured[0].items[0].logLevel, 'DEBUG', 'sdk-console级别');
  equal(captured[0].items[0].message, 'hello token=******', 'sdk-console已掩码');

  console.log(`smoke passed: ${passed} assertions`);
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
