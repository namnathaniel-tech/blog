package dev.blog.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Replaces the block between {@code <!-- SEO:START} and {@code <!-- SEO:END -->}
 * in the built frontend's index.html with page-specific title/description/OG tags.
 *
 * In production the Dockerfile copies frontend/dist into classpath:static/.
 * In dev/tests that file is absent, so a minimal fallback template is used.
 */
@Component
public class SeoRenderer {

    static final String START = "<!-- SEO:START";
    static final String END = "<!-- SEO:END -->";

    private static final String FALLBACK_TEMPLATE = """
            <!doctype html>
            <html lang="en"><head><meta charset="UTF-8" />
            <!-- SEO:START --><!-- SEO:END -->
            </head><body><div id="root"></div></body></html>
            """;

    private final String template;

    public SeoRenderer() {
        this(new ClassPathResource("static/index.html"));
    }

    SeoRenderer(Resource indexHtml) {
        this.template = load(indexHtml);
    }

    public String render(SeoTags tags) {
        int start = template.indexOf(START);
        int end = template.indexOf(END);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("index.html is missing SEO:START/SEO:END markers");
        }
        return template.substring(0, start) + tagsHtml(tags) + template.substring(end + END.length());
    }

    private static String tagsHtml(SeoTags t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<title>").append(esc(t.title())).append("</title>\n");
        meta(sb, "name", "description", t.description());
        sb.append("<link rel=\"canonical\" href=\"").append(esc(t.canonicalUrl())).append("\" />\n");
        meta(sb, "property", "og:title", t.title());
        meta(sb, "property", "og:description", t.description());
        meta(sb, "property", "og:url", t.canonicalUrl());
        meta(sb, "property", "og:type", t.type());
        meta(sb, "name", "twitter:card", t.imageUrl() != null ? "summary_large_image" : "summary");
        if (t.imageUrl() != null) {
            meta(sb, "property", "og:image", t.imageUrl());
        }
        return sb.toString();
    }

    private static void meta(StringBuilder sb, String attr, String key, String value) {
        if (value == null) return;
        sb.append("<meta ").append(attr).append("=\"").append(key)
          .append("\" content=\"").append(esc(value)).append("\" />\n");
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, StandardCharsets.UTF_8.name());
    }

    private static String load(Resource resource) {
        if (!resource.exists()) return FALLBACK_TEMPLATE;
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }
}
