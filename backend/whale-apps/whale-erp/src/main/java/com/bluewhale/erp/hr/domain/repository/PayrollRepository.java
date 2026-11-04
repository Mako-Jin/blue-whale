package com.bluewhale.erp.hr.domain.repository;

import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.PayrollRun;

import java.util.List;
import java.util.Optional;

/**
 * 薪资批次仓储。
 *
 * @author BlueWhale
 */
public interface PayrollRepository {

    /**
     * 分页查询薪资批次。
     *
     * @param query 查询条件
     * @return 批次列表
     */
    List<PayrollRun> page(HrQuery query);

    /**
     * 统计批次数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(HrQuery query);

    /**
     * 按主键查询（含工资条）。
     *
     * @param id 主键
     * @return 薪资批次
     */
    Optional<PayrollRun> findById(Long id);

    /**
     * 按期间查询。
     *
     * @param period 期间
     * @return 薪资批次
     */
    Optional<PayrollRun> findByPeriod(String period);

    /**
     * 生成批次号。
     *
     * @param period 期间
     * @return 批次号
     */
    String nextRunNo(String period);

    /**
     * 新增批次与工资条。
     *
     * @param run 薪资批次
     */
    void insert(PayrollRun run);

    /**
     * 更新批次汇总与状态。
     *
     * @param run 薪资批次
     */
    void update(PayrollRun run);

    /**
     * 保存工资条（先清空再写入，仅计算阶段调用）。
     *
     * @param run 薪资批次
     */
    void savePayslips(PayrollRun run);

    /**
     * 按员工查询已审批/已发放的工资条（我的工资条）。
     *
     * @param employeeNo 员工编号
     * @param query 查询条件
     * @return 工资条列表
     */
    List<PayrollRun> pageByEmployee(String employeeNo, HrQuery query);

    /**
     * 统计员工工资条所属批次数量。
     *
     * @param employeeNo 员工编号
     * @param query 查询条件
     * @return 总条数
     */
    long countByEmployee(String employeeNo, HrQuery query);
}
