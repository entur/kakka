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
 */

package no.entur.kakka.config;

import com.google.common.base.MoreObjects;

import java.time.Instant;
import java.util.List;


/**
 * Search params relevant for searching for stop places.
 */
public class StopPlaceSearch {

    /**
     * zero-based page index
     */
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;


    private int page = DEFAULT_PAGE;

    private int size = DEFAULT_PAGE_SIZE;


    private String query;


    private List<StopTypeEnumeration> stopTypeEnumerations;


    private String submode;


    private List<String> netexIdList;


    private boolean allVersions;


    private VersionValidity versionValidity;


    private boolean withoutLocationOnly;

    private boolean withoutQuaysOnly;

    private boolean withDuplicatedQuayImportedIds;

    private boolean withNearbySimilarDuplicates;

    private boolean hasParking;


    private Long version;

    private List<String> tags;

    private boolean withTags;

    private Instant pointInTime;

    public StopPlaceSearch() {
    }

    private StopPlaceSearch(String query,
                            List<StopTypeEnumeration> stopTypeEnumerations,
                            List<String> netexIdList,
                            boolean allVersions,
                            boolean withoutLocationOnly,
                            boolean withoutQuaysOnly,
                            boolean withDuplicatedQuayImportedIds,
                            boolean withNearbySimilarDuplicates,
                            boolean hasParking,
                            Instant pointInTime,
                            Long version,
                            VersionValidity versionValidity,
                            List<String> tags,
                            boolean withTags,
                            String submode,
                            int page, int size) {
        this.query = query;
        this.stopTypeEnumerations = stopTypeEnumerations;
        this.netexIdList = netexIdList;
        this.allVersions = allVersions;
        this.withoutLocationOnly = withoutLocationOnly;
        this.withoutQuaysOnly = withoutQuaysOnly;
        this.withDuplicatedQuayImportedIds = withDuplicatedQuayImportedIds;
        this.withNearbySimilarDuplicates = withNearbySimilarDuplicates;
        this.hasParking = hasParking;
        this.pointInTime = pointInTime;
        this.version = version;
        this.versionValidity = versionValidity;
        this.tags = tags;
        this.withTags = withTags;
        this.submode = submode;
        this.page = page;
        this.size = size;
    }

    public static Builder newStopPlaceSearchBuilder() {
        return new Builder();
    }

    public String getQuery() {
        return query;
    }

    public List<StopTypeEnumeration> getStopTypeEnumerations() {
        return stopTypeEnumerations;
    }

    public void setStopTypeEnumerations(List<StopTypeEnumeration> stopTypeEnumerations) {
        this.stopTypeEnumerations = stopTypeEnumerations;
    }

    public String getSubmode() {
        return submode;
    }

    public List<String> getNetexIdList() {
        return netexIdList;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isAllVersions() {
        return allVersions;
    }

    public boolean isWithoutLocationOnly() {
        return withoutLocationOnly;
    }

    public boolean isWithoutQuaysOnly() {
        return withoutQuaysOnly;
    }

    public boolean isWithDuplicatedQuayImportedIds() {
        return withDuplicatedQuayImportedIds;
    }

    public boolean isWithNearbySimilarDuplicates() {
        return withNearbySimilarDuplicates;
    }

    public boolean isHasParking() {
        return hasParking;
    }

    public boolean isWithTags() {
        return withTags;
    }

    public Instant getPointInTime() {
        return pointInTime;
    }

    public VersionValidity getVersionValidity() {
        return versionValidity;
    }

    public void setVersionValidity(VersionValidity versionValidity) {
        this.versionValidity = versionValidity;
    }

    public List<String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("q", getQuery())
                .add("stopPlaceType", getStopTypeEnumerations())
                .add("submode", getSubmode())
                .add("netexIdList", getNetexIdList())
                .add("allVersions", isAllVersions())
                .add("versionValidity", getVersionValidity())
                .add("pointInTime", getPointInTime())
                .add("withoutLocationOnly", isWithoutLocationOnly())
                .add("withoutQuaysOnly", isWithoutQuaysOnly())
                .add("withDuplicatedQuayImportedIds", isWithDuplicatedQuayImportedIds())
                .add("withTags", tags)
                .add("tags", tags)
                .add("page", page)
                .add("size", size)
                .toString();
    }

    public static class Builder {

        private String query;
        private List<StopTypeEnumeration> stopTypeEnumerations;
        private String submode;
        private List<String> idList;
        private boolean allVersions;
        private boolean withoutLocationOnly;
        private boolean withoutQuaysOnly;
        private boolean withDuplicatedQuayImportedIds;
        private boolean withNearbySimilarDuplicates;
        private boolean hasParking;
        private boolean withTags;
        private Long version;
        private Instant pointInTime;
        private VersionValidity versionValidity;
        private List<String> tags;
        private int page = DEFAULT_PAGE;
        private int size = DEFAULT_PAGE_SIZE;

        private Builder() {
        }

        public Builder setQuery(String query) {
            this.query = query;
            return this;
        }

        public Builder setStopTypeEnumerations(List<StopTypeEnumeration> stopTypeEnumerations) {
            this.stopTypeEnumerations = stopTypeEnumerations;
            return this;
        }

        public Builder setSubmode(String submode) {
            this.submode = submode;
            return this;
        }

        public Builder setNetexIdList(List<String> netexIdList) {
            this.idList = netexIdList;
            return this;
        }

        public Builder setPage(int page) {
            this.page = page;
            return this;
        }

        public Builder setSize(int size) {
            this.size = size;
            return this;
        }

        public Builder setAllVersions(boolean allVersions) {
            this.allVersions = allVersions;
            return this;
        }

        public Builder setWithoutLocationOnly(boolean withoutLocationOnly) {
            this.withoutLocationOnly = withoutLocationOnly;
            return this;
        }

        public Builder setWithoutQuaysOnly(boolean withoutQuaysOnly) {
            this.withoutQuaysOnly = withoutQuaysOnly;
            return this;
        }

        public void setWithDuplicatedQuayImportedIds(boolean withDuplicatedQuayImportedIds) {
            this.withDuplicatedQuayImportedIds = withDuplicatedQuayImportedIds;
        }

        public void setWithNearbySimilarDuplicates(boolean withNearbySimilarDuplicates) {
            this.withNearbySimilarDuplicates = withNearbySimilarDuplicates;
        }

        public void setHasParking(boolean hasParking) {
            this.hasParking = hasParking;
        }

        public Builder setVersion(Long version) {
            this.version = version;
            return this;
        }

        public Builder setPointInTime(Instant pointInTime) {
            this.pointInTime = pointInTime;
            return this;
        }

        public Builder setVersionValidity(VersionValidity versionValidity) {
            this.versionValidity = versionValidity;
            return this;
        }

        public Builder setTags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder setWithTags(boolean withTags) {
            this.withTags = withTags;
            return this;
        }

        public StopPlaceSearch build() {
            return new StopPlaceSearch(query,
                    stopTypeEnumerations,
                    idList,
                    allVersions,
                    withoutLocationOnly,
                    withoutQuaysOnly,
                    withDuplicatedQuayImportedIds,
                    withNearbySimilarDuplicates,
                    hasParking,
                    pointInTime,
                    version,
                    versionValidity,
                    tags,
                    withTags,
                    submode,
                    page,
                    size);
        }

    }
}
