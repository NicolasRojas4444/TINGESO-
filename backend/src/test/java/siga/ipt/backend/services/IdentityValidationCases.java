package siga.ipt.backend.services;

import java.util.stream.Stream;
import siga.ipt.backend.dtos.UserDto;

final class IdentityValidationCases {

    private IdentityValidationCases() {}

    static Stream<UserDto> invalidUsers() {
        return Stream.of(null,
                user("bad-run", "Name", "a@example.test", "subject"),
                user("12345678-5", null, "a@example.test", "subject"),
                user("12345678-5", "Name", "not-an-email", "subject"),
                user("12345678-5", "Name", "a@example.test", null));
    }

    static UserDto validUser() {
        return user(" 12.345.678-5 ", " Test Person ", " PERSON@EXAMPLE.TEST ", " subject ");
    }

    private static UserDto user(String run, String name, String email, String subject) {
        return new UserDto(null, subject, run, name, email);
    }
}
