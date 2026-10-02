package uy.edu.um.xperience;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import uy.edu.um.xperience.security.RequiereAccion;
import static org.assertj.core.api.Assertions.*;

/** R8: every endpoint declares an action from the documented catalog, so a new feature stays denied until it is added there. */
@SpringBootTest
class EndpointConventionTest {
    // Has no rows in the permission table on purpose: the PEP validates its invitation token instead (see the document).
    private static final Set<String> SPECIAL_ACTIONS = Set.of("invitacion.aceptar");

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping;

    // Exclusion list on purpose: any new handler, even one added by a library, must be reviewed explicitly.
    // Spring Boot's /error is the documented exception: only the internal error dispatch reaches it.
    private boolean isOwnEndpoint(HandlerMethod handler) {
        return !BasicErrorController.class.isAssignableFrom(handler.getBeanType());
    }

    private List<HandlerMethod> ownEndpoints() {
        var endpoints = mapping.getHandlerMethods().values().stream().filter(this::isOwnEndpoint).toList();
        assertThat(endpoints).as("the application must expose endpoints").isNotEmpty();
        return endpoints;
    }

    private static String describe(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
    }

    private static Set<String> documentedActions() throws Exception {
        var actions = new HashSet<String>();
        for (String line : Files.readAllLines(Path.of("../docs/seguridad/modelo-autorizacion.md"))) {
            String[] columns = line.split("\\|", -1);
            if (columns.length != 9 || !columns[1].strip().matches("`[^`]+`")) continue;
            actions.add(columns[1].strip().replace("`", ""));
        }
        assertThat(actions).as("the catalog table must be readable").isNotEmpty();
        return actions;
    }

    @Test void everyEndpointDeclaresTheActionItRequires() {
        assertThat(ownEndpoints())
            .filteredOn(handler -> handler.getMethodAnnotation(RequiereAccion.class) == null)
            .map(EndpointConventionTest::describe)
            .as("endpoints without @RequiereAccion").isEmpty();
    }

    @Test void everyDeclaredActionBelongsToTheDocumentedCatalog() throws Exception {
        var catalog = documentedActions();
        assertThat(ownEndpoints())
            .filteredOn(handler -> handler.getMethodAnnotation(RequiereAccion.class) != null)
            .filteredOn(handler -> {
                String action = handler.getMethodAnnotation(RequiereAccion.class).value();
                return !catalog.contains(action) && !SPECIAL_ACTIONS.contains(action);
            })
            .map(handler -> describe(handler) + " -> " + handler.getMethodAnnotation(RequiereAccion.class).value())
            .as("actions missing from the catalog in modelo-autorizacion.md").isEmpty();
    }
}
