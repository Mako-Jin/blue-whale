package com.bluewhale.i18n.admin;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.i18n.admin.dto.I18nMessageQueryRequest;
import com.bluewhale.i18n.admin.dto.I18nMessageSaveRequest;
import com.bluewhale.i18n.admin.vo.I18nMessageVO;
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

/**
 * 服务本地国际化文案管理接口
 *
 * <p>路径由 {@code whale.i18n.admin.path} 配置（默认 {@code /i18n/local/message}），
 * 默认不装配；服务显式开启后可直接维护自己库中的文案，无需集中式 i18n 服务。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("${whale.i18n.admin.path:/i18n/local/message}")
@RequiredArgsConstructor
public class I18nLocalMessageController {

    /**
     * 审计操作对象类型
     */
    private static final String TARGET_TYPE = "i18n_message";

    private final I18nLocalMessageService i18nLocalMessageService;

    /**
     * 分页查询国际化消息
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:i18n:list")
    public Result<PageResult<I18nMessageVO>> page(I18nMessageQueryRequest request) {
        return Result.ok(i18nLocalMessageService.page(request));
    }

    /**
     * 消息详情
     *
     * @param id 消息ID
     * @return 消息视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:i18n:list")
    public Result<I18nMessageVO> detail(@PathVariable Long id) {
        return Result.ok(i18nLocalMessageService.getDetail(id));
    }

    /**
     * 新增消息
     *
     * @param request 新增请求
     * @return 新增后的消息视图对象
     */
    @PostMapping
    @RequirePermission("sys:i18n:add")
    @AuditLog(operation = "新增国际化消息", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<I18nMessageVO> create(@Valid @RequestBody I18nMessageSaveRequest request) {
        return Result.ok(i18nLocalMessageService.create(request));
    }

    /**
     * 更新消息
     *
     * @param id      消息ID
     * @param request 更新请求
     * @return 更新后的消息视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:i18n:edit")
    @AuditLog(operation = "更新国际化消息", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<I18nMessageVO> update(@PathVariable Long id, @Valid @RequestBody I18nMessageSaveRequest request) {
        return Result.ok(i18nLocalMessageService.update(id, request));
    }

    /**
     * 删除消息（逻辑删除）
     *
     * @param id 消息ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:i18n:delete")
    @AuditLog(operation = "删除国际化消息", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        i18nLocalMessageService.delete(id);
        return Result.ok();
    }
}
