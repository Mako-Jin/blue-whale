package com.bluewhale.twin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.twin.entity.TwinSnapshot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 孪生发布快照 Mapper。
 *
 * <p>快照是审计数据，正常流程只插入不删除；唯一例外是同版本重新冻结
 * （发布 → 停用 → 未改动再发布，version 不变会撞 {@code uk_scene_version}），
 * 此时物理移除旧版本行后重新落一行。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface TwinSnapshotMapper extends BaseMapper<TwinSnapshot> {

    /**
     * 物理删除指定场景指定版本的快照（同版本重新冻结专用）。
     *
     * @param sceneId 场景ID
     * @param version 版本号
     * @return 删除行数
     */
    @Delete("DELETE FROM twin_snapshot WHERE scene_id = #{sceneId} AND version = #{version}")
    int physicalDeleteByVersion(@Param("sceneId") Long sceneId, @Param("version") Integer version);
}
