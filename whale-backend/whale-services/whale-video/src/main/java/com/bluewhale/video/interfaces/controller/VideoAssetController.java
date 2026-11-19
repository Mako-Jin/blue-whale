package com.bluewhale.video.interfaces.controller;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.service.CatalogAppService;
import com.bluewhale.video.application.service.ContentAppService;
import com.bluewhale.video.application.service.MovieAppService;
import com.bluewhale.video.application.service.MovieCollectionAppService;
import com.bluewhale.video.application.service.PlayAppService;
import com.bluewhale.video.application.service.SeriesAppService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 图片静态代理接口：内容封面与创作者头像（不暴露文件 ID）。
 *
 * <p>通过应用层解析文件 ID 后由 {@code whale-file} 内容接口代理返回，
 * Range / If-Range 透传；响应为二进制图片，私有缓存 600 秒。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoAssetController {

    /** 图片代理缓存策略：私有缓存 10 分钟，避免公共缓存泄漏私密封面 */
    private static final String CACHE_CONTROL_VALUE = "private, max-age=600";

    /** 禁止浏览器嗅探响应内容类型的安全响应头 */
    private static final String NOSNIFF_HEADER = "X-Content-Type-Options";

    /** 禁止嗅探的响应头取值 */
    private static final String NOSNIFF_VALUE = "nosniff";

    /** 支持的范围单位声明 */
    private static final String ACCEPT_RANGES_VALUE = "bytes";

    /** 未知 MIME 类型的兜底 */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /** 越界响应 Content-Range 格式 */
    private static final String CONTENT_RANGE_REFUSED_FORMAT = "bytes */%d";

    private final ContentAppService contentAppService;

    private final CatalogAppService catalogAppService;

    private final PlayAppService playAppService;

    private final SeriesAppService seriesAppService;

    private final MovieAppService movieAppService;

    private final MovieCollectionAppService movieCollectionAppService;

    /**
     * 内容封面代理。
     *
     * @param id 内容 ID
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @param request 当前请求（读取网关注入的用户头，可为空）
     * @return 图片流响应
     */
    @GetMapping("/content/{id}/cover")
    public ResponseEntity<Resource> cover(@PathVariable Long id,
                                          @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                          @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                          HttpServletRequest request) {
        Long fileId = contentAppService.coverFileId(id, UserContext.optionalUserId(request));
        return imageResponse(openImage(fileId, range, ifRange));
    }

    /**
     * 系列封面代理。
     *
     * @param id 系列 ID
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @return 图片流响应
     */
    @GetMapping("/series/{id}/cover")
    public ResponseEntity<Resource> seriesCover(@PathVariable Long id,
                                                @RequestHeader(value = HttpHeaders.RANGE, required = false)
                                                String range,
                                                @RequestHeader(value = HttpHeaders.IF_RANGE, required = false)
                                                String ifRange) {
        return imageResponse(openImage(seriesAppService.coverFileId(id), range, ifRange));
    }

    /**
     * 创作者头像代理。
     *
     * @param id 创作者 ID
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @return 图片流响应
     */
    @GetMapping("/creator/{id}/avatar")
    public ResponseEntity<Resource> avatar(@PathVariable Long id,
                                           @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                           @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange) {
        return imageResponse(openImage(catalogAppService.avatarFileId(id), range, ifRange));
    }

    /**
     * 电影海报代理。
     *
     * @param id 电影内容 ID
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @param request 当前请求（读取网关注入的用户头，可为空）
     * @return 图片流响应
     */
    @GetMapping("/movies/{id}/poster")
    public ResponseEntity<Resource> moviePoster(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
            HttpServletRequest request) {
        Long fileId = movieAppService.posterFileId(id, UserContext.optionalUserId(request));
        return imageResponse(openImage(fileId, range, ifRange));
    }

    /**
     * 电影背景图代理。
     *
     * @param id 电影内容 ID
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @param request 当前请求（读取网关注入的用户头，可为空）
     * @return 图片流响应
     */
    @GetMapping("/movies/{id}/backdrop")
    public ResponseEntity<Resource> movieBackdrop(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
            HttpServletRequest request) {
        Long fileId = movieAppService.backdropFileId(id, UserContext.optionalUserId(request));
        return imageResponse(openImage(fileId, range, ifRange));
    }

    /**
     * 片单封面代理。
     *
     * @param id 片单 ID
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @return 图片流响应
     */
    @GetMapping("/collections/{id}/cover")
    public ResponseEntity<Resource> collectionCover(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange) {
        return imageResponse(openImage(movieCollectionAppService.coverFileId(id), range, ifRange));
    }

    /**
     * 打开图片内容流。
     *
     * @param fileId 图片文件 ID
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @return 图片流视图
     */
    private VideoStreamView openImage(Long fileId, String range, String ifRange) {
        if (fileId == null || fileId <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "video.asset.image-not-found", "图片不存在");
        }
        return playAppService.openImage(fileId, TenantContext.requireId(), range, ifRange);
    }

    /**
     * 组装图片响应（200 / 206 / 416）。
     *
     * @param view 图片流视图
     * @return 图片响应
     */
    private ResponseEntity<Resource> imageResponse(VideoStreamView view) {
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
