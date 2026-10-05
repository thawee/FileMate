package com.apincer.fileserver

import com.apincer.fileserver.sharing.incomingBasename
import org.junit.Assert.*
import org.junit.Test

class IncomingBasenameTest {
    @Test fun providerNamesCannotChoosePaths() {
        assertEquals("_etc_passwd", incomingBasename("/etc/passwd", 0))
        assertEquals(".._outside", incomingBasename("../outside", 0))
        assertEquals(".._outside", incomingBasename("..\\outside", 0))
        assertEquals("received-1", incomingBasename("..", 0))
        assertEquals("received-2", incomingBasename(null, 1))
        assertEquals(240, incomingBasename("x".repeat(300), 0).length)
    }
}
