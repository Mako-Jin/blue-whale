package com.bluewhale.oa.document.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocTemplateEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公文模板 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocTemplateMapper extends BaseMapper<DocTemplateEntity> {
}
