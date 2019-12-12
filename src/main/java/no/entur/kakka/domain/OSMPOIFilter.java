package no.entur.kakka.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import javax.validation.Constraint;
import java.lang.annotation.Annotation;

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
    private int priority;

    public void setId(Long id) {
        this.id = id;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setPriority(int priority) {
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

    public int getPriority() {
        return priority;
    }
}
