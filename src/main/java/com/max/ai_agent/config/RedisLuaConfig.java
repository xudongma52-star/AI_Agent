package com.max.ai_agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisLuaConfig {
    @Bean
    public DefaultRedisScript<Long> addMessageScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        script.setScriptText("""
        local listKey = KEYS[1]
        local counterKey = KEYS[2]
        local messageJson = ARGV[1]
        local ttl = tonumber(ARGV[2])
        local mysqlMaxOrder = tonumber(ARGV[3])
        
        local counterExists = redis.call('EXISTS', counterKey)
        
        local order
        if counterExists == 0 then
            order = mysqlMaxOrder + 1
            redis.call('SET', counterKey, order)
        else
            order = redis.call('INCR', counterKey)
        end
        
        redis.call('RPUSH', listKey, messageJson)
        redis.call('EXPIRE', listKey, ttl)
        redis.call('EXPIRE', counterKey, ttl)
        
        return order
    """);

        script.setResultType(Long.class);
        return script;
    }
}
