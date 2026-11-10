package com.bluewhale.lms.credit.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.lms.credit.domain.model.CreditBucket;
import com.bluewhale.lms.credit.infrastructure.persistence.entity.CreditLedgerEntity;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学分台账 Mapper。
 *
 * <p>看板聚合走原生 SQL：按人合计再分档是「先分组再分桶」的两层聚合，
 * 用 Wrapper 得把每个人的合计拉回内存自己归档，人数一多就白读一遍全表。</p>
 *
 * @author BlueWhale
 */
public interface CreditLedgerMapper extends BaseMapper<CreditLedgerEntity> {

    /**
     * 学分总量。
     *
     * @return 学分合计（无记录为 0）
     */
    @Select("SELECT COALESCE(SUM(credit), 0) FROM lr_credit_ledger WHERE deleted = 0")
    BigDecimal sumTotal();

    /**
     * 获学分人数。
     *
     * @return 去重学员数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM lr_credit_ledger WHERE deleted = 0")
    long countEarners();

    /**
     * 按人合计学分分档（0-5 / 5-10 / 10-20 / 20+，左闭右开，末档不封顶）。
     *
     * @return 档位人数列表（空档不出现）
     */
    @Select("""
            SELECT CASE WHEN t.total >= 20 THEN '20+'
                        WHEN t.total >= 10 THEN '10-20'
                        WHEN t.total >= 5  THEN '5-10'
                        ELSE '0-5' END AS label,
                   COUNT(*) AS earners
              FROM (SELECT user_id, SUM(credit) AS total
                      FROM lr_credit_ledger WHERE deleted = 0 GROUP BY user_id) t
             GROUP BY label
            """)
    List<CreditBucket> distribution();
}
