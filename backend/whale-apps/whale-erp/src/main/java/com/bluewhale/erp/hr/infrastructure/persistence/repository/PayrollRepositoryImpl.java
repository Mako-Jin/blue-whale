package com.bluewhale.erp.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.PayrollRun;
import com.bluewhale.erp.hr.domain.model.Payslip;
import com.bluewhale.erp.hr.domain.repository.PayrollRepository;
import com.bluewhale.erp.hr.infrastructure.persistence.entity.PayrollRunEntity;
import com.bluewhale.erp.hr.infrastructure.persistence.entity.PayslipEntity;
import com.bluewhale.erp.hr.infrastructure.persistence.mapper.PayslipMapper;
import com.bluewhale.erp.hr.infrastructure.persistence.mapper.PayrollRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 薪资批次仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PayrollRepositoryImpl implements PayrollRepository {

    /** 批次号前缀 */
    private static final String DOC_PREFIX = "PR-";

    private final PayrollRunMapper payrollRunMapper;

    private final PayslipMapper payslipMapper;

    @Override
    public List<PayrollRun> page(HrQuery query) {
        Page<PayrollRunEntity> page = payrollRunMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(HrQuery query) {
        return payrollRunMapper.selectCount(filter(query));
    }

    @Override
    public Optional<PayrollRun> findById(Long id) {
        PayrollRunEntity entity = payrollRunMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        PayrollRun run = toDomain(entity);
        run.setPayslips(listPayslips(id, null));
        return Optional.of(run);
    }

    @Override
    public Optional<PayrollRun> findByPeriod(String period) {
        PayrollRunEntity entity = payrollRunMapper.selectOne(new LambdaQueryWrapper<PayrollRunEntity>()
                .eq(PayrollRunEntity::getPeriod, period)
                .last("limit 1"));
        if (entity == null) {
            return Optional.empty();
        }
        PayrollRun run = toDomain(entity);
        run.setPayslips(listPayslips(entity.getId(), null));
        return Optional.of(run);
    }

    @Override
    public String nextRunNo(String period) {
        long sequence = payrollRunMapper.selectCount(new LambdaQueryWrapper<PayrollRunEntity>()
                .eq(PayrollRunEntity::getPeriod, period)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, period.replace("-", ""), sequence);
    }

    @Override
    public void insert(PayrollRun run) {
        PayrollRunEntity entity = new PayrollRunEntity();
        fill(entity, run);
        payrollRunMapper.insert(entity);
        run.setId(entity.getId());
        if (run.getPayslips() != null) {
            for (Payslip payslip : run.getPayslips()) {
                payslipMapper.insert(payslipEntity(entity.getId(), payslip));
            }
        }
    }

    @Override
    public void update(PayrollRun run) {
        PayrollRunEntity entity = new PayrollRunEntity();
        entity.setId(run.getId());
        entity.setStatus(run.getStatus());
        entity.setEmployeeCount(run.getEmployeeCount());
        entity.setTotalGross(run.getTotalGross());
        entity.setTotalSocialInsurance(run.getTotalSocialInsurance());
        entity.setTotalHousingFund(run.getTotalHousingFund());
        entity.setTotalTax(run.getTotalTax());
        entity.setTotalNet(run.getTotalNet());
        entity.setRemark(run.getRemark());
        payrollRunMapper.updateById(entity);
    }

    @Override
    public void savePayslips(PayrollRun run) {
        payslipMapper.delete(new LambdaQueryWrapper<PayslipEntity>()
                .eq(PayslipEntity::getPayrollId, run.getId()));
        if (run.getPayslips() == null) {
            return;
        }
        for (Payslip payslip : run.getPayslips()) {
            PayslipEntity entity = payslipEntity(run.getId(), payslip);
            payslipMapper.insert(entity);
            payslip.setId(entity.getId());
        }
    }

    @Override
    public List<PayrollRun> pageByEmployee(String employeeNo, HrQuery query) {
        List<Long> payrollIds = payrollIdsOf(employeeNo);
        if (payrollIds.isEmpty()) {
            return List.of();
        }
        Page<PayrollRunEntity> page = payrollRunMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)),
                employeeFilter(query, payrollIds));
        return page.getRecords().stream().map(entity -> {
                    PayrollRun run = toDomain(entity);
                    run.setPayslips(listPayslips(entity.getId(), employeeNo));
                    return run;
                }).toList();
    }

    @Override
    public long countByEmployee(String employeeNo, HrQuery query) {
        List<Long> payrollIds = payrollIdsOf(employeeNo);
        if (payrollIds.isEmpty()) {
            return 0L;
        }
        return payrollRunMapper.selectCount(employeeFilter(query, payrollIds));
    }

    /**
     * 查询员工工资条所属批次主键。
     *
     * @param employeeNo 员工编号
     * @return 批次主键列表
     */
    private List<Long> payrollIdsOf(String employeeNo) {
        return payslipMapper.selectList(new LambdaQueryWrapper<PayslipEntity>()
                        .eq(PayslipEntity::getEmployeeNo, employeeNo))
                .stream().map(PayslipEntity::getPayrollId).distinct().toList();
    }

    /**
     * 查询批次下的工资条。
     *
     * @param payrollId 批次主键
     * @param employeeNo 员工编号（为空表示全部）
     * @return 工资条列表
     */
    private List<Payslip> listPayslips(Long payrollId, String employeeNo) {
        LambdaQueryWrapper<PayslipEntity> wrapper = new LambdaQueryWrapper<PayslipEntity>()
                .eq(PayslipEntity::getPayrollId, payrollId);
        if (StringUtils.hasText(employeeNo)) {
            wrapper.eq(PayslipEntity::getEmployeeNo, employeeNo);
        }
        return payslipMapper.selectList(wrapper.orderByAsc(PayslipEntity::getEmployeeNo))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 构建批次查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<PayrollRunEntity> filter(HrQuery query) {
        LambdaQueryWrapper<PayrollRunEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PayrollRunEntity::getRunNo, query.getKeyword())
                    .or().like(PayrollRunEntity::getPeriod, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PayrollRunEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPeriod())) {
            wrapper.eq(PayrollRunEntity::getPeriod, query.getPeriod());
        }
        return wrapper.orderByDesc(PayrollRunEntity::getPeriod).orderByDesc(PayrollRunEntity::getRunNo);
    }

    /**
     * 构建员工工资条所属批次查询条件。
     *
     * @param query 查询条件
     * @param payrollIds 批次主键列表
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<PayrollRunEntity> employeeFilter(HrQuery query, List<Long> payrollIds) {
        LambdaQueryWrapper<PayrollRunEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(PayrollRunEntity::getId, payrollIds);
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PayrollRunEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPeriod())) {
            wrapper.eq(PayrollRunEntity::getPeriod, query.getPeriod());
        }
        return wrapper.orderByDesc(PayrollRunEntity::getPeriod).orderByDesc(PayrollRunEntity::getRunNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PayrollRun toDomain(PayrollRunEntity entity) {
        PayrollRun run = new PayrollRun();
        run.setId(entity.getId());
        run.setRunNo(entity.getRunNo());
        run.setPeriod(entity.getPeriod());
        run.setStatus(entity.getStatus());
        run.setEmployeeCount(entity.getEmployeeCount() == null ? 0 : entity.getEmployeeCount());
        run.setTotalGross(entity.getTotalGross());
        run.setTotalSocialInsurance(entity.getTotalSocialInsurance());
        run.setTotalHousingFund(entity.getTotalHousingFund());
        run.setTotalTax(entity.getTotalTax());
        run.setTotalNet(entity.getTotalNet());
        run.setRemark(entity.getRemark());
        return run;
    }

    /**
     * 工资条实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Payslip toDomain(PayslipEntity entity) {
        Payslip payslip = new Payslip();
        payslip.setId(entity.getId());
        payslip.setPayrollId(entity.getPayrollId());
        payslip.setEmployeeNo(entity.getEmployeeNo());
        payslip.setEmployeeName(entity.getEmployeeName());
        payslip.setOrgCode(entity.getOrgCode());
        payslip.setBaseSalary(entity.getBaseSalary());
        payslip.setAllowance(entity.getAllowance());
        payslip.setOvertimePay(entity.getOvertimePay());
        payslip.setLeaveDeduction(entity.getLeaveDeduction());
        payslip.setAbsenceDeduction(entity.getAbsenceDeduction());
        payslip.setGrossPay(entity.getGrossPay());
        payslip.setSocialInsurance(entity.getSocialInsurance());
        payslip.setHousingFund(entity.getHousingFund());
        payslip.setTax(entity.getTax());
        payslip.setNetPay(entity.getNetPay());
        return payslip;
    }

    /**
     * 领域模型写入批次实体。
     *
     * @param entity 实体
     * @param run 薪资批次
     */
    private void fill(PayrollRunEntity entity, PayrollRun run) {
        entity.setRunNo(run.getRunNo());
        entity.setPeriod(run.getPeriod());
        entity.setStatus(run.getStatus());
        entity.setEmployeeCount(run.getEmployeeCount());
        entity.setTotalGross(run.getTotalGross());
        entity.setTotalSocialInsurance(run.getTotalSocialInsurance());
        entity.setTotalHousingFund(run.getTotalHousingFund());
        entity.setTotalTax(run.getTotalTax());
        entity.setTotalNet(run.getTotalNet());
        entity.setRemark(run.getRemark());
    }

    /**
     * 工资条领域模型转实体。
     *
     * @param payrollId 批次主键
     * @param payslip 工资条
     * @return 实体
     */
    private PayslipEntity payslipEntity(Long payrollId, Payslip payslip) {
        PayslipEntity entity = new PayslipEntity();
        entity.setPayrollId(payrollId);
        entity.setEmployeeNo(payslip.getEmployeeNo());
        entity.setEmployeeName(payslip.getEmployeeName());
        entity.setOrgCode(payslip.getOrgCode());
        entity.setBaseSalary(payslip.getBaseSalary());
        entity.setAllowance(payslip.getAllowance());
        entity.setOvertimePay(payslip.getOvertimePay());
        entity.setLeaveDeduction(payslip.getLeaveDeduction());
        entity.setAbsenceDeduction(payslip.getAbsenceDeduction());
        entity.setGrossPay(payslip.getGrossPay());
        entity.setSocialInsurance(payslip.getSocialInsurance());
        entity.setHousingFund(payslip.getHousingFund());
        entity.setTax(payslip.getTax());
        entity.setNetPay(payslip.getNetPay());
        return entity;
    }
}
