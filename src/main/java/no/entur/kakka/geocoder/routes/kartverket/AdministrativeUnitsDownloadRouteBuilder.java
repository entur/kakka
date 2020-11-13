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

package no.entur.kakka.geocoder.routes.kartverket;


import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdministrativeUnitsDownloadRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${kartverket.administrative.units.download.cron.schedule:0+0+23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${kartverket.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@Value("${kartverket.administrative.units.county.dataSetId:6093c8a8-fa80-11e6-bc64-92361f002671}")
	private String countyDataSetId;


	@Value("${kartverket.administrative.units.municipality.dataSetId:041f1e6e-bdbc-4091-b48f-8a5990f3cc5b}")
	private String municipalityDataSetId;



	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://kakka/administrativeUnitsDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{kartverket.administrative.units.download.autoStartup:false}}")
				.filter(e -> isSingletonRouteActive(e.getFromRouteId()))
				.log(LoggingLevel.INFO, "Quartz triggers download of administrative units.")
				.setBody(constant(GeoCoderConstants.KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD))
				.to(ExchangePattern.InOnly,"direct:geoCoderStart")
				.routeId("admin-units-download-quartz");

		from(GeoCoderConstants.KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD.getEndpoint())
				.log(LoggingLevel.INFO, "Start downloading administrative units")
				.process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.ADMINISTRATIVE_UNITS_DOWNLOAD).build()).to("direct:updateStatus")
				.to("direct:transferCountyFile")
				.to("direct:transferMunicipalityFile")
				.choice()
				.when(simple("${header." + Constants.CONTENT_CHANGED + "}"))
				.log(LoggingLevel.INFO, "Uploaded updated administrative units from mapping authority. Initiating update of Tiamat")
				.setBody(constant(null))
				.setProperty(GeoCoderConstants.GEOCODER_NEXT_TASK, constant(GeoCoderConstants.TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START))
				.otherwise()
				.log(LoggingLevel.INFO, "Finished downloading administrative units from mapping authority with no changes")
				.end()
				.process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
				.routeId("admin-units-download");

		from("direct:transferCountyFile")
				.setHeader(Constants.KARTVERKET_DATASETID, constant(countyDataSetId))
				.setHeader(Constants.KARTVERKET_FORMAT, constant("SOSI"))
				.setHeader(Constants.FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/administrativeUnits/county"))
				.to("direct:uploadUpdatedFiles")
				.routeId("administrative-units-county-to-blobstore");

		from("direct:transferMunicipalityFile")
				.setHeader(Constants.KARTVERKET_DATASETID, constant(municipalityDataSetId))
				.setHeader(Constants.KARTVERKET_FORMAT, constant("SOSI"))
				.setHeader(Constants.FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/administrativeUnits/municipality"))
				.to("direct:uploadUpdatedFiles")
				.routeId("administrative-units-municipality-to-blobstore");
	}

}
