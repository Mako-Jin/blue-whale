package com.bluewhale.form.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.form.dto.FormDataQueryRequest;
import com.bluewhale.form.dto.FormDataSubmitRequest;
import com.bluewhale.form.service.FormDataService;
import com.bluewhale.form.support.TenantResolver;
import com.bluewhale.form.vo.FormDataExportVO;
import com.bluewhale.form.vo.FormDataVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 表单数据管理接口。
 *
 * <p>服务内路径前缀 {@code /form/data}；经网关按 {@code Path=/form/**} 规则转发，
 * 外部路径与本文档一致。全部接口受 {@code @RequirePermission("sys:form:data:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。
 * 租户ID 由请求头 {@code X-Tenant-Id} 解析（缺省 1）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/form/data")
@RequiredArgsConstructor
public class FormDataController {

    private final FormDataService formDataService;

    /**
     * 提交表单数据。
     *
     * @param request 提交请求：definitionId / dataJson 必填，bizKey 可选
     * @param httpRequest 当前请求（读取 {@code X-Username} 回填提交人、{@code X-Tenant-Id} 解析租户）
     * @return 表单数据视图对象（含结构化字段）
     */
    @PostMapping
    @RequirePermission("sys:form:data:submit")
    public Result<FormDataVO> submit(@Valid @RequestBody FormDataSubmitRequest request,
                                     HttpServletRequest httpRequest) {
        return Result.ok(formDataService.submit(request, WebHeaders.username(httpRequest),
                TenantResolver.resolve(httpRequest)));
    }

    /**
     * 表单数据详情。
     *
     * @param id 表单数据ID
     * @param httpRequest 当前请求（读取 {@code X-Tenant-Id} 解析租户）
     * @return 表单数据视图对象（含结构化字段）
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:form:data:list")
    public Result<FormDataVO> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(formDataService.getDetail(id, TenantResolver.resolve(httpRequest)));
    }

    /**
     * 分页查询表单数据。
     *
     * @param request 查询条件：definitionCode 等值、bizKey 等值、submitter 等值
     * @param httpRequest 当前请求（读取 {@code X-Tenant-Id} 解析租户）
     * @return 表单数据分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:form:data:list")
    public Result<PageResult<FormDataVO>> page(FormDataQueryRequest request, HttpServletRequest httpRequest) {
        return Result.ok(formDataService.page(request, TenantResolver.resolve(httpRequest)));
    }

    /**
     * 删除表单数据（主表与物理表逻辑删除）。
     *
     * @param id 表单数据ID
     * @param httpRequest 当前请求（读取 {@code X-Tenant-Id} 解析租户）
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:form:data:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        formDataService.delete(id, TenantResolver.resolve(httpRequest));
        return Result.ok();
    }

    /**
     * 导出表单数据（CSV / XLSX 文件下载，不包 {@code Result} 信封）。
     *
     * @param request 查询条件：{@code definitionCode} 必填，{@code bizKey} / {@code submitter} 可选
     * @param format 导出格式：{@code csv}（默认）或 {@code xlsx}
     * @param httpRequest 当前请求（读取 {@code X-Tenant-Id} 解析租户）
     * @return 带 {@code Content-Disposition} 的文件响应
     */
    @GetMapping("/export")
    @RequirePermission("sys:form:data:list")
    public ResponseEntity<byte[]> export(FormDataQueryRequest request,
                                         @RequestParam(defaultValue = "csv") String format,
                                         HttpServletRequest httpRequest) {
        FormDataExportVO exported =
                formDataService.export(request, TenantResolver.resolve(httpRequest), format);
        String filename = URLEncoder.encode(exported.getFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, exported.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(exported.getContent());
    }
}
