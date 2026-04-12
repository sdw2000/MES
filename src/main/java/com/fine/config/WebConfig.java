package com.fine.config;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置Jackson将Long类型序列化为String
     * 解决前端在处理Long类型大数字时的精度丢失问题
     */
    @Bean("jackson2ObjectMapperBuilderCustomizer")
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            JsonSerializer<?> longSerializer = Objects.requireNonNull(ToStringSerializer.instance);
            builder.serializerByType(Long.class, longSerializer);
            builder.serializerByType((Class<?>) long.class, longSerializer);
        };
    }
}
