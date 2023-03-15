/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

angular.module('service.zones', [])
    .service('zonesService', function ($http, groupsService, $log, utilityService) {

        this.getZones = function (limit, startFrom, query, searchByAdminGroup, ignoreAccess) {
            if (query == "") {
                query = null;
            }
            var params = {
                "maxItems": limit,
                "startFrom": startFrom,
                "nameFilter": query,
                "searchByAdminGroup": searchByAdminGroup,
                "ignoreAccess": ignoreAccess
            };
            var url = groupsService.urlBuilder("/api/zones", params);
            let loader = $("#loader");
            loader.modal({
                          backdrop: "static", //remove ability to close modal with click
                          keyboard: false, //remove option to close with keyboard
                          show: true //Display loader!
                          })
            let promis =  $http.get(url);
            // Hide loader when api gets response
            promis.then(()=>loader.modal("hide"), ()=>loader.modal("hide"))
            return promis
        };

        this.getZoneChanges = function (limit, startFrom, zoneId) {
                    var params = {
                        "maxItems": limit,
                        "startFrom": startFrom
                        }
                    var url = utilityService.urlBuilder ( "/api/zones/" + zoneId + "/changes", params);
                    return $http.get(url);
        };

        this.getBackendIds = function() {
            var url = "/api/zones/backendids";
            return $http.get(url);
        }

        this.sendZone = function (payload) {
            var sanitizedPayload = this.sanitizeConnections(payload);
            $log.debug("service.zones: sending zone", sanitizedPayload);
            return $http.post("/api/zones", sanitizedPayload, {headers: utilityService.getCsrfHeader()});
        };

        this.delZone = function (id) {
            return $http.delete("/api/zones/"+id, {headers: utilityService.getCsrfHeader()});
        };

        this.updateZone = function (id, payload) {
            var sanitizedPayload = this.sanitizeConnections(payload);
            $log.debug("service.zones: updating zone", sanitizedPayload);
            return $http.put("/api/zones/"+id, sanitizedPayload, {headers: utilityService.getCsrfHeader()});
        };

        this.sanitizeConnections = function(payload) {
            var sanitizedPayload = {};
            angular.forEach(payload, function(value, key){
                if(key == "connection" || key == "transferConnection") {
                    var sanitizedConnection = sanitize(payload[key]);
                    if(!angular.equals(sanitizedConnection, {}) &&
                        !(Object.keys(sanitizedConnection).length == 1 && 'name' in sanitizedConnection)) {
                        sanitizedConnection.name = payload.name;
                        sanitizedPayload[key] = sanitizedConnection;
                    }
                } else {
                    sanitizedPayload[key] = value
                }
            });
            return sanitizedPayload;
        };

        this.normalizeZoneDates = function(zone) {
            if (zone.created != undefined) {
                zone.created = this.toApiIso(zone.created);
            }
            if (zone.updated != undefined) {
                zone.updated = this.toApiIso(zone.updated);
            }
            if (zone.latestSync != undefined) {
                zone.latestSync = this.toApiIso(zone.latestSync);
            }
            return zone;
        };

        this.setConnectionKeys = function(zone) {
            if (zone.connection != undefined) {
                if (zone.hiddenKey.trim() != '') {
                    zone.connection.key = zone.hiddenKey;
                } else if (zone.hiddenKey.trim() == '' && zone.connection.keyName == '' && zone.connection.primaryServer == '') {
                    zone.connection.key = '';
                }
            }
            if (zone.transferConnection != undefined) {
                if (zone.hiddenKey.trim() != '') {
                    zone.transferConnection.key = zone.hiddenKey;
                } else if (zone.hiddenTransferKey.trim() == '' && zone.transferConnection.keyName == '' && zone.transferConnection.primaryServer == '') {
                    zone.transferConnection.key = '';
                }
            }
            return zone;
        };

        this.checkBackendId = function(zone) {
            if (zone.backendId === ''){
                zone.backendId = undefined;
            }
            return zone;
        };

        this.checkSharedStatus = function(zone) {
            zone.shared = (String(zone.shared).toLowerCase() == 'true');
            return zone;
        }

        this.toApiIso = function(date) {
            /* when we parse the DateTimes from the api it gets turned into javascript readable format
             * instead of just staying the way it is, so for now converting it back to ISO format on the fly,
             * ISO 8601 standards are YYYY-MM-DDTHH:MM:SS:SSSZ, but the DateTime ISO the api uses is
             * YYYY-MM-DDTHH:MM:SSZ, so the SSS has to be dropped */
            return (new Date(date)).toISOString().slice(0,19) + 'Z';
        };

        this.toDisplayAclRule = function(rule) {
            var newRule = angular.copy(rule);
            if (newRule.groupId != undefined) {
                newRule.priority = "Group";
            } else if (newRule.userId != undefined) {
                newRule.priority = "User";
                newRule.userName = newRule.displayName;
            } else {
                newRule.priority = "All Users";
            }
            if (newRule.accessLevel == 'NoAccess') {
                newRule.accessLevel = 'No Access';
            }
            return newRule;
        };

        this.toVinylAclRule = function(rule) {
            var newRule = {
                accessLevel: rule.accessLevel,
                description: rule.description,
                recordMask: rule.recordMask,
                recordTypes: rule.recordTypes,
                displayName: rule.displayName
            };
            switch (rule.priority) {
                case 'User':
                    newRule.userId = rule.userId;
                    break;
                case 'Group':
                    newRule.groupId = rule.groupId;
                    break;
                default:
                    break;
            }
            if (newRule.accessLevel == 'No Access') {
                newRule.accessLevel = 'NoAccess';
            }

            return newRule;
        };

        function sanitize(connection) {

            var sanitizedConnection = {};
            angular.forEach(connection, function(value, key) {

                if(value.trim() != "") {
                    sanitizedConnection[key] = value;
                }
            });

            return sanitizedConnection;
        }
    });
