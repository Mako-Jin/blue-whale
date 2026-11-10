package com.bluewhale.video.interfaces.controller;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.service.PlayAppService;
import com.bluewhale.video.application.view.VideoStreamView;
import com.bluewhale.video.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视频流式播放接口：票据代理 whale-file 内容，透传 HTTP Range。
 *
 * <p>不暴露 {@code file_id}；票据绑定租户 / 用户 / 内容。响应统一
 * {@code Cache-Control: private, no-store} + {@code X-Content-Type-Options: nosniff}，
 * 避免共享缓存泄漏票据 URL；200 全量、206 分段、416 越界。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoStreamController {

    /** 禁止浏览器嗅探响应内容类型的安全响应头 */
    private static final String NOSNIFF_HEADER = "X-Content-Type-Options";

    /** 禁止嗅探的响应头取值 */
    private static final String NOSNIFF_VALUE = "nosniff";

    /** 支持的范围单位声明 */
    private static final String ACCEPT_RANGES_VALUE = "bytes";

    /** 私有且不允许缓存：防止代理缓存票据 URL 后的内容 */
    private static final String CACHE_CONTROL_VALUE = "private, no-store";

    /** 未知 MIME 类型的兜底 */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /** 越界响应 Content-Range 格式 */
    private static final String CONTENT_RANGE_REFUSED_FORMAT = "bytes */%d";

    private final PlayAppService playAppService;

    /**
     * 按票据流式播放视频。
     *
     * @param contentId 内容 ID
     * @param ticket 播放票据
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @param request 当前请求（读取网关注入的用户头）
     * @return 视频流响应：200 全量、206 分段或 416 越界
     */
    @GetMapping("/stream/{contentId}")
    public ResponseEntity<Resource> stream(@PathVariable Long contentId,
                                           // 缺失票据交由应用层统一判定，避免框架层抛缺参异常返回系统异常
                                           @RequestParam(value = "ticket", required = false) String ticket,
                                           @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                           @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                           HttpServletRequest request) {
        VideoStreamView view = playAppService.openStream(contentId, ticket,
                UserContext.requireUserId(request), TenantContext.requireId(), range, ifRange);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_VALUE);
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        headers.set(NOSNIFF_HEADER, NOSNIFF_VALUE);
        headers.set(HttpHeaders.CONTENT_TYPE,
                view.getContentType() == null ? DEFAULT_CONTENT_TYPE : view.getContentType());
        if (view.getEtag() != null) {
            headers.setETag(view.getEtag());
        }
        if (view.getStatus() == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
            headers.set(HttpHeaders.CONTENT_RANGE,
                    String.format(CONTENT_RANGE_REFUSED_FORMAT, view.getContentRangeTotal()));
            headers.setContentLength(0L);
            return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        if (view.getContentRange() != null) {
            headers.set(HttpHeaders.CONTENT_RANGE, view.getContentRange());
        }
        if (view.getContentLength() >= 0) {
            headers.setContentLength(view.getContentLength());
        }
        HttpStatus status = view.getStatus() == HttpStatus.PARTIAL_CONTENT.value()
                ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(new InputStreamResource(view.getBody()), headers, status);
    }
}
