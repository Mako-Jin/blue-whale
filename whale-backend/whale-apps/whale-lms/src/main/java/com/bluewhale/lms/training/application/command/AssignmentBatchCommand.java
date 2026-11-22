package com.bluewhale.lms.training.application.command;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 必修分配命令（批量）。
 *
 * <p>{@code scopeType / scopeValue} 只作追溯标记：本轮名单由调用方解析后提交，
 * 服务端负责批量落库与幂等；M5 接入 ERP 员工目录后按范围做增量补人。</p>
 *
 * @author BlueWhale
 */
@Data
public class AssignmentBatchCommand {

    /** 分配范围：ALL / DEPT / ROLE / USER */
    private String scopeType;

    /** 范围取值（部门编码 / 角色编码，USER 可空） */
    @Size(max = 128, message = "{validation.lms.assignment-batch.scope-value-size}")
    private String scopeValue;

    /** 学员用户 ID 列表 */
    @NotEmpty(message = "{validation.lms.assignment-batch.user-ids-required}")
    private List<Long> userIds;
}
