package dev.blog.web;

import dev.blog.config.BlogProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React SPA's index.html for client-side routes, with SEO tags injected.
 *
 * KEEP IN SYNC with frontend/src/router.tsx. Each public route needs a mapping here.
 * Phase 1+ adds handlers that look up the post/project and build real SeoTags
 * (e.g. GET /posts/{slug} -> post title, excerpt, cover image; 404 if missing).
 * Static assets (/assets/**, /favicon.svg) are served by Spring's default resource handler.
 */
@Controller
public class SpaIndexController {

    private final SeoRenderer renderer;
    private final BlogProperties props;

    public SpaIndexController(SeoRenderer renderer, BlogProperties props) {
        this.renderer = renderer;
        this.props = props;
    }

    @GetMapping({"/", "/projects", "/search", "/admin", "/admin/**"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        SeoTags tags = new SeoTags(
                props.siteName(),
                props.siteDescription(),
                props.baseUrl() + request.getRequestURI(),
                null,
                "website");
        return html(renderer.render(tags));
    }

    static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noCache())
                .body(body);
    }
}
