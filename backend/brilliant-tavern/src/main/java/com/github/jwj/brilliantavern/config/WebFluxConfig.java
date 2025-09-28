package com.github.jwj.brilliantavern.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebFluxConfig {

    /**
     * 配置WebClient，用于FishSpeech TTS服务调用
     * 优化连接池配置，减少冷启动延迟
     */
    @Bean
    public WebClient webClient() {
        // 配置连接池，优化连接复用和预热
        ConnectionProvider connectionProvider = ConnectionProvider.builder("tts-http-pool")
                .maxConnections(50)  // 最大连接数
                .maxIdleTime(Duration.ofSeconds(60))  // 连接空闲时间
                .maxLifeTime(Duration.ofMinutes(10))  // 连接最大生命周期
                .pendingAcquireTimeout(Duration.ofSeconds(10))  // 获取连接超时
                .evictInBackground(Duration.ofSeconds(30))  // 后台清理间隔
                .build();
        
        log.info("初始化TTS WebClient连接池: maxConnections=50, maxIdleTime=60s");

        // 配置HTTP客户端，启用连接保持和优化超时
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .keepAlive(true)  // 启用连接保持
                .responseTimeout(Duration.ofSeconds(45))  // 响应超时（TTS可能需要更长时间）
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)  // 连接超时5秒
                .option(io.netty.channel.ChannelOption.TCP_NODELAY, true)  // 禁用Nagle算法
                .option(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)  // 启用TCP KeepAlive
                .doOnConnected(conn -> 
                    conn.addHandlerLast(new ReadTimeoutHandler(45, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    // 增加内存缓冲区大小，用于处理大文件
                    configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024); // 16MB，适应TTS音频文件
                })
                .build();
    }
}
