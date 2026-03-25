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

package no.entur.kakka.pipeline;


import no.entur.kakka.pipeline.routes.control.PipelineTask;

public class PipelineTasks {

    public static final PipelineTask KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD
            = new PipelineTask(PipelineTask.Phase.DOWNLOAD_SOURCE_DATA, "direct:kartverketAdministrativeUnitsDownload");

    public static final PipelineTask TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START
            = new PipelineTask(PipelineTask.Phase.TIAMAT_UPDATE, "direct:tiamatAdministrativeUnitsUpdate");

    public static final PipelineTask TIAMAT_TARIFF_ZONES_UPDATE_START
            = new PipelineTask(PipelineTask.Phase.TIAMAT_UPDATE, "direct:tiamatTariffZonesUpdate");

    public static final PipelineTask TIAMAT_NEIGHBOURING_COUNTRIES_UPDATE_START
            = new PipelineTask(PipelineTask.Phase.TIAMAT_UPDATE, "direct:tiamatNeighbouringCountriesUpdateStart");

}
