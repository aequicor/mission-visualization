package io.aequicor.visualization.subsystems.annotations.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataUriPayloadTest {

    @Test
    fun extractsBase64PayloadFromDataUri() {
        assertEquals("AAAA", dataUriBase64Payload("data:image/png;base64,AAAA"))
    }

    @Test
    fun headerMatchIsCaseInsensitive() {
        assertEquals("AAAA", dataUriBase64Payload("DATA:image/png;BASE64,AAAA"))
    }

    @Test
    fun stripsWhitespaceInsidePayload() {
        assertEquals("AAAABBBB", dataUriBase64Payload("data:image/png;base64,AAAA\nBB BB"))
    }

    @Test
    fun rejectsNonDataUriSources() {
        assertNull(dataUriBase64Payload("assets/screenshot.png"))
        assertNull(dataUriBase64Payload("https://example.com/x.png"))
    }

    @Test
    fun rejectsNonBase64DataUris() {
        assertNull(dataUriBase64Payload("data:image/svg+xml;utf8,<svg></svg>"))
    }

    @Test
    fun rejectsMalformedOrEmptyDataUris() {
        assertNull(dataUriBase64Payload("data:image/png;base64"))
        assertNull(dataUriBase64Payload("data:image/png;base64,"))
    }
}
