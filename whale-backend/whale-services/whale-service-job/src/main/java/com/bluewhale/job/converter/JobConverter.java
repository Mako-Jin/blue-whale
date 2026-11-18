package com.bluewhale.job.converter;

import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobInfoUpdateRequest;
import com.bluewhale.job.po.JobAlertPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.po.JobLogPo;
import com.bluewhale.job.vo.JobAlertVO;
import com.bluewhale.job.vo.JobInfoVO;
import com.bluewhale.job.vo.JobLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 定时任务模块实体与 VO/DTO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段
 * 对齐。实体继承 BaseEntity 的审计字段不在请求体中，声明 ignore；下次触发时间由
 * 调度器装配。</p>
 * @author BlueWhale
 */
@Mapper
public interface JobConverter {

    /** 单例（无状态映射器） */
    JobConverter INSTANCE = Mappers.getMapper(JobConverter.class);

    /**
     * 任务实体转视图对象（下次触发时间由调度器装配）
     *
     * @param entity 任务实体
     * @return 任务视图对象
     */
    @Mapping(target = "nextFireTime", ignore = true)
    JobInfoVO toInfoVo(JobInfoPo entity);

    /**
     * 告警实体转视图对象
     *
     * @param entity 告警实体
     * @return 告警视图对象
     */
    @Mapping(target = "recovered", ignore = true)
    JobAlertVO toAlertVo(JobAlertPo entity);

    /**
     * 执行日志实体转视图对象
     *
     * @param entity 日志实体
     * @return 日志视图对象
     */
    JobLogVO toLogVo(JobLogPo entity);

    /**
     * 创建请求转任务实体（默认值与校验由服务层处理）
     *
     * @param request 创建请求
     * @return 任务实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    JobInfoPo toEntity(JobInfoCreateRequest request);

    /**
     * 更新请求覆盖既有任务实体（保留主键与审计字段）
     *
     * @param request 更新请求
     * @param entity  待更新的任务实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(JobInfoUpdateRequest request, @MappingTarget JobInfoPo entity);
}
