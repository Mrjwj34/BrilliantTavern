package com.github.jwj.brilliantavern.service.util;

import org.springframework.util.StringUtils;

/**
 * 处理模型回复中包含的 ASR 转写标记，支持批量与流式两种模式。
 */
public final class AsrMarkupProcessor {

    public static final String OPEN_TAG = "[ASR_TRANSCRIPTION]";
    public static final String CLOSE_TAG = "[/ASR_TRANSCRIPTION]";

    private static final int OPEN_TAG_LENGTH = OPEN_TAG.length();
    private static final int CLOSE_TAG_LENGTH = CLOSE_TAG.length();

    private AsrMarkupProcessor() {
    }

    /**
     * 处理完整文本，提取去标记后的模型回复和用户转写。
     */
    public static Result process(String input) {
        if (!StringUtils.hasText(input)) {
            return new Result("", "");
        }
        StreamingProcessor processor = new StreamingProcessor();
        processor.append(input);
        processor.drain();
        return new Result(
                normalizeWhitespace(processor.getSanitizedText()),
                normalizeWhitespace(processor.getTranscription())
        );
    }

    /**
     * 压缩多余空白字符并去除首尾空格。
     */
    public static String normalizeWhitespace(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        return input.replaceAll("\\s+", " ").trim();
    }

    /**
     * 流式处理器，用于在流式响应中动态去除 ASR 标记并提取转写。
     */
    public static final class StreamingProcessor {

        private final StringBuilder buffer = new StringBuilder();
        private final StringBuilder sanitized = new StringBuilder();
        private final StringBuilder transcription = new StringBuilder();
        private boolean insideAsr = false;
        private int emittedSanitizedLength = 0;

        /**
         * 追加新的流式文本片段，返回新增的、已去除 ASR 标记的文本。
         */
        public synchronized String append(String chunk) {
            if (!StringUtils.hasLength(chunk)) {
                return "";
            }
            buffer.append(chunk);
            processBuffer(false);
            return extractSanitizedDelta();
        }

        /**
         * 冲刷剩余内容，通常在流式结束时调用。
         */
        public synchronized String drain() {
            processBuffer(true);
            return extractSanitizedDelta();
        }

        /**
         * 获取截至当前的完整模型回复文本（未裁剪空白）。
         */
        public synchronized String getSanitizedText() {
            return sanitized.toString();
        }

        /**
         * 获取截至当前的完整用户转写文本（未裁剪空白）。
         */
        public synchronized String getTranscription() {
            return transcription.toString();
        }

        private void processBuffer(boolean flushRemaining) {
            int searchFrom = 0;
            while (true) {
                if (insideAsr) {
                    int closeIdx = buffer.indexOf(CLOSE_TAG, searchFrom);
                    if (closeIdx < 0) {
                        if (flushRemaining) {
                            transcription.append(buffer.substring(searchFrom));
                            buffer.setLength(0);
                        } else {
                            int retainFrom = Math.max(buffer.length() - CLOSE_TAG_LENGTH + 1, searchFrom);
                            if (retainFrom > searchFrom) {
                                transcription.append(buffer.substring(searchFrom, retainFrom));
                                buffer.delete(0, retainFrom);
                            }
                        }
                        break;
                    }
                    transcription.append(buffer.substring(searchFrom, closeIdx));
                    buffer.delete(0, closeIdx + CLOSE_TAG_LENGTH);
                    insideAsr = false;
                    searchFrom = 0;
                } else {
                    int openIdx = buffer.indexOf(OPEN_TAG, searchFrom);
                    if (openIdx < 0) {
                        if (flushRemaining) {
                            sanitized.append(buffer.substring(searchFrom));
                            buffer.setLength(0);
                        } else {
                            int retainFrom = Math.max(buffer.length() - OPEN_TAG_LENGTH + 1, searchFrom);
                            if (retainFrom > searchFrom) {
                                sanitized.append(buffer.substring(searchFrom, retainFrom));
                                buffer.delete(0, retainFrom);
                            }
                        }
                        break;
                    }
                    sanitized.append(buffer.substring(searchFrom, openIdx));
                    buffer.delete(0, openIdx + OPEN_TAG_LENGTH);
                    insideAsr = true;
                    searchFrom = 0;
                }
            }
        }

        private String extractSanitizedDelta() {
            if (sanitized.length() == emittedSanitizedLength) {
                return "";
            }
            String delta = sanitized.substring(emittedSanitizedLength);
            emittedSanitizedLength = sanitized.length();
            return delta;
        }
    }

    /**
     * 去标记处理结果。
     */
    public record Result(String sanitizedText, String transcription) {}
}
