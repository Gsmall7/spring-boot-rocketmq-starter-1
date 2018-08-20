package org.rocketmq.spring.boot.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.rocketmq.spring.boot.common.AbstractRocketMqConsumer;
import org.rocketmq.spring.boot.config.RocketMqProperties;
import org.rocketmq.spring.boot.containner.RocketMqConsumeFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class RocketmqListenerAnnotationBeanPostProcessor implements BeanPostProcessor,Ordered,BeanFactoryAware{
	private BeanFactory beanFactory;
	private final Set<Class<?>> nonAnnotatedClasses =
			Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>(64));

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (!this.nonAnnotatedClasses.contains(bean.getClass())) {
			System.err.println(beanName);
			if (beanName.toLowerCase().contains("rocketmq")) {
				System.err.println();
			}
			Class<?> targetClass = AopUtils.getTargetClass(bean);
			Collection<RocketMqListener> classLevelListeners = findListenerAnnotations(targetClass);
			final boolean hasClassLevelListeners = classLevelListeners.size() > 0;
			final List<Method> multiMethods = new ArrayList<Method>();
			Map<Method, Set<RocketMqListener>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
					new MethodIntrospector.MetadataLookup<Set<RocketMqListener>>() {

						@Override
						public Set<RocketMqListener> inspect(Method method) {
							Set<RocketMqListener> listenerMethods = findListenerAnnotations(method);
							return (!listenerMethods.isEmpty() ? listenerMethods : null);
						}

					});
			if (hasClassLevelListeners) {
				Set<Method> methodsWithHandler = MethodIntrospector.selectMethods(targetClass,
						new ReflectionUtils.MethodFilter() {

							@Override
							public boolean matches(Method method) {
								return AnnotationUtils.findAnnotation(method, RocketHandler.class) != null;
							}

						});
				multiMethods.addAll(methodsWithHandler);
			}
			if (annotatedMethods.isEmpty()) {
				this.nonAnnotatedClasses.add(bean.getClass());
				if (this.log.isTraceEnabled()) {
					this.log.trace("No @RocketListner annotations found on bean type: " + bean.getClass());
				}
			}
			else {
				// Non-empty set of methods
				for (Map.Entry<Method, Set<RocketMqListener>> entry : annotatedMethods.entrySet()) {
					Method method = entry.getKey();
					for (RocketMqListener listener : entry.getValue()) {
					}
				}
				if (this.log.isDebugEnabled()) {
					this.log.debug(annotatedMethods.size() + " @RocketListner methods processed on bean '"
							+ beanName + "': " + annotatedMethods);
				}
			}
			if (hasClassLevelListeners) {
				processMultiMethodListeners(classLevelListeners.iterator().next(), multiMethods, bean, beanName);
			}
		}
		return bean;
	}
	/*
	 * AnnotationUtils.getRepeatableAnnotations does not look at interfaces
	 */
	private Collection<RocketMqListener> findListenerAnnotations(Class<?> clazz) {
		Set<RocketMqListener> listeners = new HashSet<RocketMqListener>();
		RocketMqListener ann = AnnotationUtils.findAnnotation(clazz, RocketMqListener.class);
		if (ann != null) {
			listeners.add(ann);
		}
		RocketMqListeners anns = AnnotationUtils.findAnnotation(clazz, RocketMqListeners.class);
		if (anns != null) {
			listeners.addAll(Arrays.asList(anns.value()));
		}
		return listeners;
	}

	/*
	 * AnnotationUtils.getRepeatableAnnotations does not look at interfaces
	 */
	private Set<RocketMqListener> findListenerAnnotations(Method method) {
		Set<RocketMqListener> listeners = new HashSet<RocketMqListener>();
		RocketMqListener ann = AnnotationUtils.findAnnotation(method, RocketMqListener.class);
		if (ann != null) {
			listeners.add(ann);
		}
		RocketMqListeners anns = AnnotationUtils.findAnnotation(method, RocketMqListeners.class);
		if (anns != null) {
			listeners.addAll(Arrays.asList(anns.value()));
		}
		return listeners;
	}
	private void processMultiMethodListeners(RocketMqListener classLevelListeners, List<Method> multiMethods,
			Object bean, String beanName) {
		List<Method> checkedMethods = new ArrayList<Method>();
		for (Method method : multiMethods) {
			checkedMethods.add(checkProxy(method, bean));
		}
		processListener(classLevelListeners, bean,checkedMethods);
	}
	private void processListener(RocketMqListener classLevelListener, Object bean,List<Method> list) {
		AbstractRocketMqConsumer consumer=RocketMqConsumeFactory.createConsume(classLevelListener,bean,list);
		try {
			consumer.init();
			startConsume(consumer);
		} catch (Exception e) {
			log.error(e.getMessage(),e);
		}
	}
	private RocketMqProperties getRocketMqPropertie() {
		return beanFactory.getBean(RocketMqProperties.class);
	}
	private void startConsume(AbstractRocketMqConsumer rocketMqConsumer) {
		RocketMqProperties rocketMqProperties=getRocketMqPropertie();
		Map<String, Set<String>> subscribeTopicTags = rocketMqConsumer.subscribeTopicTags();
        DefaultMQPushConsumer mqPushConsumer = rocketMqConsumer.getConsumer();
        mqPushConsumer.setNamesrvAddr(rocketMqProperties.getNameServer());
        subscribeTopicTags.entrySet().forEach(e -> {
            try {
                String rocketMqTopic = e.getKey();
                Set<String> rocketMqTags = e.getValue();
                if (CollectionUtils.isEmpty(rocketMqTags)) {
                    mqPushConsumer.subscribe(rocketMqTopic, "*");
                } else {
                    String tags = StringUtils.join(rocketMqTags, " || ");
                    mqPushConsumer.subscribe(rocketMqTopic, tags);
                    log.info("subscribe, topic:{}, tags:{}", rocketMqTopic, tags);
                }
            } catch (MQClientException ex) {
                log.error("consumer subscribe error", ex);
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("consumer shutdown");
            mqPushConsumer.shutdown();
            log.info("consumer has shutdown");
        }));

        try {
            mqPushConsumer.start();
            rocketMqConsumer.setStarted(true);
            log.info("rocketmq consumer started, nameserver:{}, group:{}", rocketMqProperties.getNameServer(),
                    rocketMqConsumer.getConsumerGroup());
        } catch (MQClientException e) {
            log.error("consumer start error, nameserver:{}, group:{}", rocketMqProperties.getNameServer(),
                    rocketMqConsumer.getConsumerGroup(), e);
        }
	}

	private Method checkProxy(Method methodArg, Object bean) {
		Method method = methodArg;
		if (AopUtils.isJdkDynamicProxy(bean)) {
			try {
				// Found a @RocketListner method on the target class for this JDK proxy ->
				// is it also present on the proxy itself?
				method = bean.getClass().getMethod(method.getName(), method.getParameterTypes());
				Class<?>[] proxiedInterfaces = ((Advised) bean).getProxiedInterfaces();
				for (Class<?> iface : proxiedInterfaces) {
					try {
						method = iface.getMethod(method.getName(), method.getParameterTypes());
						break;
					}
					catch (NoSuchMethodException noMethod) {
					}
				}
			}
			catch (SecurityException ex) {
				ReflectionUtils.handleReflectionException(ex);
			}
			catch (NoSuchMethodException ex) {
				throw new IllegalStateException(String.format(
						"@RocketListner method '%s' found on bean target class '%s', " +
						"but not found in any interface(s) for bean JDK proxy. Either " +
						"pull the method up to an interface or switch to subclass (CGLIB) " +
						"proxies by setting proxy-target-class/proxyTargetClass " +
						"attribute to 'true'", method.getName(), method.getDeclaringClass().getSimpleName()), ex);
			}
		}
		return method;
	}
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory=beanFactory;
	}

}
