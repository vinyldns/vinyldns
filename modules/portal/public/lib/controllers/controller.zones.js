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

angular.module('controller.zones', [])
    .controller('ZonesController', function ($scope, $http, $location, $log, recordsService, zonesService, profileService,
                                             groupsService, utilityService, $timeout, pagingService) {

    $scope.alerts = [];
    $scope.zonesLoaded = false;
    $scope.hasZones = false; // Re-assigned each time zones are fetched without a query

    $scope.query = "";

    // Paging status for zone sets
    var zonesPaging = pagingService.getNewPagingParams(100);

    profileService.getAuthenticatedUserData().then(function (results) {
        if (results.data) {
            $scope.profile = results.data;
            $scope.profile.active = 'zones';
        }
    }, function () {
        $scope.profile = $scope.profile || {};
        $scope.profile.active = 'zones';
    });

    $scope.resetCurrentZone = function () {
        $scope.currentZone = {};

        if($scope.myGroups && $scope.myGroups.length) {
            $scope.currentZone.adminGroupId = $scope.myGroups[0].id;
        }

        $scope.currentZone.connection = {};
        $scope.currentZone.transferConnection = {};
    };

    groupsService.getMyGroups().then(function (results) {
        if (results.data) {
            $scope.myGroups = results.data.groups;
        }
        $scope.resetCurrentZone();
    });

    zonesService.getBackendIds().then(function (results) {
        if (results.data) {
            $scope.backendIds = results.data;
        }
    });

    $scope.isGroupMember = function(groupId) {
        var groupMember = $scope.myGroups.find(function(group) {
            return groupId === group.id;
        });
        return groupMember !== undefined
    };

    /* Refreshes zone data set and then re-displays */
    $scope.refreshZones = function () {
        zonesPaging = pagingService.resetPaging(zonesPaging);
        function success(response) {
            $log.log('zonesService::getZones-success (' + response.data.zones.length + ' zones)');
            zonesPaging.next = response.data.nextId;
            updateZoneDisplay(response.data.zones);
            if (!$scope.query.length) {
                $scope.hasZones = response.data.zones.length > 0;
            }
        }

        return zonesService
            .getZones(zonesPaging.maxItems, undefined, $scope.query)
            .then(success)
            .catch(function (error) {
                handleError(error, 'zonesService::getZones-failure');
            });
    };

    function updateZoneDisplay (zones) {
        $scope.zones = zones;
        $scope.zonesLoaded = true;
        $log.log("Displaying zones: ", $scope.zones);
        if($scope.zones.length > 0) {
            $("td.dataTables_empty").hide();
        } else {
            $("td.dataTables_empty").show();
        }
    }

    /* Set total number of zones  */

    $scope.addZoneConnection = function () {
        if ($scope.processing) {
            $log.log('zoneConnection::processing is true; exiting');
            return;
        }

        //flag to prevent multiple clicks until previous promise has resolved.
        $scope.processing = true;
        zone = zonesService.checkBackendId(zone);

        zonesService.sendZone($scope.currentZone)
            .then(function () {
                $timeout($scope.refreshZones(), 1000);
                $("#zone_connection_modal").modal("hide");
                $scope.processing = false;
            })
            .catch(function (error){
                $("#zone_connection_modal").modal("hide");
                $scope.zoneError = true;
                handleError(error, 'zonesService::sendZone-failure');
                $scope.processing = false;
            });
    };

    $scope.confirmDeleteZone = function (zoneInfo) {
        $scope.currentZone = zoneInfo;
        $("#delete_zone_connection_modal").modal("show");
    };

    $scope.submitDeleteZone = function (id) {
        zonesService.delZone(id)
            .then(function () {
                $("#delete_zone_connection_modal").modal("hide");
                $scope.refreshZones();
            })
            .catch(function (error) {
                $("#delete_zone_connection_modal").modal("hide");
                $scope.zoneError = true;
                handleError(error, 'zonesService::sendZone-failure');
            });
    };

    $scope.cancel = function () {
        $scope.resetCurrentZone();
        $("#modal_zone_connect").modal("hide");
        $("#delete_zone_connection_modal").modal("hide");
    };

    function handleError(error, type) {
        $scope.zoneError = true;
        var alert = utilityService.failure(error, type);
        $scope.alerts.push(alert);

        if(error.data !== undefined && error.data.errors !== undefined) {
            var errors = error.data.errors;
            for(i in errors) {
                $scope.alerts.push({type: "danger", content:errors[i]});
            }
        }
    }

    /*
     * Zone set paging
     */
    $scope.prevPageEnabled = function () {
        return pagingService.prevPageEnabled(zonesPaging);
    };

    $scope.nextPageEnabled = function () {
        return pagingService.nextPageEnabled(zonesPaging);
    };

    $scope.getZonePageTitle = function () {
        return pagingService.getPanelTitle(zonesPaging);
    };

    $scope.prevPage = function () {
        var startFrom = pagingService.getPrevStartFrom(zonesPaging);
        return zonesService
            .getZones(zonesPaging.maxItems, startFrom, $scope.query)
            .then(function(response) {
                zonesPaging = pagingService.prevPageUpdate(response.data.nextId, zonesPaging);
                updateZoneDisplay(response.data.zones);
            })
            .catch(function (error) {
                handleError(error,'zonesService::prevPage-failure');
            });
    };

    $scope.nextPage = function () {
        return zonesService
            .getZones(zonesPaging.maxItems, zonesPaging.next, $scope.query)
            .then(function(response) {
                var zoneSets = response.data.zones;
                zonesPaging = pagingService.nextPageUpdate(zoneSets, response.data.nextId, zonesPaging);

                if (zoneSets.length > 0) {
                    updateZoneDisplay(response.data.zones);
                }
            })
            .catch(function (error) {
               handleError(error,'zonesService::nextPage-failure')
            });
    };

    $timeout($scope.refreshZones, 0);
});
