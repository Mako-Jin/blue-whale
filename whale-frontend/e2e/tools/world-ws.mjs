/**
 * 同城频道真实 WebSocket 冒烟 —— 两个客户端、真服务、真网关。
 *
 * <p>前面几个 world-*.mjs 跑的都是"没登录 → 本地模拟"这条路，也就是说
 * <b>它们全绿也不代表联机是通的</b>。这一条专门验真链路：
 * 网关放行 → 一次性 ticket 消费 → join 进场景 → world.say 中继 → 速率限制。</p>
 *
 * <p>为什么要走网关（8080）而不是直连 im（8091）：`/im/world` 曾经不在网关白名单里，
 * 网关先回 401，前端 net.ts 收到失败后<b>静默退回本地模拟</b>，画面里"其他玩家"全是假的，
 * 只有 console 一行 warn。直连 8091 会绕过这个缺陷，测了等于没测。</p>
 *
 * <p>ticket 来源：正常流程是 `POST /im/v1/ws-ticket`（要 JWT）。本机超管口令已被改过，
 * 为了不碰真实账号，这里用 Redis 直接种一张票——票的读写格式与
 * {@code WsTicketService} 完全一致（`whale:im:ticket:` 前缀 + userId），
 * 属于测试侧后门，不进产品代码。</p>
 *
 * 用法：node tools/world-ws.mjs
 *   环境变量：WS_BASE（默认 ws://127.0.0.1:8080）、REDIS_CONTAINER（默认 bw-redis）
 */

import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

const WS_BASE = process.env.WS_BASE ?? 'ws://127.0.0.1:8080';
const REDIS = process.env.REDIS_CONTAINER ?? 'bw-redis';
const SCENE = 'bluewhale-city-3d';

let passed = 0;
let failed = 0;

function ok(condition, label) {
  if (condition) {
    passed += 1;
    console.log(`  PASS ${label}`);
  } else {
    failed += 1;
    console.log(`  FAIL ${label}`);
  }
}

/** 种一张一次性 ticket，返回票面 */
function plantTicket(userId) {
  const ticket = randomUUID().replaceAll('-', '');
  execFileSync('docker', [
    'exec', REDIS, 'redis-cli', 'SET', `whale:im:ticket:${ticket}`, String(userId), 'EX', '300',
  ], { encoding: 'utf8' });
  return ticket;
}

/** 一个客户端：连上、攒下行帧、能发帧 */
function connect(label, userId) {
  const ticket = plantTicket(userId);
  const url = `${WS_BASE}/im/world?ticket=${ticket}`;
  const ws = new WebSocket(url);
  const frames = [];
  const waiters = [];

  ws.addEventListener('message', (event) => {
    let frame;
    try {
      frame = JSON.parse(String(event.data));
    } catch {
      return;
    }
    frames.push(frame);
    for (let i = waiters.length - 1; i >= 0; i -= 1) {
      if (waiters[i].match(frame)) {
        waiters[i].resolve(frame);
        waiters.splice(i, 1);
      }
    }
  });

  const client = {
    label,
    userId,
    ws,
    frames,
    closed: false,
    opened: false,
    send: (frame) => ws.send(JSON.stringify(frame)),
    /** 等一个满足条件的新帧（只看调用之后到达的） */
    wait: (match, timeoutMs = 6000) => new Promise((resolve, reject) => {
      const hit = frames.find(match);
      if (hit) {
        resolve(hit);
        return;
      }
      const waiter = { match, resolve };
      waiters.push(waiter);
      setTimeout(() => {
        const index = waiters.indexOf(waiter);
        if (index >= 0) {
          waiters.splice(index, 1);
          reject(new Error(`${label} 等待帧超时`));
        }
      }, timeoutMs);
    }),
    open: () => new Promise((resolve, reject) => {
      // 没有超时的 open 会让"代理没转发 WS 升级"这种情况永远挂着，看不出是哪一层断的
      const timer = setTimeout(() => reject(new Error(`${label} 握手超时（10s，多半是代理没转发 WS 升级）`)), 10_000);
      ws.addEventListener('open', () => {
        clearTimeout(timer);
        client.opened = true;
        resolve();
      });
      ws.addEventListener('error', () => {
        clearTimeout(timer);
        if (!client.opened) {
          reject(new Error(`${label} 握手失败（${url.replace(/ticket=\w+/, 'ticket=***')}）`));
        }
      });
    }),
    close: () => ws.close(),
  };
  ws.addEventListener('close', () => {
    client.closed = true;
  });
  return client;
}

const join = (name, x, z) => ({
  type: 'world.join', sceneKey: SCENE, name, robe: '#2b4a63', x, z, yaw: 0,
});

const errorsOf = (code) => (frame) => frame.type === 'world.error' && frame.code === code;

console.log('同城频道真实 WS 冒烟（网关 → im → 两个客户端）');

/* ---- 1. 握手：网关放行 + ticket 被消费 ---- */
const a = connect('A', 990001);
const b = connect('B', 990002);
try {
  await a.open();
  await b.open();
  ok(true, '网关放行 /im/world 且 ticket 通过');
} catch (error) {
  ok(false, `握手失败：${error.message}`);
  console.log(`\n存在失败：${passed} passed, ${failed + 1} failed`);
  process.exit(1);
}

/* ---- 2. 进场景：拿到 world.snapshot，且 selfId 正确 ---- */
a.send(join('甲', 0, 0));
const snapshotA = await a.wait((f) => f.type === 'world.snapshot');
ok(String(snapshotA.selfId) === '990001', `快照回带 selfId（${snapshotA.selfId}）`);

b.send(join('乙', 12, 0));
const snapshotB = await b.wait((f) => f.type === 'world.snapshot');
ok(String(snapshotB.selfId) === '990002', `B 的 selfId 正确（${snapshotB.selfId}）`);
ok(Array.isArray(snapshotB.peers) && snapshotB.peers.length >= 1,
  `B 进场景能看到 A（peers ${snapshotB.peers?.length ?? 0}）`);

/* ---- 3. 空话被拒（先做，免得撞速率限制） ---- */
a.send({ type: 'world.say', sceneKey: SCENE, text: '   ' });
await a.wait(errorsOf('BAD_SAY'));
ok(true, '空话被拒（BAD_SAY）');

/* ---- 4. 喊话：自己回显 + AOI 内他人收到 ---- */
a.send({ type: 'world.say', sceneKey: SCENE, text: '见过主公。' });
const echo = await a.wait((f) => f.type === 'world.say' && f.text === '见过主公。');
ok(String(echo.from) === '990001', `自己收到回显（from ${echo.from}）`);
ok(echo.name === '甲', `回显带昵称（${echo.name}）`);

const relay = await b.wait((f) => f.type === 'world.say' && f.text === '见过主公。');
ok(String(relay.from) === '990001', `B 收到 A 的喊话（from ${relay.from}）`);
ok(Number(relay.x) === 0 && Number(relay.z) === 0, `喊话带坐标（${relay.x},${relay.z}）`);

/* ---- 5. 1.5 秒内连说被限流（明确报错，不是静默丢弃） ---- */
a.send({ type: 'world.say', sceneKey: SCENE, text: '再来一句。' });
await a.wait(errorsOf('SAY_LIMITED'));
ok(true, '连说被限流（SAY_LIMITED）');

/* ---- 6. 超长截断到 120 字 ---- */
await new Promise((resolve) => setTimeout(resolve, 1700));
a.send({ type: 'world.say', sceneKey: SCENE, text: '水'.repeat(200) });
const long = await a.wait((f) => f.type === 'world.say' && f.text?.startsWith('水水水'));
ok(long.text.length === 120, `超长截断到 120 字（实际 ${long.text.length}）`);

/* ---- 7. AOI：120 米外的人听不到 ---- */
// 坐标上限 200（COORD_LIMIT），(150,150) 距原点约 212 米，刚好在 AOI 之外又合法
const far = connect('C', 990003);
await far.open();
far.send(join('丙', 150, 150));
await far.wait((f) => f.type === 'world.snapshot');
await new Promise((resolve) => setTimeout(resolve, 1700));
a.send({ type: 'world.say', sceneKey: SCENE, text: '城里有人吗？' });
await a.wait((f) => f.type === 'world.say' && f.text === '城里有人吗？');
await new Promise((resolve) => setTimeout(resolve, 600));
const farHeard = far.frames.some((f) => f.type === 'world.say' && f.text === '城里有人吗？');
ok(!farHeard, '120 米外的人听不到（AOI 生效）');

/* ---- 8. 未 join 就说话被拒 ---- */
const d = connect('D', 990004);
await d.open();
d.send({ type: 'world.say', sceneKey: SCENE, text: '我先来的' });
await d.wait(errorsOf('NOT_JOINED'));
ok(true, '未进场景就喊话被拒（NOT_JOINED）');

/* ---- 9. ticket 一次性：握手成功即从 Redis 删除 ---- */
const leftover = execFileSync('docker', ['exec', REDIS, 'redis-cli', 'KEYS', 'whale:im:ticket:*'],
  { encoding: 'utf8' }).trim();
ok(leftover === '', `ticket 用后即删（残留 ${leftover === '' ? 0 : leftover.split('\n').length} 张）`);

for (const client of [a, b, far, d]) {
  client.close();
}

console.log(`\n${failed === 0 ? '全部通过' : '存在失败'}：${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
