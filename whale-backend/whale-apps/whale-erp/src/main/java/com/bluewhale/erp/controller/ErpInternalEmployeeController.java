package com.bluewhale.erp.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.hr.application.service.EmployeeAppService;
import com.bluewhale.erp.hr.application.view.EmployeeView;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.vo.ErpInternalEmployeeView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ERP 员工目录内网只读契约接口。
 *
 * <p>供 whale-lms 同步员工只读快照（判断在职状态、组织归属、是否已开通平台账号）。
 * 网关已将 {@code /erp/internal/**} 加入 internal-deny，外部访问一律 403。</p>
 *
 * <p>不按关键字/状态过滤，只支持按页全量拉取：同步要的是完整快照，
 * 支持过滤反而会让调用方误以为可以只同步一部分，导致快照长期残缺。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/internal/employee")
@RequiredArgsConstructor
public class ErpInternalEmployeeController {

    /** 单页最大条数（防止一次拉爆 ERP 内存） */
    private static final long MAX_PAGE_SIZE = 500L;

    /** 未指定条数时的默认页大小 */
    private static final long DEFAULT_PAGE_SIZE = 100L;

    private final EmployeeAppService employeeAppService;

    /**
     * 分页拉取员工目录。
     *
     * @param current 页码（从 1 起）
     * @param size 每页条数（上限 500）
     * @return 员工目录分页
     */
    @GetMapping("/directory")
    public Result<PageResult<ErpInternalEmployeeView>> directory(
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "size", required = false) Long size) {
        HrQuery query = new HrQuery();
        query.setCurrent(Math.max(current == null ? 1L : current, 1L));
        query.setSize(Math.min(Math.max(size == null ? DEFAULT_PAGE_SIZE : size, 1L), MAX_PAGE_SIZE));
        PageResult<EmployeeView> page = employeeAppService.page(query);
        List<ErpInternalEmployeeView> views = page.getRecords().stream().map(this::narrow).toList();
        return Result.ok(PageResult.of(views, page.getTotal(), query.getCurrent(), query.getSize()));
    }

    /**
     * 裁成内网契约字段集。
     *
     * @param view 员工视图
     * @return 内网视图
     */
    private ErpInternalEmployeeView narrow(EmployeeView view) {
        ErpInternalEmployeeView narrow = new ErpInternalEmployeeView();
        narrow.setEmployeeNo(view.getEmployeeNo());
        narrow.setUserId(view.getUserId());
        narrow.setName(view.getName());
        narrow.setOrgCode(view.getOrgCode());
        narrow.setPosition(view.getPosition());
        narrow.setStatus(view.getStatus());
        narrow.setHireDate(view.getHireDate());
        narrow.setLeaveDate(view.getLeaveDate());
        return narrow;
    }
}
