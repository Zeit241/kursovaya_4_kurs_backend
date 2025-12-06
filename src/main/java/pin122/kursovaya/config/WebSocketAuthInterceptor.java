package pin122.kursovaya.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import pin122.kursovaya.utils.JwtTokenProvider;

import java.security.Principal;
import java.util.List;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public WebSocketAuthInterceptor(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Получаем токен из заголовков
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            String token = null;
            
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }
            
            // Если токен не найден в заголовках, пробуем получить из query параметра при подключении
            if (token == null) {
                // Получаем query параметры из URL при подключении
                // Это работает только при CONNECT команде
                String queryString = accessor.getFirstNativeHeader("query");
                if (queryString == null) {
                    // Пробуем получить из других заголовков
                    List<String> queryHeaders = accessor.getNativeHeader("query");
                    if (queryHeaders != null && !queryHeaders.isEmpty()) {
                        queryString = queryHeaders.get(0);
                    }
                }
                
                if (queryString != null && queryString.contains("token=")) {
                    int tokenIndex = queryString.indexOf("token=") + 6;
                    token = queryString.substring(tokenIndex);
                    if (token.contains("&")) {
                        token = token.substring(0, token.indexOf("&"));
                    }
                }
            }
            
            if (token != null && jwtTokenProvider.validateToken(token)) {
                try {
                    String username = jwtTokenProvider.extractUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    
                    if (jwtTokenProvider.validateToken(token, userDetails)) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(auth);
                    }
                } catch (Exception e) {
                    // Ошибка аутентификации
                    return null;
                }
            } else {
                // Токен невалидный или отсутствует
                return null;
            }
        }
        
        return message;
    }
}

