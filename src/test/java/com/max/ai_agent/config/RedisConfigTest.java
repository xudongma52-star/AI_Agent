package com.max.ai_agent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisConfig 配置类 单元测试")
class RedisConfigTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    private RedisConfig redisConfig;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
    }

    @Nested
    @DisplayName("Bean 创建测试")
    class BeanCreationTests {

        @Test
        @DisplayName("redisTemplate() - 不应为null")
        void redisTemplate_shouldNotBeNull() {
            RedisTemplate<String, Object> template =
                    redisConfig.redisTemplate(connectionFactory);
            assertNotNull(template);
        }

        @Test
        @DisplayName("redisTemplate() - 应绑定传入的ConnectionFactory")
        void redisTemplate_shouldUseProvidedFactory() {
            RedisTemplate<String, Object> template =
                    redisConfig.redisTemplate(connectionFactory);
            assertEquals(connectionFactory, template.getConnectionFactory());
        }
    }

    @Nested
    @DisplayName("序列化器配置测试")
    class SerializerTests {

        private RedisTemplate<String, Object> template;

        @BeforeEach
        void setup() {
            template = redisConfig.redisTemplate(connectionFactory);
        }

        @Test
        @DisplayName("Key序列化器 - 应为StringRedisSerializer")
        void keySerializer_shouldBeString() {
            RedisSerializer<?> keySerializer = template.getKeySerializer();
            assertNotNull(keySerializer);
            assertInstanceOf(StringRedisSerializer.class, keySerializer);
        }

        @Test
        @DisplayName("HashKey序列化器 - 应为StringRedisSerializer")
        void hashKeySerializer_shouldBeString() {
            assertInstanceOf(StringRedisSerializer.class,
                    template.getHashKeySerializer());
        }

        @Test
        @DisplayName("Value序列化器 - 应为Jackson2JsonRedisSerializer")
        void valueSerializer_shouldBeJackson() {
            assertInstanceOf(Jackson2JsonRedisSerializer.class,
                    template.getValueSerializer());
        }

        @Test
        @DisplayName("HashValue序列化器 - 应为Jackson2JsonRedisSerializer")
        void hashValueSerializer_shouldBeJackson() {
            assertInstanceOf(Jackson2JsonRedisSerializer.class,
                    template.getHashValueSerializer());
        }

        @Test
        @DisplayName("Key和HashKey - 同为String序列化器")
        void keyAndHashKey_bothString() {
            assertAll(
                    () -> assertInstanceOf(StringRedisSerializer.class,
                            template.getKeySerializer()),
                    () -> assertInstanceOf(StringRedisSerializer.class,
                            template.getHashKeySerializer())
            );
        }

        @Test
        @DisplayName("Value和HashValue - 同为Jackson序列化器")
        void valueAndHashValue_bothJackson() {
            assertAll(
                    () -> assertInstanceOf(Jackson2JsonRedisSerializer.class,
                            template.getValueSerializer()),
                    () -> assertInstanceOf(Jackson2JsonRedisSerializer.class,
                            template.getHashValueSerializer())
            );
        }
    }

    @Nested
    @DisplayName("Bean 独立性测试")
    class IndependenceTests {

        @Test
        @DisplayName("多次调用 - 每次返回新实例")
        void multipleCall_shouldReturnNewInstance() {
            RedisTemplate<String, Object> t1 =
                    redisConfig.redisTemplate(connectionFactory);
            RedisTemplate<String, Object> t2 =
                    redisConfig.redisTemplate(connectionFactory);
            assertNotSame(t1, t2);
        }

        @Test
        @DisplayName("不同Factory - 各自绑定正确Factory")
        void differentFactory_shouldBindCorrectly() {
            RedisConnectionFactory anotherFactory =
                    mock(RedisConnectionFactory.class);

            RedisTemplate<String, Object> t1 =
                    redisConfig.redisTemplate(connectionFactory);
            RedisTemplate<String, Object> t2 =
                    redisConfig.redisTemplate(anotherFactory);

            assertEquals(connectionFactory, t1.getConnectionFactory());
            assertEquals(anotherFactory, t2.getConnectionFactory());
        }
    }
}