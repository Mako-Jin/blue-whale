package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.interfaces.vo.DocMetaVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档服务元信息接口。
 *
 * <p>服务内路径 {@code /doc/meta}，经网关按 {@code Path=/doc/**} 转发；用于前端子应用
 * 启动探测与能力开关读取。M1 仅暴露运行参数，不返回任何业务数据。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/doc/v{version}", version = "1")
@RequiredArgsConstructor
public class DocMetaController {

    private final DocProperties docProperties;

    /**
     * 读取文档服务运行元信息。
     *
     * @param request 当前请求（读取网关注入的用户头）
     * @return 运行元信息
     */
    @GetMapping("/meta")
    @RequirePermission("sys:doc:space:list")
    public Result<DocMetaVO> meta(HttpServletRequest request) {
        DocMetaVO meta = new DocMetaVO();
        meta.setService("whale-doc");
        meta.setTenantId(TenantContext.requireId());
        meta.setUserId(WebHeaders.userId(request));
        meta.setCollabEnabled(docProperties.isCollabEnabled());
        meta.setApprovalEnabled(docProperties.isApprovalEnabled());
        meta.setContentMaxBytes(docProperties.getContentMaxBytes());
        meta.setContentTextMaxBytes(docProperties.getContentTextMaxBytes());
        meta.setAttachmentMaxBytes(docProperties.getAttachmentMaxBytes());
        meta.setMaxDepth(docProperties.getMaxDepth());
        return Result.ok(meta);
    }
}
