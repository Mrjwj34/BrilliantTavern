package com.github.jwj.brilliantavern.service.tts;

import com.github.jwj.brilliantavern.entity.TTSVoice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * TTS语音管理服务接口
 */
public interface TTSVoiceService {
    
    /**
     * 创建新的语音引用
     * 
     * @param userId 用户ID
     * @param name 语音名称
     * @param description 语音描述
     * @param audioBytes 音频文件的原始字节
     * @param referenceText 参考文本
     * @param isPublic 是否公开
     * @return 创建的语音对象
     */
    Mono<TTSVoice> createVoice(String userId, String name, String description, 
                              byte[] audioBytes, String referenceText, Boolean isPublic);

    /**
     * 删除语音引用
     * 
     * @param voiceId 语音ID
     * @param userId 用户ID（用于权限验证）
     * @return 完成信号
     */
    Mono<Void> deleteVoice(String voiceId, String userId);
    
    /**
     * 获取用户的语音列表
     * 
     * @param userId 用户ID
     * @return 语音列表
     */
    Flux<TTSVoice> getUserVoices(String userId);
    
    /**
     * 获取公开语音列表
     * 
     * @return 公开语音列表
     */
    Flux<TTSVoice> getPublicVoices(String userId, String sort);
    
    /**
     * 根据ID获取语音
     * 
     * @param voiceId 语音ID
     * @param userId 用户ID（用于权限验证）
     * @return 语音对象
     */
    Mono<TTSVoice> getVoice(String voiceId, String userId);
    
    /**
     * 更新语音信息
     * 
     * @param voiceId 语音ID
     * @param userId 用户ID（用于权限验证）
     * @param name 新名称
     * @param description 新描述
     * @param isPublic 是否公开
     * @return 更新后的语音对象
     */
    Mono<TTSVoice> updateVoice(String voiceId, String userId, String name, 
                              String description, Boolean isPublic);

    
    /**
     * 搜索语音
     * 
     * @param keyword 搜索关键词
     * @param userId 用户ID
     * @param includePublic 是否包含公开语音
     * @return 匹配的语音列表
     */
    Flux<TTSVoice> searchVoices(String keyword, String userId, Boolean includePublic, String sort);

    /**
     * 点赞音色
     *
     * @param voiceId 语音ID
     * @param userId 用户ID
     * @return 更新后的语音对象
     */
    Mono<TTSVoice> likeVoice(String voiceId, String userId);

    /**
     * 取消点赞音色
     *
     * @param voiceId 语音ID
     * @param userId 用户ID
     * @return 更新后的语音对象
     */
    Mono<TTSVoice> unlikeVoice(String voiceId, String userId);

}
