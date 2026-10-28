package siga.ipt.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.repositories.*;

@Service
@RequiredArgsConstructor
public class UserCleanupService {
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final AcademicPeriodRepository periods;
    private final UserRepository users;

    @Transactional
    public void deleteIfUnreferenced(User user) {
        if (!students.existsByUserId(user.getId()) && !teachers.existsByUserId(user.getId())
                && !periods.existsByClosedById(user.getId())) {
            users.delete(user);
        }
    }
}
