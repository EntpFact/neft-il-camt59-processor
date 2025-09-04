package com.hdfcbank.camt59.exception;

import com.hdfcbank.camt59.model.Fault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NILExceptionTest {

    @Test
    void testConstructorWithMessage() {
        String message = "Test error message";
        NILException exception = new NILException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getErrors());
    }

    @Test
    void testDefaultConstructor() {
        NILException exception = new NILException();

        assertNull(exception.getMessage());
        assertNull(exception.getErrors());
    }

    @Test
    void testConstructorWithMessageAndCause() {
        String message = "Wrapped exception";
        Throwable cause = new RuntimeException("Root cause");

        NILException exception = new NILException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }



}
