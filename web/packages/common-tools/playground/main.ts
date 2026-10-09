import {
  CONTRAST_THEME,
  DEFAULT_THEME,
  type LogLevelThreshold,
  type TimestampFormat,
  createLogger,
  setLoggerSink,
} from '../src/logger';
import { ConsoleMirror } from './mirror';

function must<T>(value: T | null | undefined, name: string): T {
  if (value === null || value === undefined) throw new Error(`缺少必要的 DOM 节点：${name}`);
  return value;
}

const output = must(document.querySelector<HTMLElement>('#console-output'), '#console-output');
const toolbar = must(document.querySelector<HTMLElement>('#toolbar'), '#toolbar');
const mirror = new ConsoleMirror(output);

/** 同时写页面镜像与真实控制台 —— 两边应当完全一致。 */
setLoggerSink((method, args) => {
  mirror.handle(method, args);
  const target = console as unknown as Record<string, ((...rest: unknown[]) => void) | undefined>;
  target[method]?.apply(console, args);
});

const app = createLogger('bluewhale').child('portal');
// 演示页默认全开，方便一次看清所有级别
app.configure({ level: 'trace' });

// ==================== 工具栏组件 ====================

function group(title: string): HTMLElement {
  const section = document.createElement('section');
  section.className = 'group';
  const heading = document.createElement('h2');
  heading.textContent = title;
  section.append(heading);
  toolbar.append(section);
  return section;
}

function action(parent: HTMLElement, label: string, onClick: () => void, modifier = ''): void {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = `btn ${modifier}`.trim();
  button.textContent = label;
  button.addEventListener('click', onClick);
  parent.append(button);
}

interface Option<T extends string> {
  value: T;
  label: string;
}

function selectField<T extends string>(
  parent: HTMLElement,
  label: string,
  options: readonly Option<T>[],
  value: T,
  onChange: (value: T) => void,
): void {
  const row = document.createElement('label');
  row.className = 'field';

  const caption = document.createElement('span');
  caption.textContent = label;

  const select = document.createElement('select');
  for (const option of options) {
    const item = document.createElement('option');
    item.value = option.value;
    item.textContent = option.label;
    select.append(item);
  }
  select.value = value;
  select.addEventListener('change', () => onChange(select.value as T));

  row.append(caption, select);
  parent.append(row);
}

function booleanField(
  parent: HTMLElement,
  label: string,
  checked: boolean,
  onChange: (checked: boolean) => void,
): void {
  const row = document.createElement('label');
  row.className = 'field';

  const caption = document.createElement('span');
  caption.textContent = label;

  const input = document.createElement('input');
  input.type = 'checkbox';
  input.checked = checked;
  input.addEventListener('change', () => onChange(input.checked));

  row.append(caption, input);
  parent.append(row);
}

// ==================== 级别 ====================

const levelGroup = group('级别与配色');

action(levelGroup, 'trace', () => app.trace('组件挂载前的准备工作'));
action(levelGroup, 'debug', () => app.debug('路由解析结果', { path: '/dashboard', matched: true }));
action(levelGroup, 'info', () => app.info('用户登录成功', { userId: 1024 }));
action(levelGroup, 'success', () => app.success('配置加载完成'));
action(levelGroup, 'perf', () => app.perf('首屏渲染', 128.4));
action(levelGroup, 'warn', () => app.warn('接口响应偏慢，已自动降级'));
action(levelGroup, 'error', () => app.error('订单创建失败', { orderId: 'A-1001' }));
action(levelGroup, 'error(Error)', () => app.error(new Error('远程服务不可用')));

// ==================== 层级 ====================

const namespaceGroup = group('命名空间层级');

action(namespaceGroup, 'child("auth")', () => app.child('auth').info('token 刷新成功'));
action(namespaceGroup, 'child("auth").child("login")', () =>
  app.child('auth').child('login').info('凭据校验通过'),
);
action(namespaceGroup, 'withContext', () =>
  app.withContext({ traceId: 'a1b2c3d4', retry: 2 }).info('请求重试'),
);
action(namespaceGroup, '子模块独立阈值', () => {
  const order = app.child('order', { level: 'debug' });
  order.debug('子模块用自己的阈值，父级配置照常继承');
});

// ==================== 结构 ====================

const structureGroup = group('结构化输出');

action(structureGroup, 'group 折叠分组', () =>
  app.group('启动流程', () => {
    app.info('加载配置');
    app.info('注册路由');
    app.success('启动完成');
  }),
);

action(structureGroup, 'table', () =>
  app.table(
    [
      { layer: 'Frontend', technology: 'React 19', detail: 'qiankun 主应用' },
      { layer: 'Build', technology: 'Vite 8', detail: 'pnpm + Turborepo' },
      { layer: 'Backend', technology: 'Spring Boot 3.3', detail: 'Java 21' },
    ],
    ['layer', 'technology', 'detail'],
  ),
);

action(structureGroup, 'time 计时', () => {
  const stop = app.time('模拟耗时任务');
  window.setTimeout(() => stop(), 60);
});

action(structureGroup, 'banner', () =>
  app.banner({
    title: 'BlueWhale',
    subtitle: '企业级协作平台',
    version: '0.1.0',
    asciiArt: [
      '▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄',
      '   B L U E W H A L E   P O R T A L',
      '▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀',
    ].join('\n'),
  }),
);

action(structureGroup, 'section / keyValue / divider', () => {
  app.section('运行环境');
  app.keyValues({ 构建模式: 'development', 渲染模式: 'css', 日志阈值: app.isEnabled('debug') });
  app.divider();
});

action(structureGroup, 'credits', () =>
  app.credits([
    { label: '文档', url: 'https://example.com/docs' },
    { label: '仓库', url: 'https://example.com/repo' },
  ]),
);

// ==================== 实时配置 ====================

const configGroup = group('实时配置（热更新已创建的实例）');

selectField<LogLevelThreshold>(
  configGroup,
  '阈值',
  [
    { value: 'trace', label: 'trace（全部）' },
    { value: 'debug', label: 'debug' },
    { value: 'info', label: 'info（默认）' },
    { value: 'perf', label: 'perf' },
    { value: 'success', label: 'success' },
    { value: 'warn', label: 'warn' },
    { value: 'error', label: 'error' },
    { value: 'silent', label: 'silent（静默）' },
  ],
  'trace',
  (value) => {
    app.configure({ level: value });
    app.info(`阈值已切换为 ${value}`);
  },
);

selectField<'default' | 'contrast'>(
  configGroup,
  '主题',
  [
    { value: 'default', label: '默认（深色控制台）' },
    { value: 'contrast', label: '高对比' },
  ],
  'default',
  (value) => {
    app.configure({ theme: value === 'contrast' ? CONTRAST_THEME : DEFAULT_THEME });
    app.info(`主题已切换为 ${value === 'contrast' ? '高对比' : '默认'}`);
  },
);

booleanField(configGroup, '着色', true, (checked) => {
  app.configure({ colorize: checked });
  app.info(checked ? '已开启着色' : '已关闭着色（纯文本，适合重定向到文件）');
});

selectField<TimestampFormat | 'off'>(
  configGroup,
  '时间戳',
  [
    { value: 'off', label: '不显示' },
    { value: 'HH:mm:ss', label: 'HH:mm:ss' },
    { value: 'HH:mm:ss.SSS', label: 'HH:mm:ss.SSS' },
    { value: 'YYYY-MM-DD HH:mm:ss', label: 'YYYY-MM-DD HH:mm:ss' },
    { value: 'ISO', label: 'ISO' },
  ],
  'HH:mm:ss.SSS',
  (value) => {
    if (value === 'off') {
      app.configure({ showTimestamp: false });
    } else {
      app.configure({ showTimestamp: true, timestampFormat: value });
    }
    app.info(`时间戳格式已切换为 ${value}`);
  },
);

booleanField(configGroup, '显示命名空间', true, (checked) => {
  app.configure({ showNamespace: checked });
  app.info('命名空间显示已切换');
});

booleanField(configGroup, '显示级别标签', true, (checked) => {
  app.configure({ showLevel: checked });
  app.info('级别标签显示已切换');
});

action(configGroup, '清空', () => app.clear(), 'btn--danger');
action(configGroup, '演示：全部级别一览', () => {
  app.trace('trace');
  app.debug('debug');
  app.info('info');
  app.success('success');
  app.perf('perf', 12.5);
  app.warn('warn');
  app.error('error');
});

// 首次进入给一段引导输出
app.banner({ title: 'BlueWhale', subtitle: 'Logger Playground', version: '0.1.0' });
app.info('点击左侧任意按钮开始体验 —— 输出会同时出现在这里和浏览器控制台');
