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

package no.entur.kakka.geocoder.sosi;

import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import no.vegvesen.nvdb.sosi.Sosi;
import no.vegvesen.nvdb.sosi.document.SosiDocument;
import no.vegvesen.nvdb.sosi.document.SosiElement;
import no.vegvesen.nvdb.sosi.reader.SosiReader;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SosiTopographicPlaceAdapterReader {

    private static final String AREA_TYPE = "FLATE";
    private static final String SVERM_TYPE = "SVERM";
    private static final String POINT_TYPE = "PUNKT";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Map<String, TopographicPlaceAdapter> adapterMap = new HashMap<>();
    private final SosiElementWrapperFactory wrapperFactory;
    private InputStream sosiInputStream;
    private File sosiFile;
    private SosiCoordinates coordinates;

    public SosiTopographicPlaceAdapterReader(SosiElementWrapperFactory wrapperFactory, InputStream sosiInputStream) {
        this.sosiInputStream = sosiInputStream;
        this.wrapperFactory = wrapperFactory;
    }

    public SosiTopographicPlaceAdapterReader(SosiElementWrapperFactory wrapperFactory, File sosiFile) {
        this.sosiFile = sosiFile;
        this.wrapperFactory = wrapperFactory;
    }

    public Collection<TopographicPlaceAdapter> read() {
        try {
            readToAdapterMap();
        } catch (IOException ioE) {
            throw new RuntimeException("Failed to read topographic places from SOSI: " + ioE.getMessage(), ioE);
        }
        return adapterMap.values();
    }

    /**
     * Read content from SOSI file
     * <p>
     * 1. Map all shapes with coordinates
     * 2. Read all areas and wrap in TopographicPlaceAdapter
     *
     * @throws IOException
     */
    private void readToAdapterMap() throws IOException {
        if (sosiInputStream == null) {
            sosiInputStream = new FileInputStream(sosiFile);
        }
        SosiReader reader = Sosi.createReader(sosiInputStream);

        SosiDocument doc = reader.read();
        coordinates = new SosiCoordinates(doc.getHead());
        doc.getElements().forEach(se -> coordinates.collectCoordinates(se));
        doc.getElements().forEach(this::collectAdminUnits);
        sosiInputStream.close();
    }

    private void collectAdminUnits(SosiElement sosiElement) {

        if ((sosiElement.getName().equals(SVERM_TYPE) || sosiElement.getName().equals(AREA_TYPE) || sosiElement.getName().equals(POINT_TYPE)) && sosiElement.hasSubElements()) {

            TopographicPlaceAdapter area = wrapperFactory.createWrapper(sosiElement, coordinates);
            if (area != null) {
                String id = area.getId();
                TopographicPlaceAdapter existingArea = adapterMap.get(id);
                if (area.isValid() && shouldAddNewArea(area, existingArea)) {
                    adapterMap.put(id, area);
                }
            }
        }
    }

    /**
     * To avoid duplicates exclaves are discarded.
     * <p>
     * Area is added if id does not already exist or if area is greater than existing area for id.
     */
    private boolean shouldAddNewArea(TopographicPlaceAdapter area, TopographicPlaceAdapter existingArea) {
        if (existingArea == null) {
            return true;
        }
        Geometry areaGeo = area.getDefaultGeometry();
        Geometry existingAreaGeo = existingArea.getDefaultGeometry();

        if (existingAreaGeo == null) {
            return areaGeo != null;
        }

        return areaGeo != null && areaGeo.getArea() > existingAreaGeo.getArea();
    }

}
