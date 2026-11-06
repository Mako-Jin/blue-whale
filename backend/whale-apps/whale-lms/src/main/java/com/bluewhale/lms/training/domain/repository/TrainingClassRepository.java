package com.bluewhale.lms.training.domain.repository;

import com.bluewhale.lms.training.domain.model.ClassSession;
import com.bluewhale.lms.training.domain.model.TrainingClass;
import com.bluewhale.lms.training.domain.model.TrainingClassQuery;

import java.util.List;
import java.util.Optional;

/**
 * 培训班聚合仓储（含聚合内实体场次）。
 *
 * @author BlueWhale
 */
public interface TrainingClassRepository {

    /**
     * 按 ID 查询培训班。
     *
     * @param id 培训班 ID
     * @return 培训班
     */
    Optional<TrainingClass> findById(Long id);

    /**
     * 按编码查询（唯一性判定）。
     *
     * @param classCode 规范化编码
     * @return 培训班
     */
    Optional<TrainingClass> findByCode(String classCode);

    /**
     * 保存培训班。
     *
     * @param trainingClass 培训班
     * @return 主键
     */
    Long save(TrainingClass trainingClass);

    /**
     * 逻辑删除培训班。
     *
     * @param id 培训班 ID
     */
    void delete(Long id);

    /**
     * 分页查询。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 培训班列表
     */
    List<TrainingClass> page(TrainingClassQuery query, long offset, int size);

    /**
     * 统计总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(TrainingClassQuery query);

    /**
     * 查询某班的全部场次（按序号升序）。
     *
     * @param classId 培训班 ID
     * @return 场次列表
     */
    List<ClassSession> listSessions(Long classId);

    /**
     * 按 ID 查询场次。
     *
     * @param sessionId 场次 ID
     * @return 场次
     */
    Optional<ClassSession> findSessionById(Long sessionId);

    /**
     * 统计某班场次数（发布前置校验用）。
     *
     * @param classId 培训班 ID
     * @return 场次数
     */
    long countSessions(Long classId);

    /**
     * 保存场次。
     *
     * @param session 场次
     * @return 主键
     */
    Long saveSession(ClassSession session);

    /**
     * 逻辑删除场次。
     *
     * @param sessionId 场次 ID
     */
    void deleteSession(Long sessionId);
}
