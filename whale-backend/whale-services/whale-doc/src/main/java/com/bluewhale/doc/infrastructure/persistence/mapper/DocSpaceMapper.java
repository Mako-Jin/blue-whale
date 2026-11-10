package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档空间 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocSpaceMapper extends BaseMapper<DocSpace> {

    /**
     * 统计空间编码占用数量（包含逻辑删除行，避免唯一键冲突）。
     *
     * @param tenantId 租户ID
     * @param spaceKey 空间编码
     * @return 占用数量
     */
    @Select("SELECT COUNT(*) FROM doc_space WHERE tenant_id = #{tenantId} AND space_key = #{spaceKey}")
    long countKeyIncludeDeleted(@Param("tenantId") Long tenantId, @Param("spaceKey") String spaceKey);
}
