package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
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
 * 告警通知接收人检索（Wave 6.11 M2）。
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
     * 检索候选接收人（用户名/昵称/手机号模糊匹配，仅启用用户）。
     *
     * @param keyword     关键字（可空表示取前若干人）
     * @param size        条数（默认 20，上限 50）
     * @param httpRequest 当前请求（取租户ID透传给 auth）
     * @return 候选接收人列表；auth 不可用时返回空列表
     */
    @GetMapping("/recipients")
    @RequirePermission("sys:twin:scene")
    public Result<List<TwinNotifyRecipientVO>> recipients(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest httpRequest) {
        return Result.ok(directoryClient.search(tenantId(httpRequest), keyword, size));
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
