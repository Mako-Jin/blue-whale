# 工作空间规则（BlueWhale）

## 一、硬规则：项目数据一律不得写入 C 盘

本项目全部依赖缓存、构建产物、临时文件、日志、下载物、运行时数据，**只允许写入工作空间 `f:\blue-whale` 内**。
禁止写入以下位置（含其任意子目录）：

- `C:\Users\<用户>\AppData\Local\Temp`、`C:\Windows\Temp`
- `C:\Users\<用户>\AppData\Local\npm-cache`、`AppData\Local\pnpm`、`AppData\Local\Yarn`
- `C:\Users\<用户>\.m2`、`.gradle`、`.cache`、`.npm`、`.pnpm-store`
- `C:\Users\<用户>\.local\share`（工具下载物 / 二进制）
- `C:\Users\<用户>\logs`、`C:\Users\<用户>\nacos` 等项目运行时目录

## 二、目录约定（统一落 `f:\blue-whale\.toolchains`）

| 用途 | 路径 |
| --- | --- |
| npm 缓存 | `f:\blue-whale\.toolchains\npm-cache` |
| pnpm store | `f:\blue-whale\.toolchains\pnpm\store` |
| Maven 仓库 | `F:\respository\maven`（`D:\maven\conf\settings.xml` 中 `localRepository`） |
| Maven/JVM 临时目录 | `f:\blue-whale\.toolchains\tmp`（见 `.mvn/jvm.config`） |
| 本地工具与下载物 | `f:\blue-whale\.toolchains\bluewhale-tools`（Memurai / Redis / 启动脚本） |
| 本地基础设施数据 | `f:\blue-whale\.toolchains\infra`（Nacos 注册数据、日志等） |
| uv 缓存 | `f:\blue-whale\.toolchains\uv`（`UV_CACHE_DIR`） |
| pip 缓存 | `f:\blue-whale\.toolchains\pip`（`pip.ini` 的 `global.cache-dir`） |
| XDG 缓存 | `f:\blue-whale\.toolchains\cache`（`XDG_CACHE_HOME`） |
| Playwright 浏览器 | `f:\blue-whale\.toolchains\playwright`（`PLAYWRIGHT_BROWSERS_PATH`） |
| 组件日志 | `f:\blue-whale\.toolchains\bluewhale-tools\logs` |

`.toolchains/` 已被 `.gitignore` 忽略：可按需重建，**不得提交入库**。

## 三、强制配置（已落地，修改前需评估）

1. 仓库根 `.npmrc`：`cache=f:\blue-whale\.toolchains\npm-cache`、`registry=https://registry.npmmirror.com/`。
2. 全局 npm/pnpm 配置：`npm config get cache`、`pnpm config get store-dir` 均指向 `.toolchains`。
3. `.mvn/jvm.config`：`-Djava.io.tmpdir=F:\blue-whale\.toolchains\tmp`，Maven 及其派生 JVM 临时文件不落 C 盘。
4. Maven 仓库由全局 `settings.xml` 固定为 `F:\respository\maven`，不得改回 `C:\Users\<用户>\.m2`。
5. 启动脚本（`run-*.ps1`、`fetch-memurai.ps1`、`gateway-local.yml`）中所有路径必须使用 `f:\blue-whale\...`，禁止出现 `C:\Users\...`。

## 四、日常执行要求

- 运行 npm/pnpm 前确认缓存路径生效；如需临时变量，显式设置
  `npm_config_cache` / `TEMP` / `TMP` 到 `.toolchains`。
- 运行 Maven 时如需额外 JVM 参数，保持 `-Djava.io.tmpdir=F:\blue-whale\.toolchains\tmp`。
- 下载安装包、解压二进制、抓取日志、生成临时验证脚本，一律放 `.toolchains` 或项目内被忽略目录（`/tmp`、`/logs`、`/.cache`）。
- 发现任何命令/工具把数据写到 C 盘，必须迁移到 `.toolchains` 并修正调用方式或脚本；迁移后清理 C 盘残留。
- 新引入工具链（JDK 之外的 SDK、包管理器、数据库、缓存服务）默认安装/存储路径必须是 `F:` 盘工作空间内。

## 五、豁免（仅限无法迁移的运行时与系统项）

以下为 IDE/系统自身占用，**只允许其自身最小运行数据**，不得主动向其中写入项目数据：

- `C:\Users\<用户>\.workbuddy`：IDE 运行时（二进制、模型缓存），运行中不可迁移。
- `C:\Users\<用户>\.trae-cn`：IDE 配置、插件、会话记忆。
- 已安装到 C 盘的系统程序本体及其必需配置（注册表、服务、驱动）。
- 工具必需的配置文件（如 `~/.npmrc`，仅数十字节），不得在其中存放缓存或业务数据。

## 六、自检命令

```powershell
npm config get cache          # 期望 f:\blue-whale\.toolchains\npm-cache
pnpm config get store-dir     # 期望 f:\blue-whale\.toolchains\pnpm\store
mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout
                              # 期望 F:\respository\maven
```
