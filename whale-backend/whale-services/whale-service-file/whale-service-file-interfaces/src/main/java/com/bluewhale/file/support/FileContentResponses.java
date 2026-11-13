package com.bluewhale.file.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.vo.FileInfoVO;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 文件内容响应构建：统一下载、预览与内网内容接口的响应头，并实现 HTTP Range 语义。
 *
 * <p>仅支持单段字节区间（{@code bytes=start-end}、{@code bytes=start-}、{@code bytes=-suffix}）：
 * 无 Range 时返回 200 全量；区间可满足时返回 206 与 {@code Content-Range}；语法合法但越界时
 * 返回 416 与 {@code Content-Range: bytes *&#47;size}；多段或语法非法时按 RFC 9110 忽略 Range，
 * 回落 200 全量。所有响应均声明 {@code Accept-Ranges: bytes}，并以文件 MD5 生成强 {@code ETag}，
 * 同时支持 {@code If-Range} 协商：校验失败时不返回区间，避免区间与旧版本文件错配。</p>
 *
 * @author BlueWhale
 */
public final class FileContentResponses {

    /** 文件名缺失时的占位名 */
    private static final String FALLBACK_FILENAME = "download";

    /** 禁止浏览器嗅探响应内容类型的安全响应头 */
    private static final String NOSNIFF_HEADER = "X-Content-Type-Options";

    /** 禁止嗅探的响应头取值 */
    private static final String NOSNIFF_VALUE = "nosniff";

    /** Range 头的单位前缀 */
    private static final String RANGE_UNIT_PREFIX = "bytes=";

    /** 弱 ETag 前缀，不能用于 If-Range 强校验 */
    private static final String WEAK_ETAG_PREFIX = "W/";

    /** 支持的范围单位声明 */
    private static final String ACCEPT_RANGES_VALUE = "bytes";

    /** 分段响应 Content-Range 格式 */
    private static final String CONTENT_RANGE_FORMAT = "bytes %d-%d/%d";

    /** 越界响应 Content-Range 格式 */
    private static final String CONTENT_RANGE_REFUSED_FORMAT = "bytes */%d";

    /** 可内联预览的文本类型白名单：其余 text/*（如 text/html、text/xml）可执行内联脚本，强制 attachment */
    private static final Set<String> PREVIEWABLE_TEXT_TYPES =
            Set.of("text/plain", "text/csv", "text/markdown");

    /** 禁止内联预览的类型：SVG 可内嵌 {@code <script>}，浏览器内联渲染会执行 */
    private static final String SVG_TYPE = "image/svg+xml";

    /**
     * 私有构造：仅提供静态方法。
     */
    private FileContentResponses() {
    }

    /**
     * 构建文件内容响应，统一处理响应头与 HTTP Range。
     *
     * @param fileInfoService 文件应用服务，用于按需读取全量或区间内容
     * @param info 文件元数据
     * @param requesterId 调用者用户 ID；内网服务调用可为 {@code null}
     * @param canManage 是否持有文件管理权限
     * @param inline 是否以 {@code inline} 呈现（预览）；否则 {@code attachment} 下载
     * @param rangeHeader 请求的 {@code Range} 头，可为 {@code null}
     * @param ifRangeHeader 请求的 {@code If-Range} 头，可为 {@code null}
     * @return 文件内容响应：200 全量、206 分段或 416 越界
     */
    public static ResponseEntity<Resource> build(FileInfoService fileInfoService, FileInfoVO info,
                                                 Long requesterId, boolean canManage, boolean inline,
                                                 String rangeHeader, String ifRangeHeader) {
        // fileSize 为 null 意味着元数据异常（正常登记不可能缺失），不能按空文件 0 字节响应
        if (info.getFileSize() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件元数据缺少大小信息: " + info.getId());
        }
        long size = info.getFileSize();
        String etag = buildEtag(info, size);
        HttpHeaders headers = baseHeaders(info, inline, etag);
        RequestedRange range = resolveRange(rangeHeader, ifRangeHeader, etag, size);
        if (range.refused()) {
            headers.set(HttpHeaders.CONTENT_RANGE, String.format(CONTENT_RANGE_REFUSED_FORMAT, size));
            headers.setContentLength(0L);
            return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        if (range.satisfiable()) {
            headers.setContentLength(range.length());
            headers.set(HttpHeaders.CONTENT_RANGE,
                    String.format(CONTENT_RANGE_FORMAT, range.start(), range.end(), size));
            return new ResponseEntity<>(
                    new InputStreamResource(fileInfoService.openContent(info.getId(), requesterId,
                            canManage, range.start(), range.length())),
                    headers, HttpStatus.PARTIAL_CONTENT);
        }
        headers.setContentLength(size);
        return new ResponseEntity<>(
                new InputStreamResource(fileInfoService.openContent(info.getId(), requesterId, canManage)),
                headers, HttpStatus.OK);
    }

    /**
     * 判断 MIME 类型是否可在线预览（不区分大小写，忽略 {@code ;} 之后的参数）：
     * 图片（SVG 除外）、音视频、白名单文本与 PDF 为安全类型；其余类型回落 attachment。
     *
     * <p>{@code text/html}、{@code text/xml} 等文本类型可执行内联脚本，SVG 可内嵌
     * {@code <script>}，均不允许内联渲染（nosniff 只防类型嗅探，不防同类型脚本执行）。</p>
     *
     * @param contentType 存储的 MIME 类型
     * @return 可安全内联预览时返回 {@code true}
     */
    public static boolean isPreviewable(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String type = contentType.trim().toLowerCase(Locale.ROOT);
        int paramIndex = type.indexOf(';');
        if (paramIndex >= 0) {
            type = type.substring(0, paramIndex).trim();
        }
        if (SVG_TYPE.equals(type)) {
            return false;
        }
        if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")) {
            return true;
        }
        return PREVIEWABLE_TEXT_TYPES.contains(type) || type.equals("application/pdf");
    }

    /**
     * 构建与区间无关的公共响应头：范围声明、ETag、内容处置、nosniff 与媒体类型。
     *
     * @param info 文件元数据
     * @param inline 是否以 {@code inline} 呈现
     * @param etag 响应 ETag
     * @return 响应头
     */
    private static HttpHeaders baseHeaders(FileInfoVO info, boolean inline, String etag) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_VALUE);
        headers.setETag(etag);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(info.getOriginalName(), inline));
        headers.set(NOSNIFF_HEADER, NOSNIFF_VALUE);
        headers.setContentType(resolveMediaType(info.getContentType()));
        return headers;
    }

    /**
     * 解析 Range 请求头并结合 If-Range 判定生效区间。
     *
     * @param rangeHeader 请求的 {@code Range} 头
     * @param ifRangeHeader 请求的 {@code If-Range} 头
     * @param etag 当前文件 ETag
     * @param size 文件总字节数
     * @return 区间解析结果
     */
    private static RequestedRange resolveRange(String rangeHeader, String ifRangeHeader, String etag, long size) {
        if (!StringUtils.hasText(rangeHeader) || !ifRangeAllows(ifRangeHeader, etag)) {
            return RequestedRange.absent();
        }
        String header = rangeHeader.trim();
        if (!header.toLowerCase(Locale.ROOT).startsWith(RANGE_UNIT_PREFIX)) {
            return RequestedRange.absent();
        }
        String spec = header.substring(RANGE_UNIT_PREFIX.length()).trim();
        if (spec.isEmpty() || spec.contains(",")) {
            return RequestedRange.absent();
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return RequestedRange.absent();
        }
        String startText = spec.substring(0, dash).trim();
        String endText = spec.substring(dash + 1).trim();
        try {
            if (startText.isEmpty()) {
                return resolveSuffixRange(endText, size);
            }
            long start = Long.parseLong(startText);
            if (start < 0) {
                return RequestedRange.absent();
            }
            if (start >= size) {
                return RequestedRange.unsatisfiable();
            }
            long end = endText.isEmpty() ? size - 1 : Long.parseLong(endText);
            if (end < start) {
                return RequestedRange.unsatisfiable();
            }
            return RequestedRange.of(start, Math.min(end, size - 1));
        } catch (NumberFormatException e) {
            return RequestedRange.absent();
        }
    }

    /**
     * 解析后缀区间（{@code bytes=-N}，取末尾 N 个字节）。
     *
     * @param endText {@code -} 之后的文本
     * @param size 文件总字节数
     * @return 区间解析结果
     */
    private static RequestedRange resolveSuffixRange(String endText, long size) {
        if (endText.isEmpty()) {
            return RequestedRange.absent();
        }
        long suffix;
        try {
            suffix = Long.parseLong(endText);
        } catch (NumberFormatException e) {
            return RequestedRange.absent();
        }
        if (suffix <= 0 || size <= 0) {
            return RequestedRange.unsatisfiable();
        }
        return RequestedRange.of(Math.max(size - suffix, 0L), size - 1);
    }

    /**
     * 判定 {@code If-Range} 是否允许返回区间：缺失时允许；弱校验或与当前 ETag 不一致时拒绝。
     *
     * @param ifRangeHeader 请求的 {@code If-Range} 头
     * @param etag 当前文件 ETag
     * @return 允许返回区间时返回 {@code true}
     */
    private static boolean ifRangeAllows(String ifRangeHeader, String etag) {
        if (!StringUtils.hasText(ifRangeHeader)) {
            return true;
        }
        String value = ifRangeHeader.trim();
        return !value.startsWith(WEAK_ETAG_PREFIX) && value.equals(etag);
    }

    /**
     * 生成强 ETag：优先使用文件 MD5，缺失时以文件标识与字节数兜底。
     *
     * @param info 文件元数据
     * @param size 文件总字节数
     * @return 带引号的 ETag
     */
    private static String buildEtag(FileInfoVO info, long size) {
        String token = StringUtils.hasText(info.getMd5()) ? info.getMd5() : info.getId() + "-" + size;
        return "\"" + token + "\"";
    }

    /**
     * 构建响应头中的 {@code Content-Disposition}（RFC 5987，UTF-8 文件名）。
     *
     * @param originalName 原始文件名
     * @param inline 是否以 {@code inline} 方式呈现（预览）；否则 {@code attachment} 下载
     * @return Content-Disposition 头值
     */
    private static String buildContentDisposition(String originalName, boolean inline) {
        String name = StringUtils.hasText(originalName) ? originalName : FALLBACK_FILENAME;
        ContentDisposition.Builder disposition = inline
                ? ContentDisposition.inline() : ContentDisposition.attachment();
        return disposition.filename(name, StandardCharsets.UTF_8).build().toString();
    }

    /**
     * 解析响应媒体类型，非法或缺失时回落 {@code application/octet-stream}。
     *
     * @param contentType 存储的 MIME 类型
     * @return 响应媒体类型
     */
    private static MediaType resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 解析后的单段 Range 请求：区分未请求、越界拒绝与可满足三种状态。
     *
     * @param satisfiable 是否为可满足区间
     * @param refused 是否为语法合法但越界的区间
     * @param start 起始字节（含）
     * @param end 结束字节（含）
     * @author BlueWhale
     */
    private record RequestedRange(boolean satisfiable, boolean refused, long start, long end) {

        /**
         * 未请求区间或应忽略 Range：返回 200 全量。
         *
         * @return 未请求状态
         */
        static RequestedRange absent() {
            return new RequestedRange(false, false, 0L, 0L);
        }

        /**
         * 区间越界：返回 416。
         *
         * @return 越界状态
         */
        static RequestedRange unsatisfiable() {
            return new RequestedRange(false, true, 0L, 0L);
        }

        /**
         * 可满足区间（闭区间）。
         *
         * @param start 起始字节（含）
         * @param end 结束字节（含）
         * @return 可满足状态
         */
        static RequestedRange of(long start, long end) {
            return new RequestedRange(true, false, start, end);
        }

        /**
         * 区间字节数。
         *
         * @return 区间长度
         */
        long length() {
            return end - start + 1;
        }
    }
}
