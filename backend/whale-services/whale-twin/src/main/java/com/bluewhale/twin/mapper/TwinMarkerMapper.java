package com.bluewhale.twin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.twin.entity.TwinMarker;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 孪生标注热点 Mapper。
 *
 * <p>回滚重建时需要物理删除（{@code BaseEntity.deleted} 带
 * {@code @TableLogic}，常规 {@code deleteById} 是逻辑删除），避免
 * 残留行与新插入数据混淆；标注表虽无唯一键，口径与实体表保持一致。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface TwinMarkerMapper extends BaseMapper<TwinMarker> {

    /**
     * 物理删除场景下全部标注（回滚重建专用）。
     *
     * @param sceneId 场景ID
     * @return 删除行数
     */
    @Delete("DELETE FROM twin_marker WHERE scene_id = #{sceneId}")
    int physicalDeleteByScene(@Param("sceneId") Long sceneId);
}
