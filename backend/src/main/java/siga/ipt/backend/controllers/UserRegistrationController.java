package siga.ipt.backend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import siga.ipt.backend.dtos.UserRegistrationRequest;
import siga.ipt.backend.dtos.UserRegistrationResponse;
import siga.ipt.backend.services.UserRegistrationService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserRegistrationController {

    private final UserRegistrationService registrationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserRegistrationResponse register(@RequestBody UserRegistrationRequest request) {
        return registrationService.register(request);
    }
}
