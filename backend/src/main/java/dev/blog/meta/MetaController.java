package dev.blog.meta;

import dev.blog.config.BlogProperties;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/meta")
public class MetaController {

    private final BlogProperties props;

    public MetaController(BlogProperties props) {
        this.props = props;
    }

    @GetMapping
    public SiteMetaResponse meta(Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new SiteMetaResponse(props.siteName(), props.version(), admin);
    }
}
