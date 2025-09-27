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
        StringBuilder sb = new StringBuilder(256);
        sb.append("语音轮次耗时报告 session=")
          .append(sessionId)
          .append(" message=")
          .append(messageId)
          .append('\n');

        sb.append("  总耗时: ")
          .append(formatDurationMillis(startNano, completed))
          .append(" ms\n");

        long llmStart = marks.getOrDefault("llm_start", 0L);
        long llmFirst = marks.getOrDefault("llm_first_token", 0L);
        long llmCompleted = marks.getOrDefault("llm_completed", 0L);
        if (llmStart > 0L) {
            sb.append("  LLM:\n");
            sb.append("    发起: ")
              .append(relativeMillis(llmStart))
              .append(" ms\n");
            if (llmFirst > 0L) {
                sb.append("    首字: ")
                  .append(relativeMillis(llmFirst))
                  .append(" ms (延迟 ")
                  .append(formatDurationMillis(llmStart, llmFirst))
                  .append(" ms)\n");
            }
            if (llmCompleted > 0L) {
                sb.append("    完成: ")
                  .append(relativeMillis(llmCompleted))
                  .append(" ms (生成耗时 ")
                  .append(formatDurationMillis(llmStart, llmCompleted))
                  .append(" ms)\n");
            }
        }

        long historyStart = marks.getOrDefault("history_start", 0L);
        long historyDone = marks.getOrDefault("history_done", 0L);
        if (historyStart > 0L) {
            sb.append("  历史落库: ")
              .append(relativeMillis(historyDone > 0L ? historyDone : historyStart))
              .append(" ms");
            if (historyDone > 0L) {
                sb.append(" (耗时 ")
                  .append(formatDurationMillis(historyStart, historyDone))
                  .append(" ms)");
            }
            sb.append('\n');
        }

        long ttsStart = marks.getOrDefault("tts_start", 0L);
        long ttsFirst = marks.getOrDefault("tts_first_chunk", 0L);
        long ttsDone = marks.getOrDefault("tts_completed", 0L);
        if (ttsStart > 0L) {
            sb.append("  TTS:\n")
              .append("    发起: ")
              .append(relativeMillis(ttsStart))
              .append(" ms\n");
            if (ttsFirst > 0L) {
                sb.append("    首音: ")
                  .append(relativeMillis(ttsFirst))
                  .append(" ms (延迟 ")
                  .append(formatDurationMillis(ttsStart, ttsFirst))
                  .append(" ms)\n");
            }
            if (ttsDone > 0L) {
        sb.append("    完成: ")
          .append(relativeMillis(ttsDone))
          .append(" ms (耗时 ")
          .append(formatDurationMillis(ttsStart, ttsDone))
                  .append(" ms)\n");
            }
        }

        return sb.toString();
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
