package com.thecommitcrew.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

class CheckAuthTest {

    @Test
    void annotation_isAvailableAtRuntime() throws Exception {
        Method method = TestController.class.getDeclaredMethod("securedMethod");

        CheckAuth annotation = method.getAnnotation(CheckAuth.class);

        assertNotNull(annotation);
    }

    @Test
    void annotation_isAbsentFromUnannotatedMethod() throws Exception {
        Method method = TestController.class.getDeclaredMethod("publicMethod");

        assertFalse(method.isAnnotationPresent(CheckAuth.class));
    }

    @Test
    void annotation_hasRuntimeRetention() {
        Retention retention = CheckAuth.class.getAnnotation(Retention.class);

        assertNotNull(retention);
        assertSame(RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void annotation_targetsMethodsOnly() {
        Target target = CheckAuth.class.getAnnotation(Target.class);

        assertNotNull(target);
        assertArrayEquals(new ElementType[]{ElementType.METHOD}, target.value());
    }

    @Test
    void annotation_hasNoDeclaredAttributes() {
        assertEquals(0, CheckAuth.class.getDeclaredMethods().length);
    }

    private static class TestController {

        @CheckAuth
        void securedMethod() {
            // Intentionally empty: used to test @CheckAuth authorization behavior.
        }

        @SuppressWarnings("unused")
        void publicMethod() {
            // Intentionally empty: used as a control method without @CheckAuth.
        }
    }
}