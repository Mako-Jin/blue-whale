package com.bluewhale.forum.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ForumMetaVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 门面接口单元测试：枚举完整性、上限透传、匿名开关 fail-closed。
 *
 * <p>这几条断言对应验收清单里的「起服冒烟」与「匿名不可绕过」：
 * 枚举少一个不报错，只会让前端少一个选项；匿名密钥为空却仍开启，
 * 等于编号可预测、封禁可绕过。两者都必须在单测层拦住。</p>
 *
 * @author BlueWhale
 */
class ForumMetaControllerTest {

    private ForumMetaController controller(ForumProperties properties) {
        return new ForumMetaController(properties);
    }

    @Test
    @DisplayName("meta 下发全部枚举，缺一个前端就少一个选项")
    void meta_should_expose_all_enums() {
        Result<ForumMetaVO> result = controller(new ForumProperties()).meta();

        assertThat(result.getCode()).isZero();
        ForumMetaVO view = result.getData();
        assertThat(view.getService()).isEqualTo("whale-forum");
        assertThat(view.getThreadTypes()).containsExactlyInAnyOrder(
                "NORMAL", "QA", "VOTE", "NOTICE", "ACTIVITY", "ARTICLE");
        assertThat(view.getThreadStatuses()).containsExactlyInAnyOrder(
                "DRAFT", "SCHEDULED", "PENDING", "PUBLISHED",
                "SOLVED", "CLOSED", "REJECTED", "DELETED");
        assertThat(view.getBoardVisibilities()).containsExactlyInAnyOrder("ALL", "ROLE", "DEPT");
        assertThat(view.getAuditModes()).containsExactlyInAnyOrder("POST", "PRE");
        assertThat(view.getReportStatuses()).containsExactlyInAnyOrder(
                "PENDING", "HANDLING", "RESOLVED", "REJECTED");
        assertThat(view.getReactionTypes()).containsExactlyInAnyOrder("LIKE", "COLLECT");
        assertThat(view.getModerationActions()).contains("AUDIT_REJECT", "REMOVE", "MUTE", "BAN");
    }

    @Test
    @DisplayName("上限按配置透传，前端与后端不各写一套")
    void meta_should_pass_through_limits() {
        ForumProperties properties = new ForumProperties();
        properties.setMaxTitleLength(80);
        properties.setMaxPostsPerThread(1234);
        properties.setPageSizeMax(50);

        ForumMetaVO view = controller(properties).meta().getData();

        assertThat(view.getLimits().getMaxTitleLength()).isEqualTo(80);
        assertThat(view.getLimits().getMaxPostsPerThread()).isEqualTo(1234);
        assertThat(view.getLimits().getPageSizeMax()).isEqualTo(50);
        assertThat(view.getLimits().getReplyIntervalSeconds()).isEqualTo(10);
    }

    @Test
    @DisplayName("匿名密钥为空时匿名开关必须关闭（fail-closed）")
    void anonymous_should_be_disabled_without_secret() {
        ForumProperties properties = new ForumProperties();
        properties.setAnonymousSecret("");
        assertThat(controller(properties).meta().getData().isAnonymousEnabled()).isFalse();

        properties.setAnonymousSecret("   ");
        assertThat(controller(properties).meta().getData().isAnonymousEnabled()).isFalse();

        properties.setAnonymousSecret("forum-anon-secret");
        assertThat(controller(properties).meta().getData().isAnonymousEnabled()).isTrue();
    }

    @Test
    @DisplayName("枚举列表不可为空，空列表会让前端开关全部灰掉")
    void enums_should_never_be_empty() {
        ForumMetaVO view = controller(new ForumProperties()).meta().getData();

        for (List<String> values : List.of(view.getThreadTypes(), view.getThreadStatuses(),
                view.getBoardVisibilities(), view.getAuditModes(), view.getReportStatuses(),
                view.getReactionTypes(), view.getModerationActions())) {
            assertThat(values).isNotEmpty();
        }
    }
}
