package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档模板持久层。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocTemplateMapper extends BaseMapper<DocTemplate> {
}
