package siga.ipt.backend.entitys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "courses", uniqueConstraints = @UniqueConstraint(
        columnNames = {"study_plan_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Course extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_plan_id", nullable = false)
    private StudyPlan studyPlan;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private Integer semester;

    @Column(name = "theory_hours", nullable = false)
    private Integer theoryHours;

    @Column(name = "exercise_hours", nullable = false)
    private Integer exerciseHours;

    @Column(name = "laboratory_hours", nullable = false)
    private Integer laboratoryHours;

    @Column(nullable = false)
    private Integer sct;
}
