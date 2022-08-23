package edu.oxy.util.xml

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.expect

class SlaxTest {
    @Test
    fun test1() {
        val raw =
            """<cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
              |   <cas:authenticationFailure code="INVALID_TICKET">
              |      Ticket ST-97c833c837d442028b4ed5c51ba5fa18-eis.oxy.edu not recognized.
              |   </cas:authenticationFailure>
              |</cas:serviceResponse>""".trimMargin()
        val root = slax(StringReader(raw))
        expect("cas:serviceResponse") { root.name }
        expect(1) { root.children.size }

        expect(3) { root.filterNodes { true }.size }
        expect(2) { root.filterElements { true }.size }

        expect("Ticket ST-97c833c837d442028b4ed5c51ba5fa18-eis.oxy.edu not recognized.") {
            root.getText().let { ts ->
                expect(1) { ts.size }
                ts[0]
            }
        }
        expect(1) {
            // The full qname is treated as one great big name, thus avoiding the hassle of namespaces.
            root.filterByNameExact("cas:authenticationFailure").size
        }
        expect(1) {
            root.filterByNameRegex(".*authenticationFailure".toRegex()).size
        }
        expect(1) {
            root.filterByNameContains("authenticationFailure").size
        }
    }
}