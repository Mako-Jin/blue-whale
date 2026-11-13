/**
 * 天气互动冒烟 —— 雨天脚下有水花、城里人自己撑伞，切回晴天都收干净。
 *
 * <p>为什么整条都用真按钮点：工具栏那排 `<button data-action>` 曾经**一个都没绑上**
 * （host 查不到兄弟节点），按钮看着在、点了没反应、也不报错。天气这条链正好横跨
 * 按钮 → 状态 → 粒子 → 水花 → 伞具，用它守住"按钮绑定"这件事最省事。</p>
 *
 * <p>[4] 是负向断言：雪天不该溅水花、也不该撑伞。少了它，"只要下雨相关逻辑被点亮就通"
 * 的半成品会在测试里全绿。</p>
 *
 * 用法：BASE=http://127.0.0.1:8125/world/ node tools/world-weather.mjs
 */

import { chromium } from '@playwright/test';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/';

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

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
page.on('pageerror', (error) => errors.push(String(error)));

await page.addInitScript(() => {
  localStorage.setItem(
    'bww.avatar.v1',
    JSON.stringify({
      name: '赶路人',
      height: 1,
      build: 1,
      faceWidth: 1,
      skin: '#e8c4a0',
      robe: '#2b4a63',
      hair: '#15110f',
    }),
  );
});

await page.goto(`${BASE}?debug=1`, { waitUntil: 'domcontentloaded' });
await page.waitForFunction(() => typeof window.__bwwWeather === 'function', { timeout: 30000 });
await page.waitForTimeout(2400);

const weather = () => page.evaluate(() => window.__bwwWeather());
const toast = () =>
  page.evaluate(() => {
    const el = document.getElementById('bww-toast');
    return el ? (el.textContent ?? '').replace(/\s+/g, ' ') : '';
  });
/** 真点工具栏上的「天象」按钮（它循环 晴→雨→雪→雾→晴） */
const clickWeatherBtn = async () => {
  await page.click('[data-action="weather"]');
  await page.waitForTimeout(150);
};
const btnLabel = () =>
  page.evaluate(() => document.querySelector('[data-action="weather"]')?.textContent ?? '');

/* 先回到晴天，别被上一次会话留下的状态影响 */
await page.evaluate(() => window.__bwwSetWeather(0));
await page.waitForTimeout(400);

/* ------------------------------------------------------------ [1] 晴天 */
console.log('\n[1] 晴天：地上没有水花，城民不撑伞');
const clear = await weather();
ok(clear.weather === 0, `初始是晴天（weather=${clear.weather}）`);
ok(clear.wet < 0.05, `雨强为 0（wet=${clear.wet}）`);
ok(clear.splashVisible === false, '水花层整组不绘制');
ok(clear.umbrella < 0.05, `伞是收着的（opacity=${clear.umbrella}）`);

/* -------------------------------------------------------- [2] 点按钮转雨 */
console.log('\n[2] 真点「天象」按钮切到雨天');
await clickWeatherBtn();
const afterClick = await weather();
ok(afterClick.weather === 1, `按钮真的把天象切到了雨（weather=${afterClick.weather}）`);
ok((await btnLabel()).includes('雨'), `按钮文案跟着变（${await btnLabel()}）`);
ok((await toast()).includes('天象'), '切换有提示');

/* --------------------------------------------------- [3] 雨天的三处表现 */
console.log('\n[3] 雨天：雨强、地面水花、城民撑伞');
// 渐变是逐帧插值的，直接顶到目标值再断言，免得等三秒还断言在"半路"
await page.evaluate(() => window.__bwwSetWeather(1));
await page.waitForTimeout(700);
const rainy = await weather();
ok(rainy.wet > 0.99, `雨强到位（wet=${rainy.wet}）`);
ok(rainy.splashVisible === true, '地面水花层开始绘制');
ok(rainy.splashes >= 3, `脚下出现多圈水花（${rainy.splashes} 处）`);
ok(rainy.umbrella > 0.99, `城民撑起伞（opacity=${rainy.umbrella}）`);
// 钉住"被路面盖住"这个坑：环形路 y=0.06、御道 y=0.07，水花只要落回 0.03
// 就会被石板整片遮掉——探针一直有十几个活跃水花，画面上却一滴都看不到
ok(
  rainy.splashSample !== null && rainy.splashSample.y > 0.08,
  `水花浮在石板路面之上（y=${rainy.splashSample?.y}，路面 0.07）`,
);
ok(
  rainy.splashSample !== null && rainy.splashSample.op > 0.1,
  `水花有实际不透明度（op=${rainy.splashSample?.op}）`,
);

// 水花要真的在扩、在消失，否则只是摆了一圈静止的环
const alive1 = rainy.splashes;
await page.waitForTimeout(420);
const alive2 = (await weather()).splashes;
ok(alive2 > 0, `水花在持续循环（${alive1} → ${alive2}）`);

/* -------------------------------------------------- [4] 雪天不溅不撑 */
console.log('\n[4] 负向：雪天不溅水花、也不撑伞');
await page.evaluate(() => window.__bwwSetWeather(2));
await page.waitForTimeout(600);
const snowy = await weather();
ok(snowy.weather === 2, '当前是雪天');
ok(snowy.snow > 0.99, `雪强到位（snow=${snowy.snow}）`);
ok(snowy.wet < 0.01, `雨强归零（wet=${snowy.wet}）`);
ok(snowy.splashVisible === false, '雪天没有水花（雪不溅水）');
ok(snowy.umbrella < 0.05, `雪天没人撑伞（opacity=${snowy.umbrella}）`);

/* ------------------------------------------------------- [5] 循环回晴 */
console.log('\n[5] 按钮再点两下（雪→雾→晴）回到晴天');
await clickWeatherBtn();
await page.waitForTimeout(200);
ok((await weather()).weather === 3, '切到雾');
await clickWeatherBtn();
await page.waitForTimeout(200);
ok((await weather()).weather === 0, '切回晴');
await page.evaluate(() => window.__bwwSetWeather(0));
await page.waitForTimeout(600);
const backClear = await weather();
ok(backClear.splashVisible === false, '回到晴天后水花收起');
ok(backClear.umbrella < 0.05, '伞也收起了');

/* --------------------------------------------------------- [6] 收尾 */
console.log('\n[6] 无异常');
ok(errors.length === 0, `页面无未捕获异常${errors.length ? `：${errors[0]}` : ''}`);

console.log(`\n通过 ${passed} · 失败 ${failed}`);
await browser.close();
process.exit(failed === 0 ? 0 : 1);
