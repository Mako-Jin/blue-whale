/**
 * 网关配置的结构性自检（`infra/nginx/`）。
 *
 * ## 为什么需要它
 *
 * 开发机没有 nginx、也没有 docker（`nginx -v` / `docker --version` 均 command not found），
 * 所以 `nginx -t` 跑不了。但这个文件**不试图替代 `nginx -t`** —— 它补的是另一类错：
 *
 *   | 这类错 | `nginx -t` 能发现吗 | 本脚本能发现吗 |
 *   | --- | --- | --- |
 *   | 花括号不配对、指令拼写错 | 能 | 不能 |
 *   | `limit_req zone=xxx` 引用了**不存在的 zone** | 能（但只在启动时） | 能 |
 *   | 模板变量漏在 `.env.example` 之外 | 不能（渲染后是空值） | 能 |
 *   | envsubst 白名单漏了、把 nginx 自己的 `$request_method` 替换掉了 | 不能（配置看起来是合法的） | 能 |
 *   | 上报路径没挂上限流 / `map` 丢了 | 不能 | 能 |
 *   | 真实 IP 段被误配成 `0.0.0.0/0` | 不能 | 能 |
 *
 * 上表中间几行是**只有本脚本能挡**的：它们都能让配置"通过 `nginx -t` 但行为是错的"。
 * 反过来，它会说"通过"而 `nginx -t` 报语法错 —— 所以**上线前两者都要跑**。
 *
 * ## 用法
 *
 *     node scripts/verify-nginx-config.mjs
 *
 * 不需要 nginx、不需要网络、不写任何文件。失败时非零退出（可用在 CI 里）。
 */
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const NGINX_DIR = join(ROOT, 'infra', 'nginx');
const TEMPLATE_PATH = join(NGINX_DIR, 'conf.d', 'bluewhale.conf.template');
const ENV_EXAMPLE_PATH = join(NGINX_DIR, '.env.example');
const REAL_IP_PATH = join(NGINX_DIR, 'snippets', 'bluewhale-real-ip.conf');
const PROXY_PATH = join(NGINX_DIR, 'snippets', 'bluewhale-proxy.conf');

const problems = [];

function check(label, ok, detail) {
  console.log(`${ok ? '  ✓' : '  ✗'} ${label}${detail ? ` — ${detail}` : ''}`);
  if (!ok) problems.push(label);
}

function read(path) {
  try {
    return readFileSync(path, 'utf8');
  } catch (cause) {
    console.log(`  ✗ 读不到文件 ${path}`);
    problems.push(`读不到 ${path}`);
    return null;
  }
}

/** 取出以 `header` 开头、到配对的右花括号为止的那一段（找不到返回 null） */
function block(text, header) {
  const start = text.indexOf(header);
  if (start < 0) return null;
  let depth = 0;
  let seenBrace = false;
  for (let i = text.indexOf('{', start); i < text.length; i += 1) {
    if (text[i] === '{') {
      depth += 1;
      seenBrace = true;
    } else if (text[i] === '}') {
      depth -= 1;
      if (seenBrace && depth === 0) return text.slice(start, i + 1);
    }
  }
  return null;
}

/** 一行是不是注释（nginx 用 `#`） */
const isComment = (line) => line.trim().startsWith('#');

const template = read(TEMPLATE_PATH);
const envText = read(ENV_EXAMPLE_PATH);
const realIpText = read(REAL_IP_PATH);
const proxyText = read(PROXY_PATH);

if (!template || !envText || !realIpText || !proxyText) {
  console.log('\n✗ 前置文件缺失，无法继续');
  process.exit(1);
}

/* ---------------------------------------------------------------- 一、模板变量 */

console.log('\n== 一、模板变量 ==\n');

const env = new Map();
for (const line of envText.split(/\r?\n/)) {
  const matched = /^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*)$/.exec(line);
  if (matched) env.set(matched[1], matched[2].trim());
}

const usedVars = new Set(
  [...template.matchAll(/\$\{([A-Z][A-Z0-9_]*)\}/g)].map((matched) => matched[1]),
);
const missingVars = [...usedVars].filter((name) => !env.has(name));
check(
  '模板里用到的每个变量都在 .env.example 里定义',
  missingVars.length === 0,
  missingVars.join(', '),
);

const unusedVars = [...env.keys()].filter((name) => !usedVars.has(name));
check(
  '.env.example 里没有模板用不到的变量',
  unusedVars.length === 0,
  unusedVars.length ? `多余的：${unusedVars.join(', ')}` : undefined,
);

check('变量数不为零（配置确实被参数化了）', env.size > 0, `共 ${env.size} 个`);

/* ------------------------------------------------------------ 二、渲染与内置变量 */

console.log('\n== 二、渲染（模拟 envsubst 白名单） ==\n');

let rendered = template;
for (const [name, value] of env) {
  rendered = rendered.split(`\${${name}}`).join(value);
}

// 只认**变量形状**的残留。写成 `[^}]*` 会把注释里的散文（比如"占位符"的示意写法）
// 也算进来 —— 那个正则确实抓到过一次误报。
const leftovers = rendered.match(/\$\{[A-Z][A-Z0-9_]*\}/g) ?? [];
check('渲染后不残留任何变量形状的占位符', leftovers.length === 0, leftovers.join(', '));

/*
 * 这些是 nginx 自己的变量。它们不是 ${...} 形式，所以正确的白名单 envsubst 不会碰它们；
 * 一旦被替换掉，配置看起来仍然合法，但限流与转发全部静默失效 —— 本脚本的核心检查之一。
 *
 * 必须在**渲染后的模板 + 两个 snippet** 上一起查：`$host` / `$remote_addr` / `$scheme`
 * / `$proxy_add_x_forwarded_for` 住在 bluewhale-proxy.conf 里（模板只是 include 它），
 * 只看模板会得到"它们不见了"的假警报。部署后的配置正是这三份拼起来的结果。
 */
const deployed = `${rendered}\n${realIpText}\n${proxyText}`;
const NGINX_VARS = [
  '$binary_remote_addr',
  '$request_method',
  '$proxy_add_x_forwarded_for',
  '$remote_addr',
  '$host',
  '$scheme',
];
const lostVars = NGINX_VARS.filter((name) => !deployed.includes(name));
check(
  'nginx 内置变量未被误替换（白名单没漏）',
  lostVars.length === 0,
  lostVars.length ? `被吃掉的：${lostVars.join(', ')}` : undefined,
);
check(
  '.env.example 里没有和 nginx 内置变量同名的键',
  ![...env.keys()].some((name) => NGINX_VARS.includes(`$${name.toLowerCase()}`)),
);

/* ------------------------------------------------------------------ 三、限流 zone */

console.log('\n== 三、限流 zone 的定义与引用 ==\n');

const definedZones = new Set(
  [...rendered.matchAll(/limit_(?:req|conn)_zone\s+\S+\s+zone=([A-Za-z0-9_]+):/g)].map(
    (matched) => matched[1],
  ),
);
const referencedZones = new Set([
  ...[...rendered.matchAll(/limit_req\s+zone=([A-Za-z0-9_]+)/g)].map((matched) => matched[1]),
  ...[...rendered.matchAll(/limit_conn\s+([A-Za-z0-9_]+)\s+\d+/g)].map((matched) => matched[1]),
]);
const danglingZones = [...referencedZones].filter((name) => !definedZones.has(name));

check('至少定义了一个限流 zone', definedZones.size > 0, [...definedZones].join(', '));
check('没有定义了却没人用的 zone', definedZones.size === referencedZones.size);
check(
  '每个被引用的 zone 都已定义（zone 名拼错会在这里现形）',
  danglingZones.length === 0,
  danglingZones.join(', '),
);

/* ---------------------------------------------------------------- 四、上报通道 */

console.log('\n== 四、上报通道的挂载 ==\n');

const REPORT_PATHS = ['/api/logs/client', '/api/events/client'];
for (const path of REPORT_PATHS) {
  const serverBlock = block(rendered, `location = ${path} {`) ?? block(rendered, `location = ${path}{`);
  check(`存在 exact-match location：${path}`, serverBlock !== null);
  if (serverBlock) {
    check(`  └ ${path} 挂了限流`, /limit_req\s+zone=\S+/.test(serverBlock));
    check(`  └ ${path} 挂了并发限制`, /limit_conn\s+\S+\s+\d+/.test(serverBlock));
    check(`  └ ${path} 转发到后端`, /proxy_pass/.test(serverBlock) || /bluewhale-proxy/.test(serverBlock));
  }
}

// 上报与查询共用 URI（POST 上报 / GET 查询），只能靠方法区分 —— 这是本配置最容易写错的一处。
const mapBlock = block(rendered, 'map $request_method $bluewhale_report_key {');
check('存在按请求方法构造 key 的 map', mapBlock !== null);
if (mapBlock) {
  check('  └ 默认 key 为空（非 POST 不计入）', /default\s+""\s*;/.test(mapBlock));
  check('  └ POST 映射到客户端 IP', /POST\s+\$binary_remote_addr\s*;/.test(mapBlock));
}
check(
  '上报 zone 的 key 用的是上面那个 map（而不是裸 IP，否则 GET 查询会被误限）',
  /limit_req_zone\s+\$bluewhale_report_key\s+zone=/.test(rendered),
);

/* -------------------------------------------------------------------- 五、429 */

console.log('\n== 五、429 的形态 ==\n');

const errorPage = /error_page\s+429\s*=\s*@([A-Za-z0-9_]+)/.exec(rendered);
check('error_page 429 指向一个命名 location', errorPage !== null, errorPage?.[1]);
if (errorPage) {
  const named = block(rendered, `location @${errorPage[1]} {`);
  check(`  └ 命名 location @${errorPage[1]} 存在`, named !== null);
  if (named) {
    check('  └ 声明为 JSON', /default_type\s+application\/json/.test(named));
    check('  └ 响应体 code 是 42900（与后端错误码表对齐）', /"code"\s*:\s*42900/.test(named));
    check(
      '  └ 响应体不带 timestamp（nginx 拿不到 epoch 毫秒，宁缺勿错）',
      !/"timestamp"/.test(named),
    );
  }
}
check('limit_req_status 显式设为 429', /limit_req_status\s+429\s*;/.test(rendered));
check('limit_conn_status 显式设为 429', /limit_conn_status\s+429\s*;/.test(rendered));

/* ------------------------------------------------------- 六、反向代理与体量闸门 */

console.log('\n== 六、反向代理与体量闸门 ==\n');

check('定义了 upstream', /upstream\s+bluewhale_backend\s*\{/.test(rendered));
check('upstream 指向模板变量 BACKEND_UPSTREAM 的值', rendered.includes(env.get('BACKEND_UPSTREAM') ?? '\u0000'));
check('设置了请求体字节上限', /client_max_body_size\s+\S+\s*;/.test(rendered));

for (const header of ['Host', 'X-Real-IP', 'X-Forwarded-For', 'X-Forwarded-Proto']) {
  check(`snippet 设置了 ${header}`, new RegExp(`proxy_set_header\\s+${header}\\b`).test(proxyText));
}
check(
  'X-Forwarded-For 是**追加**而非覆盖（否则丢掉中间跳）',
  /proxy_set_header\s+X-Forwarded-For\s+\$proxy_add_x_forwarded_for/.test(proxyText),
);

/* ------------------------------------------------------------ 七、真实 IP 的分界 */

console.log('\n== 七、真实 IP（配错会让限流静默失效） ==\n');

const activeRealIp = realIpText
  .split(/\r?\n/)
  .filter((line) => line.trim() && !isComment(line));

check(
  '默认不启用 set_real_ip_from（本机即边缘）',
  activeRealIp.length === 0,
  activeRealIp.length ? `有 ${activeRealIp.length} 行生效` : undefined,
);
if (activeRealIp.length > 0) {
  // 一旦有人启用了它，就必须确认没有"信任所有人"——那等于把限流的钥匙交出去。
  const tooBroad = activeRealIp.filter((line) => /0\.0\.0\.0\/0|::\/0|all;/.test(line));
  check('  └ 没有把可信网段写成 0.0.0.0/0（等于信任所有来源）', tooBroad.length === 0, tooBroad.join(' '));
  check(
    '  └ 启用时同时设置了 real_ip_header 与 real_ip_recursive',
    activeRealIp.some((line) => /real_ip_header/.test(line)) &&
      activeRealIp.some((line) => /real_ip_recursive/.test(line)),
  );
}

check(
  '模板 include 了真实 IP snippet（无论启不启用，include 必须能成功）',
  /include\s+\S*bluewhale-real-ip\.conf\s*;/.test(rendered),
);

/* -------------------------------------------------------------------- 结论 */

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log('  注意：这是结构性自检，**不能替代 `nginx -t`** —— 首次部署前必须真跑一次。');
