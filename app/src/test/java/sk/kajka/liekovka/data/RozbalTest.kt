package sk.kajka.liekovka.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Pribalené dáta sa musia dať prečítať zbalené (gzip) aj rozbalené (čistý JSON). */
class RozbalTest {
    private val json = """{"format":1,"verzia":"V1"}"""

    @Test fun gzip() {
        val b = ByteArrayOutputStream()
        GZIPOutputStream(b).use { it.write(json.toByteArray()) }
        assertEquals(json, Repository.rozbal(b.toByteArray().inputStream()).readBytes().decodeToString())
    }

    @Test fun cistyJson() {
        assertEquals(json, Repository.rozbal(json.byteInputStream()).readBytes().decodeToString())
    }
}
