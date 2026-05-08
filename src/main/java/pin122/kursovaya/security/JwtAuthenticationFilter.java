package pin122.kursovaya.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pin122.kursovaya.utils.JwtTokenProvider;

import java.io.IOException;

/**
 * Фильтр, выполняющийся один раз за запрос: извлекает JWT из заголовка {@code Authorization}, валидирует и
 * устанавливает {@link org.springframework.security.core.context.SecurityContext}.
 *
 * @see JwtTokenProvider
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * @param jwtTokenProvider     проверка и разбор JWT
     * @param userDetailsService загрузка пользователя по имени из токена
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Пропускает OPTIONS и публичные пути; для остальных извлекает Bearer-токен, при успехе устанавливает аутентификацию.
     *
     * @param request     HTTP-запрос
     * @param response    HTTP-ответ
     * @param filterChain цепочка фильтров
     * @throws ServletException при ошибке сервлета
     * @throws IOException      при ошибке ввода-вывода
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // Пропускаем OPTIONS запросы (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Пропускаем публичные эндпоинты
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || path.startsWith("/api/users/create")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Токен отсутствует - Spring Security вернет 401 через AuthenticationEntryPoint
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            // Проверяем, что токен валидный
            if (!jwtTokenProvider.validateToken(jwt)) {
                // Токен невалидный - Spring Security вернет 401 через AuthenticationEntryPoint
                filterChain.doFilter(request, response);
                return;
            }

            userEmail = jwtTokenProvider.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtTokenProvider.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Токен невалидный или ошибка при обработке - Spring Security вернет 401
            // Логируем ошибку для отладки
            System.err.println("JWT Authentication error: " + e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}

