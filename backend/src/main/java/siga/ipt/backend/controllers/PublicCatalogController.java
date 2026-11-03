package siga.ipt.backend.controllers;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import siga.ipt.backend.dtos.PublicDegreeProgramDto;
import siga.ipt.backend.services.DegreeProgramService;

@RestController
@SecurityRequirements
@Tag(name = "Public catalog", description = "Unauthenticated registration options: active programs with a current study plan. No private academic or identity data is returned.")
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCatalogController {

    private final DegreeProgramService degreeProgramService;

    @GetMapping("/degree-programs")
    public List<PublicDegreeProgramDto> getRegistrationOptions() {
        return degreeProgramService.getRegistrationOptions();
    }
}
