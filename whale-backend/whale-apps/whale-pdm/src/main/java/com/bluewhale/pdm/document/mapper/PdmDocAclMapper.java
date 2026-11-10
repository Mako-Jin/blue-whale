package com.bluewhale.pdm.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pdm.document.entity.PdmDocAcl;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图文档访问控制规则 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface PdmDocAclMapper extends BaseMapper<PdmDocAcl> {
}
