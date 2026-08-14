package com.dai.flipper.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDiagnosticsReportTest {

    @Test
    fun `idle report indicates no diagnostics`() {
        val report = ConnectionDiagnosticsReport.idle()

        assertEquals("No link diagnostics run yet.", report.summary)
        assertEquals(0, report.checks.size)
        assertFalse(report.hasFailures)
        assertFalse(report.hasWarnings)
    }

    @Test
    fun `report summary indicates failures when any check fails`() {
        val report = ConnectionDiagnosticsReport(
            startedAtMs = 1000L,
            completedAtMs = 1200L,
            checks = listOf(
                ConnectionCheckResult("BLE Transport", ConnectionCheckLevel.PASS, "ok"),
                ConnectionCheckResult("RPC Ping", ConnectionCheckLevel.FAIL, "timeout")
            )
        )

        assertTrue(report.hasFailures)
        assertFalse(report.hasWarnings)
        assertEquals(200L, report.durationMs)
        assertEquals("Link diagnostics found blocking failures (1/2).", report.summary)
    }

    @Test
    fun `report summary indicates warning state without failures`() {
        val report = ConnectionDiagnosticsReport(
            checks = listOf(
                ConnectionCheckResult("CLI Probe", ConnectionCheckLevel.PASS, "ok"),
                ConnectionCheckResult("RPC Ping", ConnectionCheckLevel.WARN, "not confirmed"),
                ConnectionCheckResult("CLI Echo", ConnectionCheckLevel.SKIPPED, "rpc only")
            )
        )

        assertFalse(report.hasFailures)
        assertTrue(report.hasWarnings)
        assertEquals("Link diagnostics found partial readiness (1 warnings).", report.summary)
    }
}

