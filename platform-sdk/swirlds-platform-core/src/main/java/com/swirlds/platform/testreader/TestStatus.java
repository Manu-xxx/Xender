package com.swirlds.platform.testreader;

/**
 * The result of a JRS test run.
 */
public enum TestStatus {
    /**
     * The test status.
     */
    PASS,
    /**
     * The test failed.
     */
    FAIL,
    /**
     * The test status is could not be determined.
     * This is either old test data without proper marker files,
     * or a test runner failure.
     */
    UNKNOWN
}
