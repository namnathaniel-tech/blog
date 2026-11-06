package dev.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecoveryCodeServiceTest {

    @Test
    void normalizeStripsSeparatorsAndMapsLookalikes() {
        assertThat(RecoveryCodeService.normalize(" abcd-efgh-jkmn ")).isEqualTo("ABCDEFGHJKMN");
        assertThat(RecoveryCodeService.normalize("o0il-L1Ii-0000")).isEqualTo("00111111" + "0000");
        assertThat(RecoveryCodeService.normalize(null)).isEmpty();
    }

    @Test
    void formatGroupsInFours() {
        assertThat(RecoveryCodeService.format("ABCDEFGHJKMN")).isEqualTo("ABCD-EFGH-JKMN");
    }
}
