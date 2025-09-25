package com.github.jwj.brilliantavern.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebFluxConfig {

    /**
     * 配置WebClient，用于FishSpeech TTS服务调用
     */
    @Bean
    public WebClient webClient() {
        // 配置HTTP客户端超时
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn -> 
                    conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    // 增加内存缓冲区大小，用于处理大文件
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
                })
                .build();
    }
}
