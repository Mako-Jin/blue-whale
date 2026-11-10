package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageShare;
import org.apache.ibatis.annotations.Mapper;

/**
 * 页面分享持久层。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocPageShareMapper extends BaseMapper<DocPageShare> {
}
