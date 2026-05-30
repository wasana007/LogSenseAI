package com.logai.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String EMAIL = "user@example.com";
    private static final String NAME = "Test User";
    private static final long EXPIRATION = 3600_000L;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-must-be-long-enough-for-hs256");
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION);
    }

    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("returns non-null token")
        void returnsNonNullToken() {
            String token = jwtUtil.generateToken(EMAIL, NAME);
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("returns a JWT with three parts")
        void returnsWellFormedJwt() {
            String token = jwtUtil.generateToken(EMAIL, NAME);
            assertThat(token.split("\\.")).hasSize(3);
        }
    }

    @Nested
    @DisplayName("extractEmail()")
    class ExtractEmail {

        @Test
        @DisplayName("extracts email from valid token")
        void extractsEmailFromToken() {
            String token = jwtUtil.generateToken(EMAIL, NAME);
            assertThat(jwtUtil.extractEmail(token)).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("extracts correct email for different users")
        void extractsCorrectEmailForDifferentUsers() {
            String token = jwtUtil.generateToken("other@example.com", "Other User");
            assertThat(jwtUtil.extractEmail(token)).isEqualTo("other@example.com");
        }
    }

    @Nested
    @DisplayName("isValid()")
    class IsValid {

        @Test
        @DisplayName("returns true for a valid token")
        void returnsTrueForValidToken() {
            String token = jwtUtil.generateToken(EMAIL, NAME);
            assertThat(jwtUtil.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("returns false for a tampered token")
        void returnsFalseForTamperedToken() {
            String token = jwtUtil.generateToken(EMAIL, NAME);
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";
            assertThat(jwtUtil.isValid(tampered)).isFalse();
        }

        @Test
        @DisplayName("returns false for a random string")
        void returnsFalseForRandomString() {
            assertThat(jwtUtil.isValid("not.a.token")).isFalse();
        }

        @Test
        @DisplayName("returns false for empty string")
        void returnsFalseForEmptyString() {
            assertThat(jwtUtil.isValid("")).isFalse();
        }

        @Test
        @DisplayName("returns false for expired token")
        void returnsFalseForExpiredToken() {
            ReflectionTestUtils.setField(jwtUtil, "expiration", -1000L);
            String token = jwtUtil.generateToken(EMAIL, NAME);

            // reset to normal expiration so only expiry is being tested
            ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION);

            assertThat(jwtUtil.isValid(token)).isFalse();
        }

        @Test
        @DisplayName("returns false for token signed with different secret")
        void returnsFalseForTokenSignedWithDifferentSecret() {
            JwtUtil otherJwtUtil = new JwtUtil();
            ReflectionTestUtils.setField(otherJwtUtil, "secret", "other-secret-key-must-be-long-enough-ok");
            ReflectionTestUtils.setField(otherJwtUtil, "expiration", EXPIRATION);

            String tokenFromOther = otherJwtUtil.generateToken(EMAIL, NAME);

            assertThat(jwtUtil.isValid(tokenFromOther)).isFalse();
        }
    }

    @Nested
    @DisplayName("round-trip")
    class RoundTrip {

        @Test
        @DisplayName("generated token is valid and contains correct email")
        void generatedTokenIsValidAndContainsEmail() {
            String token = jwtUtil.generateToken(EMAIL, NAME);

            assertThat(jwtUtil.isValid(token)).isTrue();
            assertThat(jwtUtil.extractEmail(token)).isEqualTo(EMAIL);
        }
    }
}