package pin122.kursovaya.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Настройка STOMP/WebSocket: брокер {@code /queue}, {@code /topic}, префикс приложения {@code /app}, эндпоинт {@code /queue-websocket}.
 *
 * @see WebSocketAuthInterceptor
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    /**
     * @param authInterceptor перехватчик для проверки JWT при CONNECT
     */
    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * Включает in-memory брокер и префикс назначений для сообщений от клиентов.
     *
     * @param config реестр брокера сообщений
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Включаем простой брокер сообщений для отправки сообщений клиентам
        config.enableSimpleBroker("/queue", "/topic");
        // Префикс для сообщений от клиента к серверу
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Регистрирует точку подключения {@code /queue-websocket} с SockJS и без него, разрешая любые origin patterns.
     *
     * @param registry реестр STOMP-эндпоинтов
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Регистрируем WebSocket endpoint
        registry.addEndpoint("/queue-websocket")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Также поддерживаем обычный WebSocket без SockJS
        registry.addEndpoint("/queue-websocket")
                .setAllowedOriginPatterns("*");
    }

    /**
     * Подключает {@link WebSocketAuthInterceptor} к входящему каналу клиента.
     *
     * @param registration регистрация канала
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Добавляем interceptor для авторизации
        registration.interceptors(authInterceptor);
    }
}

