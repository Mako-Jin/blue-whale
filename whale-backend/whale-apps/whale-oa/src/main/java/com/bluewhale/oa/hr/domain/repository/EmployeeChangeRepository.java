package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.domain.model.EmployeeChange;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeQuery;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeTask;

import java.util.List;

/**
 * 人员异动仓储端口。
 *
 * @author BlueWhale
 */
public interface EmployeeChangeRepository {

    /**
     * 按主键查询异动单。
     *
     * @param id 主键
     * @return 异动单，不存在返回 null
     */
    EmployeeChange findById(Long id);

    /**
     * 按单号查询异动单。
     *
     * @param changeNo 异动单号
     * @return 异动单，不存在返回 null
     */
    EmployeeChange findByChangeNo(String changeNo);

    /**
     * 分页查询异动单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<EmployeeChange> page(EmployeeChangeQuery query);

    /**
     * 查询异动单下的任务。
     *
     * @param changeId 异动单ID
     * @return 任务列表
     */
    List<EmployeeChangeTask> findTasks(Long changeId);

    /**
     * 按主键查询任务。
     *
     * @param changeId 异动单ID
     * @param taskId 任务ID
     * @return 任务，不存在返回 null
     */
    EmployeeChangeTask findTask(Long changeId, Long taskId);

    /**
     * 新增异动单。
     *
     * @param change 异动单
     */
    void insert(EmployeeChange change);

    /**
     * 更新异动单。
     *
     * @param change 异动单
     */
    void update(EmployeeChange change);

    /**
     * 新增任务。
     *
     * @param task 任务
     */
    void insertTask(EmployeeChangeTask task);

    /**
     * 更新任务。
     *
     * @param task 任务
     */
    void updateTask(EmployeeChangeTask task);
}
