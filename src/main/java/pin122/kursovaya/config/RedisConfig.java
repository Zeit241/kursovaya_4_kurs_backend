package pin122.kursovaya.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Конфигурация клиента Redis: шаблон для строковых ключей/значений и Lua-скрипт сдвига очереди.
 */
@Configuration
public class RedisConfig {

    /**
     * Настраивает {@link RedisTemplate} со строковой сериализацией ключей и значений (включая hash).
     *
     * @param connectionFactory фабрика соединений Spring Data Redis
     * @return готовый к использованию шаблон
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Регистрирует Lua-скрипт {@code remove-and-shift.lua} для атомарного удаления элемента и сдвига индексов в очереди.
     *
     * @return скрипт с типом результата {@link Long}
     */
    @Bean
    public DefaultRedisScript<Long> removeAndShiftScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("remove-and-shift.lua")));
        script.setResultType(Long.class);
        return script;
    }
}

