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

package no.entur.kakka.security;

import org.rutebanken.helper.organisation.RoleAssignment;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthorizationService {

    @Value("${authorization.enabled:true}")
    protected boolean authorizationEnabled;
    @Autowired
    private RoleAssignmentExtractor roleAssignmentExtractor;

    public void verifyAtLeastOne(String... roles) {
        if (!authorizationEnabled) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<RoleAssignment> roleAssignments = roleAssignmentExtractor.getRoleAssignmentsForUser(authentication);

        boolean authorized = false;
        for (String role : roles) {
            authorized |= roleAssignments.stream().anyMatch(ra -> role.equals(ra.getRole()));
        }

        if (!authorized) {
            throw new AccessDeniedException("Insufficient privileges for operation");
        }

    }

}
