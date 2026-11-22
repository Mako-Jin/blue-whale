#!/usr/bin/env node
/**
 * 从 `docs/specs/overview-contract/overview.schema.json` 生成前端类型。
 *
 * 为什么要有这个脚本：概览契约是前后端**唯一**的接缝，手写 TS 类型一定会漂移
 * （后端加了个字段、前端忘了加，编译不报错、运行时字段是 undefined）。
 * 生成 + `--check` 进 CI 之后，漂移在提交时就红。
 *
 * 用法：
 *   node scripts/gen-overview-types.mjs            # 生成 src/world/overview.types.ts
 *   node scripts/gen-overview-types.mjs --check    # 只校验是否同步，不同步 exit 1
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const appRoot = resolve(here, '..');
const schemaPath = resolve(appRoot, '../../../docs/specs/overview-contract/overview.schema.json');
const outPath = resolve(appRoot, 'src/world/overview.types.ts');

const checkOnly = process.argv.includes('--check');

const schema = JSON.parse(readFileSync(schemaPath, 'utf8'));
const defs = schema.$defs ?? {};

/**
 * 枚举字面量用**单引号**输出。
 *
 * <p>仓库的前端代码经 prettier 统一成单引号，生成物在提交前也会被格式化；
 * 若这里用 {@code JSON.stringify} 出双引号，`--check` 就会因为引号风格
 * 永远报"不同步"——真不同步与假不同步混在一起，门禁等于没有。
 * 枚举值都是标识符式字符串（live / bar / count），转义只防单引号本身。</p>
 *
 * @param value 枚举值
 * @returns 单引号 TS 字面量
 */
function literal(value) {
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;
}

/** 把 schema 的类型表达式翻成 TS */
function tsType(node, indent) {
  if (!node || typeof node !== 'object') return 'unknown';
  if (node.$ref) {
    const name = node.$ref.split('/').pop();
    return name;
  }
  if (Array.isArray(node.enum)) {
    return node.enum.map((value) => literal(value)).join(' | ');
  }
  if (node.type === 'array') {
    const item = tsType(node.items, indent);
    return item.includes(' ') ? `Array<${item}>` : `${item}[]`;
  }
  if (node.type === 'number' || node.type === 'integer') return 'number';
  if (node.type === 'boolean') return 'boolean';
  if (node.type === 'string') return 'string';
  if (node.type === 'object' && node.properties) {
    return inlineObject(node, indent);
  }
  return 'unknown';
}

/** 内联对象（当前契约没用到，留作兜底，避免生成 unknown） */
function inlineObject(node, indent) {
  const pad = '  '.repeat(indent + 1);
  const required = new Set(node.required ?? []);
  const lines = Object.entries(node.properties).map(([key, value]) => {
    const optional = required.has(key) ? '' : '?';
    return `${pad}${key}${optional}: ${tsType(value, indent + 1)};`;
  });
  return `{\n${lines.join('\n')}\n${'  '.repeat(indent)}}`;
}

/** 生成一个具名定义块 */
function emitBlock(name, node) {
  const required = new Set(node.required ?? []);
  const lines = [];
  const doc = node.description ? [`/** ${node.description} */`] : [];
  if (node.enum) {
    if (doc.length > 0) lines.push(...doc);
    lines.push(`export type ${name} = ${node.enum.map((v) => literal(v)).join(' | ')};`);
    return lines.join('\n');
  }
  if (node.type === 'object' && node.properties) {
    lines.push(`export interface ${name} {`);
    for (const [key, value] of Object.entries(node.properties)) {
      if (value.description) lines.push(`  /** ${value.description} */`);
      const optional = required.has(key) ? '' : '?';
      lines.push(`  ${key}${optional}: ${tsType(value, 1)};`);
    }
    lines.push('}');
    return lines.join('\n');
  }
  return `export type ${name} = ${tsType(node, 0)};`;
}

const header = [
  '/**',
  ' * 自动生成，请勿手改。',
  ' *',
  ' * 来源：docs/specs/overview-contract/overview.schema.json',
  ' * 重新生成：pnpm --filter whale-world gen:overview',
  ' * 校验同步：pnpm --filter whale-world check:overview',
  ' */',
  '',
].join('\n');

const defNames = Object.keys(defs);
const defBlocks = defNames.map((name) => emitBlock(name, defs[name]));
const rootName = schema.title ?? 'OverviewSnapshot';
const rootBlock = emitBlock(rootName, schema);

const output = `${header}${defBlocks.join('\n\n')}\n\n${rootBlock}\n`;

if (checkOnly) {
  if (!existsSync(outPath)) {
    console.error(`[overview-types] 缺少生成物：${outPath}`);
    process.exit(1);
  }
  const current = readFileSync(outPath, 'utf8');
  if (current !== output) {
    console.error('[overview-types] 生成物与 schema 不同步，请执行：pnpm --filter whale-world gen:overview');
    process.exit(1);
  }
  console.log('[overview-types] 同步（OK）');
  process.exit(0);
}

writeFileSync(outPath, output, 'utf8');
console.log(`[overview-types] 已生成 ${outPath}（${defNames.length + 1} 个类型）`);
