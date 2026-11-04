// PDM 零件主档 / 物料分类 / BOM API 层：经网关 8080 代理到 whale-pdm（/pdm/**）
import { http, type PageResult } from './client';

/** 零件类型 */
export type PartType = 'PRODUCT' | 'ASSEMBLY' | 'PART' | 'RAW_MATERIAL';

/** 来源类型 */
export type SourceType = 'STANDARD' | 'COMMON' | 'UNIQUE';

/** 零件状态 */
export type PartStatus = 'ENABLED' | 'DISABLED';

/** BOM 状态 */
export type BomStatus = 'DRAFT' | 'RELEASED' | 'OBSOLETE';

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
  view: string;
  productCode: string;
  status: BomStatus;
  parentPartNo?: string;
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
  view: string;
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
  view?: string;
  status?: BomStatus;
}

/** BOM 新建入参 */
export interface BomInput {
  productId: string;
  bomNo?: string;
  view?: string;
  remark?: string;
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
  view: string;
  checksum: string;
  lineCount: number;
  releasedBy?: string;
  releasedAt?: string;
  createTime?: string;
}

/** 零件分页查询 */
export function listParts(params: PartQuery): Promise<PageResult<PdmPart>> {
  return http.get<PageResult<PdmPart>>('/pdm/part/page', params as Record<string, unknown>);
}

/** 零件详情 */
export function getPart(id: string): Promise<PdmPart> {
  return http.get<PdmPart>(`/pdm/part/${id}`);
}

/** 保存零件（无 id 走新增） */
export function savePart(input: PartInput, id?: string): Promise<PdmPart> {
  return id
    ? http.put<PdmPart>(`/pdm/part/${id}`, input)
    : http.post<PdmPart>('/pdm/part', input);
}

/** 删除零件 */
export function deletePart(id: string): Promise<void> {
  return http.del<void>(`/pdm/part/${id}`);
}

/** 零件使用处 */
export function listWhereUsed(id: string): Promise<PartWhereUsed[]> {
  return http.get<PartWhereUsed[]>(`/pdm/part/${id}/where-used`);
}

/** 物料分类树 */
export function getCategoryTree(): Promise<PartCategory[]> {
  return http.get<PartCategory[]>('/pdm/part/category/tree');
}

/** 保存物料分类（无 id 走新增） */
export function saveCategory(input: CategoryInput, id?: string): Promise<PartCategory> {
  return id
    ? http.put<PartCategory>(`/pdm/part/category/${id}`, input)
    : http.post<PartCategory>('/pdm/part/category', input);
}

/** 删除物料分类 */
export function deleteCategory(id: string): Promise<void> {
  return http.del<void>(`/pdm/part/category/${id}`);
}

/** BOM 分页查询 */
export function listBoms(params: BomQuery): Promise<PageResult<PdmBom>> {
  return http.get<PageResult<PdmBom>>('/pdm/bom/page', params as Record<string, unknown>);
}

/** BOM 详情 */
export function getBom(id: string): Promise<PdmBom> {
  return http.get<PdmBom>(`/pdm/bom/${id}`);
}

/** 新建 BOM 版本 */
export function createBom(input: BomInput): Promise<PdmBom> {
  return http.post<PdmBom>('/pdm/bom', input);
}

/** 删除草稿 BOM */
export function deleteBom(id: string): Promise<void> {
  return http.del<void>(`/pdm/bom/${id}`);
}

/** 新增 BOM 行 */
export function addBomLine(id: string, input: BomLineInput): Promise<PdmBom> {
  return http.post<PdmBom>(`/pdm/bom/${id}/lines`, input);
}

/** 更新 BOM 行 */
export function updateBomLine(id: string, lineId: string, input: BomLineInput): Promise<PdmBom> {
  return http.put<PdmBom>(`/pdm/bom/${id}/lines/${lineId}`, input);
}

/** 删除 BOM 行 */
export function deleteBomLine(id: string, lineId: string): Promise<PdmBom> {
  return http.del<PdmBom>(`/pdm/bom/${id}/lines/${lineId}`);
}

/** 发布 BOM */
export function releaseBom(id: string): Promise<BomRelease> {
  return http.post<BomRelease>(`/pdm/bom/${id}/release`);
}

/** BOM 发布记录 */
export function listBomReleases(bomNo: string): Promise<BomRelease[]> {
  return http.get<BomRelease[]>(`/pdm/bom/${bomNo}/releases`);
}
