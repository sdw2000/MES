package com.fine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
	 @org.springframework.lang.NonNull
	 private CorsConfiguration buildConfig() {
			CorsConfiguration corsConfiguration = new CorsConfiguration();
			// Allow all origins (使用 allowedOriginPatterns 替代 allowedOrigin，以兼容 allowCredentials)
			corsConfiguration.addAllowedOriginPattern("*");
			// Allow all headers including custom Token header
			corsConfiguration.addAllowedHeader("*");
			// Allow all HTTP methods
			corsConfiguration.addAllowedMethod("*");
			// Allow credentials
			corsConfiguration.setAllowCredentials(true);
			// return non-null configuration
			return corsConfiguration;
		}
	    @Bean
	    public CorsFilter corsFilter() {
	        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
	        //配置 可以访问的地址
	        source.registerCorsConfiguration("/**", buildConfig()); // 4
	        return new CorsFilter(source);
	    }
}