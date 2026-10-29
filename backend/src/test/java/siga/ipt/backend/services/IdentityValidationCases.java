package siga.ipt.backend.services;

import java.util.stream.Stream;
import siga.ipt.backend.dtos.UserDto;

final class IdentityValidationCases {

    private IdentityValidationCases() {}

    static Stream<UserDto> invalidUsers() {
        return Stream.of(null,
                user(null, "Name", "a@example.test", "subject"),
                user(" ", "Name", "a@example.test", "subject"),
                user("12345678901-0", "Name", "a@example.test", "subject"),
                user("bad-run", "Name", "a@example.test", "subject"),
                user("12345678-5", null, "a@example.test", "subject"),
                user("12345678-5", " ", "a@example.test", "subject"),
                user("12345678-5", "x".repeat(201), "a@example.test", "subject"),
                user("12345678-5", "Name", null, "subject"),
                user("12345678-5", "Name", " ", "subject"),
                user("12345678-5", "Name", "x".repeat(255), "subject"),
                user("12345678-5", "Name", "not-an-email", "subject"),
                user("12345678-5", "Name", "a@example.test", null),
                user("12345678-5", "Name", "a@example.test", " "),
                user("12345678-5", "Name", "a@example.test", "x".repeat(256)));
    }

    static UserDto validUser() {
        return user(" 12.345.678-5 ", " Test Person ", " PERSON@EXAMPLE.TEST ", " subject ");
    }

    private static UserDto user(String run, String name, String email, String subject) {
        return new UserDto(null, subject, run, name, email);
    }
}
