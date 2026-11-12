package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumNotifyOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 通知 outbox Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumNotifyOutboxMapper extends BaseMapper<ForumNotifyOutbox> {

    /**
     * 取出待重投的消息（补偿任务用，按时间升序限量）。
     *
     * @param limit 单批条数
     * @return 待投消息
     */
    @Select("SELECT * FROM fm_notify_outbox WHERE status = 'PENDING' AND deleted = 0 "
            + "AND (next_retry_time IS NULL OR next_retry_time <= NOW()) "
            + "ORDER BY create_time LIMIT #{limit}")
    List<ForumNotifyOutbox> selectPending(@Param("limit") int limit);

    /**
     * 标记投递成功。
     *
     * @param id 消息ID
     * @return 影响行数
     */
    @Update("UPDATE fm_notify_outbox SET status = 'SENT', last_error = NULL, update_time = NOW() "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markSent(@Param("id") Long id);

    /**
     * 记录一次失败：累加重试次数并给出下次重试时间。
     *
     * @param id 消息ID
     * @param error 失败原因（截断到 512）
     * @param retryCount 已重试次数
     * @param giveUp 是否放弃（超过上限置 FAILED）
     * @return 影响行数
     */
    @Update("UPDATE fm_notify_outbox SET retry_count = #{retryCount}, last_error = #{error}, "
            + "status = IF(#{giveUp}, 'FAILED', 'PENDING'), "
            + "next_retry_time = DATE_ADD(NOW(), INTERVAL #{delaySeconds} SECOND), update_time = NOW() "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markFailed(@Param("id") Long id,
                   @Param("error") String error,
                   @Param("retryCount") int retryCount,
                   @Param("giveUp") boolean giveUp,
                   @Param("delaySeconds") int delaySeconds);
}
