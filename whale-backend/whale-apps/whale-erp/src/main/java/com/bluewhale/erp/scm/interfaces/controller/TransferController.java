package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.TransferCreateCommand;
import com.bluewhale.erp.scm.application.service.TransferAppService;
import com.bluewhale.erp.scm.application.view.TransferView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存调拨接口：查询、建单、过账、取消。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/scm/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferAppService transferAppService;

    /**
     * 调拨单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 调拨单分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:transfer:list")
    public Result<PageResult<TransferView>> page(
            @ModelAttribute ScmQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(transferAppService.page(query));
    }

    /**
     * 调拨单详情。
     *
     * @param id 调拨单 ID
     * @return 调拨单视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:transfer:list")
    public Result<TransferView> detail(@PathVariable Long id) {
        return Result.ok(transferAppService.detail(id));
    }

    /**
     * 新增调拨单。
     *
     * @param command 创建命令
     * @return 调拨单视图
     */
    @PostMapping
    @RequirePermission("sys:erp:transfer:add")
    @AuditLog(operation = "新增库存调拨单", targetType = "erp_scm_transfer", type = OperateType.CREATE)
    public Result<TransferView> create(@Valid @RequestBody TransferCreateCommand command) {
        return Result.ok(transferAppService.create(command));
    }

    /**
     * 调拨过账。
     *
     * @param id 调拨单 ID
     * @return 调拨单视图
     */
    @PostMapping("/{id}/post")
    @RequirePermission("sys:erp:transfer:post")
    @AuditLog(operation = "库存调拨过账", targetType = "erp_scm_transfer", type = OperateType.UPDATE)
    public Result<TransferView> post(@PathVariable Long id) {
        return Result.ok(transferAppService.post(id));
    }

    /**
     * 取消调拨单。
     *
     * @param id 调拨单 ID
     * @return 调拨单视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:erp:transfer:cancel")
    @AuditLog(operation = "取消库存调拨单", targetType = "erp_scm_transfer", type = OperateType.UPDATE)
    public Result<TransferView> cancel(@PathVariable Long id) {
        return Result.ok(transferAppService.cancel(id));
    }
}
