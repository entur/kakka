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

package no.entur.kakka.geocoder.routes.control;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class GeoCoderTaskTest {

    @Test
    public void testSortingByStartedTasksAndThenEarliestPhase() {

        GeoCoderTask phase1 = new GeoCoderTask(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA, 0, "s1");
        GeoCoderTask phase1OtherTarget = new GeoCoderTask(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA, 0, "s1Other");
        GeoCoderTask phase2 = new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_UPDATE, 0, "s2");
        GeoCoderTask startedTask = new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_UPDATE, 2, "s2");
        GeoCoderTask phase4 = new GeoCoderTask(GeoCoderTask.Phase.PELIAS_UPDATE, 0, "s4");

        List<GeoCoderTask> expectedOrder = Arrays.asList(startedTask, phase1, phase1OtherTarget, phase2, phase4);
        Iterator<GeoCoderTask> itrExpected = expectedOrder.iterator();

        SortedSet<GeoCoderTask> sorted = new TreeSet<>(expectedOrder);

        sorted.forEach(s -> Assertions.assertEquals(itrExpected.next(), s));

        Assertions.assertFalse(sorted.add(new GeoCoderTask(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA, 0, "s1")), "Duplicates should not be allowed");
    }
}
