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
    $scope.allZonesLoaded = false;
    $scope.hasZones = false; // Re-assigned each time zones are fetched without a query

    $scope.query = "";

    // Paging status for zone sets
    var zonesPaging = pagingService.getNewPagingParams(100);
    var allZonesPaging = pagingService.getNewPagingParams(100);

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
            $scope.myGroupIds = results.data.groups.map(function(grp) {return grp['id']});
        }
        $scope.resetCurrentZone();
    });

    zonesService.getBackendIds().then(function (results) {
        if (results.data) {
            $scope.backendIds = results.data;
        }
    });

    $scope.canAccessGroup = function(groupId) {
        return $scope.myGroupIds.indexOf(groupId) > -1;
    };

    $scope.canAccessZone = function(accessLevel) {
        if (accessLevel == 'Read' || accessLevel == 'Delete') {
            return true;
        } else {
            return false;
        }
    };

    /* Refreshes zone data set and then re-displays */
    $scope.refreshZones = function () {
        zonesPaging = pagingService.resetPaging(zonesPaging);
        allZonesPaging = pagingService.resetPaging(allZonesPaging);

        zonesService
            .getZones(zonesPaging.maxItems, undefined, $scope.query)
            .then(function (response) {
                $log.log('zonesService::getZones-success (' + response.data.zones.length + ' zones)');
                zonesPaging.next = response.data.nextId;
                updateZoneDisplay(response.data.zones);
                if (!$scope.query.length) {
                    $scope.hasZones = response.data.zones.length > 0;
                }
            })
            .catch(function (error) {
                handleError(error, 'zonesService::getZones-failure');
            });

        zonesService
            .getZones(zonesPaging.maxItems, undefined, $scope.query, true)
            .then(function (response) {
                $log.log('zonesService::getZones-success (' + response.data.zones.length + ' zones)');
                allZonesPaging.next = response.data.nextId;
                updateAllZonesDisplay(response.data.zones);
            })
            .catch(function (error) {
                handleError(error, 'zonesService::getZones-failure');
            });
    };

    function updateZoneDisplay (zones) {
        $scope.zones = zones;
        $scope.myZoneIds = zones.map(function(zone) {return zone['id']});
        $scope.zonesLoaded = true;
        $log.log("Displaying my zones: ", $scope.zones);
        if($scope.zones.length > 0) {
            $("td.dataTables_empty").hide();
        } else {
            $("td.dataTables_empty").show();
        }
    }

    function updateAllZonesDisplay (zones) {
        $scope.allZones = zones;
        $scope.allZonesLoaded = true;
        $log.log("Displaying all zones: ", $scope.allZones);
        if($scope.allZones.length > 0) {
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
        $scope.currentZone = zonesService.checkBackendId($scope.currentZone);

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
     $scope.getZonesPageNumber = function(tab) {
         switch(tab) {
             case 'myZones':
                 return pagingService.getPanelTitle(zonesPaging);
             case 'allZones':
                 return pagingService.getPanelTitle(allZonesPaging);
         }
     };

    $scope.prevPageEnabled = function(tab) {
        switch(tab) {
            case 'myZones':
                return pagingService.prevPageEnabled(zonesPaging);
            case 'allZones':
                return pagingService.prevPageEnabled(allZonesPaging);
        }
    };

    $scope.nextPageEnabled = function(tab) {
        switch(tab) {
            case 'myZones':
                return pagingService.nextPageEnabled(zonesPaging);
            case 'allZones':
                return pagingService.nextPageEnabled(allZonesPaging);
        }
    };

    $scope.prevPageMyZones = function() {
        var startFrom = pagingService.getPrevStartFrom(zonesPaging);
        return zonesService
            .getZones(zonesPaging.maxItems, startFrom, $scope.query, false)
            .then(function(response) {
                zonesPaging = pagingService.prevPageUpdate(response.data.nextId, zonesPaging);
                updateZoneDisplay(response.data.zones);
            })
            .catch(function (error) {
                handleError(error,'zonesService::prevPage-failure');
            });
    }

    $scope.prevPageAllZones = function() {
        var startFrom = pagingService.getPrevStartFrom(allZonesPaging);
        return zonesService
            .getZones(allZonesPaging.maxItems, startFrom, $scope.query, true)
            .then(function(response) {
                allZonesPaging = pagingService.prevPageUpdate(response.data.nextId, allZonesPaging);
                updateAllZonesDisplay(response.data.zones);
            })
            .catch(function (error) {
                handleError(error,'zonesService::prevPage-failure');
            });
    }

    $scope.nextPageMyZones = function () {
        return zonesService
            .getZones(zonesPaging.maxItems, zonesPaging.next, $scope.query, false)
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

    $scope.nextPageAllZones = function () {
        return zonesService
            .getZones(allZonesPaging.maxItems, allZonesPaging.next, $scope.query, true)
            .then(function(response) {
                var zoneSets = response.data.zones;
                allZonesPaging = pagingService.nextPageUpdate(zoneSets, response.data.nextId, allZonesPaging);

                if (zoneSets.length > 0) {
                    updateAllZonesDisplay(response.data.zones);
                }
            })
            .catch(function (error) {
               handleError(error,'zonesService::nextPage-failure')
            });
    };

    $timeout($scope.refreshZones, 0);
});
