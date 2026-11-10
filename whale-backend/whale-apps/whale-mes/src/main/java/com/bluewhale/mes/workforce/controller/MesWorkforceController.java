package com.bluewhale.mes.workforce.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.workforce.dto.MesHandoverConfirmRequest;
import com.bluewhale.mes.workforce.dto.MesHandoverQueryRequest;
import com.bluewhale.mes.workforce.dto.MesHandoverSaveRequest;
import com.bluewhale.mes.workforce.dto.MesPerformanceAggregateRequest;
import com.bluewhale.mes.workforce.dto.MesPerformanceLockRequest;
import com.bluewhale.mes.workforce.dto.MesPerformanceQueryRequest;
import com.bluewhale.mes.workforce.dto.MesShiftAssignmentQueryRequest;
import com.bluewhale.mes.workforce.dto.MesShiftAssignmentSaveRequest;
import com.bluewhale.mes.workforce.dto.MesSkillMatrixQueryRequest;
import com.bluewhale.mes.workforce.dto.MesSkillMatrixSaveRequest;
import com.bluewhale.mes.workforce.dto.MesTeamMemberQueryRequest;
import com.bluewhale.mes.workforce.dto.MesTeamMemberSaveRequest;
import com.bluewhale.mes.workforce.dto.MesTeamMemberStatusRequest;
import com.bluewhale.mes.workforce.dto.MesWorkHourQueryRequest;
import com.bluewhale.mes.workforce.dto.MesWorkHourSaveRequest;
import com.bluewhale.mes.workforce.service.MesWorkforceService;
import com.bluewhale.mes.workforce.vo.MesHandoverVO;
import com.bluewhale.mes.workforce.vo.MesPerformanceLockVO;
import com.bluewhale.mes.workforce.vo.MesPerformanceStatVO;
import com.bluewhale.mes.workforce.vo.MesShiftAssignmentVO;
import com.bluewhale.mes.workforce.vo.MesSkillMatrixVO;
import com.bluewhale.mes.workforce.vo.MesTeamMemberVO;
import com.bluewhale.mes.workforce.vo.MesWorkHourVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MES 人员绩效接口
 *
 * <p>覆盖班组成员、排班分配、交接班、工时记录、绩效统计与技能矩阵六类能力，
 * 统一挂载在 {@code /mes/workforce} 路径下；月度绩效锁定后禁止重算。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/workforce")
@RequiredArgsConstructor
public class MesWorkforceController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_workforce";

    /** 查询权限 */
    private static final String PERM_LIST = "sys:mes:workforce:list";

    /** 班组成员维护权限 */
    private static final String PERM_TEAM = "sys:mes:workforce:team";

    /** 排班维护权限 */
    private static final String PERM_SHIFT = "sys:mes:workforce:shift";

    /** 交接班维护权限 */
    private static final String PERM_HANDOVER = "sys:mes:workforce:handover";

    /** 工时维护权限 */
    private static final String PERM_WORK_HOUR = "sys:mes:workforce:work-hour";

    /** 绩效汇总权限 */
    private static final String PERM_PERFORMANCE = "sys:mes:workforce:performance";

    /** 月度锁定权限 */
    private static final String PERM_LOCK = "sys:mes:workforce:lock";

    /** 技能矩阵维护权限 */
    private static final String PERM_SKILL = "sys:mes:workforce:skill";

    private final MesWorkforceService workforceService;

    /**
     * 保存班组成员
     *
     * @param request 保存请求
     * @return 成员视图
     */
    @PostMapping("/team-member")
    @RequirePermission(PERM_TEAM)
    @AuditLog(operation = "MES 班组成员保存", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#request.id")
    public Result<MesTeamMemberVO> saveTeamMember(@Valid @RequestBody MesTeamMemberSaveRequest request) {
        return Result.ok(workforceService.saveTeamMember(request));
    }

    /**
     * 分页查询班组成员
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/team-member/page")
    @RequirePermission(PERM_LIST)
    public Result<PageResult<MesTeamMemberVO>> pageTeamMembers(MesTeamMemberQueryRequest request) {
        return Result.ok(workforceService.pageTeamMembers(request));
    }

    /**
     * 启停班组成员
     *
     * @param id 成员 ID
     * @param request 启停请求
     * @return 成员视图
     */
    @PostMapping("/team-member/{id}/status")
    @RequirePermission(PERM_TEAM)
    @AuditLog(operation = "MES 班组成员启停", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesTeamMemberVO> changeTeamMemberStatus(@PathVariable Long id,
                                                         @Valid @RequestBody
                                                         MesTeamMemberStatusRequest request) {
        return Result.ok(workforceService.changeTeamMemberStatus(id, request));
    }

    /**
     * 保存排班分配
     *
     * @param request 保存请求
     * @return 排班视图
     */
    @PostMapping("/shift-assignment")
    @RequirePermission(PERM_SHIFT)
    @AuditLog(operation = "MES 排班分配保存", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#request.id")
    public Result<MesShiftAssignmentVO> saveShiftAssignment(
            @Valid @RequestBody MesShiftAssignmentSaveRequest request) {
        return Result.ok(workforceService.saveShiftAssignment(request));
    }

    /**
     * 分页查询排班分配
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/shift-assignment/page")
    @RequirePermission(PERM_LIST)
    public Result<PageResult<MesShiftAssignmentVO>> pageShiftAssignments(
            MesShiftAssignmentQueryRequest request) {
        return Result.ok(workforceService.pageShiftAssignments(request));
    }

    /**
     * 取消排班分配
     *
     * @param id 排班 ID
     * @return 排班视图
     */
    @PostMapping("/shift-assignment/{id}/cancel")
    @RequirePermission(PERM_SHIFT)
    @AuditLog(operation = "MES 排班分配取消", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesShiftAssignmentVO> cancelShiftAssignment(@PathVariable Long id) {
        return Result.ok(workforceService.cancelShiftAssignment(id));
    }

    /**
     * 新建交接班
     *
     * @param request 保存请求
     * @return 交接班视图
     */
    @PostMapping("/handover")
    @RequirePermission(PERM_HANDOVER)
    @AuditLog(operation = "MES 交接班新建", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesHandoverVO> createHandover(@Valid @RequestBody MesHandoverSaveRequest request) {
        return Result.ok(workforceService.createHandover(request));
    }

    /**
     * 分页查询交接班
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/handover/page")
    @RequirePermission(PERM_LIST)
    public Result<PageResult<MesHandoverVO>> pageHandovers(MesHandoverQueryRequest request) {
        return Result.ok(workforceService.pageHandovers(request));
    }

    /**
     * 确认交接班
     *
     * @param id 交接班 ID
     * @param request 确认请求
     * @return 交接班视图
     */
    @PostMapping("/handover/{id}/confirm")
    @RequirePermission(PERM_HANDOVER)
    @AuditLog(operation = "MES 交接班确认", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesHandoverVO> confirmHandover(@PathVariable Long id,
                                                 @Valid @RequestBody MesHandoverConfirmRequest request) {
        return Result.ok(workforceService.confirmHandover(id, request));
    }

    /**
     * 登记工时
     *
     * @param request 工时登记请求
     * @return 工时视图
     */
    @PostMapping("/work-hour")
    @RequirePermission(PERM_WORK_HOUR)
    @AuditLog(operation = "MES 工时登记", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesWorkHourVO> registerWorkHour(@Valid @RequestBody MesWorkHourSaveRequest request) {
        return Result.ok(workforceService.registerWorkHour(request));
    }

    /**
     * 分页查询工时记录
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/work-hour/page")
    @RequirePermission(PERM_LIST)
    public Result<PageResult<MesWorkHourVO>> pageWorkHours(MesWorkHourQueryRequest request) {
        return Result.ok(workforceService.pageWorkHours(request));
    }

    /**
     * 作废工时记录
     *
     * @param id 工时 ID
     * @return 工时视图
     */
    @PostMapping("/work-hour/{id}/void")
    @RequirePermission(PERM_WORK_HOUR)
    @AuditLog(operation = "MES 工时作废", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesWorkHourVO> voidWorkHour(@PathVariable Long id) {
        return Result.ok(workforceService.voidWorkHour(id));
    }

    /**
     * 分页查询绩效统计
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/performance/page")
    @RequirePermission(PERM_LIST)
    public Result<PageResult<MesPerformanceStatVO>> pagePerformance(MesPerformanceQueryRequest request) {
        return Result.ok(workforceService.pagePerformance(request));
    }

    /**
     * 按期间汇总绩效统计
     *
     * @param request 汇总请求
     * @return 绩效统计列表
     */
    @PostMapping("/performance/aggregate")
    @RequirePermission(PERM_PERFORMANCE)
    @AuditLog(operation = "MES 绩效统计汇总", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<List<MesPerformanceStatVO>> aggregatePerformance(
            @Valid @RequestBody MesPerformanceAggregateRequest request) {
        return Result.ok(workforceService.aggregatePerformance(request));
    }

    /**
     * 锁定月度绩效
     *
     * @param request 锁定请求
     * @return 锁定结果
     */
    @PostMapping("/performance/lock")
    @RequirePermission(PERM_LOCK)
    @AuditLog(operation = "MES 月度绩效锁定", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesPerformanceLockVO> lockPerformance(@Valid @RequestBody MesPerformanceLockRequest request) {
        return Result.ok(workforceService.lockPerformance(request));
    }

    /**
     * 保存技能矩阵记录
     *
     * @param request 保存请求
     * @return 技能视图
     */
    @PostMapping("/skill")
    @RequirePermission(PERM_SKILL)
    @AuditLog(operation = "MES 技能矩阵保存", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#request.id")
    public Result<MesSkillMatrixVO> saveSkill(@Valid @RequestBody MesSkillMatrixSaveRequest request) {
        return Result.ok(workforceService.saveSkill(request));
    }

    /**
     * 分页查询技能矩阵
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/skill/page")
    @RequirePermission(PERM_LIST)
    public Result<PageResult<MesSkillMatrixVO>> pageSkills(MesSkillMatrixQueryRequest request) {
        return Result.ok(workforceService.pageSkills(request));
    }
}
