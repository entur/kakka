package no.entur.kakka.geocoder.routes.pelias.mapper.netex;

import net.opengis.gml._3.AbstractRingType;
import net.opengis.gml._3.LinearRingType;
import org.apache.commons.collections.CollectionUtils;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.rutebanken.netex.model.AlternativeName;
import org.rutebanken.netex.model.AlternativeNames_RelStructure;
import org.rutebanken.netex.model.GroupOfEntities_VersionStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.NameTypeEnumeration;
import org.rutebanken.netex.model.ValidBetween;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NetexPeliasMapperUtil {

    /**
     * Create list of all names for an entity from official name and all alternative names.
     */
    public static List<MultilingualString> getAllNames(MultilingualString name, AlternativeNames_RelStructure alternativeNames) {
        List<MultilingualString> names = new ArrayList<>();
        if (name != null) {
            names.add(name);
        }

        if (alternativeNames != null && !CollectionUtils.isEmpty(alternativeNames.getAlternativeName())) {
            alternativeNames.getAlternativeName().stream().filter(an -> an.getName() != null && an.getName().getLang() != null).forEach(n -> names.add(n.getName()));
        }

        return NetexPeliasMapperUtil.filterUnique(names);
    }

    /**
     * Create display name for pelias from official name and appropriate label (nickname)
     */
    public static MultilingualString getDisplayName(MultilingualString name, AlternativeNames_RelStructure alternativeNames) {
        if (name == null) {
            return null;
        }

        StringBuilder displayName = new StringBuilder(name.getValue());

        if (alternativeNames != null && !CollectionUtils.isEmpty(alternativeNames.getAlternativeName())) {
            List<AlternativeName> labels = alternativeNames.getAlternativeName().stream().filter(an -> NameTypeEnumeration.LABEL.equals(an.getNameType())).filter(an -> an.getName() != null).collect(Collectors.toList());

            AlternativeName label = labels.stream().filter(an -> Objects.equals(an.getName().getLang(), name.getLang())).findFirst().orElse(labels.stream().findFirst().orElse(null));
            if (label != null) {
                displayName.append(" (").append(label.getName().getValue()).append(")");
            }
        }

        return new MultilingualString().withValue(displayName.toString()).withLang(name.getLang());
    }


    public static List<MultilingualString> filterUnique(List<MultilingualString> strings) {
        return strings.stream().filter(distinctByKey(name -> name.getValue())).collect(Collectors.toList());
    }

    public static boolean isValid(GroupOfEntities_VersionStructure object) {
        if (!CollectionUtils.isEmpty(object.getValidBetween()) && object.getValidBetween().stream().noneMatch(vb -> isValidNow(vb))) {
            return false;
        }
        return true;
    }

    // Should compare instant with validbetween from/to in timezone defined in PublicationDelivery, but makes little difference in practice
    private static boolean isValidNow(ValidBetween validBetween) {
        LocalDateTime now = LocalDateTime.now();
        if (validBetween != null) {
            if (validBetween.getFromDate() != null && validBetween.getFromDate().isAfter(now)) {
                return false;
            }

            if (validBetween.getToDate() != null && validBetween.getToDate().isBefore(now)) {
                return false;
            }
        }
        return true;
    }

    public static Polygon toPolygon(AbstractRingType ring) {

        if (ring instanceof LinearRingType) {
            LinearRingType linearRing = (LinearRingType) ring;

            List<LngLatAlt> coordinates = new ArrayList<>();
            LngLatAlt coordinate = null;
            LngLatAlt prevCoordinate = null;
            for (Double val : linearRing.getPosList().getValue()) {
                if (coordinate == null) {
                    coordinate = new LngLatAlt();
                    coordinate.setLatitude(val);
                } else {
                    coordinate.setLongitude(val);
                    if (prevCoordinate == null || !equals(coordinate, prevCoordinate)) {
                        coordinates.add(coordinate);
                    }
                    prevCoordinate = coordinate;
                    coordinate = null;
                }
            }
            return new Polygon(coordinates);

        }
        return null;
    }

    private static boolean equals(LngLatAlt coordinate, LngLatAlt other) {
        return other.getLatitude() == coordinate.getLatitude() && other.getLongitude() == coordinate.getLongitude();
    }

    protected static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}
