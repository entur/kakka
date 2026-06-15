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

package no.entur.kakka.task;


import no.entur.kakka.task.routes.control.Task;

public class TaskConstants {


    public static final String NEXT_TASK = "RutebankenTaskNextTask";
    public static final String CURRENT_TASK = "RutebankenTaskCurrentTask";
    public static final String RESCHEDULE_TASK = "RutebankgenTaskRescheduleTask";

    public static final Task KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD
            = new Task(Task.Phase.DOWNLOAD_SOURCE_DATA, "direct:kartverketAdministrativeUnitsDownload");

    public static final Task TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START
            = new Task(Task.Phase.TIAMAT_UPDATE, "direct:tiamatAdministrativeUnitsUpdate");

    public static final Task TIAMAT_NEIGHBOURING_COUNTRIES_UPDATE_START
            = new Task(Task.Phase.TIAMAT_UPDATE, "direct:tiamatNeighbouringCountriesUpdateStart");

}
