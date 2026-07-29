package net.martinhenkel.hoernix.profil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilPruefungTest {

    private fun gueltig(ergebnis: PruefErgebnis): Profil {
        assertTrue("erwartet Gueltig, war: $ergebnis", ergebnis is PruefErgebnis.Gueltig)
        return (ergebnis as PruefErgebnis.Gueltig).profil
    }

    private fun abgewiesen(ergebnis: PruefErgebnis): String {
        assertTrue("erwartet Abgewiesen, war: $ergebnis", ergebnis is PruefErgebnis.Abgewiesen)
        return (ergebnis as PruefErgebnis.Abgewiesen).grund
    }

    @Test
    fun exportUndImportErhaltenDasProfil() {
        val profil = Profil(
            name = "Alltag",
            links = OhrEinstellung(
                baenderDb = List(11) { it - 5.0f },
                kompressionAktiv = true,
                grenzFrequenzHz = 2000.0f,
                verhaeltnis = 2.5f,
            ),
            gekoppelt = true,
            schwelleDb = -18.0f,
            audiogrammLinksDb = List(11) { 40.0f },
        )
        val zurueck = gueltig(ProfilPruefung.vonJson(ProfilPruefung.nachJson(profil)))
        assertEquals(profil, zurueck)
    }

    @Test
    fun unbekannteSchluesselWerdenAbgewiesen() {
        val text = ProfilPruefung.nachJson(Profil("A"))
            .replace("\"name\"", "\"schadcode\": true, \"name\"")
        abgewiesen(ProfilPruefung.vonJson(text))
    }

    @Test
    fun kaputtesJsonWirdAbgewiesen() {
        abgewiesen(ProfilPruefung.vonJson("{ kein json"))
    }

    @Test
    fun ueberlangeDateiWirdAbgewiesen() {
        val text = " ".repeat(ProfilGrenzen.IMPORT_MAX_BYTES + 1)
        assertEquals("Datei größer als 64 KiB", abgewiesen(ProfilPruefung.vonJson(text)))
    }

    @Test
    fun falscheBandanzahlWirdAbgewiesen() {
        val profil = Profil("A", links = OhrEinstellung(baenderDb = List(10) { 0.0f }))
        abgewiesen(ProfilPruefung.pruefeUndKlemme(profil))
    }

    @Test
    fun nichtEndlicheWerteWerdenAbgewiesen() {
        val nan = Profil("A", links = OhrEinstellung(baenderDb = List(11) { Float.NaN }))
        abgewiesen(ProfilPruefung.pruefeUndKlemme(nan))
        val unendlich = Profil("A", schwelleDb = Float.POSITIVE_INFINITY)
        abgewiesen(ProfilPruefung.pruefeUndKlemme(unendlich))
    }

    @Test
    fun pegelwerteWerdenGeklemmt() {
        val profil = Profil(
            name = "A",
            links = OhrEinstellung(baenderDb = List(11) { 99.0f }),
            rechts = OhrEinstellung(baenderDb = List(11) { -99.0f }),
            schwelleDb = 0.0f,
        )
        val geklemmt = gueltig(ProfilPruefung.pruefeUndKlemme(profil))
        assertTrue(geklemmt.links.baenderDb.all { it == ProfilGrenzen.BAND_MAX_DB })
        assertTrue(geklemmt.rechts.baenderDb.all { it == ProfilGrenzen.BAND_MIN_DB })
        assertEquals(ProfilGrenzen.SCHWELLE_MAX_DB, geklemmt.schwelleDb, 0.0f)
    }

    @Test
    fun leererNameWirdAbgewiesen() {
        abgewiesen(ProfilPruefung.pruefeUndKlemme(Profil("   ")))
        abgewiesen(ProfilPruefung.pruefeUndKlemme(Profil("x".repeat(65))))
    }

    @Test
    fun verschiebungsBereicheWerdenGeprueft() {
        // Grenzfrequenz unterhalb des zulässigen Bereichs.
        abgewiesen(
            ProfilPruefung.pruefeUndKlemme(
                Profil("A", links = OhrEinstellung(grenzFrequenzHz = 50.0f))
            )
        )
        // Quellbereich verkehrt herum.
        abgewiesen(
            ProfilPruefung.pruefeUndKlemme(
                Profil("A", links = OhrEinstellung(quelleVonHz = 8000.0f, quelleBisHz = 6000.0f))
            )
        )
        // Versatz größer als Quellbeginn (Ziel läge unter 0 Hz).
        abgewiesen(
            ProfilPruefung.pruefeUndKlemme(
                Profil("A", links = OhrEinstellung(versatzHz = 7000.0f))
            )
        )
        // Verhältnis außerhalb.
        abgewiesen(
            ProfilPruefung.pruefeUndKlemme(
                Profil("A", rechts = OhrEinstellung(verhaeltnis = 0.5f))
            )
        )
    }

    @Test
    fun audiogrammWirdGeprueft() {
        abgewiesen(
            ProfilPruefung.pruefeUndKlemme(
                Profil("A", audiogrammLinksDb = List(3) { 40.0f })
            )
        )
        abgewiesen(
            ProfilPruefung.pruefeUndKlemme(
                Profil("A", audiogrammRechtsDb = List(11) { 200.0f })
            )
        )
        gueltig(
            ProfilPruefung.pruefeUndKlemme(
                Profil("A", audiogrammLinksDb = List(11) { 40.0f })
            )
        )
    }

    @Test
    fun fehlendeOptionaleFelderErhaltenVoreinstellungen() {
        val profil = gueltig(ProfilPruefung.vonJson("""{ "name": "Minimal" }"""))
        assertEquals(Profil("Minimal"), profil)
        assertEquals(-12.0f, profil.schwelleDb, 0.0f)
    }
}
