/**
 * whale-common-three 渲染态验收：Playwright 无头断言脚本。
 *
 * <p>前置：先启动 serve.mjs；运行需 @playwright/test 与 Chromium
 * （frontend/e2e 工作区已装，从其 node_modules 解析）。
 * 用法：node verify.mjs [页面地址]，默认 http://127.0.0.1:8199/acceptance/index.html</p>
 *
 * <p>退出码：0 验收通过 / 1 失败。结果打印到 stdout，供评审文档引用。</p>
 *
 * @author BlueWhale
 */
import { createRequire } from 'node:module';

/** e2e 工作区 package.json 地址（本包不装浏览器依赖，从其 node_modules 解析 playwright） */
const E2E_PACKAGE_URL = new URL('../../../frontend/e2e/package.json', import.meta.url).href;

const require = createRequire(E2E_PACKAGE_URL);

const { chromium } = require('playwright-core');

const target = process.argv[2] ?? 'http://127.0.0.1:8199/acceptance/index.html';

const browser = await chromium.launch({
  headless: true,
  args: ['--use-angle=default', '--enable-unsafe-swiftshader'],
});
try {
  const page = await browser.newPage();
  const consoleErrors = [];
  page.on('pageerror', (error) => consoleErrors.push(String(error)));
  await page.goto(target, { waitUntil: 'networkidle', timeout: 30_000 });
  await page.waitForFunction(() => window.__ACCEPT__?.ready === true
    || window.__ACCEPT__?.error != null, null, { timeout: 20_000 });
  const result = await page.evaluate(() => window.__ACCEPT__);
  console.log(JSON.stringify({ ...result, consoleErrors }, null, 2));
  process.exit(result.ready ? 0 : 1);
} finally {
  await browser.close();
}
