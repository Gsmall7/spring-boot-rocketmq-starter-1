package org.rocketmq.spring.boot.config;

public abstract class RocketmqListenerConfigUtils {
	/**
	 * The bean name of the internally managed Kafka listener annotation processor.
	 */
	public static final String ROCKETMQ_LISTENER_ANNOTATION_PROCESSOR_BEAN_NAME =
			"org.springframework.rocketmq.config.internalRocketmqListenerAnnotationProcessor";
}
