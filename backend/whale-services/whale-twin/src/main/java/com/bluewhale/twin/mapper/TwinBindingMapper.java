package com.bluewhale.twin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.twin.entity.TwinBinding;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 孪生数据源绑定 Mapper。
 *
 * <p>绑定表无场景列，物理删除按场景实体子查询圈定；回滚时须先删绑定
 * 再删实体（子查询依赖实体行仍在）。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface TwinBindingMapper extends BaseMapper<TwinBinding> {

    /**
     * 物理删除场景下全部绑定（回滚重建专用，按场景实体子查询）。
     *
     * @param sceneId 场景ID
     * @return 删除行数
     */
    @Delete("DELETE FROM twin_binding WHERE entity_id IN "
            + "(SELECT id FROM twin_entity WHERE scene_id = #{sceneId})")
    int physicalDeleteByScene(@Param("sceneId") Long sceneId);
}
