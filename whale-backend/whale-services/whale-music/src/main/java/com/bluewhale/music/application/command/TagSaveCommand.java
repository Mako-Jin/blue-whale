package com.bluewhale.music.application.command;

import com.bluewhale.music.domain.model.TagType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标签保存命令。
 *
 * @author BlueWhale
 */
@Data
public class TagSaveCommand {

    /** 标签名称 */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 64, message = "标签名称最多 64 个字符")
    private String name;

    /** 标签类型；缺省 GENRE */
    private TagType tagType;

    /** 排序 */
    private Integer sortNo;
}
