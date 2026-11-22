package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.po.PayReconcileDiffPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 对账差异 Mapper。
 *
 * @author BlueWhale
 */
public interface PayReconcileDiffMapper extends BaseMapper<PayReconcileDiffPo> {

    /**
     * 未处置差异数（OPEN + SUSPENDED）：挂起不等于关闭，仍需计入概览告警。
     *
     * @param tenantId 租户 ID
     * @return 未处置差异数
     */
    @Select("SELECT COUNT(*) FROM pay_reconcile_diff "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND handle_status IN ('OPEN', 'SUSPENDED')")
    long countOpen(@Param("tenantId") Long tenantId);

    /**
     * 未处置的金额/币种不一致差异数（概览红色告警口径）。
     *
     * @param tenantId 租户 ID
     * @return 未处置金额差异数
     */
    @Select("SELECT COUNT(*) FROM pay_reconcile_diff "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND diff_type = 'AMOUNT_MISMATCH' "
            + "AND handle_status IN ('OPEN', 'SUSPENDED')")
    long countOpenAmountMismatch(@Param("tenantId") Long tenantId);

    /**
     * 条件更新处置状态：仅未终态的差异可处置，避免并发重复补偿。
     *
     * @param id     差异 ID
     * @param status 目标处置状态
     * @param remark 处置备注
     * @param userId 处置人
     * @return 影响行数（0 表示已被并发处置）
     */
    @Update("UPDATE pay_reconcile_diff SET handle_status = #{status}, handle_remark = #{remark}, "
            + "handled_by = #{userId}, handled_at = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND handle_status IN ('OPEN', 'SUSPENDED') AND deleted = 0")
    int markHandled(@Param("id") Long id, @Param("status") String status,
                    @Param("remark") String remark, @Param("userId") Long userId);
}
