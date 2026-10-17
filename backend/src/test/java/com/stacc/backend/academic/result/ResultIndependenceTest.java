package com.stacc.backend.academic.result;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.academic.attendance.AttendanceManagementService;
import com.stacc.backend.academic.result.api.FacultyResultController;
import com.stacc.backend.academic.result.api.FacultyResultQueryService;
import com.stacc.backend.academic.result.api.StudentResultController;
import com.stacc.backend.academic.result.api.StudentResultQueryService;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentGradingService;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import org.junit.jupiter.api.Test;

/**
 * Official results are academic data recorded on their own. They must keep working without an LMS course, and
 * neither LMS assignment grades nor attendance may feed them, or be changed by them.
 */
class ResultIndependenceTest {

    private static final List<Class<?>> RESULT_CLASSES = List.of(CourseResult.class, CourseResultRepository.class,
            CourseResultManagementService.class, FacultyResultQueryService.class, StudentResultQueryService.class,
            FacultyResultController.class, StudentResultController.class);

    @Test
    void noResultClassUsesTheLmsOrAttendance() {
        for (Class<?> type : RESULT_CLASSES) {
            for (Class<?> used : typesUsedBy(type)) {
                assertFalse(used.getName().startsWith("com.stacc.backend.lms."), type.getSimpleName() + " uses " + used.getName());
                assertFalse(used.getName().startsWith("com.stacc.backend.academic.attendance."),
                        type.getSimpleName() + " uses " + used.getName());
            }
        }
    }

    @Test
    void lmsGradingAndAttendanceDoNotTouchResults() {
        for (Class<?> type : List.of(LmsSubmissionGrade.class, LmsAssignmentGradingService.class, AttendanceManagementService.class)) {
            for (Class<?> used : typesUsedBy(type)) {
                assertFalse(used.getName().startsWith("com.stacc.backend.academic.result."),
                        type.getSimpleName() + " uses " + used.getName());
            }
        }
    }

    private static List<Class<?>> typesUsedBy(Class<?> type) {
        List<Class<?>> used = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            used.add(field.getType());
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                used.add(parameter.getType());
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            used.add(method.getReturnType());
            for (Parameter parameter : method.getParameters()) {
                used.add(parameter.getType());
            }
        }
        return used;
    }
}
