CREATE TABLE custom_configuration
(
    id    SERIAL PRIMARY KEY,
    key   VARCHAR(200) NOT NULL UNIQUE,
    value VARCHAR      NOT NULL
);

INSERT INTO custom_configuration
VALUES (DEFAULT, 'poiFilter',
        'amenity=cinema,amenity=clinic,amenity=college,amenity=doctors,amenity=embassy,amenity=exhibition_center,amenity=hospital,amenity=kindergarten,amenity=nursing_home,amenity=place_of_worship,amenity=prison,amenity=school,amenity=theatre,amenity=university,landuse=cemetery,leisure=park,leisure=sports_centre,leisure=stadium,office=government,shop=mall,social_facility=nursing_home,tourism=museum,name=Entur AS,name=Kristiansand Dyrepark'
       );
