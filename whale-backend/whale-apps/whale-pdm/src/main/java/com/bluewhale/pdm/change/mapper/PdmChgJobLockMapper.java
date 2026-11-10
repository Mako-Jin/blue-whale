package com.bluewhale.pdm.change.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 变更任务锁 Mapper
 *
 * <p>定时任务单实例执行：先插入占位行，再以条件更新抢占；
 * 锁到期或本人持有时可继续持有。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface PdmChgJobLockMapper {

    /**
     * 插入锁行（已存在则忽略）
     *
     * @param lockKey     锁键
     * @param owner       持有者
     * @param lockedUntil 锁到期时间
     * @return 影响行数
     */
    @Insert("INSERT IGNORE INTO pdm_chg_job_lock (lock_key, owner, locked_until, create_time, update_time) "
            + "VALUES (#{lockKey}, #{owner}, #{lockedUntil}, NOW(), NOW())")
    int insertIgnore(@Param("lockKey") String lockKey, @Param("owner") String owner,
                     @Param("lockedUntil") LocalDateTime lockedUntil);

    /**
     * 抢占锁：锁为空、已到期或本人持有时更新成功
     *
     * @param lockKey     锁键
     * @param owner       持有者
     * @param lockedUntil 锁到期时间
     * @return 影响行数，大于 0 表示抢占成功
     */
    @Update("UPDATE pdm_chg_job_lock SET owner = #{owner}, locked_until = #{lockedUntil}, update_time = NOW() "
            + "WHERE lock_key = #{lockKey} "
            + "AND (locked_until IS NULL OR locked_until < NOW() OR owner = #{owner})")
    int acquire(@Param("lockKey") String lockKey, @Param("owner") String owner,
                @Param("lockedUntil") LocalDateTime lockedUntil);

    /**
     * 释放锁
     *
     * @param lockKey 锁键
     * @param owner   持有者
     * @return 影响行数
     */
    @Update("UPDATE pdm_chg_job_lock SET locked_until = NOW(), update_time = NOW() "
            + "WHERE lock_key = #{lockKey} AND owner = #{owner}")
    int release(@Param("lockKey") String lockKey, @Param("owner") String owner);
}
