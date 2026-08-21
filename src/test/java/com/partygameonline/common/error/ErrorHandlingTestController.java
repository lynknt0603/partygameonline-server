package com.partygameonline.common.error;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/__test")
class ErrorHandlingTestController {

    @PostMapping("/validate")
    void validate(@jakarta.validation.Valid @RequestBody SampleRequest request) {
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("secret internals must not leak");
    }

    @GetMapping("/missing")
    void missing() {
        throw new ResourceNotFoundException("THING_NOT_FOUND", "The thing was not found");
    }

    record SampleRequest(
            @NotBlank @Size(max = 20) String displayName
    ) {
    }
}
