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
    $scope.hasGeneratedZones = false;
    $scope.generatedZonesLoaded = false;
    $scope.allGroups = [];
    $scope.myDeletedZones = [];
    $scope.allDeletedZones = [];
    $scope.ignoreAccess = false;
    $scope.validEmailDomains= [];
    $scope.nameserverSelection = {};
    $scope.allZonesAccess = function () {
        $scope.ignoreAccess = true;
    }

    $scope.myZonesAccess = function () {
        $scope.ignoreAccess = false;
    }

    $scope.formatLabel = function(label) {
        return label ? label.replace(/_/g, ' ') : '';
    };

    $scope.query = "";
    $scope.includeReverse = true;
    $scope.keyAlgorithms = ['HMAC-MD5', 'HMAC-SHA1', 'HMAC-SHA224', 'HMAC-SHA256', 'HMAC-SHA384', 'HMAC-SHA512'];
    $scope.createZone = {
        providerParams : {
            nameservers: [],
        }
    };
    $scope.createZone.ns_ipaddress = [];

    // Paging status for zone sets
    var zonesPaging = pagingService.getNewPagingParams(100);
    var allZonesPaging = pagingService.getNewPagingParams(100);
    var myDeletedZonesPaging = pagingService.getNewPagingParams(100);
    var allDeletedZonesPaging = pagingService.getNewPagingParams(100);
    var generatedZonesPaging = pagingService.getNewPagingParams(100);
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
        $scope.validDomains();
        if($scope.myGroups && $scope.myGroups.length) {
                $scope.currentZone.adminGroupId = $scope.myGroups[0].id;
        }
        $scope.currentZone.connection = {};
        $scope.currentZone.transferConnection = {};
    };

    $scope.resetCreateZone = function () {
        $scope.createZone = {};
        if($scope.myGroups && $scope.myGroups.length) {
            $scope.createZone.groupId = $scope.myGroups[0].id;
        }
        $scope.createZone.providerParams = {};
        $scope.createZone.providerParams.nameservers = [];
        $scope.nameserverSelection = {};
        $scope.createZone.ns_ipaddress = [];
    };

    $scope.viewCreatedZone = function (createdZone) {
        $log.debug(createdZone)
        $scope.createZone = angular.copy(createdZone);
        $scope.isEditMode = false;

        if (createdZone.provider) {
            $scope.getCreateZoneTemplate(createdZone.provider).then(function () {
                $scope.zoneFields.forEach(function (field) {
                    const fieldKey = field.field;
                    if ($scope.createZone[fieldKey] === undefined || $scope.createZone[fieldKey] === null) {
                        $scope.createZone[fieldKey] = '';
                    }
                });
            });
        }

    };

    $scope.updateCreatedInfo = function (createdZone,action) {
        $log.debug(createdZone);
        $scope.createZone = angular.copy(createdZone);

        if (action === 'delete') {
            $scope.isDelete = true
        }else $scope.isDelete = false

        if (createdZone.provider) {
            $scope.getUpdateZoneTemplate(createdZone.provider);
        }
    };

    $scope.addNameserver = function () {
      $scope.createZone.providerParams.nameservers.push('');
    };

    $scope.removeNameserver = function (index) {
      $scope.createZone.providerParams.nameservers.splice(index, 1);
    };

    $(document).ready(function () {
      $('#zone-nameservers').multiselect({
        includeSelectAllOption: true,
        enableFiltering: true,
        buttonWidth: '100%'
      });
    });



    $scope.updateNameserverSelection = function(ns) {
      if ($scope.nameserverSelection[ns]) {
        if ($scope.createZone.providerParams.nameservers.indexOf(ns) === -1) {
          $scope.createZone.providerParams.nameservers.push(ns);
        }
      } else {
        const idx = $scope.createZone.providerParams.nameservers.indexOf(ns);
        if (idx !== -1) {
          $scope.createZone.providerParams.nameservers.splice(idx, 1);
        }
      }
    };

    groupsService.getGroups(true, "").then(function (results) {
        if (results.data) {
            // Get all groups where the group members include the current user
            $scope.myGroups = results.data.groups.filter(grp => grp.members.findIndex(mem => mem.id === $scope.profile.id) >= 0);
            $scope.myGroupIds = $scope.myGroups.map(grp => grp.id);
            $scope.allGroups = results.data.groups;
        }
        $scope.resetCurrentZone();
    });

    zonesService.getBackendIds().then(function (results) {
        if (results.data) {
            $scope.backendIds = results.data;
        }
    });

    zonesService.getNameservers().then(function (results) {
        if (results.data) {
            $scope.nameservers = results.data;
        }
    });

    zonesService.getAllowedDNSProviders().then(function (results) {
        if (results.data) {
            $scope.provider = results.data.allowedDNSProviders;
        }
    });

    $scope.getCreateZoneTemplate = function(provider) {
        return zonesService.getCreateZoneTemplate(provider).then(function(results) {
            const createZoneFields = results.data["request-templates"]["create-zone"];
            $scope.requiredFields = results.data["required-fields"]["create-zone"];
            const createZone = JSON.parse(createZoneFields);
            $scope.createZoneTemplate = createZone;

            $scope.zoneFields = Object.entries(createZone).map(([label, config]) => {
                const field = label.replace(/ /g, '').toLowerCase();
                const isSelect = config.type.toLowerCase().includes('select');
                const value = isSelect ? config.value.split(',').map(v => v.trim()) : config.value;

                // Only initialize if undefined
                if ($scope.createZone[field] === undefined) {
                    $scope.createZone[field] = config.type.toLowerCase() === 'multi-select' ? [] : '';
                }
                return {
                    label,
                    type: config.type,
                    value,
                    field,
                    editable: true
                };
            });
        });
    };

    $scope.isCreateZone = function() {
        $scope.isEditMode = false;
        $scope.resetCreateZone();
    }

    $scope.getUpdatePayload = function(zone) {
        const payload = {};
        const template = $scope.updateZoneTemplate;

        if (template && typeof template === 'object') {
            Object.keys(template).forEach(function(label) {
                const field = label.replace(/ /g, '').toLowerCase();
                if (zone.hasOwnProperty(field)) {
                    payload[field] = zone[field];
                }
            });
        } else {
            $log.warn("updateZoneTemplate is not set or invalid.");
        }
        return payload;
    };

    $scope.getUpdateZoneTemplate = function(provider) {
        if (!$scope.createZone) {
                $scope.createZone = {};
        }
        return zonesService.getCreateZoneTemplate(provider).then(function(results) {
            const createZoneFields = JSON.parse(results.data["request-templates"]["create-zone"]);
            const updateZoneFields = results.data["request-templates"]["update-zone"];
            $scope.UpdateRequiredFields = results.data["required-fields"]["update-zone"];
            const updateZone = JSON.parse(updateZoneFields);
            $scope.updateZoneTemplate = updateZone;
            $scope.isEditMode = true;

            $scope.zoneFields = Object.entries(createZoneFields).map(([label, config]) => {
                const field = label.replace(/ /g, '').toLowerCase();
                const isSelect = config.type.toLowerCase().includes('select');
                const value = isSelect ? config.value.split(',').map(v => v.trim()) : config.value;

                // Only initialize if undefined
                if ($scope.createZone[field] === undefined || $scope.createZone[field] === null) {
                    $scope.createZone[field] = config.type.toLowerCase() === 'multi-select' ? [] : '';
                }

                return {
                    label,
                    type: config.type,
                    value,
                    field,
                    editable: true
                };
            });
        });
    };

    $scope.isDynamicZoneFieldRequired = function(fieldName) {
      return $scope.requiredFields && $scope.requiredFields.indexOf(fieldName) !== -1;
    };

    $scope.dynamicZoneFieldToggleSelection = function(fieldKey, value) {
        if (!$scope.createZone.providerParams[fieldKey]) {
            $scope.createZone.providerParams[fieldKey] = [];
        }
        const list = $scope.createZone.providerParams[fieldKey];
        const idx = list.indexOf(value);
        if (idx > -1) {
            list.splice(idx, 1);
        } else {
            list.push(value);
        }
    };

    $scope.canAccessGroup = function(groupId) {
         return $scope.myGroupIds !== undefined &&  $scope.myGroupIds.indexOf(groupId) > -1;
    };

    $scope.canAccessZone = function(accessLevel) {
        if (accessLevel == 'Read' || accessLevel == 'Delete') {
            return true;
        } else {
            return false;
        }
    };

    $.zoneAutocompleteSearch = function() {
    $(".zone-search-text, .generateZone-search-text").each(function() {
        const $input = $(this);
        const isGenerateZone = $input.hasClass("generateZone-search-text");

        $input.autocomplete({
        source: function(request, response) {
            if (isGenerateZone) {
            $.ajax({
                url: "/api/zones/generate/info?maxItems=100",
                dataType: "json",
                data: {
                nameFilter: request.term,
                ignoreAccess: $scope.ignoreAccess
                },
                success: function(data) {
                if (data && Array.isArray(data.zones)) {
                    const suggestions = data.zones.map(zone => ({
                    label: zone.zoneName,
                    value: zone.zoneName
                    }));
                    response(suggestions);
                } else {
                    response([]);
                }
                },
                error: function(xhr, status, error) {
                console.error('Error fetching generated zones:', error);
                response([]);
                }
            });
            } else {
            $.ajax({
                url: "/api/zones?maxItems=100",
                dataType: "json",
                data: {
                nameFilter: request.term,
                ignoreAccess: $scope.ignoreAccess
                },
                success: function(data) {
                if (data && Array.isArray(data.zones)) {
                    response(data.zones.map(zone => ({
                    label: zone.name,
                    value: zone.name
                    })));
                } else {
                    response([]);
                }
                },
                error: function(xhr, status, error) {
                console.error('Error fetching zones:', error);
                response([]);
                }
            });
            }
        },
        minLength: 1,
        select: function(event, ui) {
            $scope.$apply(function() {
            $scope.query = ui.item.value;
            });
            $(this).val(ui.item.value);
            return false;
        },
        open: function() {
            $(this).removeClass("ui-corner-all").addClass("ui-corner-top");
        },
        close: function() {
            $(this).removeClass("ui-corner-top").addClass("ui-corner-all");
        }
        });
    });
    };

    // Should be the default autocomplete search result option
    $.zoneAutocompleteSearch();
    
    $('.isGroupSearch').change(function() {
        if(this.checked) {
            // Autocomplete for search by admin group
            $(".zone-search-text").autocomplete({
              source: function( request, response ) {
                $.ajax({
                  url: "/api/groups?maxItems=100&abridged=true",
                  dataType: "json",
                  data: {groupNameFilter: request.term, ignoreAccess: $scope.ignoreAccess},
                  success: function(data) {
                      const search =  JSON.parse(JSON.stringify(data));
                      response($.map(search.groups, function(group) {
                      return {value: group.name, label: group.name}
                      }))
                  }
                });
              },
              minLength: 1,
              select: function (event, ui) {
                  $scope.query = ui.item.value;
                  $(".zone-search-text").val(ui.item.value);
                  return false;
                },
              open: function() {
                $(this).removeClass("ui-corner-all").addClass("ui-corner-top");
              },
              close: function() {
                $(this).removeClass("ui-corner-top").addClass("ui-corner-all");
              }
            });
        } else {
            $.zoneAutocompleteSearch();
        }
    });

    // Autocomplete text-highlight
    $.ui.autocomplete.prototype._renderItem = function(ul, item) {
            let txt = String(item.label).replace(new RegExp(this.term, "gi"),"<b>$&</b>");
            return $("<li></li>")
                  .data("ui-autocomplete-item", item.value)
                  .append("<div>" + txt + "</div>")
                  .appendTo(ul);
    };
   
    /* Refreshes zone data set and then re-displays */
    $scope.refreshZones = function () {
        zonesPaging = pagingService.resetPaging(zonesPaging);
        allZonesPaging = pagingService.resetPaging(allZonesPaging);
        myDeletedZonesPaging = pagingService.resetPaging(myDeletedZonesPaging);
        allDeletedZonesPaging = pagingService.resetPaging(allDeletedZonesPaging);

        zonesService
            .getZones(zonesPaging.maxItems, undefined, $scope.query, $scope.searchByAdminGroup, false, $scope.includeReverse)
            .then(function (response) {
                $log.debug('zonesService::getZones-success (' + response.data.zones.length + ' zones)');
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
            .getZones(zonesPaging.maxItems, undefined, $scope.query, $scope.searchByAdminGroup, true, $scope.includeReverse)
            .then(function (response) {
                $log.debug('zonesService::getZones-success (' + response.data.zones.length + ' zones)');
                allZonesPaging.next = response.data.nextId;
                updateAllZonesDisplay(response.data.zones);
            })
            .catch(function (error) {
                handleError(error, 'zonesService::getZones-failure');
            });

        zonesService
            .getDeletedZones(myDeletedZonesPaging.maxItems, undefined, $scope.query, false)
            .then(function (response) {
                $log.debug('zonesService::getMyDeletedZones-success (' + response.data.zonesDeletedInfo.length + ' zones)');
                myDeletedZonesPaging.next = response.data.nextId;
                updateMyDeletedZoneDisplay(response.data.zonesDeletedInfo);
            })
            .catch(function (error) {
                handleError(error, 'zonesService::getDeletedZones-failure');
            });

        zonesService
            .getDeletedZones(allDeletedZonesPaging.maxItems, undefined, $scope.query, true)
            .then(function (response) {
                $log.debug('zonesService::getAllDeletedZones-success (' + response.data.zonesDeletedInfo.length + ' zones)');
                allDeletedZonesPaging.next = response.data.nextId;
                updateAllDeletedZoneDisplay(response.data.zonesDeletedInfo);
            })
            .catch(function (error) {
                handleError(error, 'zonesService::getDeletedZones-failure');
            });
    };

    $scope.refreshGeneratedZones = function () {
        generatedZonesPaging = pagingService.resetPaging(generatedZonesPaging);
        zonesService
            .getGeneratedZones(generatedZonesPaging.maxItems, undefined, $scope.query, $scope.searchByAdminGroup, false)
            .then(function (response) {
                $log.debug('zonesService::getGeneratedZones-success (' + response.data.zones.length + ' zones)');
                generatedZonesPaging.next = response.data.nextId;
                updateGeneratedZoneDisplay(response.data.zones);

                if (!$scope.query.length) {
                    $scope.hasGeneratedZones = response.data.zones.length > 0;
                }
            })
            .catch(function (error) {
                handleError(error, 'zonesService::getGeneratedZones-failure');
            });
    };

    function updateMyDeletedZoneDisplay (myDeletedZones) {
        $scope.myDeletedZones = myDeletedZones;
        $scope.myDeletedZonesLoaded = true;
        $log.debug("Displaying my Deleted zones: ", $scope.myDeletedZones);
                if($scope.myDeletedZones.length > 0) {
                    $("td.dataTables_empty").hide();
                } else {
                    $("td.dataTables_empty").show();
                }
    }

    function updateAllDeletedZoneDisplay (allDeletedZones) {
        $scope.allDeletedZones = allDeletedZones;
        $scope.allDeletedZonesLoaded = true;
        $log.debug("Displaying all Deleted zones: ", $scope.allDeletedZones);
                if($scope.allDeletedZones.length > 0) {
                    $("td.dataTables_empty").hide();
                } else {
                    $("td.dataTables_empty").show();
                }
    }

    function updateGeneratedZoneDisplay (zones) {
        $scope.generatedZones = zones;
        $scope.myGeneratedZoneIds = zones.map(function(zone) {return zone['id']});
        $scope.generatedZonesLoaded = true;
        $log.debug("Displaying generated zones: ", $scope.generatedZones);
        if($scope.generatedZones.length > 0) {
            $("td.dataTables_empty").hide();
        } else {
            $("td.dataTables_empty").show();
        }
    }

    function updateZoneDisplay (zones) {
        $scope.zones = zones;
        $scope.myZoneIds = zones.map(function(zone) {return zone['id']});
        $scope.zonesLoaded = true;
        $log.debug("Displaying my zones: ", $scope.zones);
        if($scope.zones.length > 0) {
            $("td.dataTables_empty").hide();
        } else {
            $("td.dataTables_empty").show();
        }
    }

    function updateAllZonesDisplay (zones) {
        $scope.allZones = zones;
        $scope.allZonesLoaded = true;
        $log.debug("Displaying all zones: ", $scope.allZones);
        if($scope.allZones.length > 0) {
            $("td.dataTables_empty").hide();
        } else {
            $("td.dataTables_empty").show();
        }
    }
    $scope.validDomains=function getValidEmailDomains() {
                function success(response) {
                    $log.debug('zonesService::listEmailDomains-success', response);
                    return $scope.validEmailDomains = response.data;
                }

                return groupsService
                    .listEmailDomains($scope.ignoreAccess, $scope.query)
                    .then(success)
                    .catch(function (error) {
                        handleError(error, 'zonesService::listEmailDomains-failure');
                    });
            }

    /* Set total number of zones  */

    $scope.addZoneConnection = function () {
        if ($scope.processing) {
            $log.debug('zoneConnection::processing is true; exiting');
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

    $scope.getUpdatePayload = function (zone) {
      const providerParams = {};
      const template = $scope.updateZoneTemplate || {};
      for (const label in template) {
        const field = label.replace(/ /g, '').toLowerCase();
        const value = zone.providerParams[field];
        if (
          value !== undefined &&
          value !== null &&
          !(typeof value === 'string' && value.trim() === '') &&
          !(Array.isArray(value) && value.length === 0)
        ) {
          providerParams[field] = value;
        }
      }
      return {
        groupId: zone.groupId,
        zoneName: zone.zoneName,
        email: zone.email,
        provider: zone.provider,
        providerParams
      };
    };

    $scope.addZoneCreation = function () {
        if ($scope.processing) {
            $log.debug('zoneCreation::processing is true; exiting');
            return;
        }
        $scope.processing = true;
        if (!$scope.isEditMode) {
        // create zone service
            zonesService.generateZone($scope.createZone)
                .then(function (response) {
                    $timeout($scope.refreshGeneratedZones(), 1000);
                    $("#zone_creation_modal").modal("hide");
                    $scope.processing = false;
                    const zoneName = $scope.createZone.zoneName || 'unknown';
                    const msg = `${response.statusText} (HTTP ${response.status}): '${zoneName}' created`;
                    $scope.alerts = $scope.alerts || [];
                    $scope.alerts.push({ type: "success", content: msg });
                })
                .catch(function (error){
                    $("#zone_creation_modal").modal("hide");
                    $scope.zoneError = true;
                    handleError(error, 'zonesService::generateZone-failure');
                    $scope.processing = false;
                });
        }else {
        // update zone service
            const payload = $scope.getUpdatePayload($scope.createZone);
            zonesService.updateGeneratedZone(payload)
              .then(function (response) {
                $("#zone_creation_modal").modal("hide");
                $scope.processing = false;
                const zoneName = $scope.createZone.zoneName || 'unknown';
                const msg = `${response.statusText} (HTTP ${response.status}): '${zoneName}' updated`;
                $scope.alerts = $scope.alerts || [];
                $scope.alerts.push({ type: "success", content: msg });
              })
              .catch(function (error){
                $("#zone_creation_modal").modal("hide");
                $scope.zoneError = true;
                handleError(error, 'zonesService::generateZone-failure');
                $scope.processing = false;
              });
        }
    };

    $scope.deleteCreatedZone = function () {
        if (!confirm("Are you sure you want to delete this zone?")) return;
        zonesService.deleteGeneratedZone($scope.createZone.id)
            .then(function (response) {
                $("#zone_creation_view_modal").modal("hide");
                $log.debug("Deleting zone with ID:", $scope.createZone.id);
                const zoneName = $scope.createZone.zoneName || 'unknown';
                const msg = `${response.statusText} (HTTP ${response.status}): '${zoneName}' deleted`;
                $scope.alerts = $scope.alerts || [];
                $scope.alerts.push({ type: "success", content: msg });
            })
            .catch(function (error) {
                $("#zone_creation_view_modal").modal("hide");
                handleError(error, 'zonesService::sendZone-failure');
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
             case 'myDeletedZones':
                 return pagingService.getPanelTitle(myDeletedZonesPaging);
             case 'allDeletedZones':
                 return pagingService.getPanelTitle(allDeletedZonesPaging);
             case 'generatedZones':
                 return pagingService.getPanelTitle(generatedZonesPaging);
         }
     };

    $scope.prevPageEnabled = function(tab) {
        switch(tab) {
            case 'myZones':
                return pagingService.prevPageEnabled(zonesPaging);
            case 'allZones':
                return pagingService.prevPageEnabled(allZonesPaging);
            case 'myDeletedZones':
                return pagingService.prevPageEnabled(myDeletedZonesPaging);
            case 'allDeletedZones':
                return pagingService.prevPageEnabled(allDeletedZonesPaging);
            case 'generatedZones':
                return pagingService.prevPageEnabled(generatedZonesPaging);
        }
    };

    $scope.nextPageEnabled = function(tab) {
        switch(tab) {
            case 'myZones':
                return pagingService.nextPageEnabled(zonesPaging);
            case 'allZones':
                return pagingService.nextPageEnabled(allZonesPaging);
            case 'myDeletedZones':
                return pagingService.nextPageEnabled(myDeletedZonesPaging);
            case 'allDeletedZones':
                return pagingService.nextPageEnabled(allDeletedZonesPaging);
            case 'generatedZones':
                return pagingService.nextPageEnabled(generatedZonesPaging);
        }
    };

    $scope.prevPageMyZones = function() {
        var startFrom = pagingService.getPrevStartFrom(zonesPaging);
        return zonesService
            .getZones(zonesPaging.maxItems, startFrom, $scope.query, $scope.searchByAdminGroup, false, true)
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
            .getZones(allZonesPaging.maxItems, startFrom, $scope.query, $scope.searchByAdminGroup, true, true)
            .then(function(response) {
                allZonesPaging = pagingService.prevPageUpdate(response.data.nextId, allZonesPaging);
                updateAllZonesDisplay(response.data.zones);
            })
            .catch(function (error) {
                handleError(error,'zonesService::prevPage-failure');
            });
    }

    $scope.prevPageMyDeletedZones = function() {
        var startFrom = pagingService.getPrevStartFrom(myDeletedZonesPaging);
        return zonesService
            .getDeletedZones(myDeletedZonesPaging.maxItems, startFrom, $scope.query, false)
            .then(function(response) {
                myDeletedZonesPaging = pagingService.prevPageUpdate(response.data.nextId, myDeletedZonesPaging);
                updateMyDeletedZoneDisplay(response.data.zonesDeletedInfo);
            })
            .catch(function (error) {
                handleError(error,'zonesService::prevPage-failure');
            });
    }

    $scope.prevPageAllDeletedZones = function() {
        var startFrom = pagingService.getPrevStartFrom(allDeletedZonesPaging);
        return zonesService
            .getDeletedZones(allDeletedZonesPaging.maxItems, startFrom, $scope.query, true)
            .then(function(response) {
                allDeletedZonesPaging = pagingService.prevPageUpdate(response.data.nextId, allDeletedZonesPaging);
                updateAllDeletedZoneDisplay(response.data.zonesDeletedInfo);
        })
        .catch(function (error) {
            handleError(error,'zonesService::prevPage-failure');
        });
    }

    $scope.prevPageGeneratedZones = function() {
        var startFrom = pagingService.getPrevStartFrom(generatedZonesPaging);
        return zonesService
            .getGeneratedZones(generatedZonesPaging.maxItems, startFrom, $scope.query, $scope.searchByAdminGroup, false)
            .then(function(response) {
                generatedZonesPaging = pagingService.prevPageUpdate(response.data.nextId, generatedZonesPaging);
                updateGeneratedZoneDisplay(response.data.zones);
            })
            .catch(function (error) {
                handleError(error,'zonesService::prevPage-failure');
            });
    }

    $scope.nextPageMyZones = function () {
        return zonesService
            .getZones(zonesPaging.maxItems, zonesPaging.next, $scope.query, $scope.searchByAdminGroup, false, true)
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
            .getZones(allZonesPaging.maxItems, allZonesPaging.next, $scope.query, $scope.searchByAdminGroup, true, true)
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

    $scope.nextPageMyDeletedZones = function () {
        return zonesService
            .getDeletedZones(myDeletedZonesPaging.maxItems, myDeletedZonesPaging.next, $scope.query, false)
            .then(function(response) {
                var myDeletedZoneSets = response.data.zonesDeletedInfo;
                myDeletedZonesPaging = pagingService.nextPageUpdate(myDeletedZoneSets, response.data.nextId, myDeletedZonesPaging);

                if (myDeletedZoneSets.length > 0) {
                    updateMyDeletedZoneDisplay(response.data.zonesDeletedInfo);
                }
            })
            .catch(function (error) {
               handleError(error,'zonesService::nextPage-failure')
            });
    };

    $scope.nextPageAllDeletedZones = function () {
        return zonesService
            .getDeletedZones(allDeletedZonesPaging.maxItems, allDeletedZonesPaging.next, $scope.query, true)
            .then(function(response) {
                var allDeletedZoneSets = response.data.zonesDeletedInfo;
                allDeletedZonesPaging = pagingService.nextPageUpdate(allDeletedZoneSets, response.data.nextId, allDeletedZonesPaging);

                if (allDeletedZoneSets.length > 0) {
                    updateAllDeletedZoneDisplay(response.data.zonesDeletedInfo);
                }
            })
            .catch(function (error) {
               handleError(error,'zonesService::nextPage-failure')
            });
    };

    $scope.nextPageGeneratedZones = function () {
        return zonesService
            .getGeneratedZones(generatedZonesPaging.maxItems, generatedZonesPaging.next, $scope.query, $scope.searchByAdminGroup, false)
            .then(function(response) {
                var generatedZones = response.data.zones;
                generatedZonesPaging = pagingService.nextPageUpdate(generatedZones, response.data.nextId, generatedZonesPaging);

                if (generatedZones.length > 0) {
                    updateGeneratedZoneDisplay(response.data.zones);
                }
            })
            .catch(function (error) {
               handleError(error,'zonesService::nextPage-failure')
            });
    };

    $timeout($scope.refreshZones, 0);
    $timeout($scope.refreshGeneratedZones, 0);
});
