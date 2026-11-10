package com.bluewhale.twin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.twin.entity.TwinEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 孪生实体 Mapper。
 *
 * <p>回滚重建需要物理删除（{@code BaseEntity.deleted} 带
 * {@code @TableLogic}，常规 {@code deleteById} 是逻辑删除）：逻辑删除的
 * 残留行仍占 {@code uk_scene_code (scene_id, entity_code)}，重插同编码
 * 实体会撞唯一键（对齐 whale-iot 物模型「物理替换」先例）。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface TwinEntityMapper extends BaseMapper<TwinEntity> {

    /**
     * 物理删除场景下全部实体（回滚重建专用）。
     *
     * @param sceneId 场景ID
     * @return 删除行数
     */
    @Delete("DELETE FROM twin_entity WHERE scene_id = #{sceneId}")
    int physicalDeleteByScene(@Param("sceneId") Long sceneId);
}
