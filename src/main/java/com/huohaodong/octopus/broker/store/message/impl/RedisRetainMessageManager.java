package com.huohaodong.octopus.broker.store.message.impl;

import com.google.gson.Gson;
import com.huohaodong.octopus.broker.server.metric.annotation.RetainMetric;
import com.huohaodong.octopus.broker.server.metric.annotation.UnRetainMetric;
import com.huohaodong.octopus.broker.store.config.StoreConfig;
import com.huohaodong.octopus.broker.store.message.RetainMessage;
import com.huohaodong.octopus.broker.store.message.RetainMessageManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(value = "spring.octopus.broker.storage.retain", havingValue = "redis")
public class RedisRetainMessageManager implements RetainMessageManager {

    private final StoreConfig storeConfig;

    private final RedisTemplate<String, RetainMessage> redisTemplate = new RedisTemplate<>();

    private final Gson GSON = new Gson();

    public RedisRetainMessageManager(RedisConnectionFactory connectionFactory, StoreConfig storeConfig) {
        this.storeConfig = storeConfig;
        this.redisTemplate.setConnectionFactory(connectionFactory);
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        this.redisTemplate.setDefaultSerializer(stringSerializer);
        this.redisTemplate.setEnableDefaultSerializer(true);
        this.redisTemplate.afterPropertiesSet();
    }

    @Override
    @RetainMetric
    public int put(String topic, RetainMessage message) {
        int newTopicCount = 0;
        if (!contains(topic)) {
            newTopicCount = 1;
        }
        redisTemplate.opsForHash().put(KEY(), topic, GSON.toJson(message));
        return newTopicCount;
    }

    @Override
    public RetainMessage get(String topic) {
        return GSON.fromJson((String) redisTemplate.opsForHash().get(KEY(), topic), RetainMessage.class);
    }

    @Override
    @UnRetainMetric
    public int remove(String topic) {
        if (contains(topic)) {
            redisTemplate.opsForHash().delete(KEY(), topic);
            return 1;
        }
        return 0;
    }

    @Override
    public boolean contains(String topic) {
        return redisTemplate.opsForHash().hasKey(KEY(), topic);
    }

    public Collection<RetainMessage> getAll() {
        return redisTemplate.opsForHash().values(KEY())
                .stream()
                .map(o -> GSON.fromJson((String) o, RetainMessage.class))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<RetainMessage> getAllMatched(String topicFilter) {
        Set<RetainMessage> retainMessages = new HashSet<>();
        if (!topicFilter.contains("#") && !topicFilter.contains("+")) {
            RetainMessage message = get(topicFilter);
            if (message != null) {
                retainMessages.add(message);
            }
        } else {
            getAll().forEach(retainMessage -> {
                {
                    String topic = retainMessage.getTopic();
                    String[] splitTopics = topic.split("/");
                    String[] splitTopicFilters = topicFilter.split("/");
                    if (splitTopics.length >= splitTopicFilters.length) {
                        StringBuilder newTopicFilter = new StringBuilder();
                        for (int i = 0; i < splitTopicFilters.length; i++) {
                            String value = splitTopicFilters[i];
                            if (value.equals("+")) {
                                newTopicFilter.append("+/");
                            } else if (value.equals("#")) {
                                newTopicFilter.append("#/");
                                break;
                            } else {
                                newTopicFilter.append(splitTopics[i]).append("/");
                            }
                        }
                        newTopicFilter = new StringBuilder(newTopicFilter.substring(0, newTopicFilter.lastIndexOf("/")));
                        if (topicFilter.contentEquals(newTopicFilter)) {
                            retainMessages.add(retainMessage);
                        }
                    }
                }
            });
        }
        return retainMessages;
    }

    @Override
    public int size() {
        return Math.toIntExact(redisTemplate.opsForHash().size(KEY()));
    }

    private String KEY() {
        return storeConfig.RETAIN_PREFIX;
    }
}
