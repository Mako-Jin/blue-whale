package com.bluewhale.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.message.entity.MessageAnnouncement;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统公告 Mapper。
 *
 * <p>不用标注 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描。</p>
 *
 * @author BlueWhale
 */
public interface MessageAnnouncementMapper extends BaseMapper<MessageAnnouncement> {

    /**
     * 查询当前租户已发布、但尚未展开给指定接收人的公告（NOT EXISTS 反连接）。
     *
     * <p>由数据库一次性完成「已发布 − 已展开」差集，只返回待补写行：
     * 避免把全量已发布公告拉到应用层再查投递做差集，公告量增长后分页请求不再全表扫描。</p>
     *
     * <p>手写 SQL 不经过 BaseMapper 的逻辑删除注入，需显式带 {@code deleted = 0}；
     * 租户条件由租户插件自动追加到主表与子查询。</p>
     *
     * @param recipient 接收标识（用户ID文本）
     * @return 尚未展开的公告
     */
    @Select("""
            SELECT a.id, a.record_id, a.status, a.publisher_id, a.publish_time,
                   a.create_time, a.update_time
              FROM msg_announcement a
             WHERE a.status = 'PUBLISHED'
               AND a.deleted = 0
               AND NOT EXISTS (
                   SELECT 1
                     FROM msg_delivery d
                    WHERE d.message_id = a.record_id
                      AND d.channel = 'INAPP'
                      AND d.recipient = #{recipient}
                      AND d.deleted = 0
               )
             ORDER BY a.publish_time, a.id
            """)
    List<MessageAnnouncement> selectUnexpandedForRecipient(@Param("recipient") String recipient);
}
