CREATE TABLE osm_poi_filter
(
    id    SERIAL PRIMARY KEY,
    key   VARCHAR(200) NOT NULL,
    value VARCHAR(200) NOT NULL,
    priority INTEGER NOT NULL,
    UNIQUE (key, value)
);

INSERT INTO osm_poi_filter
VALUES  (DEFAULT, 'amenity', 'cinema', 1),
        (DEFAULT, 'amenity', 'clinic', 1),
        (DEFAULT, 'amenity', 'college', 1),
        (DEFAULT, 'amenity', 'doctors', 1),
        (DEFAULT, 'amenity', 'embassy', 1),
        (DEFAULT, 'amenity', 'exhibition_center', 1),
        (DEFAULT, 'amenity', 'hospital', 1),
        (DEFAULT, 'amenity', 'kindergarten', 1),
        (DEFAULT, 'amenity', 'nursing_home', 1),
        (DEFAULT, 'amenity', 'place_of_worship', 1),
        (DEFAULT, 'amenity', 'prison', 1),
        (DEFAULT, 'amenity', 'school', 1),
        (DEFAULT, 'amenity', 'theatre', 1),
        (DEFAULT, 'amenity', 'university', 1),
        (DEFAULT, 'landuse', 'cemetery', 1),
        (DEFAULT, 'leisure', 'park', 1),
        (DEFAULT, 'leisure', 'sports_centre', 1),
        (DEFAULT, 'leisure', 'stadium', 1),
        (DEFAULT, 'office', 'government', 1),
        (DEFAULT, 'shop', 'mall', 1),
        (DEFAULT, 'social_facility', 'nursing_home', 1),
        (DEFAULT, 'tourism', 'museum', 1),
        (DEFAULT, 'name', 'Entur AS', 1),
        (DEFAULT, 'name', 'Kristiansand Dyrepark', 1),
        (DEFAULT, 'tourism', 'event', 1),
        (DEFAULT, 'amenity', 'golf_course', 1),
        (DEFAULT, 'amenity', 'library', 1);
