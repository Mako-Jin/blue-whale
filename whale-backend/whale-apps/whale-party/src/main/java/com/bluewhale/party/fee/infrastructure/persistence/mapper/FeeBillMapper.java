package com.bluewhale.party.fee.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.fee.infrastructure.persistence.entity.FeeBillEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 党费账单 Mapper。
 *
 * <p>月度汇总用聚合 SQL 而不是把整月账单读进内存再求和：一个中型租户单月账单可能上万条，
 * 汇总只需要一个 {@code SUM}，没必要在应用层做全表遍历。租户条件由行级隔离插件追加。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface FeeBillMapper extends BaseMapper<FeeBillEntity> {

    /**
     * 统计某月应缴总额。
     *
     * @param periodMonth 所属月份 yyyyMM
     * @return 应缴合计
     */
    @Select("SELECT IFNULL(SUM(due_amount), 0) FROM pty_fee_bill WHERE period_month = #{periodMonth}")
    BigDecimal sumDueByPeriod(@Param("periodMonth") String periodMonth);

    /**
     * 统计某月实缴总额。
     *
     * @param periodMonth 所属月份 yyyyMM
     * @return 实缴合计
     */
    @Select("SELECT IFNULL(SUM(paid_amount), 0) FROM pty_fee_bill WHERE period_month = #{periodMonth}")
    BigDecimal sumPaidByPeriod(@Param("periodMonth") String periodMonth);
}
