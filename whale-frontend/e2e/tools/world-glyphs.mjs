/**
 * 殿前牌坊冒烟 —— 断言八座殿的"城情牌"能取到数、能降级、且不越权。
 *
 * <p>跑在两套场景下：</p>
 * <ol>
 *   <li><b>无令牌</b>：八块牌必须全部判 {@code denied}，而且<b>一个业务请求都不许发</b>。
 *       这条是负向验证——没有它，"未登录先探一次接口"这种改动会静默通过。</li>
 *   <li><b>有令牌</b>：不能有任何一块牌停在 {@code loading}（刷新必须收敛），
 *       {@code live} 的牌必须带指标（防止"200 但没数"的假成功）。</li>
 * </ol>
 *
 * <p>本机只起了部分服务，所以断言<b>不要求</b>八块全 live：
 * 哪座殿是 {@code unreachable} 取决于当时哪个服务在跑，这在真实部署里也是常态。</p>
 *
 * 用法：BASE=http://127.0.0.1:8125/world/ TOKEN=xxx node tools/world-glyphs.mjs
 */

import { chromium } from '@playwright/test';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/';
const TOKEN = process.env.TOKEN ?? '';
/** 八个业务系统的一级前缀 */
const BUSINESS = /^\/(erp|mes|oa|crm|lms|mall|doc|alert|monitor)(\/|$)/;

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

const AVATAR = {
  name: '验牌人',
  height: 1,
  build: 1,
  faceWidth: 1,
  skin: '#e8c4a0',
  robe: '#2b4a63',
  hair: '#15110f',
  hairStyle: 0,
  beard: false,
  accessory: 1,
};

async function open(browser, token) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const hits = [];
  page.on('request', (req) => {
    const url = new URL(req.url());
    if (url.port === '8125' && BUSINESS.test(url.pathname)) {
      hits.push(url.pathname);
    }
  });
  await page.addInitScript(([t, avatar]) => {
    if (t) {
      sessionStorage.setItem('bww.debug.token', t);
    }
    localStorage.setItem('bww.avatar.v1', JSON.stringify(avatar));
  }, [token, AVATAR]);
  await page.goto(`${BASE}?debug=1`, { waitUntil: 'load' });
  // 等 3D 起来 + 一轮刷新收敛（每路独立 8 秒超时，留足余量）
  await page.waitForTimeout(15000);
  return { page, hits };
}

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});

/* ---------------- 场景一：无令牌 ---------------- */
console.log('\n[无令牌] 未登录时牌坊应判 denied，且不得向业务系统发请求');
{
  const { page, hits } = await open(browser, '');
  const cards = await page.evaluate(() => window.__bwwGlyphs());
  ok(Array.isArray(cards) && cards.length === 8, `八块牌全部就位（${cards.length}）`);
  ok(cards.every((c) => c.state === 'denied'), '全部判定为 denied');
  ok(cards.every((c) => c.metrics.length === 0), '未登录时牌面不带任何数字');
  // 负向：这是最关键的一条
  ok(hits.length === 0, `未登录时零个业务请求（实际 ${hits.length} 个：${hits.slice(0, 3).join(',')}）`);
  await page.close();
}

/* ---------------- 场景二：有令牌 ---------------- */
if (!TOKEN) {
  console.log('\n[有令牌] 跳过（未提供 TOKEN）');
} else {
  console.log('\n[有令牌] 刷新应收敛，live 的牌必须带指标');
  const { page, hits } = await open(browser, TOKEN);
  const cards = await page.evaluate(() => window.__bwwGlyphs());
  const states = cards.map((c) => c.state);

  ok(cards.length === 8, `八块牌全部就位（${cards.length}）`);
  ok(!states.includes('loading'), '没有牌停在 loading（刷新已收敛）');
  const live = cards.filter((c) => c.state === 'live');
  ok(live.every((c) => c.metrics.length > 0), `live 的牌都带指标（${live.length} 块 live）`);
  ok(cards.every((c) => typeof c.note === 'string' && c.note.length > 0), '每块牌都有状态说明');
  const illegal = states.filter((s) => !['live', 'stale', 'denied', 'unreachable'].includes(s));
  ok(illegal.length === 0, `状态值合法（异常：${illegal.join(',') || '无'}）`);
  ok(hits.length > 0, `确实向业务系统发起了取数（${hits.length} 个请求）`);

  // 牌面位图非空：证明"数据有、牌没画"这种情况不会发生
  const face = await page.evaluate(() => window.__bwwGlyphFace('hubu-hall'));
  ok(typeof face === 'string' && face.startsWith('data:image/png') && face.length > 2000, '户部牌面位图已绘制');

  // 逐块打印，便于事后对照当时哪个服务没起
  for (const c of cards) {
    console.log(`    ${c.nodeKey.padEnd(18)} ${c.state.padEnd(12)} ${c.note.padEnd(24)} ${c.metrics.join(' / ')}`);
  }
  await page.close();
}

await browser.close();
console.log(`\n${failed === 0 ? '全部通过' : '存在失败'}：${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
