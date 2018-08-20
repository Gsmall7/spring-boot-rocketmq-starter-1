package org.rocketmq.spring.boot.containner;

import java.lang.reflect.Method;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
@Builder
@Getter
@Setter
public class RocketEndPoint {
	private String topic;
	private String cosumeGroup;
	private Object bean;
	private List<Method> methods;
}