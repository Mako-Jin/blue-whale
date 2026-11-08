package com.bluewhale.crm.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.service.infrastructure.persistence.entity.ReturnRequestItemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 退换货申请行 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ReturnRequestItemMapper extends BaseMapper<ReturnRequestItemEntity> {

    /**
     * 按申请单 ID 物理删除申请行。
     *
     * <p>行表挂 BaseEntity 的 {@code @TableLogic}，BaseMapper.delete 走逻辑删除（deleted=1），
     * 旧行仍物理存在，会顶住申请行唯一键，导致 replaceItems 重插冲突；
     * 明细行随主单留痕即可，这里用原生 SQL 物理删除（含历史残留行）。</p>
     *
     * @param requestId 申请单 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM crm_return_item WHERE request_id = #{requestId}")
    int deletePhysicallyByRequestId(@Param("requestId") Long requestId);
}
