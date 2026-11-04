package com.bluewhale.pdm.document.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.document.dto.PdmDocFolderSaveRequest;
import com.bluewhale.pdm.document.service.PdmDocFolderService;
import com.bluewhale.pdm.document.vo.PdmDocFolderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PDM 图文档目录接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/folder")
@RequiredArgsConstructor
public class PdmDocFolderController {

    private final PdmDocFolderService folderService;

    /**
     * 查询目录树
     *
     * @return 目录树
     */
    @GetMapping("/tree")
    @RequirePermission("sys:pdm:doc:list")
    public Result<List<PdmDocFolderVO>> tree() {
        return Result.ok(folderService.tree());
    }

    /**
     * 查询目录详情
     *
     * @param id 目录 ID
     * @return 目录视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:doc:list")
    public Result<PdmDocFolderVO> get(@PathVariable Long id) {
        return Result.ok(folderService.get(id));
    }

    /**
     * 新建目录
     *
     * @param request 保存请求
     * @return 目录视图
     */
    @PostMapping
    @RequirePermission("sys:pdm:folder:manage")
    @AuditLog(module = "PDM 图文档", type = OperateType.CREATE, operation = "新建图文档目录")
    public Result<PdmDocFolderVO> create(@Valid @RequestBody PdmDocFolderSaveRequest request) {
        return Result.ok(folderService.create(request));
    }

    /**
     * 更新目录
     *
     * @param id      目录 ID
     * @param request 保存请求
     * @return 目录视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:folder:manage")
    @AuditLog(module = "PDM 图文档", type = OperateType.UPDATE, operation = "更新图文档目录")
    public Result<PdmDocFolderVO> update(@PathVariable Long id,
                                         @Valid @RequestBody PdmDocFolderSaveRequest request) {
        return Result.ok(folderService.update(id, request));
    }

    /**
     * 删除目录
     *
     * @param id 目录 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:folder:manage")
    @AuditLog(module = "PDM 图文档", type = OperateType.DELETE, operation = "删除图文档目录")
    public Result<Void> delete(@PathVariable Long id) {
        folderService.delete(id);
        return Result.ok();
    }
}
