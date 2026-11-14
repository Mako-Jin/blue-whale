package com.bluewhale.form.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.form.service.FormDataService;
import com.bluewhale.form.support.TenantResolver;
import com.bluewhale.form.vo.FormArchiveVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单数据内网接口（只读）。
 *
 * <p>服务内路径前缀 {@code /form/internal}，供档案服务（whale-archive）读取表单值与字段定义；
 * 除网关 {@code internal-deny} 统一 403 外，服务端还按 {@code bluewhale.internal-token.path-patterns}
 * 校验 {@code X-Internal-Token}（档案侧经 {@code InternalTokenSupport} 自动附加），形成双重防护，
 * 故不额外加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/form/internal")
@RequiredArgsConstructor
public class FormInternalController {

    private final FormDataService formDataService;

    /**
     * 表单归档详情：表单数据（含结构化字段）+ 字段定义。
     *
     * @param id 表单数据ID
     * @param httpRequest 当前请求（读取 {@code X-Tenant-Id} 解析租户，缺失即拒绝）
     * @return 归档详情
     */
    @GetMapping("/data/{id}")
    public Result<FormArchiveVO> data(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(formDataService.getArchiveDetail(id, TenantResolver.resolve(httpRequest)));
    }
}
