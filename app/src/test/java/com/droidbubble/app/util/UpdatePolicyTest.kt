package com.droidbubble.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    @Test
    fun upstreamChecksFollowTheBuildFlag() {
        assertFalse(UpdatePolicy.shouldCheckUpstream(false))
        assertTrue(UpdatePolicy.shouldCheckUpstream(true))
    }
}
