package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.ProfileVO;
import com.bluewhale.forum.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人主页接口（F-07-01 / F-07-03 / F-07-07）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/forum/v{version}/profile", version = "1")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * 我的主页（以身份头用户为准，不接收 userId）。
     *
     * @param request 当前请求
     * @return 主页视图
     */
    @GetMapping("/me")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<ProfileVO> me(HttpServletRequest request) {
        return Result.ok(profileService.profile(ForumActor.from(request).userId()));
    }

    /**
     * 指定用户主页（仅本服务可得的公开统计）。
     *
     * @param userId 用户ID
     * @return 主页视图
     */
    @GetMapping("/{userId}")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<ProfileVO> profile(@PathVariable("userId") Long userId) {
        return Result.ok(profileService.profile(userId));
    }
}
