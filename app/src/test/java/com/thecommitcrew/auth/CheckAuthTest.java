package com.thecommitcrew.auth;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(retention.value() == RetentionPolicy.RUNTIME);
    }

    @Test
    void annotation_targetsMethodsOnly() {
        Target target = CheckAuth.class.getAnnotation(Target.class);

        assertNotNull(target);
        assertArrayEquals(new ElementType[]{ElementType.METHOD}, target.value());
    }

    @Test
    void annotation_hasNoDeclaredAttributes() {
        assertTrue(CheckAuth.class.getDeclaredMethods().length == 0);
    }

    private static class TestController {

        @CheckAuth
        void securedMethod() {
        }

        void publicMethod() {
        }
    }
}