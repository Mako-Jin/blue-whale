// 论坛申诉材料界面实机（子应用独立运行 + 注入可信身份，口径同 mall/file/forum 冒烟）
//
// 前置：① whale-forum 19011 与 whale-file 8085 在跑；② 论坛子应用 dev 已启动
// （cd whale-frontend && pnpm --filter whale-forum dev，8123）；
// ③ 先跑 scripts/forum-ui-seed-appeal.py 造一条待审工单，拿输出里的 author_id。
//
// 运行（必须在 whale-frontend/e2e 目录下，依赖本目录的 @playwright/test）：
//   node tools/forum-material-ui-check.mjs   # 环境变量见下
//   UI_AUTHOR_ID=<seed 输出> UI_SHOT_DIR=<截图目录>
//
// 为什么不上基座：基座 dev 下 qiankun 激活与本应用容器存在竞态
//
// 为什么不上基座：基座 dev 下 qiankun 激活与本应用容器存在竞态
// （`Target container with #subapp-container not existed`），属框架层问题，
// 与论坛代码无关。这里直接把论坛子应用跑起来，用 Playwright 拦截 /forum/** 接口
// 补 X-User-Id / X-Tenant-Id / X-Permission-Codes，等价冒烟的注入式身份，
// 验的是论坛自己的渲染逻辑（缩略图 / 非图片降级 / 点击放大）。
import { chromium } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const APP = process.env.UI_APP_BASE ?? 'http://localhost:8123';
const API = process.env.UI_API_BASE ?? 'http://127.0.0.1:19011';
const AUTHOR = process.env.UI_AUTHOR_ID
  ?? (() => {
    // 缺省读 seed 落盘：<repo>/.workbuddy/tmp/forum-ui-seed-latest.json
    try {
      return JSON.parse(fs.readFileSync(path.join(process.cwd(), '../../.workbuddy/tmp/forum-ui-seed-latest.json'), 'utf-8')).author_id;
    } catch {
      return '';
    }
  })();
const SHOT_DIR = process.env.UI_SHOT_DIR ?? '.workbuddy/tmp';

const FORUM_PERMS = [
  'sys:forum:browse', 'sys:forum:thread:add', 'sys:forum:thread:edit', 'sys:forum:thread:remove',
  'sys:forum:post:add', 'sys:forum:reaction:use', 'sys:forum:manage:list', 'sys:forum:manage:audit',
  'sys:forum:manage:dispose', 'sys:forum:manage:board', 'sys:forum:hot:refresh', 'sys:forum:manage:ops',
];

// 只拦接口段，别把 /forum/src、/forum/@vite 这些 dev 模块一起拦了
const API_PATH = /^\/forum\/(meta|board|thread|post|hot|search|tag|profile|my|follow|exp|appeal|report|reaction|admin|internal)(\/|$)/;

const results = [];
const record = (name, ok, detail = '') => {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'OK  ' : 'FAIL'} ${name}${detail ? ' -> ' + detail : ''}`);
};

const run = async () => {
  const browser = await chromium.launch({ executablePath: EDGE, headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on('pageerror', (err) => console.log('PAGEERROR', err.message.slice(0, 200)));

  await page.addInitScript((perms) => {
    localStorage.setItem('whale_platform_user', JSON.stringify({
      userId: '900000101',
      username: 'qa_admin',
      nickname: 'QA 验证号',
      permissions: perms,
    }));
  }, FORUM_PERMS);

  await page.route(
    (url) => url.origin === new URL(APP).origin && API_PATH.test(url.pathname),
    async (route) => {
      const req = route.request();
      const src = new URL(req.url());
      const resp = await route.fetch({
        url: `${API}${src.pathname}${src.search}`,
        method: req.method(),
        headers: {
          ...req.headers(),
          'x-user-id': '900000101',
          'x-username': 'qa_admin',
          'x-tenant-id': '1',
          'x-user-roles': 'tenant_admin',
          'x-permission-codes': FORUM_PERMS.join(','),
        },
        body: req.postData() ?? undefined,
      });
      await route.fulfill({ response: resp });
    },
  );

  await page.goto(`${APP}/forum/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.forum-nav', { timeout: 60_000 });
  record('subapp.standalone.render', true, await page.title());

  await page.getByText('管理台', { exact: true }).first().click();
  await page.getByRole('tab', { name: /申诉工单/ }).click();
  await page.waitForTimeout(1200);
  await page.screenshot({ path: `${SHOT_DIR}/ui-appeal-list.png` });

  const row = page.locator('tr', { hasText: `鲸友 #${AUTHOR}` }).first();
  await row.waitFor({ timeout: 20_000 });
  record('appeal.row.found', true, AUTHOR);
  // antd 两个汉字的按钮会插空格（「复 核」）
  await row.getByRole('button', { name: /复\s*核/ }).click();

  const modal = page.locator('.ant-modal-content').filter({ hasText: '复核申诉' }).first();
  await modal.waitFor({ timeout: 15_000 });

  const thumb = modal.locator('img[alt*="ui-"]').first();
  await thumb.waitFor({ timeout: 20_000 });
  const natural = await thumb.evaluate((el) => ({ w: el.naturalWidth, h: el.naturalHeight, src: el.src }));
  record('material.thumbnail.rendered', natural.w > 0 && natural.h > 0,
    `natural=${natural.w}x${natural.h} src=${String(natural.src).slice(0, 24)}`);

  const txtLink = modal.getByRole('link', { name: /ui-.*\.txt/ }).first();
  record('material.nonimage.link', (await txtLink.count()) > 0, (await txtLink.getAttribute('href')) ?? '');
  await page.screenshot({ path: `${SHOT_DIR}/ui-appeal-modal.png` });

  // antd Image 的预览热区是覆盖层 .ant-image-mask，直接点 img 会被它拦下
  await modal.locator('.ant-image-mask').first().click();
  const preview = page.locator('.ant-image-preview-wrap').first();
  await preview.waitFor({ timeout: 10_000 });
  const box = await preview.boundingBox();
  record('material.zoom.preview', Boolean(box && box.width > 200),
    box ? `${Math.round(box.width)}x${Math.round(box.height)}` : 'no box');
  await page.screenshot({ path: `${SHOT_DIR}/ui-appeal-zoom.png` });
  await page.keyboard.press('Escape');

  await browser.close();
};

run()
  .then(() => {
    const failed = results.filter((r) => !r.ok);
    console.log(`\n汇总：${results.length - failed.length}/${results.length} 通过`);
    fs.writeFileSync(`${SHOT_DIR}/ui-appeal-report.json`,
      JSON.stringify({ results, failed: failed.length }, null, 2));
    process.exit(failed.length ? 1 : 0);
  })
  .catch((err) => {
    console.error('SCRIPT ERROR', err);
    fs.writeFileSync(`${SHOT_DIR}/ui-appeal-report.json`,
      JSON.stringify({ results, error: String(err) }, null, 2));
    process.exit(1);
  });
