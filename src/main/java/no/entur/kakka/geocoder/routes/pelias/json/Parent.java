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

package no.entur.kakka.geocoder.routes.pelias.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.springframework.util.CollectionUtils;

import java.util.List;

@JsonRootName("parent")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Parent {

    @JsonProperty("country")
    private List<String> countryList;
    @JsonProperty("county")
    private List<String> countyList;
    @JsonProperty("postalCode")
    private List<String> postalCodeList;
    @JsonProperty("localadmin")
    private List<String> localadminList;
    @JsonProperty("locality")
    private List<String> localityList;
    @JsonProperty("borough")
    private List<String> boroughList;

    @JsonProperty("country_a")
    private List<String> countryIdList;
    @JsonProperty("county_id")
    private List<String> countyIdList;
    @JsonProperty("postalCode_id")
    private List<String> postalCodeIdList;
    @JsonProperty("localadmin_id")
    private List<String> localadminIdList;
    @JsonProperty("locality_id")
    private List<String> localityIdList;
    @JsonProperty("borough_id")
    private List<String> boroughIdList;

    public Parent() {
    }

    public static Parent.Builder builder() {
        return new Parent.Builder();
    }

    @JsonIgnore
    public String getCountry() {
        return getFirst(countryList);
    }

    public void setCountry(String country) {
        this.countryList = asList(country);
    }

    @JsonIgnore
    public String getCounty() {
        return getFirst(countyList);
    }

    public void setCounty(String county) {
        this.countyList = asList(county);
    }

    @JsonIgnore
    public String getPostalCode() {
        return getFirst(postalCodeList);
    }

    public void setPostalCode(String postalCode) {
        this.postalCodeList = asList(postalCode);
    }

    @JsonIgnore
    public String getLocaladmin() {
        return getFirst(localadminList);
    }

    public void setLocaladmin(String localadmin) {
        this.localadminList = asList(localadmin);
    }

    @JsonIgnore
    public String getLocality() {
        return getFirst(localityList);
    }

    public void setLocality(String locality) {
        this.localityList = asList(locality);
    }

    @JsonIgnore
    public String getCountryId() {
        return getFirst(countryIdList);
    }

    public void setCountryId(String countryId) {
        this.countryIdList = asList(countryId);
    }

    @JsonIgnore
    public String getCountyId() {
        return getFirst(countyIdList);
    }

    public void setCountyId(String countyId) {
        this.countyIdList = asList(countyId);
    }

    @JsonIgnore
    public String getPostalCodeId() {
        return getFirst(postalCodeIdList);
    }

    public void setPostalCodeId(String postalCodeId) {
        this.postalCodeIdList = asList(postalCodeId);
    }

    @JsonIgnore
    public String getLocaladminId() {
        return getFirst(localadminIdList);
    }

    public void setLocaladminId(String localadminId) {
        this.localadminIdList = asList(localadminId);
    }

    @JsonIgnore
    public String getLocalityId() {
        return getFirst(localityIdList);
    }

    public void setLocalityId(String localityId) {
        this.localityIdList = asList(localityId);
    }

    @JsonIgnore
    public String getBorough() {
        return getFirst(boroughList);
    }

    public void setBorough(String borough) {
        this.boroughList = asList(borough);
    }

    @JsonIgnore
    public String getBoroughId() {
        return getFirst(boroughIdList);
    }

    public void setBoroughId(String boroughId) {
        this.boroughIdList = asList(boroughId);
    }

    private <T> List<T> asList(T obj) {
        return obj == null ? null : List.of(obj);
    }

    private <T> T getFirst(List<T> list) {
        return CollectionUtils.isEmpty(list) ? null : list.getFirst();
    }

    public static class Builder {

        protected Parent parent = new Parent();

        private Builder() {
        }


        public Builder withCountry(String country) {
            parent.setCounty(country);
            return this;
        }

        public Builder withPostalCode(String postalCode) {

            parent.setPostalCode(postalCode);
            return this;
        }

        public Builder withLocaladmin(String localadmin) {
            parent.setLocaladmin(localadmin);
            return this;
        }

        public Builder withLocality(String locality) {
            parent.setLocality(locality);
            return this;
        }

        public Builder withCounty(String county) {
            parent.setCounty(county);
            return this;
        }


        public Builder withBorough(String borough) {
            parent.setBorough(borough);
            return this;
        }

        public Builder withCountryId(String countryId) {
            parent.setCountryId(countryId);
            return this;
        }

        public Builder withPostalCodeId(String postalCodeId) {
            parent.setPostalCodeId(postalCodeId);
            return this;
        }

        public Builder withLocaladminId(String localadminId) {
            parent.setLocaladminId(localadminId);
            return this;
        }

        public Builder withLocalityId(String localityId) {
            parent.setLocalityId(localityId);
            return this;
        }

        public Builder withCountyId(String countyId) {
            parent.setCountyId(countyId);
            return this;
        }

        public Builder withBoroughId(String boroughId) {
            parent.setBoroughId(boroughId);
            return this;
        }

        public Parent build() {
            return parent;
        }
    }


}
