package dev.blog.auth;

import dev.blog.config.BlogProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Establishes / tears down the admin's authenticated HTTP session (D-005). */
@Component
class AdminSessionService {

    private final SecurityContextRepository contextRepository;
    private final BlogProperties props;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    AdminSessionService(SecurityContextRepository contextRepository, BlogProperties props) {
        this.contextRepository = contextRepository;
        this.props = props;
    }

    void logIn(HttpServletRequest request, HttpServletResponse response) {
        // Session fixation protection: never reuse a pre-login session id.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                props.normalizedAdminEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(auth);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    void logOut(HttpServletRequest request) {
        holder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
