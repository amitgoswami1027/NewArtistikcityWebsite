package com.artistikcity.support;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbErrorsTest {

    /** Shaped like Connector/J's com.mysql.cj.exceptions.CJException (unchecked, getSQLState / getVendorCode). */
    public static class DriverStyleException extends RuntimeException {
        private final String state;
        private final int code;

        DriverStyleException(String message, String state, int code) {
            super(message);
            this.state = state;
            this.code = code;
        }

        public String getSQLState() {
            return state;
        }

        public int getVendorCode() {
            return code;
        }
    }

    @Test
    void recognisesDeadlocksWhateverWrapsThem() {
        assertTrue(DbErrors.isConcurrencyConflict(new DriverStyleException("Deadlock found when trying to get lock", "40001", 1213)));
        assertTrue(DbErrors.isConcurrencyConflict(new DriverStyleException("Lock wait timeout exceeded", "HY000", 1205)));
        assertTrue(DbErrors.isConcurrencyConflict(new CannotAcquireLockException("x")));
        assertTrue(DbErrors.isConcurrencyConflict(new RuntimeException("wrapped", new SQLTransactionRollbackException("Deadlock", "40001", 1213))));
        assertFalse(DbErrors.isConcurrencyConflict(new DriverStyleException("Unknown column 'x'", "42S22", 1054)));
        assertFalse(DbErrors.isConcurrencyConflict(new IllegalStateException("nothing to do with the database")));
    }

    @Test
    void recognisesDuplicateKeys() {
        assertTrue(DbErrors.isDuplicateKey(new DriverStyleException("Duplicate entry '5' for key 'cart_reservations_painting_unique'", "23000", 1062)));
        assertTrue(DbErrors.isDuplicateKey(new DuplicateKeyException("x")));
        assertTrue(DbErrors.isDuplicateKey(new RuntimeException(new SQLException("Duplicate entry", "23000", 1062))));
        assertFalse(DbErrors.isDuplicateKey(new DriverStyleException("Cannot be null", "23000", 1048)));
    }

    @Test
    void survivesCauseLoops() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertFalse(DbErrors.isConcurrencyConflict(a));
    }
}
