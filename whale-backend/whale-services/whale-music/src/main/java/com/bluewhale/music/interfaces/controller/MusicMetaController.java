package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.interfaces.vo.MusicMetaVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 音乐服务元信息接口。
 *
 * <p>服务内路径 {@code /music/meta}，经网关按 {@code Path=/music/**} 转发；
 * 用于前端子应用启动探测与上传能力开关读取。M0 仅暴露运行参数，
 * 不返回任何曲库或播放数据。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/music")
@RequiredArgsConstructor
public class MusicMetaController {

    private final MusicProperties musicProperties;

    /**
     * 读取音乐服务运行元信息。
     *
     * @param request 当前请求（读取网关注入的用户头）
     * @return 运行元信息
     */
    @GetMapping("/meta")
    @RequirePermission("sys:music:browse")
    public Result<MusicMetaVO> meta(HttpServletRequest request) {
        MusicMetaVO meta = new MusicMetaVO();
        meta.setService("whale-music");
        meta.setTenantId(TenantContext.requireId());
        meta.setUserId(WebHeaders.userId(request));
        meta.setAgreementVersion(musicProperties.getAgreementVersion());
        meta.setAgreementTitle(musicProperties.getAgreementTitle());
        meta.setUploadEnabled(true);
        meta.setUploadMaxBytes(musicProperties.getUploadMaxBytes());
        meta.setAudioFormats(musicProperties.getAudioFormats());
        meta.setPlayTicketTtlSeconds(musicProperties.getPlayTicketTtlSeconds());
        meta.setCommentPreAudit(musicProperties.isCommentPreAudit());
        return Result.ok(meta);
    }
}
