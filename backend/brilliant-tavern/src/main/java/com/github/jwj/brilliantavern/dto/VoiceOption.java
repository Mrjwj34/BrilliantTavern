package com.github.jwj.brilliantavern.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音选项DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceOption {
    
    /**
     * 语音ID
     */
    private String id;
    
    /**
     * 语音名称
     */
    private String name;
    
    /**
     * 语音描述
     */
    private String description;
    
    /**
     * 语音类型（male/female）
     */
    private String type;
    
    /**
     * 语音语言
     */
    private String language;
}
