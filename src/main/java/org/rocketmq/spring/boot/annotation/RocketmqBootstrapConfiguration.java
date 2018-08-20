package org.rocketmq.spring.boot.annotation;

import org.rocketmq.spring.boot.config.RocketmqListenerConfigUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

@Configuration
public class RocketmqBootstrapConfiguration {
	@SuppressWarnings("rawtypes")
	@Bean(name = RocketmqListenerConfigUtils.ROCKETMQ_LISTENER_ANNOTATION_PROCESSOR_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public RocketmqListenerAnnotationBeanPostProcessor rocketmqListenerAnnotationBeanPostProcessor() {
		return new RocketmqListenerAnnotationBeanPostProcessor();
	}
}
