package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.interfaces.vo.VideoMetaVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 视频服务元信息接口。
 *
 * <p>服务内路径 {@code /video/meta}，经网关按 {@code Path=/video/**} 转发，
 * 网关不剥离一级前缀（REQ-ARCH-003）。M0 仅暴露运行参数，不返回任何内容数据。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoMetaController {

    private final VideoProperties videoProperties;

    /**
     * 读取视频服务运行元信息。
     *
     * @param request 当前请求（读取网关注入的用户头）
     * @return 运行元信息
     */
    @GetMapping("/meta")
    @RequirePermission("sys:video:browse")
    public Result<VideoMetaVO> meta(HttpServletRequest request) {
        VideoMetaVO meta = new VideoMetaVO();
        meta.setService("whale-video");
        meta.setTenantId(TenantContext.requireId());
        meta.setUserId(WebHeaders.userId(request));
        meta.setDeclarationVersion(videoProperties.getDeclarationVersion());
        meta.setDeclarationTitle(videoProperties.getDeclarationTitle());
        meta.setUploadEnabled(true);
        meta.setContentTypes(List.of("SHORT_VIDEO", "VIDEO", "MOVIE"));
        meta.setShortMaxBytes(videoProperties.getShortMaxBytes());
        meta.setVideoMaxBytes(videoProperties.getVideoMaxBytes());
        meta.setMovieMaxBytes(videoProperties.getMovieMaxBytes());
        meta.setPlayTicketTtlSeconds(videoProperties.getPlayTicketTtlSeconds());
        meta.setPlayTicketRefreshSeconds(videoProperties.getPlayTicketRefreshSeconds());
        meta.setCommentPreAudit(videoProperties.isCommentPreAudit());
        return Result.ok(meta);
    }
}
