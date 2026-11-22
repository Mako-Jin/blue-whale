// PDM 零件主档 / 物料分类 / BOM API 层：经网关 8080 代理到 whale-pdm（/pdm/**）
import { download, http, type DownloadResult, type PageResult } from './client';

/** 零件类型 */
export type PartType = 'PRODUCT' | 'ASSEMBLY' | 'PART' | 'RAW_MATERIAL';

/** 来源类型 */
export type SourceType = 'STANDARD' | 'COMMON' | 'UNIQUE';

/** 零件状态 */
export type PartStatus = 'ENABLED' | 'DISABLED';

/** BOM 状态 */
export type BomStatus = 'DRAFT' | 'RELEASED' | 'OBSOLETE';

/** BOM 视图 */
export type BomView = 'EBOM' | 'PBOM' | 'MBOM';

/** 零件主档 */
export interface PdmPart {
  id: string;
  code: string;
  name: string;
  partType: PartType;
  material?: string;
  spec?: string;
  uom?: string;
  categoryCode?: string;
  erpItemCode?: string;
  sourceType: SourceType;
  status: PartStatus;
  remark?: string;
}

/** 零件分页查询条件 */
export interface PartQuery {
  current?: number;
  size?: number;
  code?: string;
  name?: string;
  partType?: PartType;
  categoryCode?: string;
  sourceType?: SourceType;
  status?: PartStatus;
}

/** 零件新增/编辑入参 */
export interface PartInput {
  code: string;
  name: string;
  partType?: PartType;
  material?: string;
  spec?: string;
  uom?: string;
  categoryCode?: string;
  erpItemCode?: string;
  sourceType?: SourceType;
  status?: PartStatus;
  remark?: string;
}

/** 物料分类 */
export interface PartCategory {
  id: string;
  parentId: string;
  code: string;
  name: string;
  path: string;
  sortNo: number;
  remark?: string;
  children: PartCategory[];
}

/** 物料分类新增/编辑入参 */
export interface CategoryInput {
  parentId?: string;
  code: string;
  name: string;
  sortNo?: number;
  remark?: string;
}

/** 零件使用处 */
export interface PartWhereUsed {
  bomId: string;
  bomNo: string;
  versionNo: string;
  view: BomView;
  productCode: string;
  status: BomStatus;
  parentPartNo?: string;
}

/** 去重候选零件 */
export interface PartDedupItem {
  id: string;
  code: string;
  name: string;
  partType: PartType;
  material?: string;
  spec?: string;
  uom?: string;
  sourceType: SourceType;
  status: PartStatus;
  erpItemCode?: string;
  createTime?: string;
}

/** 去重候选组（归一化名称+材质+规格相同） */
export interface PartDedupGroup {
  dedupKey: string;
  name: string;
  material?: string;
  spec?: string;
  count: number;
  parts: PartDedupItem[];
}

/** 零件合并入参 */
export interface PartMergeInput {
  sourceId: string;
  targetId: string;
  remark?: string;
}

/** 零件合并结果 */
export interface PartMergeResult {
  sourceId: string;
  targetId: string;
  rewrittenLines: number;
  mergedLines: number;
  skippedReleasedLines: number;
}

/** ERP 仓库库存明细 */
export interface PartErpWarehouse {
  warehouseId?: string;
  warehouseCode?: string;
  warehouseName?: string;
  quantity: number;
}

/** 零件对应的 ERP 物料与库存（ERP 不可用时 degraded=true） */
export interface PartErpStock {
  partId: string;
  partCode: string;
  erpItemCode?: string;
  itemExists: boolean;
  itemName?: string;
  itemStatus?: string;
  totalQuantity: number;
  warehouses: PartErpWarehouse[];
  degraded: boolean;
  message?: string;
}

/** BOM 树节点 */
export interface BomTreeNode {
  partId: string;
  partNo: string;
  partName: string;
  partType: PartType;
  uom?: string;
  qty?: number;
  lossRate?: number;
  lineNo?: number;
  lineId?: string;
  path: string;
  depth: number;
  children: BomTreeNode[];
}

/** BOM 平铺行 */
export interface BomLine {
  id: string;
  parentPartId: string;
  parentPartNo?: string;
  childPartId: string;
  childPartNo?: string;
  childPartName?: string;
  lineNo: number;
  qty: number;
  uom: string;
  lossRate: number;
  remark?: string;
}

/** BOM 详情 */
export interface PdmBom {
  id: string;
  bomNo: string;
  productId: string;
  productCode: string;
  productName?: string;
  view: BomView;
  versionNo: string;
  status: BomStatus;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  lines: BomLine[];
  tree: BomTreeNode[];
}

/** BOM 查询条件 */
export interface BomQuery {
  current?: number;
  size?: number;
  bomNo?: string;
  productCode?: string;
  view?: BomView;
  status?: BomStatus;
}

/** BOM 新建入参 */
export interface BomInput {
  productId: string;
  bomNo?: string;
  view?: BomView;
  remark?: string;
}

/** BOM 派生视图入参 */
export interface BomDeriveInput {
  view: 'PBOM' | 'MBOM';
  bomNo?: string;
  remark?: string;
}

/** BOM 对比明细 */
export interface BomDiffLine {
  path: string;
  changeType: 'ADDED' | 'REMOVED' | 'QTY_CHANGED' | 'LOSS_CHANGED' | 'UOM_CHANGED' | 'PART_CHANGED';
  partNo?: string;
  partName?: string;
  field?: string;
  baseValue?: string;
  targetValue?: string;
}

/** BOM 对比结果 */
export interface BomDiff {
  baseBomNo: string;
  baseVersionNo: string;
  baseView: BomView;
  targetBomNo: string;
  targetVersionNo: string;
  targetView: BomView;
  added: number;
  removed: number;
  changed: number;
  lines: BomDiffLine[];
}

/** BOM 异步对比任务状态 */
export type BomDiffTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

/** BOM 异步对比任务 */
export interface BomDiffTask {
  id: string;
  baseBomId: string;
  targetBomId: string;
  status: BomDiffTaskStatus;
  errorMessage?: string;
  diff?: BomDiff;
  createTime?: string;
}

/** BOM 行入参 */
export interface BomLineInput {
  parentPartId: string;
  childPartId: string;
  lineNo?: number;
  qty: number;
  uom?: string;
  lossRate?: number;
  remark?: string;
}

/** BOM 发布记录 */
export interface BomRelease {
  id: string;
  bomNo: string;
  versionNo: string;
  productCode: string;
  view: BomView;
  checksum: string;
  lineCount: number;
  releasedBy?: string;
  releasedAt?: string;
  createTime?: string;
}

/** 零件分页查询 */
export function listParts(params: PartQuery): Promise<PageResult<PdmPart>> {
  return http.get<PageResult<PdmPart>>('/pdm/v1/part/page', params as Record<string, unknown>);
}

/** 零件详情 */
export function getPart(id: string): Promise<PdmPart> {
  return http.get<PdmPart>(`/pdm/v1/part/${id}`);
}

/** 保存零件（无 id 走新增） */
export function savePart(input: PartInput, id?: string): Promise<PdmPart> {
  return id ? http.put<PdmPart>(`/pdm/v1/part/${id}`, input) : http.post<PdmPart>('/pdm/v1/part', input);
}

/** 删除零件 */
export function deletePart(id: string): Promise<void> {
  return http.del<void>(`/pdm/v1/part/${id}`);
}

/** 零件使用处 */
export function listWhereUsed(id: string): Promise<PartWhereUsed[]> {
  return http.get<PartWhereUsed[]>(`/pdm/v1/part/${id}/where-used`);
}

/** 零件去重候选 */
export function listDedupCandidates(): Promise<PartDedupGroup[]> {
  return http.get<PartDedupGroup[]>('/pdm/v1/part/dedup/candidates');
}

/** 人工合并零件（源件停用，草稿 BOM 引用改写） */
export function mergeParts(input: PartMergeInput): Promise<PartMergeResult> {
  return http.post<PartMergeResult>('/pdm/v1/part/merge', input);
}

/** 零件对应的 ERP 物料与库存（ERP 不可用时后端降级返回） */
export function getPartErpStock(id: string): Promise<PartErpStock> {
  return http.get<PartErpStock>(`/pdm/v1/part/${id}/erp-stock`);
}

/** 物料分类树 */
export function getCategoryTree(): Promise<PartCategory[]> {
  return http.get<PartCategory[]>('/pdm/v1/part/category/tree');
}

/** 保存物料分类（无 id 走新增） */
export function saveCategory(input: CategoryInput, id?: string): Promise<PartCategory> {
  return id
    ? http.put<PartCategory>(`/pdm/v1/part/category/${id}`, input)
    : http.post<PartCategory>('/pdm/v1/part/category', input);
}

/** 删除物料分类 */
export function deleteCategory(id: string): Promise<void> {
  return http.del<void>(`/pdm/v1/part/category/${id}`);
}

/** BOM 分页查询 */
export function listBoms(params: BomQuery): Promise<PageResult<PdmBom>> {
  return http.get<PageResult<PdmBom>>('/pdm/v1/bom/page', params as Record<string, unknown>);
}

/** BOM 详情 */
export function getBom(id: string): Promise<PdmBom> {
  return http.get<PdmBom>(`/pdm/v1/bom/${id}`);
}

/** 新建 BOM 版本 */
export function createBom(input: BomInput): Promise<PdmBom> {
  return http.post<PdmBom>('/pdm/v1/bom', input);
}

/** 删除草稿 BOM */
export function deleteBom(id: string): Promise<void> {
  return http.del<void>(`/pdm/v1/bom/${id}`);
}

/** 新增 BOM 行 */
export function addBomLine(id: string, input: BomLineInput): Promise<PdmBom> {
  return http.post<PdmBom>(`/pdm/v1/bom/${id}/lines`, input);
}

/** 更新 BOM 行 */
export function updateBomLine(id: string, lineId: string, input: BomLineInput): Promise<PdmBom> {
  return http.put<PdmBom>(`/pdm/v1/bom/${id}/lines/${lineId}`, input);
}

/** 删除 BOM 行 */
export function deleteBomLine(id: string, lineId: string): Promise<PdmBom> {
  return http.del<PdmBom>(`/pdm/v1/bom/${id}/lines/${lineId}`);
}

/** 发布 BOM */
export function releaseBom(id: string): Promise<BomRelease> {
  return http.post<BomRelease>(`/pdm/v1/bom/${id}/release`);
}

/** BOM 发布记录 */
export function listBomReleases(bomNo: string): Promise<BomRelease[]> {
  return http.get<BomRelease[]>(`/pdm/v1/bom/${bomNo}/releases`);
}

/** 从 EBOM 派生 PBOM/MBOM */
export function deriveBom(id: string, input: BomDeriveInput): Promise<PdmBom> {
  return http.post<PdmBom>(`/pdm/v1/bom/${id}/derive`, input);
}

/** 对比两个 BOM（同一产品） */
export function diffBoms(baseId: string, targetId: string): Promise<BomDiff> {
  return http.get<BomDiff>('/pdm/v1/bom/diff', { baseId, targetId });
}

/** 创建 BOM 异步对比任务（不限行数） */
export function createBomDiffTask(baseId: string, targetId: string): Promise<BomDiffTask> {
  const query = `baseId=${encodeURIComponent(baseId)}&targetId=${encodeURIComponent(targetId)}`;
  return http.post<BomDiffTask>(`/pdm/v1/bom/diff/tasks?${query}`);
}

/** 查询 BOM 异步对比任务 */
export function getBomDiffTask(id: string): Promise<BomDiffTask> {
  return http.get<BomDiffTask>(`/pdm/v1/bom/diff/tasks/${encodeURIComponent(id)}`);
}

/** 导出 BOM Excel */
export function exportBom(id: string): Promise<DownloadResult> {
  return download(`/pdm/v1/bom/${id}/export`);
}
