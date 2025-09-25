package com.github.jwj.brilliantavern.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * TTS测试语音缓存服务
 * 使用Spring Cache来缓存测试语音，提高响应速度
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TTSCacheService {

    // 前端固定的测试文本
    public static final String TEST_TEXT = "这是一个语音测试，用来演示音色效果。";

    private static final String CACHE_NAME = "tts-test-audio";

    private final CacheManager cacheManager;

    /**
     * 检查文本是否为测试文本
     */
    public boolean isTestText(String text) {
        return TEST_TEXT.equals(text);
    }

    /**
     * 生成缓存键（供SpEL表达式使用）
     */
    public String generateCacheKey(String text, String voiceId) {
        return (voiceId != null ? voiceId : "default") + ":" + text.hashCode();
    }

    /**
     * 缓存测试语音
     * 只有测试文本才会被缓存
     */
    public byte[] getCachedTestAudio(String text, String voiceId) {
        if (!isTestText(text)) {
            return null;
        }
        Cache cache = getCache();
        String key = generateCacheKey(text, voiceId);
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        byte[] bytes = convertToBytes(value, key, cache);
        if (bytes != null) {
            log.debug("从缓存获取测试语音: voiceId={}, textLength={}", voiceId, text != null ? text.length() : 0);
            return bytes;
        }
        return null;
    }

    public void putCachedTestAudio(String text, String voiceId, byte[] audioData) {
        if (!isTestText(text) || audioData == null || audioData.length == 0) {
            return;
        }
        Cache cache = getCache();
        String key = generateCacheKey(text, voiceId);
        String encoded = Base64.getEncoder().encodeToString(audioData);
        cache.put(key, encoded);
        log.debug("写入测试语音缓存: voiceId={}, textLength={}, size={}B", voiceId,
                text != null ? text.length() : 0, audioData.length);
    }

    /**
     * 清除指定音色的所有测试语音缓存
     */
    public void clearVoiceTestCache(String voiceId) {
        Cache cache = getCache();
        cache.clear();
        log.info("清除音色测试语音缓存: voiceId={}", voiceId);
    }

    /**
     * 清除所有测试语音缓存
     */
    public void clearAllTestCache() {
        Cache cache = getCache();
        cache.clear();
        log.info("清除所有测试语音缓存");
    }

    private Cache getCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("未找到缓存: " + CACHE_NAME);
        }
        return cache;
    }

    private byte[] convertToBytes(Object value, String key, Cache cache) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof String str) {
            try {
                return Base64.getDecoder().decode(str);
            } catch (IllegalArgumentException ex) {
                log.warn("缓存中的测试音频Base64内容异常，将移除: key={}, error={}", key, ex.getMessage());
                cache.evict(key);
                return null;
            }
        }
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                if (!(element instanceof Number number)) {
                    log.warn("缓存中的测试音频列表包含非数字元素，将移除: key={}", key);
                    cache.evict(key);
                    return null;
                }
                bytes[i] = number.byteValue();
            }
            return bytes;
        }
        log.warn("缓存中存在无法识别的测试音频数据类型({}), 将移除: key={}", value.getClass().getName(), key);
        cache.evict(key);
        return null;
    }
}
