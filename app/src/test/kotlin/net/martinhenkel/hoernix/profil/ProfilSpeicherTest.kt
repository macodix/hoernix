package net.martinhenkel.hoernix.profil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfilSpeicherTest {

    @get:Rule
    val ordner = TemporaryFolder()

    @Test
    fun ohneDateiKommtDerStandardbestand() {
        val bestand = ProfilSpeicher(ordner.root).lade()
        assertEquals(ProfilBestand.standard(), bestand)
    }

    @Test
    fun speichernUndLadenErhaltenDenBestand() {
        val speicher = ProfilSpeicher(ordner.root)
        val bestand = ProfilBestand(
            aktivName = "Zuhause",
            profile = listOf(Profil("Standard"), Profil("Zuhause", schwelleDb = -20.0f)),
        )
        speicher.speichere(bestand)
        assertEquals(bestand, speicher.lade())
    }

    @Test
    fun defekteDateiWirdGesichertUndErsetzt() {
        val datei = File(ordner.root, "profile.json")
        datei.writeText("{ kaputt")
        val bestand = ProfilSpeicher(ordner.root).lade()
        assertEquals(ProfilBestand.standard(), bestand)
        assertTrue(File(ordner.root, "profile.json.defekt").isFile)
    }

    @Test
    fun ungueltigesProfilImBestandFuehrtZumStandard() {
        val speicher = ProfilSpeicher(ordner.root)
        val kaputt = ProfilBestand(
            aktivName = "A",
            profile = listOf(Profil("A", links = OhrEinstellung(baenderDb = emptyList()))),
        )
        speicher.speichere(kaputt)
        assertEquals(ProfilBestand.standard(), speicher.lade())
        assertTrue(File(ordner.root, "profile.json.defekt").isFile)
    }

    @Test
    fun fehlenderAktivNameFaelltAufErstesProfilZurueck() {
        val speicher = ProfilSpeicher(ordner.root)
        speicher.speichere(
            ProfilBestand(aktivName = "Weg", profile = listOf(Profil("Eins")))
        )
        assertEquals("Eins", speicher.lade().aktivName)
    }
}
