package com.bluewhale.contract.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.dto.SealGrantRequest;
import com.bluewhale.contract.dto.SealSaveRequest;
import com.bluewhale.contract.service.SealService;
import com.bluewhale.contract.vo.SealGrantVO;
import com.bluewhale.contract.vo.SealVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 印章管理与用章授权接口。
 *
 * <p>服务内路径 {@code /contract/seal/**}，全部接口受 {@code sys:contract:seal:*} 权限保护，
 * 印章增删改与授权均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/contract/v{version}/seal", version = "1")
@RequiredArgsConstructor
public class ContractSealController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "ct_seal";

    private final SealService sealService;

    /**
     * 分页查询印章。
     *
     * @param current 页码
     * @param size    每页条数
     * @param keyword 名称关键字
     * @param status  状态
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:contract:seal:list")
    public Result<PageResult<SealVO>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status) {
        return Result.ok(sealService.page(current, size, keyword, status));
    }

    /**
     * 新建印章。
     *
     * @param request 保存请求
     * @return 印章视图
     */
    @PostMapping
    @RequirePermission("sys:contract:seal:add")
    @AuditLog(operation = "新建印章", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<SealVO> create(@Valid @RequestBody SealSaveRequest request) {
        return Result.ok(sealService.create(request));
    }

    /**
     * 更新印章。
     *
     * @param id      印章ID
     * @param request 保存请求
     * @return 印章视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:contract:seal:edit")
    @AuditLog(operation = "更新印章", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<SealVO> update(@PathVariable Long id, @Valid @RequestBody SealSaveRequest request) {
        return Result.ok(sealService.update(id, request));
    }

    /**
     * 删除印章。
     *
     * @param id 印章ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:contract:seal:delete")
    @AuditLog(operation = "删除印章", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        sealService.delete(id);
        return Result.ok();
    }

    /**
     * 新增用章授权。
     *
     * @param id          印章ID
     * @param request     授权请求
     * @param httpRequest 当前请求（解析操作人）
     * @return 授权视图
     */
    @PostMapping("/{id}/grant")
    @RequirePermission("sys:contract:seal:grant")
    @AuditLog(operation = "用章授权", type = OperateType.GRANT, targetType = TARGET_TYPE, targetId = "#id")
    public Result<SealGrantVO> grant(@PathVariable Long id, @Valid @RequestBody SealGrantRequest request,
                                     HttpServletRequest httpRequest) {
        return Result.ok(sealService.grant(id, request, operator(httpRequest)));
    }

    /**
     * 查询印章授权记录。
     *
     * @param id 印章ID
     * @return 授权列表
     */
    @GetMapping("/{id}/grants")
    @RequirePermission("sys:contract:seal:list")
    public Result<List<SealGrantVO>> grants(@PathVariable Long id) {
        return Result.ok(sealService.grants(id));
    }

    /**
     * 印章预览（低分辨率 + 半透明水印）。
     *
     * <p>预览接口沿用 {@code sys:contract:seal:list}；响应禁用缓存，避免浏览器把带水印的
     * 预览图当作原章复用。</p>
     *
     * @param id 印章ID
     * @return PNG 图片
     */
    @GetMapping(value = "/{id}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    @RequirePermission("sys:contract:seal:list")
    public ResponseEntity<byte[]> preview(@PathVariable Long id) {
        byte[] image = sealService.previewImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"seal-preview.png\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(image);
    }

    /**
     * 导出印章原图。
     *
     * <p>与预览接口分离授权：需要 {@code sys:contract:seal:download}，且每次导出写审计日志；
     * 响应强制以附件下载，避免浏览器直接以内联方式复用原章。</p>
     *
     * @param id 印章ID
     * @return 原图文件
     */
    @GetMapping("/{id}/download")
    @RequirePermission("sys:contract:seal:download")
    @AuditLog(operation = "下载印章原图", type = OperateType.EXPORT,
            targetType = TARGET_TYPE, targetId = "#id")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        SealService.SealImage image = sealService.downloadImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + image.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    /**
     * 上传印章图片（转存 whale-file，返回文件ID）。
     *
     * @param file 图片文件
     * @return 文件ID（字符串）
     * @throws IOException 读取异常
     */
    @PostMapping("/upload-image")
    @RequirePermission("sys:contract:seal:add")
    @AuditLog(operation = "上传印章图片", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        Long fileId = sealService.uploadImage(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return Result.ok(String.valueOf(fileId));
    }

    /**
     * 解析当前操作人。
     *
     * @param request 当前请求
     * @return 操作人上下文
     */
    private OperatorContext operator(HttpServletRequest request) {
        return new OperatorContext(WebHeaders.userId(request), WebHeaders.username(request),
                request.getHeader(WebHeaders.PERMISSION_CODES), request.getHeader(WebHeaders.USER_ROLES));
    }
}
