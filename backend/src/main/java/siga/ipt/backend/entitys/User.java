package siga.ipt.backend.entitys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(name = "keycloak_subject", nullable = false, unique = true, length = 255)
    private String keycloakSubject;

    @Column(nullable = false, unique = true, length = 10)
    private String run;

    @Column(nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean accessSuspended;

    private Instant tokensValidAfter;
}
