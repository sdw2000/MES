package com.fine.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SpringfoxCompatibilityConfig {

    @Bean
    public static BeanPostProcessor springfoxHandlerProviderBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof WebMvcRequestHandlerProvider) {
                    customizeSpringfoxHandlerMappings(getHandlerMappings(bean));
                }
                return bean;
            }

            private <T extends RequestMappingInfoHandlerMapping> void customizeSpringfoxHandlerMappings(List<T> mappings) {
                List<T> copy = new ArrayList<T>();
                for (T mapping : mappings) {
                    if (mapping.getPatternParser() == null) {
                        copy.add(mapping);
                    }
                }
                mappings.clear();
                mappings.addAll(copy);
            }

            @SuppressWarnings("unchecked")
            private List<RequestMappingInfoHandlerMapping> getHandlerMappings(Object bean) {
                Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
                if (field == null) {
                    return new ArrayList<RequestMappingInfoHandlerMapping>();
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(bean);
                    if (value instanceof List) {
                        return (List<RequestMappingInfoHandlerMapping>) value;
                    }
                } catch (IllegalAccessException ignored) {
                }
                return new ArrayList<RequestMappingInfoHandlerMapping>();
            }
        };
    }
}