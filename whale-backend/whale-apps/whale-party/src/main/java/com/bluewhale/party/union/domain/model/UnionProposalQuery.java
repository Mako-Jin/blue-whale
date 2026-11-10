package com.bluewhale.party.union.domain.model;

import lombok.Data;

/**
 * 职代会提案查询条件。
 *
 * <p>{@code mineUserId} 只由园地分页在服务层强制赋值，
 * 管理端传入也不生效，防止通过查询参数看到他人提案。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionProposalQuery {

    /** 关键字（提案编号 / 标题） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 仅看指定提交人的提案（园地强制为当前用户） */
    private Long mineUserId;
}
