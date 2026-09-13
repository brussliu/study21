package com.study21.admin.proxy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ホスト判定（{@link ProxyHostMatcher}）の単体テスト。
 *
 * <p>2.0 の私有メソッド（normalizeHost / normalizeIp / extractHostCandidate / matchesHost /
 * hostVariants / parseHostPort）の振る舞いを固定する。Netty も Spring も使わない。</p>
 */
class ProxyHostMatcherTest {

    @Test
    void normalizesHostWithPortPathAndCase() {
        assertThat(ProxyHostMatcher.normalizeHost("  WWW.Example.COM:8080/path  ")).isEqualTo("www.example.com");
        assertThat(ProxyHostMatcher.normalizeHost("example.com.")).isEqualTo("example.com");
        assertThat(ProxyHostMatcher.normalizeHost("[::1]:8888")).isEqualTo("::1");
        assertThat(ProxyHostMatcher.normalizeHost("example.com/path?q=1")).isEqualTo("example.com");
        assertThat(ProxyHostMatcher.normalizeHost("   ")).isNull();
        assertThat(ProxyHostMatcher.normalizeHost(null)).isNull();
    }

    @Test
    void normalizesClientIp() {
        assertThat(ProxyHostMatcher.normalizeIp(" 192.168.0.92 ")).isEqualTo("192.168.0.92");
        // InetSocketAddress の toString 由来（/192.168.0.92 の形）
        assertThat(ProxyHostMatcher.normalizeIp("/192.168.0.92")).isEqualTo("192.168.0.92");
        assertThat(ProxyHostMatcher.normalizeIp(" ")).isNull();
        assertThat(ProxyHostMatcher.normalizeIp(null)).isNull();
    }

    @Test
    void stripsEveryLeadingWww() {
        assertThat(ProxyHostMatcher.stripLeadingWww("www.example.com")).isEqualTo("example.com");
        assertThat(ProxyHostMatcher.stripLeadingWww("www.www.example.com")).isEqualTo("example.com");
        assertThat(ProxyHostMatcher.stripLeadingWww("example.com")).isEqualTo("example.com");
    }

    @Test
    void buildsHostVariantsWithAndWithoutWww() {
        assertThat(ProxyHostMatcher.hostVariants("www.example.com"))
                .containsExactly("www.example.com", "example.com");
        assertThat(ProxyHostMatcher.hostVariants("example.com")).containsExactly("example.com");
        assertThat(ProxyHostMatcher.hostVariants("  ")).isEmpty();
        assertThat(ProxyHostMatcher.hostVariants(null)).isEmpty();
    }

    @Test
    void extractsHostCandidateFromUrlOrHostName() {
        assertThat(ProxyHostMatcher.extractHostCandidate("https://www.YouTube.com/watch?v=1"))
                .isEqualTo("www.youtube.com");
        assertThat(ProxyHostMatcher.extractHostCandidate("youtube.com")).isEqualTo("youtube.com");
        assertThat(ProxyHostMatcher.extractHostCandidate("youtube.com:8443")).isEqualTo("youtube.com");
        assertThat(ProxyHostMatcher.extractHostCandidate("*.example.com")).isEqualTo("example.com");
        assertThat(ProxyHostMatcher.extractHostCandidate("192.168.0.100")).isEqualTo("192.168.0.100");
        assertThat(ProxyHostMatcher.extractHostCandidate("  ")).isNull();
        assertThat(ProxyHostMatcher.extractHostCandidate(null)).isNull();
    }

    @Test
    void usesSiteUrlAndHostNameAsCandidates() {
        // 2.1 は サイトURL → ホスト名 の順に候補を作る（サイト名称は使わない）
        assertThat(ProxyHostMatcher.extractCandidateHosts("https://www.youtube.com/watch", "youtube.com"))
                .containsExactly("www.youtube.com", "youtube.com");
        // 同じ値のときは重複させない
        assertThat(ProxyHostMatcher.extractCandidateHosts("youtube.com", "youtube.com"))
                .containsExactly("youtube.com");
        assertThat(ProxyHostMatcher.extractCandidateHosts(null, null)).isEmpty();
    }

    @Test
    void matchesBySuffixMethod() {
        assertThat(ProxyHostMatcher.matches("www.youtube.com", "youtube.com", "SUFFIX")).isTrue();
        assertThat(ProxyHostMatcher.matches("youtube.com", "www.youtube.com", "SUFFIX")).isTrue();
        assertThat(ProxyHostMatcher.matches("YouTube.COM", "youtube.com", "SUFFIX")).isTrue();
        // 2.0 の末尾一致は「～youtube.com」で終われば一致する（"notyoutube.com" も一致する）
        assertThat(ProxyHostMatcher.matches("notyoutube.com", "youtube.com", "SUFFIX")).isTrue();
        assertThat(ProxyHostMatcher.matches("youtube.co.jp", "youtube.com", "SUFFIX")).isFalse();
    }

    @Test
    void matchesByPrefixMethod() {
        assertThat(ProxyHostMatcher.matches("dmm-eikaiwa.example.com", "dmm-eikaiwa", "PREFIX")).isTrue();
        // www. を除いた形でも照合する（2.0 の hostVariants と同じ）
        assertThat(ProxyHostMatcher.matches("www.dmm-eikaiwa.example.com", "dmm-eikaiwa", "PREFIX")).isTrue();
        assertThat(ProxyHostMatcher.matches("www.dmm-eikaiwa.example.com", "www.dmm-eikaiwa", "PREFIX")).isTrue();
        assertThat(ProxyHostMatcher.matches("example.com", "dmm-eikaiwa", "PREFIX")).isFalse();
    }

    @Test
    void matchesByContainsMethod() {
        assertThat(ProxyHostMatcher.matches("www.youtube.com", "tube", "CONTAINS")).isTrue();
        assertThat(ProxyHostMatcher.matches("example.com", "tube", "CONTAINS")).isFalse();
    }

    @Test
    void matchesByExactMethod() {
        assertThat(ProxyHostMatcher.matches("www.youtube.com", "youtube.com", "EXACT")).isTrue();
        assertThat(ProxyHostMatcher.matches("youtube.com", "youtube.com", "EXACT")).isTrue();
        assertThat(ProxyHostMatcher.matches("m.youtube.com", "youtube.com", "EXACT")).isFalse();
    }

    @Test
    void normalizesJudgeMethodAndFallsBackToSuffix() {
        assertThat(ProxyHostMatcher.normalizeJudgeMethod("PREFIX")).isEqualTo("PREFIX");
        assertThat(ProxyHostMatcher.normalizeJudgeMethod(" suffix ")).isEqualTo("SUFFIX");
        assertThat(ProxyHostMatcher.normalizeJudgeMethod("CONTAINS")).isEqualTo("CONTAINS");
        assertThat(ProxyHostMatcher.normalizeJudgeMethod("EXACT")).isEqualTo("EXACT");
        // 2.0 の日本語表記も受け付ける
        assertThat(ProxyHostMatcher.normalizeJudgeMethod("先頭一致")).isEqualTo("PREFIX");
        assertThat(ProxyHostMatcher.normalizeJudgeMethod("末尾一致")).isEqualTo("SUFFIX");
        assertThat(ProxyHostMatcher.normalizeJudgeMethod("含める")).isEqualTo("CONTAINS");
        assertThat(ProxyHostMatcher.normalizeJudgeMethod("完全一致")).isEqualTo("EXACT");
        // 未設定・未知の値は 2.0 と同じく末尾一致
        assertThat(ProxyHostMatcher.normalizeJudgeMethod(null)).isEqualTo("SUFFIX");
        assertThat(ProxyHostMatcher.normalizeJudgeMethod("")).isEqualTo("SUFFIX");
        assertThat(ProxyHostMatcher.normalizeJudgeMethod("unknown")).isEqualTo("SUFFIX");
    }

    @Test
    void returnsFalseWhenEitherSideIsMissing() {
        assertThat(ProxyHostMatcher.matches(null, "youtube.com", "SUFFIX")).isFalse();
        assertThat(ProxyHostMatcher.matches("youtube.com", null, "SUFFIX")).isFalse();
        assertThat(ProxyHostMatcher.matches("  ", "youtube.com", "SUFFIX")).isFalse();
        assertThat(ProxyHostMatcher.matches("youtube.com", "", "SUFFIX")).isFalse();
    }

    @Test
    void parsesHostPort() {
        assertThat(ProxyHostMatcher.parseHostPort("example.com:8080"))
                .isEqualTo(new ProxyHostMatcher.HostPort("example.com", 8080));
        assertThat(ProxyHostMatcher.parseHostPort("example.com"))
                .isEqualTo(new ProxyHostMatcher.HostPort("example.com", null));
        assertThat(ProxyHostMatcher.parseHostPort("[::1]:443"))
                .isEqualTo(new ProxyHostMatcher.HostPort("::1", 443));
        assertThat(ProxyHostMatcher.parseHostPort("[2001:db8::1]:8443"))
                .isEqualTo(new ProxyHostMatcher.HostPort("2001:db8::1", 8443));
        // 範囲外のポートは null（2.0 の parsePort と同じ）
        assertThat(ProxyHostMatcher.parseHostPort("example.com:70000").port()).isNull();
        assertThat(ProxyHostMatcher.parseHostPort("example.com:0").port()).isNull();
        assertThat(ProxyHostMatcher.parseHostPort("example.com:abc").port()).isNull();
        assertThat(ProxyHostMatcher.parseHostPort("   "))
                .isEqualTo(new ProxyHostMatcher.HostPort(null, null));
        assertThat(ProxyHostMatcher.parseHostPort(null))
                .isEqualTo(new ProxyHostMatcher.HostPort(null, null));
    }

    @Test
    void parsesPortValue() {
        assertThat(ProxyHostMatcher.parsePort(" 8888 ")).isEqualTo(8888);
        assertThat(ProxyHostMatcher.parsePort("0")).isNull();
        assertThat(ProxyHostMatcher.parsePort("65536")).isNull();
        assertThat(ProxyHostMatcher.parsePort("")).isNull();
    }

    @Test
    void extractsCandidatesForTheRealApprovedSiteRows() {
        // 実データの例（NET_サイト情報）: 192.168.0.100 / EXACT と dmm-eikaiwa / PREFIX
        List<String> candidates = ProxyHostMatcher.extractCandidateHosts("192.168.0.100", "192.168.0.100");
        assertThat(candidates).containsExactly("192.168.0.100");
        assertThat(ProxyHostMatcher.matches("192.168.0.100", candidates.get(0), "EXACT")).isTrue();

        List<String> prefixCandidates = ProxyHostMatcher.extractCandidateHosts("dmm-eikaiwa", "dmm-eikaiwa");
        assertThat(ProxyHostMatcher.matches("dmm-eikaiwa.example.com", prefixCandidates.get(0), "PREFIX")).isTrue();
    }
}
