package com.github.jwj.brilliantavern.service.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 记录一次语音轮次中的关键耗时节点，生成可读的耗时报告。
 */
public final class ConversationMetrics {

    private final String sessionId;
    private final String messageId;
    private final long startNano;
    private final Map<String, Long> marks = new ConcurrentHashMap<>();
    private final AtomicBoolean summarized = new AtomicBoolean(false);

    private ConversationMetrics(String sessionId, String messageId) {
        this.sessionId = sessionId;
        this.messageId = messageId;
        this.startNano = System.nanoTime();
    }

    public static ConversationMetrics start(String sessionId, String messageId) {
        return new ConversationMetrics(sessionId, messageId);
    }

    public void mark(String label) {
        marks.put(label, System.nanoTime());
    }

    public void markIfAbsent(String label) {
        marks.putIfAbsent(label, System.nanoTime());
    }

    public String buildReport() {
        if (!summarized.compareAndSet(false, true)) {
            return "";
        }
        long completed = marks.getOrDefault("flow_completed", System.nanoTime());
        StringBuilder sb = new StringBuilder(512);
        sb.append("流式语音对话性能报告 session=")
          .append(sessionId)
          .append(" message=")
          .append(messageId)
          .append('\n');

        sb.append("  总耗时: ")
          .append(formatDurationMillis(startNano, completed))
          .append(" ms\n");

        // AI模型处理指标
        long llmStart = marks.getOrDefault("llm_start", 0L);
        long llmFirst = marks.getOrDefault("llm_first_token", 0L);
        long llmCompleted = marks.getOrDefault("llm_completed", 0L);
        if (llmStart > 0L) {
            sb.append("  AI模型处理:\n");
            sb.append("    发起: ")
              .append(relativeMillis(llmStart))
              .append(" ms\n");
            if (llmFirst > 0L) {
                sb.append("    首Token: ")
                  .append(relativeMillis(llmFirst))
                  .append(" ms (延迟 ")
                  .append(formatDurationMillis(llmStart, llmFirst))
                  .append(" ms)\n");
            }
            if (llmCompleted > 0L) {
                sb.append("    流式完成: ")
                  .append(relativeMillis(llmCompleted))
                  .append(" ms (生成耗时 ")
                  .append(formatDurationMillis(llmStart, llmCompleted))
                  .append(" ms)\n");
            }
        }

        // 标签解析和异步处理指标
        appendAsyncEventMetrics(sb);

        // 历史记录保存指标
        long historyStart = marks.getOrDefault("history_start", 0L);
        long historyDone = marks.getOrDefault("history_done", 0L);
        if (historyStart > 0L) {
            sb.append("  历史记录保存: ")
              .append(relativeMillis(historyDone > 0L ? historyDone : historyStart))
              .append(" ms");
            if (historyDone > 0L) {
                sb.append(" (耗时 ")
                  .append(formatDurationMillis(historyStart, historyDone))
                  .append(" ms)");
            }
            sb.append('\n');
        }

        // 计算并发度指标
        calculateConcurrencyMetrics(sb);

        return sb.toString();
    }

    /**
     * 添加异步事件处理指标
     */
    private void appendAsyncEventMetrics(StringBuilder sb) {
        // TTS异步处理
        long ttsStart = marks.getOrDefault("tts_start", 0L);
        long ttsFirst = marks.getOrDefault("tts_first_chunk", 0L);
        long ttsDone = marks.getOrDefault("tts_completed", 0L);
        if (ttsStart > 0L) {
            sb.append("  TTS异步处理:\n")
              .append("    启动: ")
              .append(relativeMillis(ttsStart))
              .append(" ms\n");
            if (ttsFirst > 0L) {
                sb.append("    首音频块: ")
                  .append(relativeMillis(ttsFirst))
                  .append(" ms (延迟 ")
                  .append(formatDurationMillis(ttsStart, ttsFirst))
                  .append(" ms)\n");
            }
            if (ttsDone > 0L) {
                sb.append("    音频完成: ")
                  .append(relativeMillis(ttsDone))
                  .append(" ms (耗时 ")
                  .append(formatDurationMillis(ttsStart, ttsDone))
                  .append(" ms)\n");
            }
        }

        // 字幕推送处理
        long subtitleStart = marks.getOrDefault("subtitle_first_chunk", 0L);
        long subtitleDone = marks.getOrDefault("subtitle_completed", 0L);
        if (subtitleStart > 0L) {
            sb.append("  字幕流式推送:\n")
              .append("    首次推送: ")
              .append(relativeMillis(subtitleStart))
              .append(" ms\n");
            if (subtitleDone > 0L) {
                sb.append("    推送完成: ")
                  .append(relativeMillis(subtitleDone))
                  .append(" ms\n");
            }
        }
    }

    /**
     * 计算并发处理效率指标
     */
    private void calculateConcurrencyMetrics(StringBuilder sb) {
        Long ttsStart = marks.get("tts_start");
        Long subtitleStart = marks.get("subtitle_first_chunk");
        Long ttsEnd = marks.get("tts_completed");
        Long subtitleEnd = marks.get("subtitle_completed");

        if (ttsStart != null && subtitleStart != null && ttsEnd != null && subtitleEnd != null) {
            long ttsOverlap = Math.max(0, Math.min(ttsEnd, subtitleEnd) - Math.max(ttsStart, subtitleStart));
            long totalAsyncTime = Math.max(ttsEnd - ttsStart, subtitleEnd - subtitleStart);

            if (totalAsyncTime > 0) {
                double concurrencyRatio = (double) ttsOverlap / totalAsyncTime * 100;
                sb.append("  并发处理效率: ")
                  .append(String.format("%.1f%%", concurrencyRatio))
                  .append("\n");
            }
        }

        // 计算真流式处理的效果
        Long firstToken = marks.get("llm_first_token");
        if (firstToken != null) {
            long timeToFirstToken = Duration.ofNanos(firstToken - startNano).toMillis();
            sb.append("  首Token延迟: ")
              .append(timeToFirstToken)
              .append(" ms\n");
        }
    }

  private String relativeMillis(long markNano) {
    return formatMillis(Duration.ofNanos(Math.max(0L, markNano - startNano)));
    }

  private String formatDurationMillis(long start, long end) {
    return formatMillis(Duration.ofNanos(Math.max(0L, end - start)));
  }

  private String formatMillis(Duration duration) {
    double millis = duration.toNanos() / 1_000_000d;
    return String.format("%.2f", millis);
    }
}
