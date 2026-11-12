package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumExpLedger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 论坛经验流水 Mapper。
 *
 * <p>写入用 {@code INSERT IGNORE} 而不是 insert + 捕获重复键：异常控制流会把
 * 事务标成 rollback-only，与互动计数同坑。同帖同事件已发过经验时静默跳过。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumExpLedgerMapper extends BaseMapper<ForumExpLedger> {

    /**
     * 幂等写入经验流水（同租户同人同事件同帖只落一条）。
     *
     * @param ledger 流水
     * @return 影响行数（0 = 已发过，跳过）
     */
    @Insert("INSERT IGNORE INTO fm_exp_ledger "
            + "(id, tenant_id, user_id, event, amount, ref_thread_id, create_time) "
            + "VALUES (#{id}, #{tenantId}, #{userId}, #{event}, #{amount}, #{refThreadId}, NOW())")
    int insertIgnore(ForumExpLedger ledger);

    /**
     * 经验总额。
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @return 累计经验（无记录为 0）
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM fm_exp_ledger "
            + "WHERE tenant_id = #{tenantId} AND user_id = #{userId}")
    long sumExp(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
