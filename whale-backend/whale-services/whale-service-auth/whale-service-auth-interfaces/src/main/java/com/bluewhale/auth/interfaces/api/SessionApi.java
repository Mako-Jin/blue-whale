package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.response.SessionResponse;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 会话管理 API 契约（权限中心扩展 M3）：管理端 refreshToken 会话账本查询与强制下线。
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/session/v{version}", version = "1")
public interface SessionApi {

    /**
     * 分页查询用户会话（refreshToken 记录账本）
     *
     * @param pageQuery 分页参数
     * @param userId 用户 ID（可空表示不限）
     * @param onlyActive 仅未撤销会话（true 过滤 revoked=0 且未过期）
     * @return 会话分页结果
     */
    @GetMapping("/page")
    Result<PageResult<SessionResponse>> page(PageQuery pageQuery,
                                             @RequestParam(required = false) Long userId,
                                             @RequestParam(required = false, defaultValue = "false") boolean onlyActive);

    /**
     * 强制下线指定会话（按 jti）：账本标记 revoked 并写入用户撤销通道即时生效
     *
     * @param jti 会话令牌唯一标识
     * @return 操作结果
     */
    @DeleteMapping("/{jti}")
    Result<Void> revoke(@PathVariable String jti);
}
