package com.bluewhale.pdm.part.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.part.dto.PdmPartMergeRequest;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.entity.PdmBomLine;
import com.bluewhale.pdm.part.mapper.PdmBomLineMapper;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import com.bluewhale.pdm.part.service.PdmPartDedupService;
import com.bluewhale.pdm.part.vo.PdmPartDedupGroupVO;
import com.bluewhale.pdm.part.vo.PdmPartDedupItemVO;
import com.bluewhale.pdm.part.vo.PdmPartMergeResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 零件去重候选与人工合并实现
 *
 * <p>候选匹配仅做名称/材质/规格的空白与大小写归一；合并仅改写草稿 BOM 的子件引用，
 * 已发布版本与发布快照保持不可变，源零件停用并通过 {@code merged_to} 追溯。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmPartDedupServiceImpl implements PdmPartDedupService {

    /** 候选扫描上限：避免全表加载拖垮数据库 */
    private static final int SCAN_LIMIT = 5000;

    /** 单次返回候选组上限 */
    private static final int GROUP_LIMIT = 100;

    /** BOM 草稿状态 */
    private static final String BOM_DRAFT = "DRAFT";

    /** 零件启用状态 */
    private static final String PART_ENABLED = "ENABLED";

    /** 零件停用状态 */
    private static final String PART_DISABLED = "DISABLED";

    private final PdmProductMapper productMapper;

    private final PdmBomMapper bomMapper;

    private final PdmBomLineMapper bomLineMapper;

    @Override
    public List<PdmPartDedupGroupVO> listCandidates() {
        List<PdmProduct> parts = productMapper.selectList(new LambdaQueryWrapper<PdmProduct>()
                .eq(PdmProduct::getStatus, PART_ENABLED)
                .orderByAsc(PdmProduct::getId)
                .last("LIMIT " + SCAN_LIMIT));
        Map<String, List<PdmProduct>> groups = new LinkedHashMap<>();
        for (PdmProduct part : parts) {
            groups.computeIfAbsent(dedupKey(part), key -> new ArrayList<>()).add(part);
        }
        return groups.values().stream()
                .filter(group -> group.size() > 1)
                .sorted(Comparator.comparingInt((List<PdmProduct> group) -> group.size()).reversed()
                        .thenComparing(group -> group.get(0).getName(), Comparator.nullsLast(String::compareTo)))
                .limit(GROUP_LIMIT)
                .map(PdmPartDedupServiceImpl::toGroupVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPartMergeResultVO merge(PdmPartMergeRequest request) {
        Long sourceId = request.getSourceId();
        Long targetId = request.getTargetId();
        if (Objects.equals(sourceId, targetId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "源零件与保留零件不能相同");
        }
        PdmProduct source = requirePart(sourceId, "源零件不存在");
        PdmProduct target = requirePart(targetId, "保留零件不存在");
        if (!PART_ENABLED.equals(source.getStatus()) || source.getMergedTo() != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "源零件已停用或已合并");
        }
        if (!PART_ENABLED.equals(target.getStatus()) || target.getMergedTo() != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "保留零件不可用");
        }
        long bomRootCount = bomMapper.selectCount(new LambdaQueryWrapper<PdmBom>()
                .eq(PdmBom::getProductId, sourceId));
        if (bomRootCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "源零件为 BOM 主体，不能合并");
        }
        long parentLineCount = bomLineMapper.selectCount(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getParentPartId, sourceId));
        if (parentLineCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "源零件在 BOM 结构中作为父件使用，不能合并");
        }

        List<PdmBomLine> lines = bomLineMapper.selectList(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getChildPartId, sourceId));
        Map<Long, PdmBom> bomMap = loadBoms(lines);
        int rewrittenLines = 0;
        int mergedLines = 0;
        int skippedReleasedLines = 0;
        for (PdmBomLine line : lines) {
            PdmBom bom = bomMap.get(line.getBomId());
            if (bom == null || !BOM_DRAFT.equals(bom.getStatus())) {
                skippedReleasedLines++;
                continue;
            }
            PdmBomLine existing = findLine(line.getBomId(), line.getParentPartId(), targetId);
            if (existing != null) {
                existing.setQty(amount(existing.getQty()).add(amount(line.getQty())));
                bomLineMapper.updateById(existing);
                bomLineMapper.deleteById(line.getId());
                mergedLines++;
            } else {
                line.setChildPartId(targetId);
                bomLineMapper.updateById(line);
                rewrittenLines++;
            }
        }

        source.setStatus(PART_DISABLED);
        source.setMergedTo(targetId);
        productMapper.updateById(source);
        log.info("零件已合并: sourceId={}, targetId={}, rewritten={}, merged={}, skipped={}, remark={}",
                sourceId, targetId, rewrittenLines, mergedLines, skippedReleasedLines, request.getRemark());
        PdmPartMergeResultVO result = new PdmPartMergeResultVO();
        result.setSourceId(sourceId);
        result.setTargetId(targetId);
        result.setRewrittenLines(rewrittenLines);
        result.setMergedLines(mergedLines);
        result.setSkippedReleasedLines(skippedReleasedLines);
        return result;
    }

    /**
     * 加载 BOM 头并按 ID 建立索引
     *
     * @param lines BOM 行
     * @return BOM 头映射
     */
    private Map<Long, PdmBom> loadBoms(List<PdmBomLine> lines) {
        List<Long> bomIds = lines.stream().map(PdmBomLine::getBomId).filter(Objects::nonNull).distinct().toList();
        if (bomIds.isEmpty()) {
            return Map.of();
        }
        return bomMapper.selectBatchIds(bomIds).stream()
                .collect(Collectors.toMap(PdmBom::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 查询同一 BOM 同一父件下是否已存在保留件行
     *
     * @param bomId        BOM 头ID
     * @param parentPartId 父件ID
     * @param childPartId  保留件ID
     * @return 已存在的行，未找到返回 null
     */
    private PdmBomLine findLine(Long bomId, Long parentPartId, Long childPartId) {
        return bomLineMapper.selectOne(new LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId)
                .eq(PdmBomLine::getParentPartId, parentPartId)
                .eq(PdmBomLine::getChildPartId, childPartId)
                .last("LIMIT 1"));
    }

    /**
     * 查询未删除零件
     *
     * @param id      零件ID
     * @param message 未找到提示
     * @return 零件实体
     */
    private PdmProduct requirePart(Long id, String message) {
        PdmProduct part = id == null ? null : productMapper.selectById(id);
        if (part == null) {
            throw new BizException(ErrorCode.NOT_FOUND, message);
        }
        return part;
    }

    /**
     * 归一化分组键：名称|材质|规格
     *
     * @param part 零件
     * @return 分组键
     */
    private static String dedupKey(PdmProduct part) {
        return normalize(part.getName()) + "|" + normalize(part.getMaterial()) + "|" + normalize(part.getSpec());
    }

    /**
     * 归一化：去首尾空白、折叠连续空白、小写
     *
     * @param value 原值
     * @return 归一化结果，空值返回空串
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * 候选组转视图对象
     *
     * @param group 同组零件
     * @return 候选组
     */
    private static PdmPartDedupGroupVO toGroupVo(List<PdmProduct> group) {
        PdmProduct first = group.get(0);
        PdmPartDedupGroupVO vo = new PdmPartDedupGroupVO();
        vo.setDedupKey(dedupKey(first));
        vo.setName(first.getName());
        vo.setMaterial(first.getMaterial());
        vo.setSpec(first.getSpec());
        vo.setCount(group.size());
        vo.setParts(group.stream().map(PdmPartDedupServiceImpl::toItemVo).toList());
        return vo;
    }

    /**
     * 零件转候选明细
     *
     * @param part 零件
     * @return 明细
     */
    private static PdmPartDedupItemVO toItemVo(PdmProduct part) {
        PdmPartDedupItemVO item = new PdmPartDedupItemVO();
        item.setId(part.getId());
        item.setCode(part.getCode());
        item.setName(part.getName());
        item.setPartType(part.getPartType());
        item.setMaterial(part.getMaterial());
        item.setSpec(part.getSpec());
        item.setUom(part.getUom());
        item.setSourceType(part.getSourceType());
        item.setStatus(part.getStatus());
        item.setErpItemCode(part.getErpItemCode());
        item.setCreateTime(part.getCreateTime());
        return item;
    }

    /**
     * 空值安全的数量
     *
     * @param value 数量
     * @return 非空数量
     */
    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
