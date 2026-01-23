package com.fine.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fine.filler.JwtAuthenticationTokenFilter;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)		// 这里新增这个注解
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

	@Autowired
	JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;
	@Autowired
	private AuthenticationSuccessHandler successHandler;
	@Autowired
	private AuthenticationFailureHandler failureHandler;
	@Autowired
	private LogoutSuccessHandler logoutSuccessHandler;
	@Autowired
	private AuthenticationEntryPoint authenticationEntryPoint;

	@Autowired
	private AccessDeniedHandler accessDeniedHandler;
	
	
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				// 关闭csrf
				.csrf().disable()
				// 不通过Session获取SecurityContext
				.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and().authorizeRequests()
				// 对于登录接口 允许匿名访问
				.antMatchers("/user/login").anonymous()
				// 用户和角色管理接口需要认证后访问（已登录用户可访问）
				.antMatchers("/api/users/**", "/api/roles/**").authenticated()
				// 除上面外的所有请求全部需要鉴权认证
				.anyRequest().authenticated();
		http.formLogin().successHandler(successHandler).failureHandler(failureHandler);

		http.logout()
				// 配置注销成功处理器
				.logoutSuccessHandler(logoutSuccessHandler);

		// 添加过滤器
		http.addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);

		// 配置异常处理器
		http.exceptionHandling()
				// 配置认证失败处理器
				.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler);


		// 允许跨域
		http.cors();
		
		return http.build();
	}	 @Bean
	    public CorsConfigurationSource corsConfigurationSource() {
	        // 创建一个新的CorsConfiguration对象
	        CorsConfiguration configuration = new CorsConfiguration();
	        // 允许所有来源 (使用 allowedOriginPatterns 替代 allowedOrigin，以兼容 allowCredentials)
	        configuration.addAllowedOriginPattern("*");
	        // 允许所有HTTP方法
	        configuration.addAllowedMethod("*");
	        // 允许所有请求头（包括自定义 Token 头）
	        configuration.addAllowedHeader("*");
	        // 允许携带凭证（cookie / authorization header）
	        configuration.setAllowCredentials(true);
	        // 创建一个新的UrlBasedCorsConfigurationSource对象
	        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
	        // 将CorsConfiguration对象注册到URL路径"/**"，表示对所有路径生效
	        source.registerCorsConfiguration("/**", configuration);
	        // 返回配置好的CorsConfigurationSource对象
	        return source;
	    }}
