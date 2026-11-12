package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumHotSnapshot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 热榜快照 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumHotSnapshotMapper extends BaseMapper<ForumHotSnapshot> {

    /**
     * 物理清空本租户快照（刷新前置动作）。
     *
     * <p>不走 {@code delete(wrapper)}：那是逻辑删除，会留下
     * {@code deleted=1} 的行继续占用唯一键，下一轮同名次插入直接撞键，
     * 热榜永远停在第一版。</p>
     *
     * @param tenantId 租户ID
     * @param hotWindow 窗口（24h / 7d）
     * @return 删除行数
     */
    @Delete("DELETE FROM fm_hot_snapshot WHERE tenant_id = #{tenantId} AND hot_window = #{hotWindow}")
    int purgeByTenantAndWindow(@Param("tenantId") Long tenantId, @Param("hotWindow") String hotWindow);
}
