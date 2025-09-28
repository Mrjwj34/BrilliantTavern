package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.google.genai.Client;
import com.google.genai.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 图像生成服务
 * 使用Google Gen AI的Imagen模型生成图片
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private final Client genAIClient;
    
    @Value("${app.uploads.images.dir:uploads/images}")
    private String imagesDir;
    
    @Value("${app.uploads.images.base-url:/api/images}")
    private String imagesBaseUrl;

    /**
     * 异步生成图片
     */
    public Mono<ImageGenerationResult> generateImageAsync(UUID userId, 
                                                         CharacterCard characterCard,
                                                         boolean isSelf, 
                                                         String description,
                                                         String sessionId,
                                                         String messageId) {
        return Mono.fromCallable(() -> generateImageSync(userId, characterCard, isSelf, description, sessionId, messageId))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSubscribe(s -> log.info("开始异步图像生成: userId={}, characterCard={}, isSelf={}, description={}", 
                        userId, characterCard.getName(), isSelf, description))
                .doOnSuccess(result -> log.info("图像生成完成: userId={}, imageUri={}", userId, result.imageUri()))
                .doOnError(error -> log.error("图像生成失败: userId={}, characterCard={}", userId, characterCard.getName(), error));
    }

    /**
     * 同步生成图片
     */
    private ImageGenerationResult generateImageSync(UUID userId, 
                                                   CharacterCard characterCard,
                                                   boolean isSelf, 
                                                   String description,
                                                   String sessionId,
                                                   String messageId) {
        try {
            // 构建提示词
            String prompt = buildPrompt(characterCard, isSelf, description);
            log.debug("构建图像生成提示词: {}", prompt);
            
            // 创建内容
            Content content;
            if (isSelf && StringUtils.hasText(characterCard.getAvatarUrl())) {
                // 自画像模式且有头像 - 使用多模态输入
                try {
                    content = createMultimodalContent(prompt, characterCard.getAvatarUrl());
                    log.debug("使用参考图片生成图像: {}", characterCard.getAvatarUrl());
                } catch (Exception e) {
                    log.warn("加载参考图片失败，回退到纯文本模式: avatarUrl={}, error={}", 
                            characterCard.getAvatarUrl(), e.getMessage());
                    content = Content.fromParts(Part.fromText(prompt));
                }
            } else {
                // 纯文本模式
                content = Content.fromParts(Part.fromText(prompt));
            }
            
            // 调用 generateContent 方法生成图像
            GenerateContentResponse response = genAIClient.models.generateContent(
                    "gemini-2.5-flash-image-preview", 
                    content,
                    GenerateContentConfig.builder().responseModalities("TEXT", "IMAGE").build()
            );
            
            // 处理生成结果 - 查找图像数据
            log.debug("检查响应: candidates存在={}", response.candidates().isPresent());
            if (response.candidates().isPresent()) {
                var candidates = response.candidates().get();
                log.debug("候选数量: {}", candidates.size());
                
                if (!candidates.isEmpty()) {
                    var candidate = candidates.get(0);
                    log.debug("候选内容存在={}", candidate.content().isPresent());
                    
                    if (candidate.content().isPresent()) {
                        var responseContent = candidate.content().get();
                        log.debug("内容部分存在={}", responseContent.parts().isPresent());
                        
                        if (responseContent.parts().isPresent()) {
                            var parts = responseContent.parts().get();
                            log.debug("部分数量: {}", parts.size());
                            
                            for (int i = 0; i < parts.size(); i++) {
                                var part = parts.get(i);
                                log.debug("部分{}: text={}, inlineData={}", 
                                        i, part.text().isPresent(), part.inlineData().isPresent());
                                
                                if (part.inlineData().isPresent()) {
                                    // 找到图像数据
                                    var inlineData = part.inlineData().get();
                                    log.debug("内联数据: mimeType={}", inlineData.mimeType().orElse("unknown"));
                                    
                                    // 尝试不同的方式获取数据
                                    byte[] imageData = null;
                                    
                                    try {
                                        // 方法1: 直接获取data并检查类型
                                        if (inlineData.data().isPresent()) {
                                            // inlineData.data() 返回 byte[]
                                            imageData = inlineData.data().get();
                                            log.info("获取图像数据: size={}bytes", imageData.length);
                                        }
                                        
                                        // 方法2: 如果上面失败，尝试其他可能的方法
                                        if (imageData == null) {
                                            log.debug("尝试其他方法获取图像数据");
                                            // 这里可以添加其他获取方法
                                        }
                                        
                                    } catch (Exception dataEx) {
                                        log.error("提取图像数据失败", dataEx);
                                        continue;
                                    }
                                    
                                    if (imageData != null && imageData.length > 0) {
                                        // 保存图片到本地
                                        String imageUri = saveImageDataToLocal(imageData, userId, sessionId, messageId);
                                        
                                        return new ImageGenerationResult(
                                                imageUri,
                                                description,
                                                isSelf,
                                                prompt
                                        );
                                    } else {
                                        log.warn("获取到的图像数据为空或长度为0");
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                log.warn("响应中没有候选结果");
            }
            
            throw new RuntimeException("图像生成失败: 未获得有效的生成结果");
            
        } catch (Exception e) {
            log.error("图像生成过程中发生错误: userId={}, characterCard={}", userId, characterCard.getName(), e);
            throw new RuntimeException("图像生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建图像生成提示词
     */
    private String buildPrompt(CharacterCard characterCard, boolean isSelf, String description) {
        if (isSelf) {
            // 自画像模式 - 根据是否有参考图像调整提示词
            StringBuilder promptBuilder = new StringBuilder();
            
            // 强制生成图片的指令前缀
            promptBuilder.append("IMPORTANT: Generate a visual image, not text. ");
            
            if (StringUtils.hasText(characterCard.getAvatarUrl())) {
                // 有参考图像 - 基于参考图像生成自画像
                promptBuilder.append("Based on the reference image provided, create a detailed portrait of this character showing: ")
                           .append(description).append(". ");
                promptBuilder.append("Keep the character's visual identity consistent with the reference image, ");
                promptBuilder.append("but show them with the requested expression/pose. ");
                promptBuilder.append("Style: High quality anime/manga art with detailed facial features, expressive eyes, and dynamic composition. ");
                promptBuilder.append("Make it visually striking and emotionally engaging.");
            } else {
                // 无参考图像 - 基于简化的角色信息生成自画像
                promptBuilder.append("Create a detailed character portrait showing: ").append(description).append(". ");
                
                // 只添加角色名称（如果有）
                if (StringUtils.hasText(characterCard.getName())) {
                    promptBuilder.append("Character: ").append(characterCard.getName()).append(". ");
                }
                
                promptBuilder.append("Style: High quality anime/manga art with detailed facial features, expressive eyes, and dynamic composition. ");
                promptBuilder.append("Focus on emotions and personality. Make it visually appealing and full of character.");
            }
            
            return promptBuilder.toString();
        } else {
            // 普通图像生成模式 - 完全由用户描述决定，AI自由发挥
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("IMPORTANT: Generate a visual image, not text. ");
            promptBuilder.append("Create a detailed image showing: ").append(description).append(". ");
            promptBuilder.append("Be creative and add rich visual details to make it engaging and beautiful. ");
            promptBuilder.append("Style: High quality, detailed artwork with vibrant colors and clear composition. ");
            promptBuilder.append("Focus on visual storytelling and aesthetic appeal.");
            return promptBuilder.toString();
        }
    }

    /**
     * 创建多模态内容（文本 + 参考图像）
     */
    private Content createMultimodalContent(String prompt, String avatarUrl) throws IOException {
        try {
            byte[] imageData;
            String mimeType;
            
            if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
                // 网络图片 - 先下载到后端，然后作为字节数据发送
                log.debug("下载网络图片: {}", avatarUrl);
                imageData = downloadImageFromUrl(avatarUrl);
                mimeType = getMimeTypeFromUrl(avatarUrl);
                log.debug("网络图片下载完成: size={}bytes, mimeType={}", imageData.length, mimeType);
            } else {
                // 本地文件 - 读取为字节数组
                Path avatarPath = Paths.get(avatarUrl);
                if (!Files.exists(avatarPath)) {
                    throw new IOException("头像文件不存在: " + avatarUrl);
                }
                
                imageData = Files.readAllBytes(avatarPath);
                mimeType = getMimeTypeFromPath(avatarPath);
                log.debug("本地图片加载完成: size={}bytes, mimeType={}", imageData.length, mimeType);
            }
            
            // 统一使用字节数据方式，避免Vertex AI无法访问网络URL的问题
            return Content.fromParts(
                    Part.fromText(prompt),
                    Part.fromBytes(imageData, mimeType)
            );
            
        } catch (Exception e) {
            log.error("创建多模态内容失败: avatarUrl={}", avatarUrl, e);
            throw new IOException("多模态内容创建失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从网络URL下载图片数据
     */
    private byte[] downloadImageFromUrl(String imageUrl) throws IOException {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // 设置合理的超时时间
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(30000);    // 30秒读取超时
            
            // 设置用户代理，避免某些网站拒绝请求
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            // 检查响应码
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP错误: " + responseCode + " for URL: " + imageUrl);
            }
            
            // 检查内容类型
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) {
                log.warn("URL返回的不是图片类型: contentType={}, url={}", contentType, imageUrl);
            }
            
            // 读取数据
            try (InputStream is = connection.getInputStream()) {
                byte[] data = is.readAllBytes();
                log.debug("网络图片下载成功: url={}, size={}bytes, contentType={}", 
                        imageUrl, data.length, contentType);
                return data;
            }
            
        } catch (Exception e) {
            log.error("下载网络图片失败: url={}", imageUrl, e);
            throw new IOException("网络图片下载失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据URL推测MIME类型
     */
    private String getMimeTypeFromUrl(String url) {
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.endsWith(".png")) {
            return "image/png";
        } else if (lowerUrl.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerUrl.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "image/jpeg"; // 默认
        }
    }
    
    /**
     * 根据文件路径推测MIME类型
     */
    private String getMimeTypeFromPath(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".webp")) {
            return "image/webp";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "image/jpeg"; // 默认
        }
    }

    /**
     * 将图像字节数据保存到本地文件系统
     */
    private String saveImageDataToLocal(byte[] imageData, UUID userId, String sessionId, String messageId) throws IOException {
        // 确保使用绝对路径创建保存目录
        Path imagesDirPath = Paths.get(imagesDir).isAbsolute() 
                ? Paths.get(imagesDir) 
                : Paths.get(System.getProperty("user.dir"), imagesDir);
        
        log.debug("图片保存路径: imagesDir={}, absolutePath={}", imagesDir, imagesDirPath.toAbsolutePath());
        
        if (!Files.exists(imagesDirPath)) {
            Files.createDirectories(imagesDirPath);
            log.info("创建图片目录: {}", imagesDirPath.toAbsolutePath());
        }
        
        // 按日期分组存储
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path dateDirPath = imagesDirPath.resolve(datePrefix);
        if (!Files.exists(dateDirPath)) {
            Files.createDirectories(dateDirPath);
        }
        
        // 生成文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        String fileName = String.format("img_%s_%s_%s.jpg", 
                userId.toString().substring(0, 8), 
                timestamp,
                messageId.substring(0, 8));
        
        Path filePath = dateDirPath.resolve(fileName);
        
        // 保存图片数据
        Files.write(filePath, imageData);
        
        log.info("图片保存成功: filePath={}, size={}bytes", filePath, imageData.length);
        
        // 返回可访问的URI
        return String.format("%s/%s/%s", imagesBaseUrl, datePrefix, fileName);
    }


    /**
     * 图像生成结果记录
     */
    public record ImageGenerationResult(
            String imageUri,
            String description,
            boolean isSelf,
            String prompt
    ) {}
}