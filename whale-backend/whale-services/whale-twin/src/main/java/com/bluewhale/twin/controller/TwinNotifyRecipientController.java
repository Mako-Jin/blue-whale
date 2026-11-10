package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.twin.dto.TwinNotifyDeptVO;
import com.bluewhale.twin.dto.TwinNotifyRecipientVO;
import com.bluewhale.twin.support.TwinUserDirectoryClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警通知接收人检索（Wave 6.11 M2，Wave 6.12 补分页与部门树）。
 *
 * <p>单独一组路径而不是塞进 {@code /twin/scenes/{sceneId}/...}：接收人是平台级候选，
 * 与具体场景无关，选人时场景可能还没保存。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/twin/alert-notify")
@RequiredArgsConstructor
public class TwinNotifyRecipientController {

    private final TwinUserDirectoryClient directoryClient;

    /**
     * 分页检索候选接收人（用户名/昵称/手机号模糊匹配，仅启用用户）。
     *
     * @param keyword     关键字（可空表示不过滤）
     * @param deptId      部门ID（可空表示不限部门）
     * @param current     页码（默认 1）
     * @param size        每页条数（默认 20，上限 50）
     * @param httpRequest 当前请求（取租户ID透传给 auth）
     * @return 分页结果；auth 不可用时为空页
     */
    @GetMapping("/recipients")
    @RequirePermission("sys:twin:scene")
    public Result<PageResult<TwinNotifyRecipientVO>> recipients(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest httpRequest) {
        return Result.ok(directoryClient.search(tenantId(httpRequest), keyword, deptId, current, size));
    }

    /**
     * 部门树（供选择器按部门筛人）。
     *
     * @param httpRequest 当前请求（取租户ID透传给 auth）
     * @return 部门树；auth 不可用时为空列表
     */
    @GetMapping("/departments")
    @RequirePermission("sys:twin:scene")
    public Result<List<TwinNotifyDeptVO>> departments(HttpServletRequest httpRequest) {
        return Result.ok(directoryClient.departments(tenantId(httpRequest)));
    }

    /**
     * 从请求头取租户ID（网关可信注入；直连时由调用方自行带头）。
     *
     * @param httpRequest 当前请求
     * @return 租户ID；缺失或非法返回 {@code null}
     */
    private Long tenantId(HttpServletRequest httpRequest) {
        String raw = WebHeaders.tenantId(httpRequest);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
