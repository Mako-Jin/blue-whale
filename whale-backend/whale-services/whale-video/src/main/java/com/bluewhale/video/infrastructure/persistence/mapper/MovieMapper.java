package com.bluewhale.video.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.video.infrastructure.persistence.entity.MovieEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 电影元数据 Mapper（含授权到期扫描）。
 *
 * @author BlueWhale
 */
public interface MovieMapper extends BaseMapper<MovieEntity> {

    /**
     * 查询授权已到期且内容仍处于已发布状态的内容 ID（定时下线用）。
     *
     * @param now 当前时间
     * @param limit 单批上限
     * @return 内容 ID 列表
     */
    @Select("SELECT m.content_id FROM vd_movie m JOIN vd_content c ON c.id = m.content_id "
            + "WHERE m.license_expire_at IS NOT NULL AND m.license_expire_at < #{now} "
            + "AND c.publish_status = 'PUBLISHED' AND c.deleted = 0 "
            + "ORDER BY m.license_expire_at ASC LIMIT #{limit}")
    List<Long> selectExpiredLicenseContentIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 统计授权在指定截止时间前到期（含已到期）的已发布电影数量。
     *
     * @param deadline 截止时间
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM vd_movie m JOIN vd_content c ON c.id = m.content_id "
            + "WHERE m.license_expire_at IS NOT NULL AND m.license_expire_at <= #{deadline} "
            + "AND c.publish_status = 'PUBLISHED' AND c.deleted = 0")
    long countLicenseExpiring(@Param("deadline") LocalDateTime deadline);
}
