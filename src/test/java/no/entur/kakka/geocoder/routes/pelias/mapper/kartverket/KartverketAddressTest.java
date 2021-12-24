/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.entur.kakka.geocoder.routes.pelias.mapper.kartverket;


import no.entur.kakka.geocoder.routes.pelias.kartverket.KartverketAddress;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KartverketAddressTest {


    @Test
    public void testFormatFylkesNo() {
        Assertions.assertEquals("02", address("0203", null).getFylkesNo());
        Assertions.assertEquals("02", address("203", null).getFylkesNo());
        Assertions.assertNull(address(null, null).getFylkesNo());
    }

    @Test
    public void testFormatFullKommuneNo() {
        Assertions.assertEquals("0203", address("0203", null).getFullKommuneNo());
        Assertions.assertEquals("0203", address("203", null).getFullKommuneNo());
        Assertions.assertNull(address(null, null).getFullKommuneNo());
    }

    @Test
    public void testFormatFullGrunnkretsNo() {
        Assertions.assertEquals("02030560", address("0203", "0560").getFullGrunnkretsNo());
        Assertions.assertEquals("02030560", address("203", "560").getFullGrunnkretsNo());
        Assertions.assertNull(address(null, null).getFullGrunnkretsNo());
        Assertions.assertNull(address("0203", null).getFullGrunnkretsNo());
        Assertions.assertNull(address(null, "531").getFullGrunnkretsNo());
    }

    private KartverketAddress address(String kommunenr, String grunnkretsnr) {
        KartverketAddress address = new KartverketAddress();
        address.setGrunnkretsnr(grunnkretsnr);
        address.setKommunenr(kommunenr);
        return address;
    }
}
