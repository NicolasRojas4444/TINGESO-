package siga.ipt.backend.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import siga.ipt.backend.dtos.PublicDegreeProgramDto;
import siga.ipt.backend.services.DegreeProgramService;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCatalogController {

    private final DegreeProgramService degreeProgramService;

    @GetMapping("/degree-programs")
    public List<PublicDegreeProgramDto> getRegistrationOptions() {
        return degreeProgramService.getRegistrationOptions();
    }
}
