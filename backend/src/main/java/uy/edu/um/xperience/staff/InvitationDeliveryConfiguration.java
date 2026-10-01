package uy.edu.um.xperience.staff;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class InvitationDeliveryConfiguration {
    private static String message(String baseUrl, String token) {
        return "Te invitaron al staff de una empresa en XPerience.\n"
            + "Aceptá la invitación dentro de las próximas 48 horas:\n"
            + baseUrl + "/invitacion#" + token
            + "\nDespués de aceptarla, un administrador debe asignarte un rol para que puedas ingresar.\n";
    }
    @Bean @Profile("dev")
    InvitationDelivery localInvitations(@Value("${app.invitations.base-url}") String baseUrl,
            @Value("${app.invitations.directory:./.data/invitaciones}") String directory) {
        return (email, token) -> {
            try {
                Path folder = Path.of(directory);
                Files.createDirectories(folder);
                Files.writeString(folder.resolve(UUID.randomUUID() + ".txt"),
                    "Para: " + email + "\n" + message(baseUrl, token), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            } catch (IOException error) {
                throw unavailable();
            }
        };
    }
    @Bean @Profile("!dev")
    InvitationDelivery smtpInvitations(ObjectProvider<JavaMailSender> senders,
            @Value("${app.invitations.base-url}") String baseUrl,
            @Value("${app.invitations.from:}") String from) {
        return (email, token) -> {
            JavaMailSender sender = senders.getIfAvailable();
            if (sender == null || from.isBlank()) throw unavailable();
            var mail = new SimpleMailMessage();
            mail.setFrom(from); mail.setTo(email); mail.setSubject("Invitación a XPerience");
            mail.setText(message(baseUrl, token));
            try { sender.send(mail); }
            catch (org.springframework.mail.MailException error) { throw unavailable(); }
        };
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar la invitación. Intentá nuevamente.");
    }
}
