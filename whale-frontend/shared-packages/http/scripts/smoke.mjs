// 冒烟测试：直接运行构建产物 dist/index.mjs，mock 全局 fetch 覆盖核心链路
//
// 运行：pnpm --filter @bluewhale/http build && node scripts/smoke.mjs

// document 等浏览器/Node 全局已由 whale-frontend/eslint.config.js 统一声明，
// 此处不再重复写 /* global document */（否则触发 no-redeclare）。

import assert from 'node:assert/strict';
import { createHttpClient } from '../dist/index.mjs';

let handler = async () => {
  throw new Error('未设置 fetch 处理器');
};
const calls = [];

globalThis.fetch = async (url, init = {}) => {
  const record = { url: String(url), init };
  calls.push(record);
  return handler(record);
};

// Node 环境无 document：提供最小 stub 供 CSRF Cookie 读取（用例 2 覆盖）
globalThis.document = { cookie: '' };

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function headerOf(init, name) {
  return new Headers(init.headers).get(name);
}

function assertTraceId(record) {
  const traceId = headerOf(record.init, 'X-Trace-Id');
  assert.match(traceId, /^[0-9a-f]{32}$/, `X-Trace-Id 应为 32 位十六进制，实际：${traceId}`);
}

// 1. 信封解包 + Bearer 注入 + 链路 ID
{
  const client = createHttpClient({ baseUrl: 'https://api.test', getToken: () => 'token-1' });
  calls.length = 0;
  handler = async () => jsonResponse({ code: 0, message: 'ok', data: { id: 7 } });
  const data = await client.get('/user/v1/7');
  assert.deepEqual(data, { id: 7 });
  assert.equal(calls[0].url, 'https://api.test/user/v1/7');
  assert.equal(headerOf(calls[0].init, 'Authorization'), 'Bearer token-1');
  assertTraceId(calls[0]);
}

// 2. 两个并发 401 只触发一次 refresh（Cookie 静默续期），且都重放成功
{
  let refreshCount = 0;
  let currentToken = 'expired-token';
  let savedLogin = null;
  const client = createHttpClient({
    baseUrl: '/api',
    getToken: () => currentToken,
    saveLogin: (data) => {
      savedLogin = data;
      currentToken = data.accessToken;
    },
    clearAuth: () => {
      throw new Error('刷新成功时不应清理登录态');
    },
  });
  calls.length = 0;
  // 模拟服务端下发的非 HttpOnly CSRF Cookie（HttpOnly 刷新 Cookie 浏览器自动携带，前端不可读）
  document.cookie = 'whale_csrf=csrf-token-1';
  handler = async ({ url, init }) => {
    if (url === '/api/auth/refresh') {
      refreshCount += 1;
      // 让两个 401 请求都挂到同一次刷新上，验证单飞
      await new Promise((resolve) => setTimeout(resolve, 10));
      return jsonResponse({
        code: 0,
        message: 'ok',
        data: { accessToken: 'new-token' },
      });
    }
    const auth = headerOf(init, 'Authorization');
    if (auth === 'Bearer expired-token') {
      return jsonResponse({ code: 401, message: 'expired', data: null }, 401);
    }
    return jsonResponse({ code: 0, message: 'ok', data: { ok: url } });
  };
  const [first, second] = await Promise.all([client.get('/x'), client.get('/y')]);
  assert.equal(refreshCount, 1, '并发 401 应只刷新一次');
  assert.deepEqual(first, { ok: '/api/x' });
  assert.deepEqual(second, { ok: '/api/y' });
  assert.equal(savedLogin?.accessToken, 'new-token');
  assert.equal(currentToken, 'new-token');
  const refreshCall = calls.find((item) => item.url === '/api/auth/refresh');
  assertTraceId(refreshCall);
  assert.equal(headerOf(refreshCall.init, 'X-CSRF-Token'), 'csrf-token-1', '刷新应回传 CSRF 头');
  assert.equal(refreshCall.init.credentials, 'include', '刷新应携带 Cookie');
  assert.equal(refreshCall.init.body, undefined, '刷新不应携带 refreshToken 请求体');
}

// 3. 业务码非 0 抛 ApiError
{
  const client = createHttpClient({ baseUrl: '' });
  handler = async () => jsonResponse({ code: 5001, message: '业务失败', data: null });
  await assert.rejects(
    () => client.get('/bad'),
    (error) => error.name === 'ApiError' && error.code === 5001 && error.message === '业务失败',
  );
}

// 4. downloadBlob 解析 Content-Disposition 文件名
{
  const client = createHttpClient({ baseUrl: '', getToken: () => 'token-d' });
  calls.length = 0;
  handler = async () =>
    new Response(new Blob(['a,b\n1,2']), {
      status: 200,
      // 中文名使用 URL 编码，验证 decodeURIComponent 分支
      headers: { 'Content-Disposition': 'attachment; filename="%E5%AF%BC%E5%87%BA.csv"' },
    });
  const downloaded = await client.downloadBlob('/export', { type: 'x' });
  assert.equal(downloaded.filename, '导出.csv');
  assert.ok(downloaded.blob instanceof Blob);
  assert.equal(calls[0].url, '/export?type=x');
  assert.equal(headerOf(calls[0].init, 'Authorization'), 'Bearer token-d');
  assertTraceId(calls[0]);
}

// 5. streamSse 正确分发多帧 event/data
{
  const client = createHttpClient({ baseUrl: '', getToken: () => 'token-s' });
  calls.length = 0;
  handler = async () => {
    const frames = [
      'event: meta\ndata: {"id":1}\n\n',
      'event: delta\ndata: {"text":"a"}\n\n',
      'data: plain-text\n\n',
      'event: done\ndata: {"ok":true}\n\n',
    ];
    const encoder = new TextEncoder();
    return new Response(
      new ReadableStream({
        start(controller) {
          frames.forEach((frame) => controller.enqueue(encoder.encode(frame)));
          controller.close();
        },
      }),
      { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
    );
  };
  const events = [];
  await client.streamSse('/agent/chat', { question: 'hi' }, (event, data) => {
    events.push({ event, data });
  });
  assert.deepEqual(events, [
    { event: 'meta', data: { id: 1 } },
    { event: 'delta', data: { text: 'a' } },
    { event: 'message', data: 'plain-text' },
    { event: 'done', data: { ok: true } },
  ]);
  assert.equal(headerOf(calls[0].init, 'Accept'), 'text/event-stream');
  assert.equal(headerOf(calls[0].init, 'Authorization'), 'Bearer token-s');
  assertTraceId(calls[0]);
}

console.warn('smoke: 全部断言通过');
