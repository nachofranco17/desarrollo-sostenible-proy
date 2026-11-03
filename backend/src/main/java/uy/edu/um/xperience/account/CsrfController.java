package uy.edu.um.xperience.account;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.RequiereAccion;

/** Hands out the session's anti-forgery token; the PEP evaluates it like any other endpoint. */
@RestController
@RequestMapping("/api")
public class CsrfController {
    @GetMapping("/auth/csrf")
    @RequiereAccion("sesion.token_csrf")
    public Map<String, String> token(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
}
