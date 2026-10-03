package uy.edu.um.xperience.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authorization.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

@Component
public class PolicyEnforcementPoint implements AuthorizationManager<RequestAuthorizationContext> {
    private final ObjectProvider<RequestMappingHandlerMapping> mapping;
    private final Sujetos subjects;
    private final EvaluadorPolitica policy;
    private final InvitationTokens invitations;
    public PolicyEnforcementPoint(@Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mapping,
                                  Sujetos subjects, EvaluadorPolitica policy, InvitationTokens invitations) {
        this.mapping = mapping; this.subjects = subjects; this.policy = policy;
        this.invitations = invitations;
    }
    @Override public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        try {
            if (!ServletRequestPathUtils.hasParsedRequestPath(request)) ServletRequestPathUtils.parseAndCache(request);
            var chain = mapping.getObject().getHandler(request);
            if (chain == null || !(chain.getHandler() instanceof HandlerMethod handler)) {
                Denegaciones.marcarRolDenegado(request);
                return new AuthorizationDecision(false);
            }
            var action = handler.getMethodAnnotation(RequiereAccion.class);
            if (action == null) {
                Denegaciones.marcarRolDenegado(request);
                return new AuthorizationDecision(false);
            }
            request.setAttribute(Denegaciones.ACTION, action.value());
            if ("invitacion.aceptar".equals(action.value())) {
                boolean allowed = invitations.valid(request.getHeader(InvitationTokens.HEADER));
                if (!allowed) {
                    Denegaciones.marcarRolDenegado(request);
                }
                return new AuthorizationDecision(allowed);
            }
            boolean allowed = policy.puedeInvocar(subjects.resolve(authentication.get()), action.value());
            if (!allowed) {
                Denegaciones.marcarRolDenegado(request);
            }
            return new AuthorizationDecision(allowed);
        } catch (Exception error) {
            Denegaciones.marcarRolDenegado(request);
            return new AuthorizationDecision(false);
        }
    }
}
