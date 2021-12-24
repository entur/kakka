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

package no.entur.kakka.geocoder.routes.pelias.mapper;


import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import no.entur.kakka.geocoder.routes.pelias.mapper.coordinates.GeometryTransformer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GeometryTransformationTest {


	@Test
	public void testConvertCoordinateFromUTM33N() throws Exception {
		GeometryFactory factory = new GeometryFactory();
		assertCoordinates(GeometryTransformer.fromUTM(factory.createPoint(new Coordinate(0, 0)), "33"), 10.51, 0);
		assertCoordinates(GeometryTransformer.fromUTM(factory.createPoint(new Coordinate(99999, 99999)), "33"), 11.41, 0.90);
	}


	@Test
	public void testConvertCoordinateFromUTM32N() throws Exception {
		GeometryFactory factory = new GeometryFactory();
		assertCoordinates(GeometryTransformer.fromUTM(factory.createPoint(new Coordinate(0, 0)), "32"), 4.51, 0);
		assertCoordinates(GeometryTransformer.fromUTM(factory.createPoint(new Coordinate(99999, 99999)), "32"), 5.41, 0.90);
	}

	private void assertCoordinates(Geometry geometry, double expectedX, double expectedY) {
		Coordinate coordinate = geometry.getCoordinate();
		Assertions.assertEquals(expectedX, coordinate.x, 0.1);
		Assertions.assertEquals(expectedY, coordinate.y, 0.1);
	}
}
