package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserSecretQuestionPo;
import org.apache.ibatis.annotations.Delete;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户密保问题子表 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysUserSecretQuestionMapper extends BaseMapper<SysUserSecretQuestionPo> {

    /**
     * 按用户物理删除全部密保问题（聚合整体替换口径，绕过逻辑删除）。
     *
     * @param userId 用户 ID
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_user_secret_question WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
