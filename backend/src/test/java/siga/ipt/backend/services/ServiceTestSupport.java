package siga.ipt.backend.services;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import siga.ipt.backend.mappers.AcademicMapper;

@ExtendWith(MockitoExtension.class)
abstract class ServiceTestSupport {

    @Spy
    protected AcademicMapper mapper = new AcademicMapper();

    @Mock
    protected CurrentUserService currentUser;
}
