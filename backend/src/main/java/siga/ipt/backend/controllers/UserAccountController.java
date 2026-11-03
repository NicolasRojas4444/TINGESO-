package siga.ipt.backend.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import siga.ipt.backend.dtos.UserRegistrationRequest;
import siga.ipt.backend.dtos.UserRegistrationResponse;
import siga.ipt.backend.services.UserRegistrationService;
import siga.ipt.backend.dtos.UserDto;
import siga.ipt.backend.dtos.UserProfileRecoveryRequest;
import siga.ipt.backend.dtos.UserProfileRecoveryResponse;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import siga.ipt.backend.services.UserRecoveryService;

@RestController
@Tag(name = "Account profiles", description = "ADMIN-only account creation in Keycloak and recovery/linking of academic profiles.")
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserRecoveryService recoveryService;
    private final UserRegistrationService registrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a student or teacher account",
            description = "ADMIN only. Creates the account and STUDENT/TEACHER role in Keycloak and the linked academic profile. Passwords are never stored in the application database. Public registration may remain disabled.")
    public UserRegistrationResponse create(@RequestBody UserRegistrationRequest request) {
        return registrationService.registerByAdmin(request);
    }

    @GetMapping("/suspended")
    public List<UserDto> getSuspendedUsers() {
        return recoveryService.getSuspendedUsers();
    }

    @PostMapping("/{id}/restore-profile")
    public UserProfileRecoveryResponse restoreProfile(
            @PathVariable Long id,
            @RequestBody UserProfileRecoveryRequest request
    ) {
        return recoveryService.restoreProfile(id, request);
    }

    @GetMapping("/link-candidates")
    public List<UserDto> getLinkCandidates(@RequestParam SelfRegistrationRole role) {
        return recoveryService.getLinkCandidates(role);
    }

    @PostMapping("/{id}/link-profile")
    public UserProfileRecoveryResponse linkProfile(
            @PathVariable Long id,
            @RequestBody UserProfileRecoveryRequest request
    ) {
        return recoveryService.linkProfile(id, request);
    }
}
