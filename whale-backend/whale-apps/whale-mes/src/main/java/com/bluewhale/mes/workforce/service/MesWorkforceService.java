package com.bluewhale.mes.workforce.service;

import com.bluewhale.common.core.result.PageResult;
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
import com.bluewhale.mes.workforce.vo.MesHandoverVO;
import com.bluewhale.mes.workforce.vo.MesPerformanceLockVO;
import com.bluewhale.mes.workforce.vo.MesPerformanceStatVO;
import com.bluewhale.mes.workforce.vo.MesShiftAssignmentVO;
import com.bluewhale.mes.workforce.vo.MesSkillMatrixVO;
import com.bluewhale.mes.workforce.vo.MesTeamMemberVO;
import com.bluewhale.mes.workforce.vo.MesWorkHourVO;

import java.util.List;

/**
 * MES 人员绩效应用服务
 *
 * <p>覆盖班组成员、排班分配、交接班、工时记录、绩效统计与技能矩阵六类能力。
 * 月度绩效锁定后禁止任何重算或修改，跨租户数据由持久层租户隔离自动过滤。</p>
 *
 * @author BlueWhale
 */
public interface MesWorkforceService {

    /**
     * 保存班组成员（新增/编辑）
     *
     * @param request 保存请求
     * @return 成员视图
     */
    MesTeamMemberVO saveTeamMember(MesTeamMemberSaveRequest request);

    /**
     * 分页查询班组成员
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesTeamMemberVO> pageTeamMembers(MesTeamMemberQueryRequest request);

    /**
     * 启停班组成员
     *
     * @param id 成员 ID
     * @param request 启停请求
     * @return 成员视图
     */
    MesTeamMemberVO changeTeamMemberStatus(Long id, MesTeamMemberStatusRequest request);

    /**
     * 保存排班分配（新增/编辑）
     *
     * @param request 保存请求
     * @return 排班视图
     */
    MesShiftAssignmentVO saveShiftAssignment(MesShiftAssignmentSaveRequest request);

    /**
     * 分页查询排班分配
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesShiftAssignmentVO> pageShiftAssignments(MesShiftAssignmentQueryRequest request);

    /**
     * 取消排班分配
     *
     * @param id 排班 ID
     * @return 排班视图
     */
    MesShiftAssignmentVO cancelShiftAssignment(Long id);

    /**
     * 新建交接班记录
     *
     * @param request 保存请求
     * @return 交接班视图
     */
    MesHandoverVO createHandover(MesHandoverSaveRequest request);

    /**
     * 分页查询交接班记录
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesHandoverVO> pageHandovers(MesHandoverQueryRequest request);

    /**
     * 确认交接班记录
     *
     * @param id 交接班 ID
     * @param request 确认请求
     * @return 交接班视图
     */
    MesHandoverVO confirmHandover(Long id, MesHandoverConfirmRequest request);

    /**
     * 登记工时
     *
     * @param request 工时登记请求
     * @return 工时视图
     */
    MesWorkHourVO registerWorkHour(MesWorkHourSaveRequest request);

    /**
     * 分页查询工时记录
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesWorkHourVO> pageWorkHours(MesWorkHourQueryRequest request);

    /**
     * 作废工时记录
     *
     * @param id 工时 ID
     * @return 工时视图
     */
    MesWorkHourVO voidWorkHour(Long id);

    /**
     * 分页查询绩效统计
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesPerformanceStatVO> pagePerformance(MesPerformanceQueryRequest request);

    /**
     * 按期间汇总绩效统计
     *
     * @param request 汇总请求
     * @return 绩效统计视图列表
     */
    List<MesPerformanceStatVO> aggregatePerformance(MesPerformanceAggregateRequest request);

    /**
     * 锁定月度绩效
     *
     * @param request 锁定请求
     * @return 锁定结果
     */
    MesPerformanceLockVO lockPerformance(MesPerformanceLockRequest request);

    /**
     * 保存技能矩阵记录（新增/编辑）
     *
     * @param request 保存请求
     * @return 技能视图
     */
    MesSkillMatrixVO saveSkill(MesSkillMatrixSaveRequest request);

    /**
     * 分页查询技能矩阵
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesSkillMatrixVO> pageSkills(MesSkillMatrixQueryRequest request);
}
