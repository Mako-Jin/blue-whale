package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysRememberMeTokenPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记住我令牌 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysRememberMeTokenMapper extends BaseMapper<SysRememberMeTokenPo> {
}
