package no.entur.kakka.routes.file.beans;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;
import java.util.zip.ZipInputStream;

import static no.entur.kakka.routes.file.beans.FileClassifierPredicates.NETEX_PUBLICATION_DELIVERY_QNAME;
import static org.junit.jupiter.api.Assertions.*;

class FileClassifierPredicatesTest {
    @Test
    void firstElementQNameMatchesNetex() throws Exception {
        assertPredicateTrueInZipFile("tariff_zones_fare_zones_rut.zip",
                FileClassifierPredicates.firstElementQNameMatchesNetex());
    }


    @Test
    void firstElementQNameMatches() throws Exception {
        assertPredicateTrueInZipFile("tariff_zones_fare_zones_rut.zip",
                FileClassifierPredicates.firstElementQNameMatches(
                        NETEX_PUBLICATION_DELIVERY_QNAME));
    }

    @Test
    void firstElementQNameMatchesComment() throws Exception {
        assertPredicateTrueInZipFile("tariff_zones_fare_zones_rut.zip",
                FileClassifierPredicates.firstElementQNameMatches(
                        NETEX_PUBLICATION_DELIVERY_QNAME));
    }


    private void assertPredicateTrueInZipFile(String fileName, Predicate<InputStream> predicate) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(this.getClass().getResourceAsStream(fileName));
        while (zipInputStream.getNextEntry() != null) {
            if (!predicate.test(zipInputStream)) {
                throw new AssertionError();
            }
        }
    }

    private void assertPredicateFalseInZipFile(String fileName, Predicate<InputStream> predicate) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(this.getClass().getResourceAsStream(fileName));
        while (zipInputStream.getNextEntry() != null) {
            if (!predicate.test(zipInputStream)) {
                return;
            }
        }
        throw new AssertionError();
    }

}