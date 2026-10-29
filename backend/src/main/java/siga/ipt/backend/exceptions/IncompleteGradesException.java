package siga.ipt.backend.exceptions;

import java.util.List;

public class IncompleteGradesException extends IllegalStateException {

    private final List<Long> sectionIds;

    public IncompleteGradesException(List<Long> sectionIds) {
        super("Final grades are missing");
        this.sectionIds = List.copyOf(sectionIds);
    }

    public List<Long> getSectionIds() {
        return sectionIds;
    }
}
