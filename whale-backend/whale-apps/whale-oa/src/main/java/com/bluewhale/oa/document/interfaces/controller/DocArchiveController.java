package com.bluewhale.oa.document.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.document.application.service.DocArchiveAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公文归档接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/document/archive", version = "1")
@RequiredArgsConstructor
public class DocArchiveController {

    private static final String TARGET_TYPE = "oa_doc_archive";

    private final DocArchiveAppService archiveAppService;

    /**
     * 分页查询归档映射。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/refs/page")
    @RequirePermission("sys:oa:document:list")
    public Result<PageResult<DocumentViews.ArchiveRef>> page(DocQuery query) {
        return Result.ok(archiveAppService.page(query));
    }

    /**
     * 重试归档。
     *
     * @param id 归档映射ID
     * @return 归档映射
     */
    @PostMapping("/refs/{id}/retry")
    @RequirePermission("sys:oa:document:archive")
    @AuditLog(operation = "重试公文归档", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DocumentViews.ArchiveRef> retry(@PathVariable Long id) {
        return Result.ok(archiveAppService.retry(id));
    }
}
