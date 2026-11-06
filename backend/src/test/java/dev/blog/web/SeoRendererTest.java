package dev.blog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class SeoRendererTest {

    private static SeoRenderer rendererFor(String html) {
        return new SeoRenderer(new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public boolean exists() {
                return true;
            }
        });
    }

    @Test
    void replacesBlockBetweenMarkers() {
        SeoRenderer r = rendererFor("<head><!-- SEO:START keep --><title>Old</title><!-- SEO:END --></head>");
        String out = r.render(new SeoTags("Hello", "Desc", "https://x.dev/", "https://x.dev/a.png", "article"));
        assertThat(out).doesNotContain("Old").contains("<title>Hello</title>")
                .contains("og:image\" content=\"https://x.dev/a.png\"")
                .contains("summary_large_image")
                .startsWith("<head>").endsWith("</head>");
    }

    @Test
    void escapesUserControlledText() {
        SeoRenderer r = rendererFor("<!-- SEO:START --><!-- SEO:END -->");
        String out = r.render(new SeoTags("</title><script>alert(1)</script>", "\"x\"", "u", null, "website"));
        assertThat(out).doesNotContain("<script>").contains("&lt;script&gt;").contains("&quot;x&quot;");
    }

    @Test
    void failsLoudlyWithoutMarkers() {
        SeoRenderer r = rendererFor("<head></head>");
        assertThatThrownBy(() -> r.render(new SeoTags("t", "d", "u", null, "website")))
                .isInstanceOf(IllegalStateException.class);
    }
}
