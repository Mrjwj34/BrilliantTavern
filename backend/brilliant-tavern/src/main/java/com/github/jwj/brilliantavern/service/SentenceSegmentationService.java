package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.dto.voice.SentenceSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将流式AI文本拆分为句子级段落。
 */
@Slf4j
@Service
public class SentenceSegmentationService {

    private static final String SENTENCE_DELIMITER_REGEX = "(?<=[。！？!?]|\\n)";

    public Flux<SentenceSegment> segment(Flux<String> chunkFlux, String messageId) {
        return Flux.defer(() -> {
            AtomicInteger order = new AtomicInteger(0);
            StringBuilder buffer = new StringBuilder();

            return chunkFlux
                    .flatMapIterable(chunk -> extractSegments(buffer, order, chunk, messageId))
                    .concatWith(Flux.defer(() -> emitRemainder(buffer, order, messageId)));
        });
    }

    private List<SentenceSegment> extractSegments(StringBuilder buffer,
                                                  AtomicInteger order,
                                                  String chunk,
                                                  String messageId) {
        buffer.append(chunk);
        String current = buffer.toString();
        String[] pieces = current.split(SENTENCE_DELIMITER_REGEX, -1);

        List<SentenceSegment> segments = new ArrayList<>();
        if (pieces.length <= 1) {
            return segments;
        }

        for (int i = 0; i < pieces.length - 1; i++) {
            String text = pieces[i].trim();
            if (!text.isEmpty()) {
                segments.add(buildSegment(order, messageId, text, false));
            }
        }

        buffer.setLength(0);
        String tail = pieces[pieces.length - 1];
        if (!tail.isEmpty()) {
            buffer.append(tail);
        }
        return segments;
    }

    private Flux<SentenceSegment> emitRemainder(StringBuilder buffer,
                                                AtomicInteger order,
                                                String messageId) {
        if (buffer.length() == 0) {
            return Flux.empty();
        }
        String text = buffer.toString().trim();
        if (text.isEmpty()) {
            return Flux.empty();
        }
        return Flux.just(buildSegment(order, messageId, text, true));
    }

    private SentenceSegment buildSegment(AtomicInteger order,
                                         String messageId,
                                         String text,
                                         boolean isFinal) {
        int index = order.getAndIncrement();
        log.debug("生成句子片段: messageId={}, order={}, isFinal={}, text='{}'", messageId, index, isFinal,
                text.length() > 50 ? text.substring(0, 50) + "..." : text);
        return SentenceSegment.builder()
                .messageId(messageId)
                .order(index)
                .text(text)
                .isFinal(isFinal)
                .build();
    }
}
