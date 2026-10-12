package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 口令历史仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface PasswordHistoryMapper extends BaseMapper<PasswordHistoryEntity> {

}
