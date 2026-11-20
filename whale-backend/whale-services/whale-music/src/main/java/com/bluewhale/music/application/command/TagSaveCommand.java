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
    @NotBlank(message = "{validation.music.tag.name-required}")
    @Size(max = 64, message = "{validation.music.tag.name-size}")
    private String name;

    /** 标签类型；缺省 GENRE */
    private TagType tagType;

    /** 排序 */
    private Integer sortNo;
}
