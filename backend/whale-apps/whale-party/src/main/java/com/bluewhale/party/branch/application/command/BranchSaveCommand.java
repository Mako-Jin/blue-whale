package com.bluewhale.party.branch.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 支部新建 / 编辑命令。
 *
 * <p>刻意<b>不接收 branchCode</b>：编码由编号流水统一生成（{@code BR+年份+4 位流水}），
 * 允许手填会出现「编码规则两套、排序与统计都要兼容两种格式」的局面。</p>
 *
 * @author BlueWhale
 */
@Data
public class BranchSaveCommand {

    /** 支部名称 */
    @NotBlank(message = "支部名称不能为空")
    @Size(max = 128, message = "支部名称不得超过 128 字符")
    private String branchName;

    /** 上级支部 ID（空或 0 表示一级支部） */
    private Long parentId;

    /** 关联 ERP 组织编码（弱关联，可空） */
    @Size(max = 64, message = "组织编码不得超过 64 字符")
    private String orgCode;

    /** 支部书记姓名 */
    @Size(max = 64, message = "书记姓名不得超过 64 字符")
    private String secretaryName;

    /** 支部书记用户 ID */
    private Long secretaryUserId;

    /** 成立日期 */
    private LocalDate establishedDate;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
