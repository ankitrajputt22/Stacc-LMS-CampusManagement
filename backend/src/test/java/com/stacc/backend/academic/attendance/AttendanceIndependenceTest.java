package com.stacc.backend.academic.attendance;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.academic.attendance.api.FacultyAttendanceController;
import com.stacc.backend.academic.attendance.api.FacultyAttendanceQueryService;
import com.stacc.backend.academic.attendance.api.StudentAttendanceController;
import com.stacc.backend.academic.attendance.api.StudentAttendanceQueryService;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import org.junit.jupiter.api.Test;

/**
 * Attendance is academic data: it must keep working when a course offering has no LMS course. None of its
 * classes may hold, take, or return anything from the LMS module.
 */
class AttendanceIndependenceTest {

    private static final String LMS_PACKAGE = "com.stacc.backend.lms.";

    @Test
    void noAttendanceClassDependsOnTheLms() {
        for (Class<?> type : List.of(AttendanceSession.class, AttendanceRecord.class, AttendanceSessionRepository.class,
                AttendanceRecordRepository.class, AttendanceManagementService.class, FacultyTeachingAccessService.class,
                FacultyAttendanceQueryService.class, StudentAttendanceQueryService.class,
                FacultyAttendanceController.class, StudentAttendanceController.class)) {
            for (Class<?> used : typesUsedBy(type)) {
                assertFalse(used.getName().startsWith(LMS_PACKAGE), type.getSimpleName() + " uses " + used.getName());
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
