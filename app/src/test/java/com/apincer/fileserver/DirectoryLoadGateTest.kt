package com.apincer.fileserver

import com.apincer.fileserver.ui.browser.DirectoryLoadGate
import org.junit.Assert.*
import org.junit.Test

class DirectoryLoadGateTest {
    @Test fun outdatedDirectoryResultCannotReplaceCurrentListing() {
        val gate = DirectoryLoadGate()
        val older = gate.next()
        val current = gate.next()
        assertFalse(gate.isCurrent(older))
        assertTrue(gate.isCurrent(current))
    }
}
