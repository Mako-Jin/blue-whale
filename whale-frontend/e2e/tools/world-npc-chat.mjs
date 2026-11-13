/**
 * 蓝鲸城问话链路（在线）冒烟 —— P3
 *
 * 与 world-city-smoke.mjs 的分工：那个跑「离线降级」（不需要后端），这个跑
 * 「真·问数」——经网关 8080 打到 whale-agent 8090，验证权限头、SSE 帧、面板流式渲染。
 *
 * 前置：
 *   1. 网关/agent 已起（本机 docker：bw-gateway + bw-agent）
 *   2. 已构建并起预览：pnpm --filter whale-world build && pnpm --filter whale-world preview --port 8125
 *   3. 拿一个具备 sys:agent:chat 的令牌，经 TOKEN 传入
 *
 * 跑法：BASE=http://127.0.0.1:8125/world/ TOKEN=xxx node tools/world-npc-chat.mjs
 *
 * 断言：
 *   A. 坏令牌：问第一句后必须降级（徽标变「AI 未接入」+ 回落本地台词），不能挂着转圈
 *   B. 好令牌：徽标「已接入知识中枢」，问话后拿到非空应答（本机无 LLM key 时服务端降级为
 *      检索摘要——这算通过，但会额外打印一句提示，避免把「降级」误当「模型正常」）
 */
import { chromium } from '@playwright/test';

const BASE = (process.env.BASE || 'http://127.0.0.1:8125/world/') + '?debug=1';
const TOKEN = process.env.TOKEN || '';

if (!TOKEN) {
  console.error('缺少 TOKEN 环境变量');
  process.exit(2);
}

const failures = [];
const ok = (cond, msg) => {
  if (!cond) {
    failures.push(msg);
  }
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`);
};

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});

/** 打开一页并把令牌塞进 sessionStorage（city.ts 只在 ?debug=1 且非基座内读它） */
async function openWithToken(token) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.addInitScript((value) => {
    try {
      window.sessionStorage.setItem('bww.debug.token', value);
    } catch {
      /* 隐私模式下 sessionStorage 可能不可用，随后的断言会失败并暴露问题 */
    }
  }, token);
  await page.goto(BASE, { waitUntil: 'load' });
  await page.waitForTimeout(4200);
  return { page, errors };
}

/** 走到户部主事沈文昭身旁（走真实 F 键邻近判定） */
async function talkToClerk(page) {
  const pos = await page.evaluate(() => window.__bwwNpcAt('clerk-shen'));
  await page.evaluate(([x, z]) => window.__bwwWarp(x, z + 2.2), [pos.x, pos.z]);
  await page.waitForTimeout(400);
  await page.keyboard.press('KeyF');
  await page.waitForTimeout(420);
}

/** 等到 NPC 最后一条气泡出现非空文本（或面板降级），最多等 timeout 毫秒 */
async function waitForAnswer(page, timeout = 75_000) {
  const started = Date.now();
  let last = { text: '', state: '', badge: '' };
  while (Date.now() - started < timeout) {
    last = await page.evaluate(() => {
      const log = window.__bwwChatLog();
      const npc = log.filter((m) => m.who === 'npc').pop();
      return {
        text: (npc?.text ?? '').trim(),
        state: document.querySelector('#bww-chat-panel .bww-chat-state')?.textContent ?? '',
        badge: document.querySelector('#bww-chat-panel .bww-chat-badge')?.textContent ?? '',
        notes: Array.from(document.querySelectorAll('#bww-chat-panel .bww-chat-note')).map((n) => n.textContent),
      };
    });
    if (last.text.length > 2 && !last.state) {
      return last;
    }
    await page.waitForTimeout(400);
  }
  return last;
}

/* ---------- A. 坏令牌必须降级 ---------- */
{
  const { page, errors } = await openWithToken('not-a-real-token');
  await talkToClerk(page);
  const badgeBefore = await page.evaluate(
    () => document.querySelector('#bww-chat-panel .bww-chat-badge')?.textContent,
  );
  ok(badgeBefore === '已接入知识中枢', `坏令牌初始按「未知」放行一次（${badgeBefore}）`);

  await page.locator('.bww-chat-input').fill('户部今天有几张异常工单？');
  await page.locator('.bww-chat-send').click();
  const after = await waitForAnswer(page, 40_000);
  const mode = await page.evaluate(() => window.__bwwDebug().brainMode);
  ok(mode === 'offline', `401 后降级为 offline（实际 ${mode}）`);
  ok(after.badge === 'AI 未接入', `徽标切到未接入（实际 ${after.badge}）`);
  ok(after.text.length > 2, `仍给玩家一句本地台词（${after.text.slice(0, 18)}…）`);
  ok(errors.length === 0, `坏令牌场景无 pageerror（${errors[0] ?? ''}）`);
  await page.close();
}

/* ---------- B. 好令牌走通整条链 ---------- */
{
  const { page, errors } = await openWithToken(TOKEN);
  const pre = await page.evaluate(() => window.__bwwDebug());
  ok(pre.brainMode === 'online', `有令牌时判为 online（实际 ${pre.brainMode}）`);

  await talkToClerk(page);
  const badge = await page.evaluate(
    () => document.querySelector('#bww-chat-panel .bww-chat-badge')?.textContent,
  );
  ok(badge === '已接入知识中枢', `徽标显示已接入（实际 ${badge}）`);

  await page.locator('.bww-chat-input').fill('户部今天有几张异常工单？');
  await page.locator('.bww-chat-send').click();
  const answer = await waitForAnswer(page);
  // 「这个我查不到」也是合格回答（知识库里没有实时业务数字），不能只按字数判
  const substantive = answer.text.length > 10 || /查不到|答不上|没有凭据/.test(answer.text);
  ok(substantive, `拿到应答（${answer.text.length} 字）：${answer.text.slice(0, 40)}…`);
  // 回归：小模型曾把 tool_calls 当正文吐出来（"需要检索。{"name":"search_knowledge"...}"）
  ok(!/"name"\s*:\s*"search_knowledge"/.test(answer.text), '回答里没有把工具调用当正文');
  const degradedNote = (answer.notes ?? []).find((n) => n.includes('摘要') || n.includes('不可用'));
  console.log(degradedNote ? `INFO  服务端降级：${degradedNote}` : 'INFO  模型正常应答（无降级提示）');

  const convA = await page.evaluate(() => window.__bwwDebug().chat);
  ok(convA.persona === 'clerk-shen', `会话对象仍是沈文昭（实际 ${convA.persona}）`);

  // 第二问：同人设续聊不该重注入人设前缀（服务端 conversationId 已带上下文）
  await page.locator('.bww-chat-input').fill('那工单里最急的是哪张？');
  await page.locator('.bww-chat-send').click();
  await page.waitForTimeout(1200);
  const answer2 = await waitForAnswer(page);
  // 「中枢那头出了岔子」是失败兜底文案 —— 出现即说明这一问没打通，不能被当成"有回应"
  ok(answer2.text.length > 2 && !answer2.text.includes('岔子'), `二次追问有回应（${answer2.text.slice(0, 24)}…）`);
  const notes2 = await page.evaluate(
    () => document.querySelectorAll('#bww-chat-panel .bww-chat-note').length,
  );
  ok(notes2 <= 1, `降级提示不重复堆叠（实际 ${notes2} 条）`);
  ok(errors.length === 0, `在线场景无 pageerror（${errors[0] ?? ''}）`);
  await page.close();
}

await browser.close();
console.log(failures.length ? `\n${failures.length} 项失败` : '\n全部通过');
process.exit(failures.length ? 1 : 0);
