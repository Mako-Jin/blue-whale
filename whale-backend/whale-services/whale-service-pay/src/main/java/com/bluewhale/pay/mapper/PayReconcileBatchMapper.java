package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.po.PayReconcileBatchPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 对账批次 Mapper。
 *
 * @author BlueWhale
 */
public interface PayReconcileBatchMapper extends BaseMapper<PayReconcileBatchPo> {

    /**
     * 重新领取失败或降级批次：条件更新保证同一批次只有一个实例能重新执行。
     *
     * @param id 批次 ID
     * @return 影响行数（0 表示已被其他实例领取或状态不允许）
     */
    @Update("UPDATE pay_reconcile_batch SET status = 'RUNNING', started_at = NOW(), finished_at = NULL, "
            + "error_message = NULL, local_count = 0, channel_count = 0, diff_count = 0, update_time = NOW() "
            + "WHERE id = #{id} AND status IN ('FAILED', 'PARTIAL') AND deleted = 0")
    int claimRerun(@Param("id") Long id);
}
