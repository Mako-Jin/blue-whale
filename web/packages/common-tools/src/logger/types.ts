/**
 * 日志级别。
 *
 * 分级同时承担两件事：
 * 1. **过滤** —— 低于阈值的日志直接返回，不做任何字符串拼接；
 * 2. **配色** —— 每个级别在主题中对应一套 fg / bg / border。
 */
export type LogLevel = 'trace' | 'debug' | 'perf' | 'info' | 'success' | 'warn' | 'error';

/** 过滤阈值。`'silent'` 表示关闭全部输出。 */
export type LogLevelThreshold = LogLevel | 'silent';

/** 单个级别的配色。 */
export interface ColorScheme {
  /** 前景色（文字） */
  fg: string;
  /** 背景色（级别标签底色） */
  bg: string;
  /** 强调色（边框 / 分隔） */
  border: string;
}

/** 级别 → 配色。 */
export type ThemeColors = Record<LogLevel, ColorScheme>;

/** 中性色：与级别无关、所有级别共用的元素。 */
export interface ThemePalette {
  /** 时间戳 */
  timestamp: string;
  /** 命名空间 */
  namespace: string;
  /** 正文 */
  message: string;
  /** 次要文字（上下文、键名等） */
  muted: string;
  /** 强调（小标题、数值） */
  accent: string;
  /** 链接 */
  link: string;
  /** 分隔线 */
  divider: string;
}

/** 完整主题。 */
export interface Theme {
  colors: ThemeColors;
  palette: ThemePalette;
}

/**
 * 主题入参：允许只覆盖「某个级别的某个字段」，其余沿用基主题。
 *
 * 与旧实现的关键差异：旧实现是浅合并 `{ ...DEFAULT, ...patch }`，
 * 一旦调用方只传 `{ info: { fg: '#f00' } }`，该级别的 bg / border 会变成 undefined；
 * 现在按级别、按字段深合并，不会出现"半个色板"。
 */
export interface ThemeInput {
  colors?: Partial<Record<LogLevel, Partial<ColorScheme>>>;
  palette?: Partial<ThemePalette>;
}

/** 时间戳格式。也可直接传入自定义格式化函数。 */
export type TimestampFormat =
  'HH:mm:ss' | 'HH:mm:ss.SSS' | 'YYYY-MM-DD HH:mm:ss' | 'YYYY-MM-DD HH:mm:ss.SSS' | 'ISO';

/**
 * 着色渲染模式。
 *
 * - `css`   —— 浏览器控制台，走 `%c` + 样式串；
 * - `ansi`  —— 终端，走 24 位真彩转义序列；
 * - `plain` —— 不着色（重定向到文件 / CI 管道 / 显式关闭）。
 */
export type RenderMode = 'css' | 'ansi' | 'plain';

/** 需要透传到 console 的方法名 —— sink 本质是一个 console 代理。 */
export type ConsoleMethod =
  | 'log'
  | 'info'
  | 'debug'
  | 'warn'
  | 'error'
  | 'table'
  | 'group'
  | 'groupCollapsed'
  | 'groupEnd'
  | 'clear';

/**
 * 输出通道 —— 本质是 console 代理。
 *
 * 拿到的是**已渲染**的参数（浏览器下还夹着 `%c` 样式串），因此只适合消费"样子"：
 * - 单元测试断言（捕获而非打印）
 * - 页面内的可视化镜像
 *
 * 适合消费"事实"（结构化字段、上报、落库、聚合）的是 {@link LogRecordHandler}，
 * 不要用本通道做上报 —— 它拿不到级别与元数据的结构化形态，只能反向解析字符串。
 */
export type LogSink = (method: ConsoleMethod, args: unknown[]) => void;

/**
 * 结构化日志记录。
 *
 * 这是「事实」本身，与渲染无关：`LogSink` 拿到的是渲染后的样子，
 * `onRecord` 拿到的是这条记录。上报 / 落库 / 聚合一律消费这个结构。
 */
export interface LogRecord {
  /** 级别。注意与 {@link ConsoleMethod} 的差异：`info` 与 `success` 在这里是分得开的 */
  level: LogLevel;
  /** 命名空间，形如 `app › auth › login` */
  namespace: string;
  /** 毫秒时间戳 */
  timestamp: number;
  /** 消息正文，未经着色与格式拼接 */
  message: string;
  /** 随消息附带的原始数据（对象保持对象形态，不被 stringify） */
  data: unknown[];
  /** 结构化上下文 */
  context?: Record<string, unknown>;
  /** 链路标识，用于跨端串联同一次请求的日志 */
  traceId?: string;
  /** 会话标识 */
  sessionId?: string;
  /** 当前路由 */
  route?: string;
  /** 应用版本 / 构建号 */
  release?: string;
}

/** 结构化记录钩子。 */
export type LogRecordHandler = (record: LogRecord) => void;

/** 样式表：键为 CSS 属性名。 */
export type StyleMap = Record<string, string | number>;

/** 一行日志中的一个片段。 */
export interface LogSegment {
  text: string;
  style: StyleMap;
}

/**
 * Logger 配置。
 *
 * 解析顺序：内置默认 → 环境默认 → 全局配置 → 实例配置。
 * 每一层都只覆盖「显式设置了」的字段（undefined 不参与覆盖）。
 */
export interface LoggerConfig {
  /** 过滤阈值，低于该级别的日志直接返回 */
  level?: LogLevelThreshold;
  /** 是否输出时间戳，默认 true */
  showTimestamp?: boolean;
  /** 时间戳格式，默认 'HH:mm:ss.SSS' */
  timestampFormat?: TimestampFormat | ((date: Date) => string);
  /** 是否输出命名空间，默认 true */
  showNamespace?: boolean;
  /** 是否输出级别标签，默认 true */
  showLevel?: boolean;
  /** 是否着色。不设置时按运行环境自动判定 */
  colorize?: boolean;
  /** 主题覆盖，与内置主题深合并 */
  theme?: ThemeInput;
  /** 命名空间层级分隔符，默认 ' › ' */
  namespaceSeparator?: string;
  /** 每条日志附带的结构化上下文（如 traceId），以 `k=v` 形式追加在行尾 */
  context?: Record<string, unknown>;
  /** 本实例专属的输出通道，优先级高于全局 sink */
  sink?: LogSink;
  /**
   * 结构化记录钩子。
   *
   * 与 {@link LoggerConfig.sink} 的分工：
   * - `sink` 消费**渲染后的样子**（控制台、测试捕获、页面镜像）；
   * - `onRecord` 消费**结构化事实**（上报、落库、聚合）。
   *
   * 两者可同时设置、互不影响；都不设置时只走默认 console。
   */
  onRecord?: LogRecordHandler;
  /** 链路标识，随每条记录发出（通常由请求层注入） */
  traceId?: string;
  /** 会话标识，随每条记录发出 */
  sessionId?: string;
  /** 当前路由，随每条记录发出 */
  route?: string;
  /** 应用版本 / 构建号，随每条记录发出 */
  release?: string;
  /** console.table 最多显示行数，0 表示不限制，默认 50 */
  maxTableRows?: number;
  /** 是否启用 console 分组，默认 true */
  enableGrouping?: boolean;
}

/** 已解析配置：所有字段都有确定值。 */
export interface ResolvedLoggerConfig {
  level: LogLevelThreshold;
  showTimestamp: boolean;
  timestampFormat: TimestampFormat | ((date: Date) => string);
  showNamespace: boolean;
  showLevel: boolean;
  colorize: boolean;
  renderMode: RenderMode;
  theme: Theme;
  namespaceSeparator: string;
  context: Record<string, unknown> | undefined;
  sink: LogSink | undefined;
  onRecord: LogRecordHandler | undefined;
  traceId: string | undefined;
  sessionId: string | undefined;
  route: string | undefined;
  release: string | undefined;
  maxTableRows: number;
  enableGrouping: boolean;
}

/** Banner 配置。 */
export interface BannerConfig {
  title: string;
  subtitle?: string;
  version?: string;
  githubUrl?: string;
  /** ASCII 艺术字（多行字符串）。不传则退化为单行标题 */
  asciiArt?: string;
  /** 打印前是否清屏，默认 false —— 不再默认清掉控制台里别人的输出 */
  clear?: boolean;
}

/** keyValue / keyValues 的配色与缩进。 */
export interface KeyValueOptions {
  keyColor?: string;
  valueColor?: string;
  indent?: number;
}

/** 链接条目。 */
export interface CreditLink {
  label: string;
  url: string;
}
