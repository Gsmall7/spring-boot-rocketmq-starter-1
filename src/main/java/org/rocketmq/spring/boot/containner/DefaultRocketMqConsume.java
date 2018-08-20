package org.rocketmq.spring.boot.containner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.rocketmq.common.message.MessageExt;
import org.rocketmq.spring.boot.annotation.RocketHandler;
import org.rocketmq.spring.boot.common.AbstractRocketMqConsumer;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@NoArgsConstructor
@Slf4j
public class DefaultRocketMqConsume extends AbstractRocketMqConsumer{
	private RocketEndPoint endPoint;
	private final Map<String,Collection<Method>> methodMap=new ConcurrentHashMap<>();
	public DefaultRocketMqConsume(RocketEndPoint endPoint) {
		this.endPoint=endPoint;
		initTags(endPoint.getMethods());
	}
	@Override
	public Map<String, Set<String>> subscribeTopicTags() {
		Map<String,Set<String>> map =new HashMap<>();
		map.put(endPoint.getTopic(),methodMap.keySet());
		return map;
	}
	public void initTags(List<Method> list){
		list.forEach((m)->{
			RocketHandler rocketHandler=m.getAnnotation(RocketHandler.class);
			String[] tags=rocketHandler.tags();
			for (String tag : tags) {
				Collection<Method>  methodCollection=methodMap.get(tag);
				if (null==methodCollection) {
					methodCollection=new ArrayList<>();
					methodMap.put(tag,methodCollection);
				}
				methodCollection.add(m);
			}
		});
	}
	@Override
	public String getConsumerGroup() {
		return endPoint.getCosumeGroup();
	}

	@Override
	public void consumeMsg(MessageExt msg) {
		try {
			Collection<Method> methods=methodMap.get(msg.getTags());
			for (Method method : methods) {
				log.error("消息的tag是{}",msg.getTags());
				method.invoke(endPoint.getBean(),new String(msg.getBody()));
			}
		} catch (Exception e) {
			log.error(e.getMessage(),e);
		}
	}
}
