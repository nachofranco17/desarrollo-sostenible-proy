package uy.edu.um.xperience.security;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.ServletException;
import jakarta.validation.Validation;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import uy.edu.um.xperience.account.*;
import uy.edu.um.xperience.profile.*;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.staff.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordHandlingTest {
    private final ObjectMapper json = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final CharArrayPbkdf2PasswordEncoder passwords = new CharArrayPbkdf2PasswordEncoder();

    @ParameterizedTest
    @ValueSource(strings = {"una frase suficientemente larga", "clave con ñ y 🔐", "  espacios sin recortar  ", ""})
    void hashesAreCompatibleInBothDirectionsWithThePreviousEncoder(String fixture) {
        var previous = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        char[] value = fixture.toCharArray();
        try {
            String oldHash = previous.encode(fixture);
            String newHash = passwords.encode(Passwords.view(value));
            assertThat(passwords.matches(Passwords.view(value), oldHash)).isTrue();
            assertThat(previous.matches(fixture, newHash)).isTrue();
            assertThat(passwords.matches("incorrecta", oldHash)).isFalse();
            assertThat(newHash).hasSize(96).isNotEqualTo(passwords.encode(Passwords.view(value)));
            assertThat(value).containsExactly(fixture.toCharArray());
        } finally { Passwords.clear(value); }
    }

    @Test void hashingNeverInvokesToStringOnPlaintext() {
        char[] value = "una frase suficientemente larga".toCharArray();
        CharSequence sequence = new CharSequence() {
            @Override public int length() { return value.length; }
            @Override public char charAt(int index) { return value[index]; }
            @Override public CharSequence subSequence(int start, int end) { return CharBuffer.wrap(value, start, end - start); }
            @Override public String toString() { throw new AssertionError("Plaintext copied to String"); }
        };
        try {
            assertThat(passwords.matches(sequence, passwords.encode(sequence))).isTrue();
        } finally { Passwords.clear(value); }
    }

    @ParameterizedTest @ValueSource(strings = {"", "not-a-hash", "gg", "f"})
    void invalidStoredHashesFailClosed(String hash) {
        assertThat(passwords.matches("irrelevante", hash)).isFalse();
        assertThat(passwords.matches("irrelevante", null)).isFalse();
    }

    @Test void jsonPasswordsUseTheCharacterBufferAndKeepTheExistingWireFormat() throws Exception {
        JsonParser parser = mock(JsonParser.class);
        when(parser.hasToken(JsonToken.VALUE_STRING)).thenReturn(true);
        when(parser.getTextCharacters()).thenReturn("xxsecretzz".toCharArray());
        when(parser.getTextOffset()).thenReturn(2);
        when(parser.getTextLength()).thenReturn(6);
        char[] value = new PasswordDeserializer().deserialize(parser, null);
        try { assertThat(value).containsExactly('s', 'e', 'c', 'r', 'e', 't'); }
        finally { Passwords.clear(value); }
        verify(parser, never()).getText();

        try (var input = json.readValue("{\"correo\":\"a@example.test\",\"nombre\":\"Ana\",\"apellido\":\"Test\",\"password\":\"clave \\u00f1 \\uD83D\\uDD10\"}", RegisterRequest.class)) {
            assertThat(input.password()).containsExactly("clave ñ 🔐".toCharArray());
            assertThat(json.readTree(json.writeValueAsBytes(input)).path("password").isTextual()).isTrue();
            assertThat(input.toString()).doesNotContain("clave");
        }
    }

    @Test void requestCleanupErasesBindingValuesEvenWhenNoServiceWasCalled() throws Exception {
        var filter = new PasswordCleanupFilter();
        AtomicReference<char[]> retainedByBinding = new AtomicReference<>();
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            var input = json.readValue("{\"password\":\"short\"}", RegisterRequest.class);
            retainedByBinding.set(input.password());
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                assertThat(factory.getValidator().validate(input)).isNotEmpty();
            }
            throw new ServletException("validation failed before controller");
        })).isInstanceOf(ServletException.class);
        assertErased(retainedByBinding.get());
        // No request list survives on the worker thread after the filter returns.
        char[] outsideRequest = "outside".toCharArray();
        assertThat(PasswordCleanupFilter.track(outsideRequest)).isSameAs(outsideRequest);
        Passwords.clear(outsideRequest);
    }

    @Test void requestCleanupErasesArraysOrphanedByMalformedJson() throws Exception {
        char[] allocated = "una frase suficientemente larga".toCharArray();
        var parser = mock(JsonParser.class);
        when(parser.hasToken(JsonToken.VALUE_STRING)).thenReturn(true);
        when(parser.getTextCharacters()).thenReturn(allocated);
        when(parser.getTextLength()).thenReturn(allocated.length);
        AtomicReference<char[]> orphan = new AtomicReference<>();
        var filter = new PasswordCleanupFilter();
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            orphan.set(new PasswordDeserializer().deserialize(parser, null));
            json.readValue("{\"password\":", RegisterRequest.class);
        })).isInstanceOf(IOException.class);
        assertErased(orphan.get());
        Passwords.clear(allocated);
    }

    @Test void serviceFailuresErasePasswordsBeforeReturningToTheirCallers() {
        var register = new RegisterRequest("a@example.test", "Ana", "Test", "una frase suficientemente larga".toCharArray());
        var policy = mock(EvaluadorPolitica.class);
        when(policy.camposEscribibles(any(), anyString(), any())).thenThrow(new IllegalStateException("policy failed"));
        var registration = new RegistrationService(mock(AccountRepository.class), mock(ProfileRepository.class), passwords, policy);
        assertThatThrownBy(() -> registration.register(Sujeto.visitante(), register)).isInstanceOf(IllegalStateException.class);
        assertErased(register.password());

        var accept = new StaffInvitationController.AcceptRequest("Ana", "Test", "una frase suficientemente larga".toCharArray());
        var memberships = mock(uy.edu.um.xperience.persistence.Membresias.class);
        var tokens = mock(InvitationTokens.class);
        when(tokens.filter(anyString())).thenThrow(new IllegalStateException("token failed"));
        var invitations = new StaffInvitationService(null, memberships, null, tokens, null, passwords);
        assertThatThrownBy(() -> invitations.accept("token", accept)).isInstanceOf(IllegalStateException.class);
        assertErased(accept.password());

        var reauthInput = new ReauthenticationController.Input("una frase suficientemente larga".toCharArray());
        var subjects = mock(Sujetos.class);
        when(subjects.current()).thenThrow(new IllegalStateException("subject failed"));
        var reauth = new ReauthenticationController(subjects, null, null, passwords, mock(Reautenticacion.class));
        assertThatThrownBy(() -> reauth.confirm(reauthInput)).isInstanceOf(IllegalStateException.class);
        assertErased(reauthInput.password());

        var admin = new RegisterRequest("a@example.test", "Ana", "Test", "una frase suficientemente larga".toCharArray());
        var provisioner = new CompanyProvisioner(null, null, null, null, passwords, null);
        assertThatThrownBy(() -> provisioner.create(null, admin, "tests")).isInstanceOf(IllegalArgumentException.class);
        assertErased(admin.password());
    }

    @Test void profilePasswordChangesAreMutableAndErasedOnEarlyServiceFailure() throws Exception {
        try (ProfileUpdate input = json.readValue("{\"passwordActual\":\"una frase suficientemente larga\",\"passwordNueva\":\"otra frase suficientemente larga\",\"telefono\":null}", ProfileUpdate.class)) {
            char[] actual = input.passwordActual().orElse(null);
            char[] nueva = input.passwordNueva().orElse(null);
            assertThat(input.telefono()).isInstanceOf(FieldChange.Clear.class);
            assertThat(input.toString()).doesNotContain("frase");
            var subjects = mock(Sujetos.class);
            when(subjects.current()).thenThrow(new IllegalStateException("subject failed"));
            var profiles = new ProfileService(null, null, null, passwords, subjects, null);
            assertThatThrownBy(() -> profiles.updateOwn(input)).isInstanceOf(IllegalStateException.class);
            assertErased(actual);
            assertErased(nueva);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{\"passwordActual\":null}", "{\"passwordNueva\":123}",
        "{\"passwordNueva\":[\"a\"]}", "{\"passwordNueva\":\"short\"}", "{\"rol\":\"ADMIN\"}"})
    void profileRejectsInvalidPasswordTypesAndForbiddenFields(String body) {
        assertThatThrownBy(() -> json.readValue(body, ProfileUpdate.class)).isInstanceOf(JsonMappingException.class);
    }

    private static void assertErased(char[] value) {
        assertThat(value).isNotNull().containsOnly('\0');
    }
}
