#!/usr/bin/env node
// 按改动文件筛选需要重新构建的 Docker 镜像（R8：只构建变更服务）
//
// 输入（环境变量）：
//   CHANGED_FILES  换行分隔的改动文件列表；为空表示没有改动
//   ALL            1 表示全量构建（tag / 手动触发 / 无法取到 diff 时）
//
// 输出（stdout）：{"backend":[matrix 条目...],"frontend":[matrix 条目...]}
//
// 规则：
//   1) 构建期共享物（根 pom、whale-parent/whale-common/whale-sdk、deploy 模板、
//      镜像清单本身）变化 → 该侧全量，避免漏掉传递依赖；
//   2) 服务/应用目录变化 → 只构建对应镜像；
//   3) 其他路径（docs、scripts、.github 等）不触发镜像构建。

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const manifestPath = resolve(here, '../../deploy/ci/images.json');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));

const backendAll = [
  'whale-backend/pom.xml',
  'whale-backend/whale-services/pom.xml',
  'whale-backend/whale-apps/pom.xml',
  'whale-backend/whale-ops/pom.xml',
  'whale-backend/whale-registry/pom.xml',
  'whale-backend/whale-parent/',
  'whale-backend/whale-common/',
  'whale-backend/whale-sdk/',
  'deploy/backend/',
  'deploy/ci/images.json',
];

const frontendAll = [
  'whale-frontend/package.json',
  'whale-frontend/pnpm-lock.yaml',
  'whale-frontend/pnpm-workspace.yaml',
  'whale-frontend/turbo.json',
  'whale-frontend/packages/',
  'whale-frontend/shared-packages/',
  'whale-frontend/vite-plugins/',
  'deploy/frontend/',
  'deploy/ci/images.json',
];

/** 判断文件是否落在某个前缀下 */
function under(file, prefix) {
  return file === prefix || file.startsWith(prefix);
}

const all = process.env.ALL === '1';
const files = (process.env.CHANGED_FILES ?? '')
  .split(/\r?\n/)
  .map((item) => item.trim())
  .filter(Boolean);

const backend = all || files.some((file) => backendAll.includes(file) || backendAll.some((prefix) => prefix.endsWith('/') && under(file, prefix)))
  ? manifest.backend
  : manifest.backend.filter((entry) => files.some((file) => under(file, entry.match)));

const frontend = all || files.some((file) => frontendAll.includes(file) || frontendAll.some((prefix) => prefix.endsWith('/') && under(file, prefix)))
  ? manifest.frontend
  : manifest.frontend.filter((entry) => files.some((file) => under(file, `whale-frontend/${entry.dir}/`)));

process.stdout.write(JSON.stringify({ backend, frontend }));
