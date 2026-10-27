package siga.ipt.backend.entitys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import siga.ipt.backend.entitys.enums.ActiveStatus;

@Entity
@Table(name = "degree_programs")
@Getter
@Setter
@NoArgsConstructor
public class DegreeProgram extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "duration_semesters", nullable = false)
    private Integer durationSemesters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActiveStatus status;
}
