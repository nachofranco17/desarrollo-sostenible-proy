package uy.edu.um.xperience.security;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.core.JacksonException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Validator;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import uy.edu.um.xperience.account.LoginRequest;

/** Only adapts request decoding. Spring still runs session strategies, context
 * persistence, authentication events and success/failure handlers in the base filter.
 */
public final class JsonLoginAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    private final ObjectReader inputReader;
    private final Validator validator;

    public JsonLoginAuthenticationFilter(ObjectMapper json, Validator validator) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"));
        inputReader = json.readerFor(LoginRequest.class)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.validator = validator;
    }

    @Override public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        LoginRequest input;
        try {
            if (request.getContentType() == null
                    || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))) {
                throw invalid();
            }
            // Do not use getParameter(), readValue(String), a request-body cache or a JsonNode.
            input = inputReader.readValue(request.getInputStream());
        } catch (JacksonException | IOException | IllegalArgumentException invalidInput) {
            // Never attach a parser exception that might include submitted values.
            throw invalid();
        }
        try (input) {
            if (input == null || !validator.validate(input).isEmpty()) throw invalid();
            var token = UsernamePasswordAuthenticationToken.unauthenticated(input.correo(), input.password());
            token.setDetails(authenticationDetailsSource.buildDetails(request));
            try {
                return getAuthenticationManager().authenticate(token);
            } finally {
                token.eraseCredentials();
            }
        }
    }

    private static BadCredentialsException invalid() {
        return new BadCredentialsException("Credenciales inválidas");
    }
}
