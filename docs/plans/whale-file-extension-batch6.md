# whale-file 能力扩展（批次 6：上传内容魔数校验）

## 一、范围与决策

批次 1~5 已完成存储类型路由、回收站生命周期、重命名/移动、指标与健康检查、孤儿对象对账、
分享链接、批量删除与打包下载。本批次落地路线图阶段 2 的「病毒扫描与魔数校验」中的**魔数校验**部分。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 文件头魔数识别 | ✅ | 32 字节头部嗅探 PDF/ZIP/PNG/JPEG/GIF/GZIP/RAR/7Z/OLE2/MP4/MP3/EXE/ELF/Mach-O |
| 可执行文件拦截 | ✅ | 识别为 EXE / ELF / Mach-O 时拒绝上传，类型列表可配置 |
| 扩展名伪装识别 | ✅ | 扩展名与内容已知类型不一致时拒绝（如 `.png` 实为 ZIP） |
| 未知类型放行 | ✅ | 头部无法识别一律放行，避免自定义/加密格式被误伤 |
| 校验开关 | ✅ | `whale.file.security.magic-enabled`，默认开启，可整体回退 |
| 普通上传接入 | ✅ | 内容已在内存，直接取前 32 字节校验 |
| 分片上传接入 | ✅ | `PushbackInputStream` 读取头部后回推，不复制整份内容 |
| 秒传接入 | ❌ | 秒传无内容可查，仅登记既有对象；文档注明该路径不校验 |
| ClamAV 病毒扫描 | ❌ | 需要外部守护进程与端口适配，留待后续批次，避免引入未使用的端口 |
| 压缩包内含物检查 | ❌ | 只校验容器类型，不展开 ZIP 内部条目 |

**安全原则**：校验基于**内容**而非扩展名或客户端声明的 MIME；拒绝时统一返回 `PARAM_ERROR` 并给出
可区分的提示文案；校验在配额查询、MD5 计算与存储写入之前完成，失败不产生任何副作用。

## 二、设计

### 2.1 领域层

- `FileMagicType`：内容类型枚举（PDF/ZIP/PNG/JPEG/GIF/GZIP/RAR/SEVEN_ZIP/OLE2/MP4/MP3/EXE/ELF/MACHO/UNKNOWN）。
- `FileSignatureInspector#detect(byte[] header)`：静态、无状态，按固定偏移匹配魔数；
  `header` 允许为 `null` 或短于 32 字节，不足判定长度时按不匹配处理。

| 类型 | 判定 |
| --- | --- |
| PDF | 偏移 0 `%PDF-` |
| ZIP | 偏移 0 `50 4B 03 04` / `50 4B 05 06` / `50 4B 07 08`（含 docx/xlsx/pptx） |
| PNG | 偏移 0 `89 50 4E 47 0D 0A 1A 0A` |
| JPEG | 偏移 0 `FF D8 FF` |
| GIF | 偏移 0 `GIF87a` / `GIF89a` |
| GZIP | 偏移 0 `1F 8B` |
| RAR | 偏移 0 `52 61 72 21 1A 07`（RAR4/RAR5 共前缀） |
| 7Z | 偏移 0 `37 7A BC AF 27 1C` |
| OLE2 | 偏移 0 `D0 CF 11 E0 A1 B1 1A E1`（含 doc/xls/ppt） |
| MP4 | 偏移 4 `ftyp` |
| MP3 | 偏移 0 `ID3`，或 `FF Ex`（MPEG 帧同步位） |
| EXE | 偏移 0 `MZ` |
| ELF | 偏移 0 `7F 45 4C 46` |
| Mach-O | `FE ED FA CE/CF`、`CE FA ED FE/CF`、`CA FE BA BE`（通用二进制） |

### 2.2 应用层

`FileContentPolicy`（`service/support`，`@Component`）暴露：

- `headerLength()`：嗅探所需字节数（32），供调用方按需读取；
- `validate(String originalName, byte[] header)`：按顺序执行
  1. 开关关闭 → 直接放行；
  2. 识别类型命中 `whale.file.security.blocked-types` → `PARAM_ERROR`「禁止上传可执行文件」；
  3. 扩展名有映射且识别为已知类型但与映射不符 → `PARAM_ERROR`「文件内容与扩展名不匹配」；
  4. 识别为 `UNKNOWN` → 放行。

扩展名映射：`pdf→PDF`、`png→PNG`、`jpg/jpeg→JPEG`、`gif→GIF`、`zip→ZIP`、`docx/xlsx/pptx→ZIP`、
`doc/xls/ppt→OLE2`、`mp4→MP4`、`mp3→MP3`、`gz→GZIP`、`rar→RAR`、`7z→SEVEN_ZIP`；扩展名大小写不敏感。

不采用客户端声明的 `Content-Type` 作为判定依据：浏览器与各类 SDK 常统一填
`application/octet-stream`，用它做一致性校验会大量误伤；扩展名是稳定的声明信号。

### 2.3 接入点

- `upload`：大小预检后、配额查询与 MD5 前，取内存内容前 32 字节校验；
- `completeChunkUpload`：合并流外包 `PushbackInputStream`（缓冲 32），`readNBytes` 读取头部并校验后
  `unread`，再交给 `persistFile`，保证写入存储的仍是完整内容；
- `rapidUpload`：无内容可校验，仅登记既有对象，文档注明（内容在首次上传时已校验）。

### 2.4 配置（`FileProperties.Security`）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `whale.file.security.magic-enabled` | true | 魔数校验总开关 |
| `whale.file.security.blocked-types` | EXE,ELF,MACHO | 拒绝上传的内容类型名（`FileMagicType` 常量名，大小写不敏感） |

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 领域 | 14 类魔数识别、MP4 偏移 4、短头部/空头部/`null` 返回 UNKNOWN、MP3 帧同步 |
| 应用（策略） | 可执行文件拦截、扩展名不匹配拒绝、未知类型放行、开关关闭放行、无扩展名放行、大写扩展名、blocked-types 自定义 |
| 应用（上传） | `upload` 内容与扩展名不符拒绝且不写存储、可执行文件拒绝；`completeChunkUpload` 伪装内容拒绝且保留分片 |

## 四、完成情况

- 领域：新增 `FileMagicType`（15 类）与 `FileSignatureInspector#detect`，32 字节头部嗅探；
  MP4 按偏移 4 的 `ftyp`、MP3 兼容 `ID3` 与 MPEG 帧同步、Mach-O 覆盖 5 种魔数；
  短头部、空头部与 `null` 不越界，统一返回 `UNKNOWN`；新增 `content` 子包 `package-info`。
- 应用：新增 `FileContentPolicy`（`service/support`）：开关关闭直接放行；识别为已知类型且命中
  `blocked-types` 时拒绝；扩展名有映射且与识别结果不符时拒绝；`UNKNOWN` 一律放行。
  扩展名映射覆盖 pdf/png/jpg/jpeg/gif/zip/docx/xlsx/pptx/doc/xls/ppt/mp4/mp3/gz/rar/7z，大小写不敏感。
- 接入：`upload` 在大小预检后、配额查询与 MD5 前，用内存内容前 32 字节校验；
  `completeChunkUpload` 用 `PushbackInputStream(32)` 读取头部校验后 `unread` 回推，再交给
  `persistFile`；`rapidUpload` 不校验并在文档注明原因。
- 配置：`FileProperties.Security`（`magic-enabled` / `blocked-types`）与 `application.yml` 的
  `whale.file.security.*` 环境变量绑定（`WHALE_FILE_SECURITY_MAGIC_ENABLED` /
  `WHALE_FILE_SECURITY_BLOCKED_TYPES`）。
- 测试：`FileSignatureInspectorTest` 5 项、`FileContentPolicyTest` 9 项、
  `FileInfoServiceImplTest` 新增 3 项（可执行文件拒绝、扩展名不符拒绝、分片内容不符拒绝并保留分片）。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
266 项单测零失败（批次 5 为 249，本轮新增 17 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 扩展名映射初版用不可变 `Map.ofEntries`，`get(null)` 直接抛 `NullPointerException`，
  无扩展名文件上传会变成 500；改为 `expectedOf` 在查询前对空扩展名短路，
  并补充「无扩展名」「文件名为 `null`」用例。
- 策略初版签名带 `contentType` 参数但没有使用，容易让后续维护者误以为已做 MIME 一致性校验；
  去掉该参数，只在类文档说明不使用客户端声明类型的原因。

## 七、待做项

- ClamAV 病毒扫描适配器（外部守护进程 + 超时/降级策略）。
- 压缩包内含物检查（ZIP 条目名与嵌套可执行文件）。
- 图片/文档二次解码校验（防畸形文件触发下游解析器漏洞）。
- 批次 1 路线图其余项：租户/桶级配额、文件版本、存储迁移、CDN、领域事件、多段 Range、加密、策略热更新。
