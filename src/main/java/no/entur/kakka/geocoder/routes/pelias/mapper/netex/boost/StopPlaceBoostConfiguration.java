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

package no.entur.kakka.geocoder.routes.pelias.mapper.netex.boost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.tuple.Pair;
import org.rutebanken.netex.model.AirSubmodeEnumeration;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.InterchangeWeightingEnumeration;
import org.rutebanken.netex.model.MetroSubmodeEnumeration;
import org.rutebanken.netex.model.RailSubmodeEnumeration;
import org.rutebanken.netex.model.StopTypeEnumeration;
import org.rutebanken.netex.model.TramSubmodeEnumeration;
import org.rutebanken.netex.model.WaterSubmodeEnumeration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StopPlaceBoostConfiguration {

    private static final String ALL_TYPES = "*";
    private final Map<StopTypeEnumeration, StopTypeBoostConfig> stopTypeScaleFactorMap = new HashMap<>();
    private final Map<InterchangeWeightingEnumeration, Double> interchangeScaleFactorMap = new HashMap<>();
    private long defaultValue;

    @Autowired
    public StopPlaceBoostConfiguration(@Value("${pelias.stop.place.boost.config:{\"defaultValue\":1000}}") String boostConfig) {
        init(boostConfig);
    }

    public long getPopularity(List<Pair<StopTypeEnumeration, Enum>> stopTypeAndSubModeList, InterchangeWeightingEnumeration interchangeWeighting) {
        long popularity = defaultValue;

        double stopTypeAndSubModeFactor = stopTypeAndSubModeList.stream().collect(Collectors.summarizingDouble(stopTypeAndSubMode -> getStopTypeAndSubModeFactor(stopTypeAndSubMode.getLeft(), stopTypeAndSubMode.getRight()))).getSum();

        if (stopTypeAndSubModeFactor > 0) {
            popularity *= (long) stopTypeAndSubModeFactor;
        }

        Double interchangeFactor = interchangeScaleFactorMap.get(interchangeWeighting);
        if (interchangeFactor != null) {
            popularity *= interchangeFactor;
        }

        return popularity;
    }

    private double getStopTypeAndSubModeFactor(StopTypeEnumeration stopType, Enum subMode) {
        StopTypeBoostConfig factorsPerSubMode = stopTypeScaleFactorMap.get(stopType);
        if (factorsPerSubMode != null) {
            return factorsPerSubMode.getFactorForSubMode(subMode);
        }
        return 0;
    }


    private void init(String boostConfig) {
        StopPlaceBoostConfigJSON input = fromString(boostConfig);

        defaultValue = input.defaultValue;

        if (input.interchangeFactors != null) {
            input.interchangeFactors.forEach((interchangeTypeString, factor) -> interchangeScaleFactorMap.put(InterchangeWeightingEnumeration.fromValue(interchangeTypeString), factor));
        }

        if (input.stopTypeFactors != null) {
            for (Map.Entry<String, Map<String, Double>> stopTypeConfig : input.stopTypeFactors.entrySet()) {
                StopTypeEnumeration stopType = StopTypeEnumeration.fromValue(stopTypeConfig.getKey());

                Map<String, Double> inputFactorsPerSubMode = stopTypeConfig.getValue();

                StopTypeBoostConfig stopTypeBoostConfig = new StopTypeBoostConfig(inputFactorsPerSubMode.getOrDefault(ALL_TYPES, 1.0));
                stopTypeScaleFactorMap.put(stopType, stopTypeBoostConfig);

                inputFactorsPerSubMode.remove(ALL_TYPES);
                if (inputFactorsPerSubMode != null) {
                    inputFactorsPerSubMode.forEach((subModeString, factor) -> stopTypeBoostConfig.factorPerSubMode.put(toSubModeEnum(stopType, subModeString), factor));
                }
            }
        }
    }


    private StopPlaceBoostConfigJSON fromString(String string) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.readValue(string, StopPlaceBoostConfigJSON.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Enum toSubModeEnum(StopTypeEnumeration stopType, String subMode) {
        return switch (stopType) {
            case AIRPORT -> AirSubmodeEnumeration.fromValue(subMode);
            case HARBOUR_PORT, FERRY_STOP, FERRY_PORT -> WaterSubmodeEnumeration.fromValue(subMode);
            case BUS_STATION, COACH_STATION, ONSTREET_BUS -> BusSubmodeEnumeration.fromValue(subMode);
            case RAIL_STATION -> RailSubmodeEnumeration.fromValue(subMode);
            case METRO_STATION -> MetroSubmodeEnumeration.fromValue(subMode);
            case ONSTREET_TRAM, TRAM_STATION -> TramSubmodeEnumeration.fromValue(subMode);
            default -> null;
        };
    }


    private static class StopTypeBoostConfig {

        public double defaultFactor = 1;

        public Map<Enum, Double> factorPerSubMode = new HashMap<>();

        public StopTypeBoostConfig(double defaultFactor) {
            this.defaultFactor = defaultFactor;
        }

        public Double getFactorForSubMode(Enum subMode) {
            return factorPerSubMode.getOrDefault(subMode, defaultFactor);
        }
    }


}
