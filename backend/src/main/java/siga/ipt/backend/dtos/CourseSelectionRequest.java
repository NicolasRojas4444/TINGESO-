package siga.ipt.backend.dtos;

import java.util.List;

public record CourseSelectionRequest(List<Long> sectionIds) {
}
