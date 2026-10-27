package siga.ipt.backend.entitys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import siga.ipt.backend.entitys.enums.PlanStatus;

@Entity
@Table(name = "study_plans", uniqueConstraints = @UniqueConstraint(
        columnNames = {"degree_program_id", "plan_version"}))
@Getter
@Setter
@NoArgsConstructor
public class StudyPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "degree_program_id", nullable = false)
    private DegreeProgram degreeProgram;

    @Column(name = "plan_version", nullable = false, length = 30)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status;
}
