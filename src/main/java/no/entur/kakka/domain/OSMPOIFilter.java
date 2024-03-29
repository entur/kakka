package no.entur.kakka.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "osm_poi_filter")
public class OSMPOIFilter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private Integer priority;

    public static int sort(OSMPOIFilter a, OSMPOIFilter b) {
        if (a.getPriority() > b.getPriority()) {
            return 1;
        } else if (b.getPriority() > a.getPriority()) {
            return -1;
        }
        return 0;
    }

    public static OSMPOIFilter fromKeyAndValue(String key, String value) {
        OSMPOIFilter filter = new OSMPOIFilter();
        filter.setKey(key);
        filter.setValue(value);
        return filter;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
