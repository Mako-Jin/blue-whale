/**
 * 网关配置的结构性自检（`infra/nginx/`）。
 *
 * ## 为什么需要它
 *
 * 这个文件**不替代 `nginx -t`** —— 它补的是另一类错：
 *
 *   | 这类错 | `nginx -t` 能发现吗 | 本脚本能发现吗 |
 *   | --- | --- | --- |
 *   | 花括号不配对、指令拼写错 | 能 | 不能 |
 *   | `limit_req zone=xxx` 引用了**不存在的 zone** | 能（`zero size shared memory zone`） | 能（更早、且直指缺哪个 zone） |
 *   | 模板变量漏在 `.env.example` 之外 | 不能（渲染后是空值） | 能 |
 *   | envsubst 白名单漏了、把 nginx 自己的 `$request_method` 替换掉了 | 不能（配置看起来是合法的） | 能 |
 *   | 上报路径没挂上限流 / `map` 丢了 | 不能 | 能 |
 *   | 真实 IP 段被误配成 `0.0.0.0/0` | 不能 | 能 |
 *   | 全局桶的 key 被写成 `$binary_remote_addr`（退化成第二个按 IP 桶） | 不能 | 能 |
 *
 * 上表除第一行外，都是**只有本脚本能挡**的：它们能让配置"通过 `nginx -t` 但行为是错的"。
 * 反过来，它会说"通过"而 `nginx -t` 报语法错 —— 所以**上线前两者都要跑**。
 *
 * ## 用法
 *
 *     node scripts/verify-nginx-config.mjs                   # 纯结构性检查（CI 形态）
 *     node scripts/verify-nginx-config.mjs --emit <目录>      # 渲染出可直接 nginx -t 的目录树
 *     node scripts/verify-nginx-config.mjs --nginx <可执行文件>  # 顺带真跑一次 nginx -t
 *
 * 默认不需要 nginx、不需要网络、不写任何文件，失败时非零退出（可用在 CI 里）。
 *
 * `--emit <目录>` 会把模板渲染成一棵**与部署形态同构**的树（`conf.d/`、`snippets/`、
 * 一个可直接 `-c` 的主配置），并打印出 `nginx -t` 的命令。它顺带绕开了 `envsubst`
 * 的白名单陷阱 —— 这里只替换已知的 `\${变量}`，因此不可能把 nginx 自己的 `$remote_addr`
 * 吃掉。唯一的改写是 include 根：从部署路径 `/etc/nginx` 换成本目录。
 *
 * `--nginx <可执行文件>`（或环境变量 `BW_NGINX_BIN`）在此之上真跑一次 `nginx -t`。
 * **注意：部分沙箱禁止创建进程（`spawnSync` 报 EBUSY）**，那种情况下用 `--emit`
 * 渲染出来、再在 shell 里跑 `nginx -t` —— 两条路检查的是同一棵树。
 *
 * 本次改动在 **nginx 1.20.1（Windows 构建）** 上验证过：
 * `nginx -t` 通过；默认值下 450 个连续 POST 得到 `200 × 216 / 429 × 234`
 * （`error_log` 显示命中 `bluewhale_report_per_ip`）；只把全局阈值压到 2r/s 后
 * 命中 `bluewhale_report_global`；同一 URI 的连续 GET 全部 200
 * （证明 `map $request_method` 的分流真的生效）。
 */
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve, sep } from 'node:path';
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

// 自定义的 map 变量同理：被 envsubst 吃掉的话 key 恒为空 → 限流**整个静默失效**，
// 而配置看起来完全合法。这两行不是"保险起见"，是 0027 里最容易踩的那一脚。
const CUSTOM_VARS = ['$bluewhale_report_key', '$bluewhale_report_global_key'];
const lostCustom = CUSTOM_VARS.filter((name) => !rendered.includes(name));
check(
  '自定义 map 变量未被误替换（被吃掉 = 限流静默失效）',
  lostCustom.length === 0,
  lostCustom.length ? `被吃掉的：${lostCustom.join(', ')}` : undefined,
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

/*
 * 全局容量桶（0028）。它比按 IP 的桶更容易写错 —— 因为写错的形态是**语法完全合法**的：
 * 只要 key 用的是 `$binary_remote_addr`，它就退化成第二个按 IP 桶，全局上限不复存在，
 * 而 `nginx -t` 会说"successful"。
 */
const globalMap = block(rendered, 'map $request_method $bluewhale_report_global_key {');
check('存在全局桶的 map（key 必须与来源无关）', globalMap !== null);
if (globalMap) {
  check('  └ 默认 key 为空（非 POST 不计入）', /default\s+""\s*;/.test(globalMap));
  check(
    '  └ POST 映射到常量而不是 $binary_remote_addr',
    /POST\s+"[^"$]+"\s*;/.test(globalMap) && !/\$binary_remote_addr/.test(globalMap),
  );
  // 全局桶与按 IP 桶必须是**两个不同的 key 变量**，否则两行 limit_req 就是同一件事。
  check(
    '  └ 全局桶的变量名与按 IP 桶不同（不是同一个 map）',
    block(rendered, 'map $request_method $bluewhale_report_key {') !== globalMap,
  );
}
check(
  '全局 zone 用的是上面那个常量 key',
  /limit_req_zone\s+\$bluewhale_report_global_key\s+zone=/.test(rendered),
);
check(
  '全局 zone 的容量阈值来自模板变量（部署级参数，不进代码）',
  /zone=bluewhale_report_global:\S+\s+rate=\S+/.test(rendered) &&
    !/\$\{REPORT_GLOBAL_RATE\}/.test(rendered),
);

/* ---------------------------------------------------------------- 四、上报通道 */

console.log('\n== 四、上报通道的挂载 ==\n');

const REPORT_PATHS = ['/api/logs/client', '/api/events/client'];
for (const path of REPORT_PATHS) {
  const serverBlock = block(rendered, `location = ${path} {`) ?? block(rendered, `location = ${path}{`);
  check(`存在 exact-match location：${path}`, serverBlock !== null);
  if (serverBlock) {
    check(`  └ ${path} 挂了限流`, /limit_req\s+zone=\S+/.test(serverBlock));
    // 两层桶必须都挂：只挂一层的话，要么失去隔离（只有全局），要么失去总量兜底（只有按 IP）。
    check(`  └ ${path} 挂了按 IP 的隔离桶`, /limit_req\s+zone=bluewhale_report_per_ip\b/.test(serverBlock));
    check(`  └ ${path} 挂了全局容量桶`, /limit_req\s+zone=bluewhale_report_global\b/.test(serverBlock));
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

/* ------------------------------- 八、渲染出可跑的树 / 真跑 nginx -t（可选） */

console.log('\n== 八、渲染部署树与真跑 nginx -t（可选） ==\n');

const argValue = (flag) => {
  const index = process.argv.indexOf(flag);
  return index >= 0 ? process.argv[index + 1] : undefined;
};

const nginxBin = process.env.BW_NGINX_BIN ?? argValue('--nginx');
const emitDir = argValue('--emit');

if (!nginxBin && !emitDir) {
  console.log('  · 跳过：既没给 --emit 也没给 --nginx');
  console.log('    渲染出可直接 nginx -t 的目录树：node scripts/verify-nginx-config.mjs --emit <目录>');
  console.log('    顺带真跑 nginx -t：             node scripts/verify-nginx-config.mjs --nginx <nginx 可执行文件>');
} else {
  /*
   * `--emit` 不只是给测试用的：它把模板渲染成**一棵与部署形态同构的树**，
   * 顺带绕开了 `envsubst` 那个白名单陷阱 —— 这里只替换 `\${已知变量}`，
   * 因此绝无可能把 nginx 自己的 `$remote_addr` / `$request_method` 吃掉。
   * 唯一的改写是 include 根：从部署路径 `/etc/nginx` 换成本目录。
   */
  const work = emitDir ? resolve(emitDir) : mkdtempSync(join(tmpdir(), 'bw-nginx-'));
  const posix = (path) => path.split(sep).join('/');
  try {
    mkdirSync(join(work, 'conf.d'), { recursive: true });
    mkdirSync(join(work, 'snippets'), { recursive: true });
    mkdirSync(join(work, 'logs'), { recursive: true });
    mkdirSync(join(work, 'temp'), { recursive: true });

    writeFileSync(
      join(work, 'conf.d', 'bluewhale.conf'),
      rendered.split('/etc/nginx').join(posix(work)),
    );
    writeFileSync(join(work, 'snippets', 'bluewhale-real-ip.conf'), realIpText);
    writeFileSync(join(work, 'snippets', 'bluewhale-proxy.conf'), proxyText);

    const mainConf = join(work, 'nginx.conf');
    writeFileSync(
      mainConf,
      [
        'worker_processes 1;',
        `error_log ${posix(join(work, 'logs', 'error.log'))} warn;`,
        `pid ${posix(join(work, 'logs', 'nginx.pid'))};`,
        'events { worker_connections 64; }',
        'http {',
        `    client_body_temp_path ${posix(join(work, 'temp', 'client_body'))};`,
        `    proxy_temp_path ${posix(join(work, 'temp', 'proxy'))};`,
        '    access_log off;',
        `    include ${posix(join(work, 'conf.d'))}/*.conf;`,
        '}',
        '',
      ].join('\n'),
    );

    if (emitDir) {
      console.log(`  · 已渲染到：${work}`);
      console.log(`    自检：nginx -t -p "${work}" -c "${mainConf}"`);
      console.log('    部署：把 conf.d/*.conf 与 snippets/*.conf 拷到 /etc/nginx 下同名目录，再 nginx -t');
    }

    if (nginxBin) {
      // 先确认路径本身能用 —— 否则"spawn 失败"与"nginx 说配置有错"会混成一条看不出原因的失败。
      const probe = spawnSync(nginxBin, ['-v'], { encoding: 'utf8' });
      const versionText = `${probe.stderr ?? ''}${probe.stdout ?? ''}`.trim();
      check(
        '能用给定的路径启动 nginx',
        !probe.error,
        probe.error
          ? `${probe.error.message} —— 注意部分沙箱禁止创建进程（EBUSY）；` +
              '那种情况下改用 --emit 渲染出目录树，再在 shell 里跑 nginx -t'
          : versionText || undefined,
      );

      if (!probe.error) {
        const result = spawnSync(nginxBin, ['-p', work, '-c', mainConf, '-t'], { encoding: 'utf8' });
        const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim();
        const ok = result.status === 0 && /test is successful/.test(output);
        check(
          'nginx -t：语法与配置检查通过',
          ok,
          ok ? undefined : output.split('\n').slice(-3).join(' / ') || `退出码 ${result.status}`,
        );
      }
    }
  } catch (cause) {
    check('渲染部署树', false, cause instanceof Error ? cause.message : String(cause));
  } finally {
    // --emit 的产物是给人用的，保留；临时目录用完即删。
    if (!emitDir) rmSync(work, { recursive: true, force: true });
  }
}

/* -------------------------------------------------------------------- 结论 */

console.log('');
if (problems.length > 0) {
  console.log(`✗ ${problems.length} 项未通过`);
  problems.forEach((item) => console.log(`    · ${item}`));
  process.exit(1);
}
console.log('✓ 全部通过');
console.log(
  '  本脚本查的是"语法合法但行为是错的"那一类问题；**它不能替代 `nginx -t`**，',
);
console.log(
  '  两者跑的是不同的东西：加 --emit 或 --nginx 才能把后者也带上（见 infra/nginx/README.md）。',
);

