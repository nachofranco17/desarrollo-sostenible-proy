package uy.edu.um.xperience.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fallback for failures before a controller/service is called (binding, validation, authorization). */
@Component
@ConditionalOnWebApplication
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PasswordCleanupFilter extends OncePerRequestFilter {
    private static final ThreadLocal<List<char[]>> ARRAYS = new ThreadLocal<>();

    static char[] track(char[] password) {
        List<char[]> arrays = ARRAYS.get();
        if (arrays != null) arrays.add(password);
        return password;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        List<char[]> arrays = new ArrayList<>();
        ARRAYS.set(arrays);
        try {
            chain.doFilter(request, response);
        } finally {
            arrays.forEach(Passwords::clear);
            arrays.clear();
            ARRAYS.remove();
        }
    }
}
