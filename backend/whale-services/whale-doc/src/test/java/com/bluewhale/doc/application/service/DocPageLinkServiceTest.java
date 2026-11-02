package com.bluewhale.doc.application.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageLink;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageLinkMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocBacklinkRow;
import com.bluewhale.doc.interfaces.vo.DocBacklinkVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DocPageLinkService} 引用提取与反向链接行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocPageLinkServiceTest {

    @Mock
    private DocPageLinkMapper linkMapper;

    private DocPageLinkService service;

    @BeforeEach
    void setUp() {
        service = new DocPageLinkService(linkMapper, new ObjectMapper());
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void refreshShouldSkipNullSourcePageWithoutTouchingMapper() {
        service.refresh(null, "{\"type\":\"doc\"}");

        verifyNoInteractions(linkMapper);
    }

    @Test
    void refreshShouldDeleteAndRebuildLinksFromMarkAndAttrs() {
        String json = """
                {"type":"doc","content":[
                  {"type":"text","text":"页面甲","marks":[
                    {"type":"link","attrs":{"href":"/doc/page/42"}},
                    {"type":"bold"}
                  ]},
                  {"type":"text","text":"页面乙","attrs":{"pageId":"43"}},
                  {"type":"text","text":"自身","attrs":{"href":"pageId=7"}},
                  {"type":"text","text":"无目标","attrs":{}}
                ]}
                """;

        service.refresh(7L, json);

        verify(linkMapper).deleteBySource(1L, 7L);
        ArgumentCaptor<DocPageLink> captor = ArgumentCaptor.forClass(DocPageLink.class);
        verify(linkMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(DocPageLink::getTargetPageId).containsExactly(42L, 43L);
        assertThat(captor.getAllValues()).extracting(DocPageLink::getLinkText).containsExactly("页面甲", "页面乙");
        assertThat(captor.getAllValues()).allSatisfy(link -> assertThat(link.getSourcePageId()).isEqualTo(7L));
    }

    @Test
    void refreshShouldUseLabelFallbackAndParseAbsoluteHref() {
        String json = """
                {"type":"doc","content":[
                  {"type":"text","attrs":{"href":"https://host/doc/page/88","label":"标签文本"}},
                  {"type":"text","attrs":{"href":"https://host/doc/page/88","text":"属性文本"}}
                ]}
                """;

        service.refresh(7L, json);

        ArgumentCaptor<DocPageLink> captor = ArgumentCaptor.forClass(DocPageLink.class);
        verify(linkMapper).insert(captor.capture());
        assertThat(captor.getValue().getTargetPageId()).isEqualTo(88L);
        assertThat(captor.getValue().getLinkText()).isEqualTo("标签文本");
    }

    @Test
    void refreshShouldIgnoreSelfReferenceAndBlankContent() {
        service.refresh(7L, "   ");
        verify(linkMapper).deleteBySource(1L, 7L);
        verify(linkMapper, never()).insert(org.mockito.ArgumentMatchers.any(DocPageLink.class));

        service.refresh(7L, """
                {"type":"text","text":"自身","attrs":{"href":"docpage:7"}}
                """);
        verify(linkMapper, times(2)).deleteBySource(1L, 7L);
        verify(linkMapper, never()).insert(org.mockito.ArgumentMatchers.any(DocPageLink.class));
    }

    @Test
    void refreshShouldKeepExistingLinksClearedWhenJsonInvalid() {
        service.refresh(7L, "{not-json");

        verify(linkMapper).deleteBySource(1L, 7L);
        verify(linkMapper, never()).insert(org.mockito.ArgumentMatchers.any(DocPageLink.class));
    }

    @Test
    void refreshShouldTruncateLongLinkText() {
        String text = "a".repeat(260);

        service.refresh(7L, "{\"type\":\"text\",\"text\":\"" + text + "\",\"marks\":["
                + "{\"type\":\"link\",\"attrs\":{\"href\":\"pageId=42\"}}]}");

        ArgumentCaptor<DocPageLink> captor = ArgumentCaptor.forClass(DocPageLink.class);
        verify(linkMapper).insert(captor.capture());
        assertThat(captor.getValue().getLinkText()).hasSize(200);
    }

    @Test
    void backlinksShouldMapQueryRows() {
        DocBacklinkRow row = new DocBacklinkRow();
        row.setPageId(11L);
        row.setTitle("引用方");
        row.setSpaceId(3L);
        row.setSpaceName("研发空间");
        row.setSlug("ref-page");
        row.setLinkText("跳转");
        row.setUpdateTime(LocalDateTime.of(2026, 1, 2, 3, 4));
        when(linkMapper.selectBacklinks(1L, 2L, 9L, 50)).thenReturn(List.of(row));

        List<DocBacklinkVO> result = service.backlinks(9L, 2L);

        assertThat(result).singleElement().satisfies(vo -> {
            assertThat(vo.getPageId()).isEqualTo(11L);
            assertThat(vo.getTitle()).isEqualTo("引用方");
            assertThat(vo.getSpaceId()).isEqualTo(3L);
            assertThat(vo.getSpaceName()).isEqualTo("研发空间");
            assertThat(vo.getSlug()).isEqualTo("ref-page");
            assertThat(vo.getLinkText()).isEqualTo("跳转");
            assertThat(vo.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        });
    }

    @Test
    void refreshShouldRequireTenantId() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.refresh(7L, "{\"type\":\"doc\"}"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(400);
        verify(linkMapper, never()).deleteBySource(anyLong(), anyLong());
    }
}
