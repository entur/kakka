package no.entur.kakka.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

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

    public void setId(Long id) {
        this.id = id;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getKey() {
        return key;
    }

    public Long getId() {
        return id;
    }

    public String getValue() {
        return value;
    }

    public Integer getPriority() {
        return priority;
    }

    public static int sort(OSMPOIFilter a, OSMPOIFilter b) {
        if (a.getPriority() > b.getPriority()) {
            return 1;
        } else if (b.getPriority() > a.getPriority()) {
            return -1;
        }
        return 0;
    }
}
