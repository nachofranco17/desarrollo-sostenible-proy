package uy.edu.um.xperience.profile;

import com.fasterxml.jackson.databind.JsonNode;
import uy.edu.um.xperience.security.RequiereAccion;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @RequiereAccion("perfil.gestionar")
    @GetMapping("/me")
    public ProfileView me() {
        return profiles.getOwn();
    }

    @RequiereAccion("perfil.gestionar")
    @PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProfileView update(@RequestBody JsonNode body) {
        return profiles.updateOwn(ProfileUpdateParser.parse(body));
    }
}
