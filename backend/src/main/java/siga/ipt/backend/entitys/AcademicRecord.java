package siga.ipt.backend.entitys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import siga.ipt.backend.entitys.enums.GradeResult;

/** Curriculum and result data consolidated during period closing. */
@Entity
@Table(name = "academic_records")
@Getter
@Setter
@NoArgsConstructor
public class AcademicRecord extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, unique = true)
    private CourseRegistration registration;

    @Column(nullable = false, length = 30)
    private String courseCode;

    @Column(nullable = false, length = 200)
    private String courseName;

    @Column(nullable = false)
    private Integer curriculumSemester;

    @Column(nullable = false)
    private Integer theoryHours;

    @Column(nullable = false)
    private Integer exerciseHours;

    @Column(nullable = false)
    private Integer laboratoryHours;

    @Column(nullable = false)
    private Integer sct;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal finalGrade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GradeResult result;

    @Column(nullable = false)
    private Instant consolidatedAt;
}
