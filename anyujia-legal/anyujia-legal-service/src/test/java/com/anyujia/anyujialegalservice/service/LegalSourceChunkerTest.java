package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LegalSourceChunkerTest {
    private final LegalSourceChunker chunker = new LegalSourceChunker();

    @Test
    void splitsConsecutiveArticlesAndInheritsChapter() {
        var chunks = chunker.split("第一章 总则\n\n"
                + "第一条 第一条内容。\n\n"
                + "第二条 第二条内容。\n\n"
                + "第三条 第三条内容。");

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(LegalSourceChunker.LawChunk::articleNumber)
                .containsExactly(1, 2, 3);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.chapter()).isEqualTo("第一章 总则");
            assertThat(chunk.chunkIndex()).isZero();
        });
    }

    @Test
    void doesNotTreatInlineArticleReferenceAsHeading() {
        var chunks = chunker.split("第一条 根据本法第三条规定执行。\n\n第二条 第二条正文。");

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(LegalSourceChunker.LawChunk::articleNumber)
                .containsExactly(1, 2);
        assertThat(chunks.get(0).text()).contains("根据本法第三条规定执行");
    }

    @Test
    void supportsArabicArticleNumber() {
        var chunks = chunker.split("第123条 测试内容");

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.articleNumber()).isEqualTo(123);
            assertThat(chunk.articleLabel()).isEqualTo("第123条");
        });
    }

    @Test
    void inheritsPartChapterAndSectionAndEnhancesEmbeddingText() {
        String text = "中华人民共和国民法典\n\n第三编 合同\n\n第十四章 租赁合同\n\n"
                + "第一节 一般规定\n\n第七百二十一条\n承租人应当按照约定的期限支付租金。\n\n"
                + "第七百二十二条\n承租人逾期不支付的，出租人可以解除合同。";

        var chunks = chunker.split(text);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.part()).isEqualTo("第三编 合同");
            assertThat(chunk.chapter()).isEqualTo("第十四章 租赁合同");
            assertThat(chunk.section()).isEqualTo("第一节 一般规定");
        });
        assertThat(chunks.get(0).text()).isEqualTo("第三编 合同\n第十四章 租赁合同\n"
                + "第一节 一般规定\n第七百二十一条\n承租人应当按照约定的期限支付租金。");
        assertThat(count(chunks.get(0).text(), "第七百二十一条")).isOne();
    }

    @Test
    void splitsLongArticleAtSentenceBoundariesWithSemanticOverlap() {
        StringBuilder source = new StringBuilder("第十条\n");
        for (int index = 0; index < 180; index++) {
            source.append("这是第").append(index).append("个用于验证语义边界的完整句子；");
        }

        var chunks = chunker.split(source.toString());

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.articleNumber()).isEqualTo(10);
            assertThat(chunk.text().length()).isLessThanOrEqualTo(1800);
            assertThat(chunk.text()).endsWith("；");
        });
        assertThat(chunks).extracting(LegalSourceChunker.LawChunk::chunkIndex)
                .containsExactlyElementsOf(indexes(chunks.size()));
        String first = chunks.get(0).text();
        int priorBoundary = first.lastIndexOf('；', first.length() - 2);
        String finalSentence = first.substring(priorBoundary + 1);
        assertThat(chunks.get(1).text()).contains(finalSentence);
    }

    @Test
    void fallsBackToSemanticTextChunksWithoutArticleHeadings() {
        var chunks = chunker.split("最高人民法院关于某事项的说明   \r\n\r\n\r\n"
                + "这是第一段。  \r\n\r\n这是第二段。");

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.articleNumber()).isNull();
            assertThat(chunk.articleLabel()).isNull();
            assertThat(chunk.chunkIndex()).isZero();
            assertThat(chunk.text()).isEqualTo("最高人民法院关于某事项的说明\n\n这是第一段。\n\n这是第二段。");
        });
    }

    @Test
    void parsesRequiredChineseNumbers() {
        var chunks = chunker.split("第十条 内容。\n第十一条 内容。\n第二十条 内容。\n"
                + "第一百零一条 内容。\n第一百二十三条 内容。\n"
                + "第七百二十二条 内容。\n第一千零五条 内容。");

        assertThat(chunks).extracting(LegalSourceChunker.LawChunk::articleNumber)
                .containsExactly(10, 11, 20, 101, 123, 722, 1005);
    }

    @Test
    void supportsFullWidthSpaceAndArticleLabelOnItsOwnLine() {
        var chunks = chunker.split("第一条　为了规范测试。\n第二条\u2002另起一行的正文。\n"
                + "第三条\n单独标题行正文。");

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).text()).isEqualTo("第一条\n为了规范测试。");
        assertThat(chunks.get(1).text()).isEqualTo("第二条\n另起一行的正文。");
        assertThat(chunks.get(2).text()).isEqualTo("第三条\n单独标题行正文。");
    }

    @Test
    void hardSplitsMalformedLongTextAndHandlesNullAndBlankInput() {
        var chunks = chunker.split("甲".repeat(4000));

        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(1800));
        assertThat(chunker.split(null)).isEmpty();
        assertThat(chunker.split("  \r\n  ")).isEmpty();
    }

    private List<Integer> indexes(int size) {
        return java.util.stream.IntStream.range(0, size).boxed().toList();
    }

    private int count(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}
