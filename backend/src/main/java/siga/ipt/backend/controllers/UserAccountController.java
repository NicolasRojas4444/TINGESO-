package siga.ipt.backend.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import siga.ipt.backend.dtos.UserDto;
import siga.ipt.backend.dtos.UserProfileRecoveryRequest;
import siga.ipt.backend.dtos.UserProfileRecoveryResponse;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import siga.ipt.backend.services.UserRecoveryService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserRecoveryService recoveryService;

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
