package com.bluewhale.pdm.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.change.support.PdmChgOrderStatus;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import com.bluewhale.pdm.part.support.PdmPartTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 将作坊（PDM）统计口径：物料、BOM、变更、图纸四本账。
 *
 * <p><b>口径只在这里定义一次。</b>"未关闭变更单"是
 * {@code 状态不在 COMPLETED / CLOSED / REJECTED / CANCELLED}——
 * 被驳回或取消的变更单已经出了结论，不算欠账；只数"还在走流程"的。
 * 2D 变更页按状态筛选逐单看，这里聚合成一个数，两处必须同一个判定。</p>
 *
 * <p><b>不写 GROUP BY，按类型各 count 一次。</b>物料表的类型只有四种且码值固定，
 * 四次索引 count 比 CSV 聚合直观；更重要的是不走自定义 SQL 就不用自己拼
 * {@code tenant_id} 条件——租户隔离由 MyBatis-Plus 插件保证，一旦漏了就是
 * 跨租户串数，且不会报错。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PdmOverviewStatsService {

    /** 物料状态：在册 */
    private static final String PART_ENABLED = "ENABLED";

    /** 物料状态：停用 */
    private static final String PART_DISABLED = "DISABLED";

    /** BOM 状态：草稿 */
    private static final String BOM_DRAFT = "DRAFT";

    /** BOM 状态：生效 */
    private static final String BOM_RELEASED = "RELEASED";

    /** 文档状态：草稿 */
    private static final String DOC_DRAFT = "DRAFT";

    /** 文档状态：已发布 */
    private static final String DOC_RELEASED = "RELEASED";

    /** 已出结论、不算欠账的变更单状态 */
    private static final List<String> CHG_FINISHED = List.of(
            PdmChgOrderStatus.COMPLETED, PdmChgOrderStatus.CLOSED,
            PdmChgOrderStatus.REJECTED, PdmChgOrderStatus.CANCELLED);

    private final PdmProductMapper productMapper;

    private final PdmBomMapper bomMapper;

    private final PdmChgOrderMapper chgOrderMapper;

    private final PdmDocDocumentMapper docDocumentMapper;

    /**
     * 取一次统计快照。
     *
     * @return 统计快照
     */
    public PdmOverviewStats snapshot() {
        long partEnabled = count(productMapper.selectCount(new LambdaQueryWrapper<PdmProduct>()
                .eq(PdmProduct::getStatus, PART_ENABLED)));
        long partDisabled = count(productMapper.selectCount(new LambdaQueryWrapper<PdmProduct>()
                .eq(PdmProduct::getStatus, PART_DISABLED)));
        long bomReleased = count(bomMapper.selectCount(new LambdaQueryWrapper<PdmBom>()
                .eq(PdmBom::getStatus, BOM_RELEASED)));
        long bomDraft = count(bomMapper.selectCount(new LambdaQueryWrapper<PdmBom>()
                .eq(PdmBom::getStatus, BOM_DRAFT)));
        long bomTotal = count(bomMapper.selectCount(null));
        long chgOpen = count(chgOrderMapper.selectCount(new LambdaQueryWrapper<PdmChgOrder>()
                .notIn(PdmChgOrder::getStatus, CHG_FINISHED)));
        long chgAssessing = count(chgOrderMapper.selectCount(new LambdaQueryWrapper<PdmChgOrder>()
                .in(PdmChgOrder::getStatus, List.of(
                        PdmChgOrderStatus.ASSESSING, PdmChgOrderStatus.APPROVING))));
        long chgExecuting = count(chgOrderMapper.selectCount(new LambdaQueryWrapper<PdmChgOrder>()
                .eq(PdmChgOrder::getStatus, PdmChgOrderStatus.EXECUTING)));
        long docReleased = count(docDocumentMapper.selectCount(new LambdaQueryWrapper<PdmDocDocument>()
                .eq(PdmDocDocument::getStatus, DOC_RELEASED)));
        long docDraft = count(docDocumentMapper.selectCount(new LambdaQueryWrapper<PdmDocDocument>()
                .eq(PdmDocDocument::getStatus, DOC_DRAFT)));
        long docCheckedOut = count(docDocumentMapper.selectCount(new LambdaQueryWrapper<PdmDocDocument>()
                .isNotNull(PdmDocDocument::getCheckedOutBy)));
        return new PdmOverviewStats(partEnabled, partDisabled, partByType(),
                bomReleased, bomDraft, bomTotal,
                chgOpen, chgAssessing, chgExecuting,
                docReleased, docDraft, docCheckedOut);
    }

    /**
     * 在册物料按类型计数：成品 / 部件 / 零件 / 原材料。
     *
     * <p>只数在册（ENABLED）：停用的类型分布没有意义，混进来会让
     * "零件 40 个"里有一半其实是早就不用的。</p>
     *
     * @return 四个类型计数，顺序固定（与屏风图例一致）
     */
    private List<PdmOverviewStats.TypeCount> partByType() {
        List<PdmOverviewStats.TypeCount> counts = new ArrayList<>(4);
        appendType(counts, "成品", PdmPartTypes.PRODUCT);
        appendType(counts, "部件", PdmPartTypes.ASSEMBLY);
        appendType(counts, "零件", PdmPartTypes.PART);
        appendType(counts, "原材料", PdmPartTypes.RAW_MATERIAL);
        return counts;
    }

    /**
     * 数一个类型并追加。
     *
     * @param counts   目标列表
     * @param label    中文标签
     * @param partType 类型码值
     */
    private void appendType(List<PdmOverviewStats.TypeCount> counts, String label, String partType) {
        long value = count(productMapper.selectCount(new LambdaQueryWrapper<PdmProduct>()
                .eq(PdmProduct::getStatus, PART_ENABLED)
                .eq(PdmProduct::getPartType, partType)));
        counts.add(new PdmOverviewStats.TypeCount(label, value));
    }

    /**
     * 计数兜底：{@code selectCount} 在极端情况下返回 {@code null}，直接拆箱就是 NPE。
     *
     * @param value 计数值，可空
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
