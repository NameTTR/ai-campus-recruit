package com.aicampus.ai.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnowledgeSemanticChunkerTest {
    @Test void markdownHeadingsKeepPhysicalLineAnchorsAndExactOffsets() {
        String content = "# Redis 缓存\n\n## 直接结论\n缓存应设置过期时间。\n\n"
                + "### 通俗解释\n只把可重新获取的数据暂存。\n\n"
                + "#### 练习\n比较缓存命中与未命中的结果。\n\n"
                + "##### 验证\n检查失效后的行为。\n\n###### 来源\n已授权资料。";
        var parts = KnowledgeSemanticChunker.split(content);
        assertThat(parts).hasSize(5);
        assertThat(parts).allSatisfy(part -> {
            assertThat(part.text()).doesNotMatch("#+");
            assertThat(content.substring(part.startOffset(), part.endOffset())).isEqualTo(part.text());
        });
        assertThat(parts).extracting(KnowledgeSemanticChunker.Part::heading)
                .containsExactly("直接结论", "通俗解释", "练习", "验证", "来源");
        assertThat(parts.get(0).text()).startsWith("# Redis 缓存\n\n## 直接结论\n缓存应设置过期时间。");
    }

    @Test void hashInsideParagraphDoesNotBecomeANewHeading() {
        String content = "## 原始内容\n正文中的 ## 标记不是新标题。\n第二行仍是正文。\n\n## 下一节\n新内容。";
        var parts = KnowledgeSemanticChunker.split(content);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).text()).contains("正文中的 ## 标记不是新标题。");
        assertThat(parts.get(1).heading()).isEqualTo("下一节");
    }

    @Test void consecutiveTitleOnlyBlocksStayWithTheFirstBodyWithoutChangingSourceText() {
        String content = "# 文档\n\n## 专题\n\n### 背景\n背景解释和实际例子。\n\n## 练习\n检查关键结论。";
        var parts = KnowledgeSemanticChunker.split(content);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).heading()).isEqualTo("背景");
        assertThat(parts.get(0).text()).isEqualTo("# 文档\n\n## 专题\n\n### 背景\n背景解释和实际例子。");
        assertThat(parts).allSatisfy(part -> assertThat(content.substring(part.startOffset(), part.endOffset())).isEqualTo(part.text()));
    }
}
