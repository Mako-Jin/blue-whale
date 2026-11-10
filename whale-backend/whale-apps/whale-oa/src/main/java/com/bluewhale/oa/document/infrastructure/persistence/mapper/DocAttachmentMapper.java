package com.bluewhale.oa.document.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocAttachmentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公文附件 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocAttachmentMapper extends BaseMapper<DocAttachmentEntity> {
}
