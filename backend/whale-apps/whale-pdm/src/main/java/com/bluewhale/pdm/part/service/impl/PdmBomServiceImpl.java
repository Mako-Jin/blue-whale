package com.bluewhale.pdm.part.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.converter.PdmConverter;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.part.dto.PdmBomDeriveRequest;
import com.bluewhale.pdm.part.dto.PdmBomLineSaveRequest;
import com.bluewhale.pdm.part.dto.PdmBomQueryRequest;
import com.bluewhale.pdm.part.dto.PdmBomSaveRequest;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.entity.PdmBomLine;
import com.bluewhale.pdm.part.entity.PdmBomRelease;
import com.bluewhale.pdm.part.mapper.PdmBomLineMapper;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import com.bluewhale.pdm.part.mapper.PdmBomReleaseMapper;
import com.bluewhale.pdm.part.service.PdmBomService;
import com.bluewhale.pdm.part.support.PdmBomSnapshotBuilder;
import com.bluewhale.pdm.part.support.PdmBomStatus;
import com.bluewhale.pdm.part.support.PdmBomVersionNo;
import com.bluewhale.pdm.part.support.PdmBomViews;
import com.bluewhale.pdm.part.support.PdmPartTypes;
import com.bluewhale.pdm.part.vo.PdmBomDiffLineVO;
import com.bluewhale.pdm.part.vo.PdmBomDiffVO;
import com.bluewhale.pdm.part.vo.PdmBomExportRowVO;
import com.bluewhale.pdm.part.vo.PdmBomLineVO;
import com.bluewhale.pdm.part.vo.PdmBomReleaseVO;
import com.bluewhale.pdm.part.vo.PdmBomSnapshotVO;
import com.bluewhale.pdm.part.vo.PdmBomTreeNodeVO;
import com.bluewhale.pdm.part.vo.PdmBomVO;
import com.bluewhale.pdm.part.vo.PdmPartWhereUsedVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * BOM 服务实现
 *
 * <p>EBOM 以「父件 → 子件」边维护；发布时冻结结构并生成稳定快照，
 * 同一 {@code bomNo + versionNo}} 重复发布返回既有记录。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmBomServiceImpl implements PdmBomService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 在线对比最大行数（超过转异步，M1B 暂不支持） */
    private static final long MAX_DIFF_LINES = 2000L;

    /** 环检测固定前缀 */
    private static final String CYCLE_PREFIX = "BOM_CYCLE_DETECTED:";

    private final PdmBomMapper bomMapper;

    private final PdmBomLineMapper bomLineMapper;

    private final PdmBomReleaseMapper bomReleaseMapper;

    private final PdmProductMapper productMapper;

    @Override
    public PageResult<PdmBomVO> page(PdmBomQueryRequest request) {
        LambdaQueryWrapper<PdmBom> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getBomNo()), PdmBom::getBomNo, request.getBomNo());
        wrapper.like(StringUtils.hasText(request.getProductCode()), PdmBom::getProductCode,
                request.getProductCode());
        wrapper.eq(StringUtils.hasText(request.getView()), PdmBom::getView, request.getView());
        wrapper.eq(StringUtils.hasText(request.getStatus()), PdmBom::getStatus, request.getStatus());
        wrapper.orderByDesc(PdmBom::getCreateTime).orderByDesc(PdmBom::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmBom> page = bomMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PdmBomVO> records = page.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PdmBomVO detail(Long id) {
        PdmBom bom = requireBom(id);
        List<PdmBomLine> lines = linesOf(id);
        return assemble(bom, lines);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmBomVO create(PdmBomSaveRequest request) {
        PdmProduct product = requirePart(request.getProductId());
        if (!PdmPartTypes.isAssembly(product.getPartType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅成品/部件可创建 BOM");
        }
        if (!"ENABLED".equals(product.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "停用零件不能创建 BOM");
        }
        String view = StringUtils.hasText(request.getView())
                ? request.getView().trim()
                : PdmBomViews.EBOM;
        if (!PdmBomViews.isValid(view)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "视图仅支持 EBOM/PBOM/MBOM");
        }
        String bomNo = resolveBomNo(request.getBomNo(), product.getCode(), view);
        List<PdmBom> existing = bomMapper.selectList(new LambdaQueryWrapper<PdmBom>()
                .eq(PdmBom::getBomNo, bomNo)
                .orderByDesc(PdmBom::getVersionSeq));
        String versionNo = existing.isEmpty()
                ? PdmBomVersionNo.INITIAL
                : PdmBomVersionNo.nextMajor(existing.get(0).getVersionNo());

        PdmBom bom = new PdmBom();
        bom.setBomNo(bomNo);
        bom.setProductId(product.getId());
        bom.setProductCode(product.getCode());
        bom.setView(view);
        bom.setVersionNo(versionNo);
        bom.setVersionSeq(PdmBomVersionNo.seqOf(versionNo));
        bom.setStatus(PdmBomStatus.DRAFT);
        bom.setRemark(request.getRemark());
        bomMapper.insert(bom);
        log.info("BOM 已创建: id={}, bomNo={}, version={}", bom.getId(), bomNo, versionNo);
        return assemble(bom, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PdmBom bom = requireBom(id);
        if (!PdmBomStatus.DRAFT.equals(bom.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅草稿 BOM 可删除");
        }
        long releaseCount = bomReleaseMapper.selectCount(new LambdaQueryWrapper<PdmBomRelease>()
                .eq(PdmBomRelease::getBomId, id));
        if (releaseCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "BOM 已存在发布记录，不能删除");
        }
        bomLineMapper.delete(new LambdaQueryWrapper<PdmBomLine>().eq(PdmBomLine::getBomId, id));
        bomMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmBomVO addLine(Long bomId, PdmBomLineSaveRequest request) {
        PdmBom bom = requireDraftBom(bomId);
        PdmProduct parent = requirePart(request.getParentPartId());
        PdmProduct child = requirePart(request.getChildPartId());
        validateAssembly(parent);
        if (!"ENABLED".equals(child.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "停用零件不能加入 BOM");
        }
        if (!bom.getProductId().equals(parent.getId()) && !isPartInBom(bomId, parent.getId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "父件不在当前 BOM 结构中");
        }
        assertNoCycle(bomId, parent.getId(), child.getId());
        assertChildUnique(bomId, parent.getId(), child.getId(), null);

        Integer lineNo = request.getLineNo() == null
                ? nextLineNo(bomId, parent.getId())
                : request.getLineNo();
        if (lineNo == null || lineNo <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "行序号必须大于 0");
        }
        assertLineNoUnique(bomId, parent.getId(), lineNo, null);

        PdmBomLine line = new PdmBomLine();
        line.setBomId(bomId);
        line.setParentPartId(parent.getId());
        line.setChildPartId(child.getId());
        line.setLineNo(lineNo);
        line.setQty(normalizeQty(request.getQty()));
        line.setUom(StringUtils.hasText(request.getUom())
                ? request.getUom().trim()
                : defaultUom(child.getUom()));
        line.setLossRate(normalizeLoss(request.getLossRate()));
        line.setRemark(request.getRemark());
        bomLineMapper.insert(line);
        return detail(bomId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmBomVO updateLine(Long bomId, Long lineId, PdmBomLineSaveRequest request) {
        requireDraftBom(bomId);
        PdmBomLine line = requireLine(bomId, lineId);
        if (!Objects.equals(line.getParentPartId(), request.getParentPartId())
                || !Objects.equals(line.getChildPartId(), request.getChildPartId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "BOM 行的父件/子件不可修改");
        }
        Integer lineNo = request.getLineNo() == null ? line.getLineNo() : request.getLineNo();
        if (lineNo == null || lineNo <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "行序号必须大于 0");
        }
        assertLineNoUnique(bomId, line.getParentPartId(), lineNo, line.getId());
        line.setLineNo(lineNo);
        line.setQty(normalizeQty(request.getQty()));
        if (StringUtils.hasText(request.getUom())) {
            line.setUom(request.getUom().trim());
        }
        line.setLossRate(normalizeLoss(request.getLossRate()));
        line.setRemark(request.getRemark());
        bomLineMapper.updateById(line);
        return detail(bomId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmBomVO deleteLine(Long bomId, Long lineId) {
        requireDraftBom(bomId);
        PdmBomLine line = requireLine(bomId, lineId);
        long childCount = bomLineMapper.selectCount(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId)
                .eq(PdmBomLine::getParentPartId, line.getChildPartId()));
        if (childCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该行仍有下级零件，不能删除");
        }
        bomLineMapper.deleteById(lineId);
        return detail(bomId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmBomReleaseVO release(Long bomId, Long userId) {
        PdmBom bom = requireBom(bomId);
        PdmBomRelease existing = findRelease(bom.getBomNo(), bom.getVersionNo());
        if (existing != null) {
            return toReleaseVo(existing);
        }
        if (!PdmBomStatus.DRAFT.equals(bom.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅草稿 BOM 可发布");
        }
        List<PdmBomLine> lines = linesOf(bomId);
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "BOM 至少需要一行子件才能发布");
        }
        Map<Long, PdmProduct> parts = partsOf(bom, lines);
        PdmBomSnapshotVO snapshot = PdmBomSnapshotBuilder.build(bom, lines, parts);
        String payload = PdmBomSnapshotBuilder.canonicalJson(snapshot);
        String checksum = PdmBomSnapshotBuilder.checksum(payload);

        PdmBomRelease release = new PdmBomRelease();
        release.setBomId(bomId);
        release.setBomNo(bom.getBomNo());
        release.setVersionNo(bom.getVersionNo());
        release.setProductCode(bom.getProductCode());
        release.setView(bom.getView());
        release.setChecksum(checksum);
        release.setLineCount(snapshot.getLineCount());
        release.setPayload(payload);
        release.setReleasedBy(userId);
        release.setReleasedAt(LocalDateTime.now());
        bomReleaseMapper.insert(release);

        bom.setStatus(PdmBomStatus.RELEASED);
        bomMapper.updateById(bom);
        List<PdmBom> family = bomMapper.selectList(new LambdaQueryWrapper<PdmBom>()
                .eq(PdmBom::getBomNo, bom.getBomNo()));
        for (PdmBom other : family) {
            if (!other.getId().equals(bomId) && PdmBomStatus.RELEASED.equals(other.getStatus())) {
                other.setStatus(PdmBomStatus.OBSOLETE);
                bomMapper.updateById(other);
            }
        }
        log.info("BOM 已发布: bomNo={}, version={}, checksum={}",
                bom.getBomNo(), bom.getVersionNo(), checksum);
        return toReleaseVo(release);
    }

    @Override
    public List<PdmBomReleaseVO> releases(String bomNo) {
        List<PdmBomRelease> releases = bomReleaseMapper.selectList(new LambdaQueryWrapper<PdmBomRelease>()
                .eq(PdmBomRelease::getBomNo, bomNo)
                .orderByDesc(PdmBomRelease::getVersionNo));
        return releases.stream().map(PdmBomServiceImpl::toReleaseVo).toList();
    }

    @Override
    public PdmBomSnapshotVO snapshot(String bomNo, String versionNo) {
        PdmBomRelease release = findRelease(bomNo, PdmBomVersionNo.requireValid(versionNo));
        if (release == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "BOM 发布快照不存在");
        }
        PdmBomSnapshotVO snapshot = PdmBomSnapshotBuilder.parse(release.getPayload());
        snapshot.setChecksum(release.getChecksum());
        return snapshot;
    }

    @Override
    public List<PdmPartWhereUsedVO> whereUsed(Long partId) {
        if (partId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "零件不存在");
        }
        List<PdmBomLine> lines = bomLineMapper.selectList(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getChildPartId, partId));
        if (lines.isEmpty()) {
            return List.of();
        }
        Set<Long> bomIds = new HashSet<>();
        Set<Long> parentIds = new HashSet<>();
        for (PdmBomLine line : lines) {
            bomIds.add(line.getBomId());
            parentIds.add(line.getParentPartId());
        }
        Map<Long, PdmBom> boms = new HashMap<>();
        for (PdmBom bom : safeList(bomMapper.selectBatchIds(bomIds))) {
            boms.put(bom.getId(), bom);
        }
        Map<Long, PdmProduct> parents = new HashMap<>();
        for (PdmProduct part : safeList(productMapper.selectBatchIds(parentIds))) {
            parents.put(part.getId(), part);
        }
        List<PdmPartWhereUsedVO> result = new ArrayList<>();
        for (PdmBomLine line : lines) {
            PdmBom bom = boms.get(line.getBomId());
            if (bom == null) {
                continue;
            }
            PdmPartWhereUsedVO item = new PdmPartWhereUsedVO();
            item.setBomId(bom.getId());
            item.setBomNo(bom.getBomNo());
            item.setVersionNo(bom.getVersionNo());
            item.setView(bom.getView());
            item.setProductCode(bom.getProductCode());
            item.setStatus(bom.getStatus());
            PdmProduct parent = parents.get(line.getParentPartId());
            item.setParentPartNo(parent == null ? null : parent.getCode());
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmBomVO derive(Long sourceId, PdmBomDeriveRequest request) {
        PdmBom source = requireBom(sourceId);
        if (!PdmBomViews.EBOM.equals(source.getView())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅 EBOM 可派生 PBOM/MBOM");
        }
        String targetView = request.getView() == null ? "" : request.getView().trim();
        if (!PdmBomViews.isDerivable(targetView)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持派生 PBOM 或 MBOM");
        }
        List<PdmBom> derived = bomMapper.selectList(new LambdaQueryWrapper<PdmBom>()
                .eq(PdmBom::getSourceBomId, sourceId)
                .eq(PdmBom::getView, targetView)
                .orderByDesc(PdmBom::getVersionSeq));
        if (!derived.isEmpty() && PdmBomStatus.DRAFT.equals(derived.get(0).getStatus())) {
            return detail(derived.get(0).getId());
        }
        PdmBom template = derived.isEmpty() ? source : derived.get(0);
        String bomNo = StringUtils.hasText(request.getBomNo())
                ? request.getBomNo().trim()
                : (derived.isEmpty() ? source.getBomNo() + "-" + targetView : template.getBomNo());
        if (bomNo.length() > 64) {
            bomNo = bomNo.substring(0, 64);
        }
        String versionNo = derived.isEmpty()
                ? PdmBomVersionNo.INITIAL
                : PdmBomVersionNo.nextMajor(template.getVersionNo());

        PdmBom header = new PdmBom();
        header.setBomNo(bomNo);
        header.setProductId(source.getProductId());
        header.setProductCode(source.getProductCode());
        header.setView(targetView);
        header.setVersionNo(versionNo);
        header.setVersionSeq(PdmBomVersionNo.seqOf(versionNo));
        header.setStatus(PdmBomStatus.DRAFT);
        header.setSourceBomId(source.getId());
        header.setSourceVersionNo(source.getVersionNo());
        header.setRemark(StringUtils.hasText(request.getRemark())
                ? request.getRemark().trim()
                : source.getRemark());
        bomMapper.insert(header);

        List<PdmBomLine> sourceLines = linesOf(template.getId());
        List<PdmBomLine> copies = new ArrayList<>();
        for (PdmBomLine edge : sourceLines) {
            PdmBomLine copy = new PdmBomLine();
            copy.setBomId(header.getId());
            copy.setParentPartId(edge.getParentPartId());
            copy.setChildPartId(edge.getChildPartId());
            copy.setLineNo(edge.getLineNo());
            copy.setQty(edge.getQty());
            copy.setUom(edge.getUom());
            copy.setLossRate(edge.getLossRate());
            copy.setRemark(edge.getRemark());
            bomLineMapper.insert(copy);
            copies.add(copy);
        }
        log.info("BOM 视图已派生: source={}, targetId={}, view={}, version={}",
                sourceId, header.getId(), targetView, versionNo);
        return assemble(header, copies);
    }

    @Override
    public PdmBomDiffVO diff(Long baseId, Long targetId) {
        PdmBom base = requireBom(baseId);
        PdmBom target = requireBom(targetId);
        if (!Objects.equals(base.getProductId(), target.getProductId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅同一产品的 BOM 可对比");
        }
        assertDiffSize(baseId);
        assertDiffSize(targetId);
        Map<String, PdmBomTreeNodeVO> baseNodes = flatten(assemble(base, linesOf(baseId)).getTree());
        Map<String, PdmBomTreeNodeVO> targetNodes = flatten(assemble(target, linesOf(targetId)).getTree());

        PdmBomDiffVO result = new PdmBomDiffVO();
        result.setBaseBomNo(base.getBomNo());
        result.setBaseVersionNo(base.getVersionNo());
        result.setBaseView(base.getView());
        result.setTargetBomNo(target.getBomNo());
        result.setTargetVersionNo(target.getVersionNo());
        result.setTargetView(target.getView());

        Set<String> paths = new HashSet<>();
        paths.addAll(baseNodes.keySet());
        paths.addAll(targetNodes.keySet());
        List<String> ordered = paths.stream().sorted(PdmBomServiceImpl::comparePath).toList();
        for (String path : ordered) {
            PdmBomTreeNodeVO baseNode = baseNodes.get(path);
            PdmBomTreeNodeVO targetNode = targetNodes.get(path);
            if (baseNode == null) {
                result.getLines().add(change(path, "ADDED", targetNode, null, targetNode == null ? null : targetNode.getPartNo(), null));
                result.setAdded(result.getAdded() + 1);
                continue;
            }
            if (targetNode == null) {
                result.getLines().add(change(path, "REMOVED", baseNode, baseNode.getPartNo(), null, null));
                result.setRemoved(result.getRemoved() + 1);
                continue;
            }
            if (!Objects.equals(baseNode.getPartId(), targetNode.getPartId())) {
                result.getLines().add(change(path, "PART_CHANGED", targetNode,
                        baseNode.getPartNo(), targetNode.getPartNo(), "partNo"));
                result.setChanged(result.getChanged() + 1);
                continue;
            }
            if (compareDecimal(baseNode.getQty(), targetNode.getQty())) {
                result.getLines().add(change(path, "QTY_CHANGED", targetNode,
                        decimalText(baseNode.getQty()), decimalText(targetNode.getQty()), "qty"));
                result.setChanged(result.getChanged() + 1);
            }
            if (compareDecimal(baseNode.getLossRate(), targetNode.getLossRate())) {
                result.getLines().add(change(path, "LOSS_CHANGED", targetNode,
                        decimalText(baseNode.getLossRate()), decimalText(targetNode.getLossRate()), "lossRate"));
                result.setChanged(result.getChanged() + 1);
            }
            if (!Objects.equals(baseNode.getUom(), targetNode.getUom())) {
                result.getLines().add(change(path, "UOM_CHANGED", targetNode,
                        baseNode.getUom(), targetNode.getUom(), "uom"));
                result.setChanged(result.getChanged() + 1);
            }
        }
        return result;
    }

    @Override
    public List<PdmBomExportRowVO> exportRows(Long id) {
        PdmBom bom = requireBom(id);
        List<PdmBomLine> lines = linesOf(id);
        Map<Long, PdmProduct> parts = partsOf(bom, lines);
        List<PdmBomExportRowVO> rows = new ArrayList<>();
        PdmProduct root = parts.get(bom.getProductId());
        if (root == null) {
            return rows;
        }
        rows.add(exportRow(root, "", null));
        collectExportRows(rows, lines, parts, root.getId(), "");
        return rows;
    }

    /**
     * 递归收集导出行（深度优先，路径与树保持一致）。
     *
     * @param rows     导出行
     * @param lines    BOM 行
     * @param parts    零件映射
     * @param parentId 父件ID
     * @param parentPath 父路径
     */
    private void collectExportRows(List<PdmBomExportRowVO> rows, List<PdmBomLine> lines,
                                   Map<Long, PdmProduct> parts, Long parentId, String parentPath) {
        List<PdmBomLine> edges = lines.stream()
                .filter(line -> Objects.equals(line.getParentPartId(), parentId))
                .sorted(Comparator.comparing(PdmBomLine::getLineNo,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        for (int index = 0; index < edges.size(); index++) {
            PdmBomLine edge = edges.get(index);
            PdmProduct child = parts.get(edge.getChildPartId());
            if (child == null) {
                continue;
            }
            String path = parentPath.isEmpty()
                    ? String.valueOf(index + 1)
                    : parentPath + "." + (index + 1);
            rows.add(exportRow(child, path, edge));
            collectExportRows(rows, lines, parts, child.getId(), path);
        }
    }

    /**
     * 构建导出行。
     *
     * @param part 零件
     * @param path 路径
     * @param line 入边（根为空）
     * @return 导出行
     */
    private PdmBomExportRowVO exportRow(PdmProduct part, String path, PdmBomLine line) {
        PdmBomExportRowVO row = new PdmBomExportRowVO();
        row.setPath(path);
        row.setPartNo(part.getCode());
        row.setPartName(part.getName());
        row.setPartType(part.getPartType());
        row.setMaterial(part.getMaterial());
        row.setSpec(part.getSpec());
        if (line == null) {
            row.setUom(part.getUom());
            return row;
        }
        row.setLineNo(line.getLineNo());
        row.setQty(line.getQty());
        row.setLossRate(line.getLossRate());
        row.setUom(line.getUom());
        row.setRemark(line.getRemark());
        return row;
    }

    /**
     * 将树摊平为路径索引。
     *
     * @param nodes 树节点
     * @return 路径 → 节点
     */
    private static Map<String, PdmBomTreeNodeVO> flatten(List<PdmBomTreeNodeVO> nodes) {
        Map<String, PdmBomTreeNodeVO> map = new HashMap<>();
        for (PdmBomTreeNodeVO node : nodes) {
            map.put(node.getPath(), node);
            if (node.getChildren() != null) {
                map.putAll(flatten(node.getChildren()));
            }
        }
        return map;
    }

    /**
     * 构造对比明细。
     *
     * @param path       路径
     * @param changeType 变更类型
     * @param node       目标或基线节点
     * @param baseValue  基线值
     * @param targetValue 目标值
     * @param field      变更字段
     * @return 明细
     */
    private static PdmBomDiffLineVO change(String path, String changeType, PdmBomTreeNodeVO node,
                                           String baseValue, String targetValue, String field) {
        PdmBomDiffLineVO item = new PdmBomDiffLineVO();
        item.setPath(path);
        item.setChangeType(changeType);
        item.setPartNo(node == null ? null : node.getPartNo());
        item.setPartName(node == null ? null : node.getPartName());
        item.setBaseValue(baseValue);
        item.setTargetValue(targetValue);
        item.setField(field);
        return item;
    }

    /**
     * 比较两个数值（null 视为不同，除非都为 null）。
     *
     * @param left  基线值
     * @param right 目标值
     * @return 是否不同
     */
    private static boolean compareDecimal(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return false;
        }
        if (left == null || right == null) {
            return true;
        }
        return left.compareTo(right) != 0;
    }

    /**
     * 数值转文本。
     *
     * @param value 数值
     * @return 文本
     */
    private static String decimalText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 路径排序（按层级数字）。
     *
     * @param left  左路径
     * @param right 右路径
     * @return 比较结果
     */
    private static int comparePath(String left, String right) {
        if (left.isEmpty()) {
            return right.isEmpty() ? 0 : -1;
        }
        if (right.isEmpty()) {
            return 1;
        }
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.min(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int compared = Integer.compare(Integer.parseInt(leftParts[index]),
                    Integer.parseInt(rightParts[index]));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    /**
     * 校验在线对比行数上限。
     *
     * @param bomId BOM ID
     */
    private void assertDiffSize(Long bomId) {
        long count = bomLineMapper.selectCount(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId));
        if (count > MAX_DIFF_LINES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "BOM 行数超过 2000，暂不支持在线对比");
        }
    }

    /**
     * 组装 BOM 视图（头 + 平铺行 + 树）。
     *
     * @param bom   BOM 头
     * @param lines BOM 行
     * @return BOM 视图
     */
    private PdmBomVO assemble(PdmBom bom, List<PdmBomLine> lines) {
        PdmBomVO vo = toVo(bom);
        if (lines.isEmpty()) {
            return vo;
        }
        Map<Long, PdmProduct> parts = partsOf(bom, lines);
        PdmProduct root = parts.get(bom.getProductId());
        List<PdmBomLineVO> flat = new ArrayList<>();
        for (PdmBomLine line : lines) {
            PdmBomLineVO item = PdmConverter.INSTANCE.toBomLineVo(line);
            PdmProduct parent = parts.get(line.getParentPartId());
            PdmProduct child = parts.get(line.getChildPartId());
            item.setParentPartNo(parent == null ? null : parent.getCode());
            item.setChildPartNo(child == null ? null : child.getCode());
            item.setChildPartName(child == null ? null : child.getName());
            flat.add(item);
        }
        vo.setLines(flat);
        if (root != null) {
            vo.setProductName(root.getName());
            PdmBomTreeNodeVO rootNode = treeNode(root, null, 0, null);
            rootNode.setPath("");
            fillChildren(rootNode, lines, parts, "");
            vo.setTree(List.of(rootNode));
        }
        return vo;
    }

    /**
     * 递归填充树节点。
     *
     * @param node    父节点
     * @param lines   全部行
     * @param parts   零件主档
     * @param path    父路径
     */
    private void fillChildren(PdmBomTreeNodeVO node, List<PdmBomLine> lines,
                              Map<Long, PdmProduct> parts, String path) {
        List<PdmBomLine> edges = lines.stream()
                .filter(line -> Objects.equals(line.getParentPartId(), node.getPartId()))
                .sorted(Comparator.comparing(PdmBomLine::getLineNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        for (int index = 0; index < edges.size(); index++) {
            PdmBomLine edge = edges.get(index);
            String childPath = path.isEmpty() ? String.valueOf(index + 1) : path + "." + (index + 1);
            PdmProduct child = parts.get(edge.getChildPartId());
            if (child == null) {
                continue;
            }
            PdmBomTreeNodeVO childNode = treeNode(child, edge, node.getDepth() + 1, childPath);
            node.getChildren().add(childNode);
            fillChildren(childNode, lines, parts, childPath);
        }
    }

    /**
     * 构建树节点。
     *
     * @param part  零件
     * @param line  入边（根为空）
     * @param depth 层级
     * @param path  路径
     * @return 树节点
     */
    private PdmBomTreeNodeVO treeNode(PdmProduct part, PdmBomLine line, int depth, String path) {
        PdmBomTreeNodeVO node = new PdmBomTreeNodeVO();
        node.setPartId(part.getId());
        node.setPartNo(part.getCode());
        node.setPartName(part.getName());
        node.setPartType(part.getPartType());
        node.setUom(part.getUom());
        node.setDepth(depth);
        node.setPath(path);
        if (line != null) {
            node.setLineId(line.getId());
            node.setLineNo(line.getLineNo());
            node.setQty(line.getQty());
            node.setLossRate(line.getLossRate());
        }
        return node;
    }

    /**
     * 头实体转视图。
     *
     * @param bom BOM 头
     * @return 视图
     */
    private PdmBomVO toVo(PdmBom bom) {
        PdmBomVO vo = PdmConverter.INSTANCE.toBomVo(bom);
        vo.setLines(new ArrayList<>());
        vo.setTree(new ArrayList<>());
        return vo;
    }

    /**
     * 发布实体转视图。
     *
     * @param release 发布实体
     * @return 视图
     */
    private static PdmBomReleaseVO toReleaseVo(PdmBomRelease release) {
        return PdmConverter.INSTANCE.toBomReleaseVo(release);
    }

    /**
     * 读取 BOM 行。
     *
     * @param bomId BOM ID
     * @return 行列表
     */
    private List<PdmBomLine> linesOf(Long bomId) {
        return bomLineMapper.selectList(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId)
                .orderByAsc(PdmBomLine::getParentPartId)
                .orderByAsc(PdmBomLine::getLineNo));
    }

    /**
     * 收集 BOM 涉及的零件。
     *
     * @param bom   BOM 头
     * @param lines BOM 行
     * @return 零件映射
     */
    private Map<Long, PdmProduct> partsOf(PdmBom bom, Collection<PdmBomLine> lines) {
        Set<Long> ids = new HashSet<>();
        ids.add(bom.getProductId());
        for (PdmBomLine line : lines) {
            ids.add(line.getParentPartId());
            ids.add(line.getChildPartId());
        }
        Map<Long, PdmProduct> parts = new HashMap<>();
        for (PdmProduct part : safeList(productMapper.selectBatchIds(ids))) {
            parts.put(part.getId(), part);
        }
        return parts;
    }

    /**
     * 校验装配约束：父件必须为成品或部件。
     *
     * @param parent 父件
     */
    private void validateAssembly(PdmProduct parent) {
        if (!PdmPartTypes.isAssembly(parent.getPartType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅成品/部件可作为 BOM 父件");
        }
    }

    /**
     * 判断零件是否已出现在 BOM 中（作为子件）。
     *
     * @param bomId  BOM ID
     * @param partId 零件ID
     * @return 是否存在
     */
    private boolean isPartInBom(Long bomId, Long partId) {
        return bomLineMapper.selectCount(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId)
                .eq(PdmBomLine::getChildPartId, partId)) > 0;
    }

    /**
     * 环检测：若子件是父件自身或祖先，则拒绝。
     *
     * @param bomId     BOM ID
     * @param parentId  父件ID
     * @param childId   子件ID
     */
    private void assertNoCycle(Long bomId, Long parentId, Long childId) {
        if (Objects.equals(parentId, childId)) {
            throw cycleException(childId);
        }
        Set<Long> ancestors = ancestorsOf(bomId, parentId);
        if (ancestors.contains(childId)) {
            throw cycleException(childId);
        }
    }

    /**
     * 收集父件在当前 BOM 中的祖先链。
     *
     * @param bomId  BOM ID
     * @param partId 起始零件
     * @return 祖先集合（含自身）
     */
    private Set<Long> ancestorsOf(Long bomId, Long partId) {
        List<PdmBomLine> lines = linesOf(bomId);
        Map<Long, Long> parentByChild = new HashMap<>();
        for (PdmBomLine line : lines) {
            parentByChild.putIfAbsent(line.getChildPartId(), line.getParentPartId());
        }
        Set<Long> ancestors = new HashSet<>();
        Long cursor = partId;
        while (cursor != null && ancestors.add(cursor)) {
            cursor = parentByChild.get(cursor);
        }
        return ancestors;
    }

    /**
     * 构造环检测异常。
     *
     * @param childId 子件ID
     * @return 异常
     */
    private BizException cycleException(Long childId) {
        return new BizException(ErrorCode.PARAM_ERROR,
                CYCLE_PREFIX + " 零件 " + childId + " 会形成环");
    }

    /**
     * 校验同一父件下子件唯一。
     *
     * @param bomId    BOM ID
     * @param parentId 父件ID
     * @param childId  子件ID
     * @param exclude  排除行ID
     */
    private void assertChildUnique(Long bomId, Long parentId, Long childId, Long exclude) {
        LambdaQueryWrapper<PdmBomLine> wrapper = new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId)
                .eq(PdmBomLine::getParentPartId, parentId)
                .eq(PdmBomLine::getChildPartId, childId);
        if (exclude != null) {
            wrapper.ne(PdmBomLine::getId, exclude);
        }
        if (bomLineMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "同一父件下子件已存在");
        }
    }

    /**
     * 校验同级行序号唯一。
     *
     * @param bomId    BOM ID
     * @param parentId 父件ID
     * @param lineNo   行序号
     * @param exclude  排除行ID
     */
    private void assertLineNoUnique(Long bomId, Long parentId, Integer lineNo, Long exclude) {
        LambdaQueryWrapper<PdmBomLine> wrapper = new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId)
                .eq(PdmBomLine::getParentPartId, parentId)
                .eq(PdmBomLine::getLineNo, lineNo);
        if (exclude != null) {
            wrapper.ne(PdmBomLine::getId, exclude);
        }
        if (bomLineMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "同级行序号已存在");
        }
    }

    /**
     * 计算同一父件下下一个行序号。
     *
     * @param bomId    BOM ID
     * @param parentId 父件ID
     * @return 下一个行序号
     */
    private Integer nextLineNo(Long bomId, Long parentId) {
        List<PdmBomLine> siblings = bomLineMapper.selectList(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId)
                .eq(PdmBomLine::getParentPartId, parentId)
                .orderByDesc(PdmBomLine::getLineNo));
        if (siblings.isEmpty()) {
            return 1;
        }
        Integer max = siblings.get(0).getLineNo();
        return max == null ? 1 : max + 1;
    }

    /**
     * 归一化用量。
     *
     * @param qty 用量
     * @return 规范化用量
     */
    private BigDecimal normalizeQty(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用量必须大于 0");
        }
        return qty;
    }

    /**
     * 归一化损耗率。
     *
     * @param lossRate 损耗率
     * @return 规范化损耗率
     */
    private BigDecimal normalizeLoss(BigDecimal lossRate) {
        BigDecimal effective = lossRate == null ? BigDecimal.ZERO : lossRate;
        if (effective.compareTo(BigDecimal.ZERO) < 0 || effective.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "损耗率必须在 0-100 之间");
        }
        return effective;
    }

    /**
     * 默认单位。
     *
     * @param uom 子件单位
     * @return 默认单位
     */
    private String defaultUom(String uom) {
        return StringUtils.hasText(uom) ? uom : "PCS";
    }

    /**
     * 生成或规范化 BOM 编号。
     *
     * @param bomNo       请求编号
     * @param productCode 产品编码
     * @param view        视图
     * @return BOM 编号
     */
    private String resolveBomNo(String bomNo, String productCode, String view) {
        String candidate;
        if (StringUtils.hasText(bomNo)) {
            candidate = bomNo.trim();
        } else if (PdmBomViews.EBOM.equals(view)) {
            candidate = "BOM-" + productCode;
        } else {
            candidate = "BOM-" + productCode + "-" + view;
        }
        if (candidate.length() > 64) {
            candidate = candidate.substring(0, 64);
        }
        return candidate;
    }

    /**
     * 按编号与版本查询发布记录。
     *
     * @param bomNo     BOM 编号
     * @param versionNo 版本号
     * @return 发布记录或 null
     */
    private PdmBomRelease findRelease(String bomNo, String versionNo) {
        return bomReleaseMapper.selectOne(new LambdaQueryWrapper<PdmBomRelease>()
                .eq(PdmBomRelease::getBomNo, bomNo)
                .eq(PdmBomRelease::getVersionNo, versionNo)
                .last("LIMIT 1"));
    }

    /**
     * 校验 BOM 存在。
     *
     * @param id BOM ID
     * @return BOM 头
     */
    private PdmBom requireBom(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "BOM 不存在");
        }
        PdmBom bom = bomMapper.selectById(id);
        if (bom == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "BOM 不存在");
        }
        return bom;
    }

    /**
     * 校验 BOM 存在且为草稿。
     *
     * @param id BOM ID
     * @return BOM 头
     */
    private PdmBom requireDraftBom(Long id) {
        PdmBom bom = requireBom(id);
        if (!PdmBomStatus.DRAFT.equals(bom.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "BOM 已发布或已作废，不能修改");
        }
        return bom;
    }

    /**
     * 校验零件存在。
     *
     * @param id 零件ID
     * @return 零件
     */
    private PdmProduct requirePart(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "零件不存在");
        }
        PdmProduct part = productMapper.selectById(id);
        if (part == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "零件不存在");
        }
        return part;
    }

    /**
     * 校验行存在且属于指定 BOM。
     *
     * @param bomId  BOM ID
     * @param lineId 行ID
     * @return 行
     */
    private PdmBomLine requireLine(Long bomId, Long lineId) {
        if (lineId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "BOM 行不存在");
        }
        PdmBomLine line = bomLineMapper.selectById(lineId);
        if (line == null || !Objects.equals(line.getBomId(), bomId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "BOM 行不存在");
        }
        return line;
    }

    /**
     * 安全返回集合（兼容 Mapper 返回 null 的测试替身）。
     *
     * @param values 原集合
     * @param <T>    元素类型
     * @return 非空集合
     */
    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
