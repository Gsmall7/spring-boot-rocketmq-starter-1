package org.rocketmq.spring.boot.containner;

import java.lang.reflect.Method;
import java.util.List;

import org.rocketmq.spring.boot.annotation.RocketMqListener;
import org.rocketmq.spring.boot.common.AbstractRocketMqConsumer;

public class RocketMqConsumeFactory {
	
	public static AbstractRocketMqConsumer createConsume(RocketMqListener listener,Object bean,List<Method> methods){
		String topic = listener.topic();
		String group=listener.group();
		RocketEndPoint endPoint=RocketEndPoint.builder().topic(topic).cosumeGroup(group).bean(bean).methods(methods).build();
		AbstractRocketMqConsumer rocketMqConsumer=new DefaultRocketMqConsume(endPoint);
		return rocketMqConsumer;
	}
}