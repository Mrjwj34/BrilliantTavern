package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.entity.CharacterMemory;
import com.github.jwj.brilliantavern.repository.CharacterMemoryRepository;
import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.EmbedContentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 角色记忆服务
 * 负责角色全局记忆的存储、检索和向量化处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterMemoryService {

    private final CharacterMemoryRepository characterMemoryRepository;
    private final Client genAIClient;

    @Value("${vertex.ai.embedding.model:embedding-001}")
    private String embeddingModel;

    @Value("${character.memory.similarity.threshold:0.7}")
    private double similarityThreshold;

    @Value("${character.memory.max.results:5}")
    private int maxResults;

    // 限制向量维度以支持HNSW索引 (PostgreSQL vector HNSW索引最大支持2000维)
    private static final int MAX_EMBEDDING_DIMENSIONS = 1536;

    /**
     * 存储角色记忆
     */
    @Transactional
    public void storeMemory(UUID userId, UUID characterCardId, String memoryContent) {
        if (!StringUtils.hasText(memoryContent)) {
            throw new IllegalArgumentException("记忆内容不能为空");
        }

        try {
            log.debug("开始存储角色记忆: userId={}, characterCardId={}, content={}", 
                userId, characterCardId, memoryContent);

            // 生成文本嵌入向量
            float[] embedding = generateEmbedding(memoryContent);
            
            // 创建记忆实体
            CharacterMemory memory = CharacterMemory.builder()
                .userId(userId)
                .characterCardId(characterCardId)
                .memoryContent(memoryContent.trim())
                .embedding(embedding)
                .build();

            // 保存到数据库
            CharacterMemory savedMemory = characterMemoryRepository.save(memory);
            
            log.info("角色记忆存储成功: memoryId={}, userId={}, characterCardId={}, contentLength={}", 
                savedMemory.getId(), userId, characterCardId, memoryContent.length());

        } catch (Exception e) {
            log.error("存储角色记忆失败: userId={}, characterCardId={}", userId, characterCardId, e);
            throw new RuntimeException("存储记忆失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检索相似记忆
     */
    public List<CharacterMemory> retrieveSimilarMemories(UUID userId, UUID characterCardId, String queryText) {
        return retrieveSimilarMemories(userId, characterCardId, queryText, maxResults);
    }

    /**
     * 检索相似记忆（指定数量）
     */
    public List<CharacterMemory> retrieveSimilarMemories(UUID userId, UUID characterCardId, String queryText, int limit) {
        if (!StringUtils.hasText(queryText)) {
            return List.of();
        }

        try {
            log.debug("开始检索相似记忆: userId={}, characterCardId={}, queryText={}", 
                userId, characterCardId, queryText);

            // 生成查询文本的嵌入向量
            float[] queryEmbedding = generateEmbedding(queryText);
            
            // 将float数组转换为向量字符串格式
            StringBuilder embeddingBuilder = new StringBuilder("[");
            for (int i = 0; i < queryEmbedding.length; i++) {
                if (i > 0) embeddingBuilder.append(",");
                embeddingBuilder.append(queryEmbedding[i]);
            }
            embeddingBuilder.append("]");
            String embeddingStr = embeddingBuilder.toString();

            // 执行向量相似度搜索
            List<Object[]> results = characterMemoryRepository.findSimilarMemories(
                userId, characterCardId, embeddingStr, similarityThreshold, limit);

            List<CharacterMemory> memories = results.stream()
                .map(this::mapToCharacterMemory)
                .toList();

            log.debug("检索到 {} 条相似记忆", memories.size());
            return memories;

        } catch (Exception e) {
            log.error("检索相似记忆失败: userId={}, characterCardId={}", userId, characterCardId, e);
            return List.of(); // 返回空列表而不是抛出异常，避免影响主流程
        }
    }

    /**
     * 获取用户和角色卡的最新记忆
     */
    public List<CharacterMemory> getRecentMemories(UUID userId, UUID characterCardId, int limit) {
        return characterMemoryRepository.findByUserIdAndCharacterCardIdOrderByCreatedAtDesc(
            userId, characterCardId, PageRequest.of(0, limit));
    }

    /**
     * 获取用户和角色卡的所有记忆
     */
    public List<CharacterMemory> getAllMemories(UUID userId, UUID characterCardId) {
        return characterMemoryRepository.findByUserIdAndCharacterCardIdOrderByCreatedAtDesc(userId, characterCardId);
    }

    /**
     * 删除用户和角色卡的所有记忆
     */
    @Transactional
    public void deleteAllMemories(UUID userId, UUID characterCardId) {
        try {
            long count = characterMemoryRepository.countByUserIdAndCharacterCardId(userId, characterCardId);
            characterMemoryRepository.deleteByUserIdAndCharacterCardId(userId, characterCardId);
            log.info("删除角色记忆成功: userId={}, characterCardId={}, count={}", 
                userId, characterCardId, count);
        } catch (Exception e) {
            log.error("删除角色记忆失败: userId={}, characterCardId={}", userId, characterCardId, e);
            throw new RuntimeException("删除记忆失败: " + e.getMessage(), e);
        }
    }

    /**
     * 统计记忆数量
     */
    public long getMemoryCount(UUID userId, UUID characterCardId) {
        return characterMemoryRepository.countByUserIdAndCharacterCardId(userId, characterCardId);
    }

    /**
     * 生成文本嵌入向量
     */
    private float[] generateEmbedding(String text) {
        try {
            log.debug("生成文本嵌入: model={}, textLength={}", embeddingModel, text.length());

            EmbedContentConfig config;
            try {
                config = EmbedContentConfig.builder()
                    .outputDimensionality(MAX_EMBEDDING_DIMENSIONS)
                    .build();
            } catch (Exception e) {
                // 如果不支持outputDimensionality，则使用null配置，后续截断处理
                log.debug("嵌入配置不支持outputDimensionality参数，将在后续截断向量: {}", e.getMessage());
                config = null;
            }
            
            // 调用最新的Gemini嵌入API
            EmbedContentResponse response = genAIClient.models.embedContent(embeddingModel, text, config);
            
            if (response != null && response.embeddings().isPresent() && !response.embeddings().get().isEmpty()) {
                // 获取第一个嵌入对象
                ContentEmbedding contentEmbedding = response.embeddings().get().get(0);
                Optional<List<Float>> valuesOpt = contentEmbedding.values();
                
                if (valuesOpt.isPresent() && !valuesOpt.get().isEmpty()) {
                    List<Float> values = valuesOpt.get();
                    
                    // 限制向量维度以支持HNSW索引
                    int actualDimensions = Math.min(values.size(), MAX_EMBEDDING_DIMENSIONS);
                    float[] embedding = new float[actualDimensions];
                    
                    for (int i = 0; i < actualDimensions; i++) {
                        embedding[i] = values.get(i);
                    }
                    
                    if (values.size() > MAX_EMBEDDING_DIMENSIONS) {
                        log.debug("嵌入向量从{}维截断到{}维以支持HNSW索引", values.size(), actualDimensions);
                    }
                    
                    log.debug("嵌入向量生成成功: dimension={}", embedding.length);
                    return embedding;
                } else {
                    throw new RuntimeException("嵌入向量值为空");
                }
            } else {
                throw new RuntimeException("嵌入响应为空或格式错误");
            }

        } catch (Exception e) {
            log.error("生成文本嵌入失败: textLength={}", text.length(), e);
            throw new RuntimeException("生成嵌入向量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将数据库查询结果映射为CharacterMemory实体
     */
    private CharacterMemory mapToCharacterMemory(Object[] result) {
        try {
            CharacterMemory memory = new CharacterMemory();
            memory.setId(((Number) result[0]).longValue());
            memory.setUserId((UUID) result[1]);
            memory.setCharacterCardId((UUID) result[2]);
            memory.setMemoryContent((String) result[3]);
            // 注意：embedding字段从数据库查询中不需要返回，因为它很大且在检索时不需要
            memory.setCreatedAt(((java.sql.Timestamp) result[5]).toLocalDateTime().atOffset(java.time.ZoneOffset.UTC));
            memory.setUpdatedAt(((java.sql.Timestamp) result[6]).toLocalDateTime().atOffset(java.time.ZoneOffset.UTC));
            return memory;
        } catch (Exception e) {
            log.error("映射CharacterMemory失败", e);
            throw new RuntimeException("数据映射失败", e);
        }
    }
}