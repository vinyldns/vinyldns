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

angular.module('controller.generateZones', [])
    .controller('GenerateZonesController', function ($scope, $log, zonesService, groupsService, utilityService, $timeout, pagingService) {

    // State owned by this controller (shared state like $scope.alerts, $scope.myGroups,
    // $scope.query, $scope.ignoreAccess is inherited from the parent ZonesController scope)
    $scope.hasGeneratedZones = false;
    $scope.generatedZonesLoaded = false;
    $scope.nameserverSelection = {};
    $scope.createZone = {
        providerParams: {
            nameservers: [],
        }
    };
    $scope.createZone.ns_ipaddress = [];
    $scope.query = "";
    $scope.searchByAdminGroup = false;

    var generatedZonesPaging = pagingService.getNewPagingParams(100);

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

    $(document).ready(function () {
        $('#zone-nameservers').select2({
            placeholder: "Select nameservers",
            allowClear: true,
            width: '100%'
        });
    });

    $scope.resetCreateZone = function () {
        $scope.createZone = {};
        if ($scope.myGroups && $scope.myGroups.length) {
            $scope.createZone.groupId = $scope.myGroups[0].id;
        }
        $scope.createZone.providerParams = {};
        $scope.createZone.providerParams.nameservers = [];
        $scope.nameserverSelection = {};
        $scope.createZone.ns_ipaddress = [];
    };

    $scope.viewCreatedZone = function (createdZone) {
        $log.debug(createdZone);
        $scope.createZone = angular.copy(createdZone);
        $scope.isEditMode = false;
        $scope.isDelete = false;

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

    $scope.updateCreatedInfo = function (createdZone, action) {
        $log.debug(createdZone);
        $scope.createZone = angular.copy(createdZone);
        $scope.isDelete = action === 'delete';
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

    $scope.updateNameserverSelection = function (ns) {
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

    $scope.getCreateZoneTemplate = function (provider) {
        return zonesService.getCreateZoneTemplate(provider).then(function (results) {
            const createZoneFields = results.data["request-templates"]["create-zone"];
            $scope.requiredFields = results.data["required-fields"]["create-zone"];
            const createZone = JSON.parse(createZoneFields);
            $scope.createZoneTemplate = createZone;

            // Reset provider-specific params when switching providers so stale keys from a
            // previously selected provider (e.g. 'kind' from powerdns) don't persist into
            // the new provider's providerParams and fail its schema's additionalProperties check.
            const currentNameservers = ($scope.createZone.providerParams && $scope.createZone.providerParams.nameservers) || [];
            $scope.createZone.providerParams = { nameservers: currentNameservers };

            $scope.zoneFields = Object.entries(createZone).map(([label, config]) => {
                const field = label.replace(/ /g, '').toLowerCase();
                const isSelect = config.type.toLowerCase().includes('select');
                const value = isSelect ? config.value.split(',').map(v => v.trim()) : config.value;

                if ($scope.createZone[field] === undefined) {
                    $scope.createZone[field] = config.type.toLowerCase() === 'multi-select' ? [] : '';
                }
                return { label, type: config.type, value, field, editable: true };
            });
        });
    };

    $scope.isCreateZone = function () {
        $scope.isEditMode = false;
        $scope.resetCreateZone();
    };

    $scope.getUpdateZoneTemplate = function (provider) {
        if (!$scope.createZone) { $scope.createZone = {}; }
        return zonesService.getCreateZoneTemplate(provider).then(function (results) {
            const updateZoneFields = results.data["request-templates"]["update-zone"];
            $scope.UpdateRequiredFields = results.data["required-fields"]["update-zone"];
            const updateZone = JSON.parse(updateZoneFields);
            $scope.updateZoneTemplate = updateZone;
            $scope.isEditMode = true;

            if (!$scope.createZone.providerParams) {
                $scope.createZone.providerParams = {};
            }
            $scope.zoneFields = Object.entries(updateZone).map(([label, config]) => {
                const field = label.replace(/ /g, '').toLowerCase();
                const isSelect = config.type.toLowerCase().includes('select');
                const value = isSelect ? config.value.split(',').map(v => v.trim()) : config.value;

                // Only initialize providerParams entry if not already populated from existing zone data
                if ($scope.createZone.providerParams[field] === undefined || $scope.createZone.providerParams[field] === null) {
                    $scope.createZone.providerParams[field] = config.type.toLowerCase() === 'multi-select' ? [] : '';
                }
                return { label, type: config.type, value, field, editable: true };
            });
        });
    };

    $scope.isDynamicZoneFieldRequired = function (fieldName) {
        const requiredList = $scope.isEditMode ? $scope.UpdateRequiredFields : $scope.requiredFields;
        return requiredList && requiredList.indexOf(fieldName) !== -1;
    };

    $scope.dynamicZoneFieldToggleSelection = function (fieldKey, value) {
        if (!$scope.createZone.providerParams[fieldKey]) {
            $scope.createZone.providerParams[fieldKey] = [];
        }
        const list = $scope.createZone.providerParams[fieldKey];
        const idx = list.indexOf(value);
        if (idx > -1) { list.splice(idx, 1); } else { list.push(value); }
    };

    $scope.formatLabel = function (label) {
        return label ? label.replace(/_/g, ' ') : '';
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
                .catch(function (error) {
                    $("#zone_creation_modal").modal("hide");
                    $scope.zoneError = true;
                    handleError(error, 'zonesService::generateZone-failure');
                    $scope.processing = false;
                });
        } else {
            // update zone service
            const payload = $scope.getUpdatePayload($scope.createZone);
            zonesService.updateGeneratedZone(payload)
                .then(function (response) {
                    $("#zone_creation_modal").modal("hide");
                    $scope.processing = false;
                    $timeout($scope.refreshGeneratedZones, 1000);
                    const zoneName = $scope.createZone.zoneName || 'unknown';
                    const msg = `${response.statusText} (HTTP ${response.status}): '${zoneName}' updated`;
                    $scope.alerts = $scope.alerts || [];
                    $scope.alerts.push({ type: "success", content: msg });
                })
                .catch(function (error) {
                    $("#zone_creation_modal").modal("hide");
                    $scope.zoneError = true;
                    handleError(error, 'zonesService::generateZone-failure');
                    $scope.processing = false;
                });
        }
    };

    $scope.deleteCreatedZone = function () {
        $scope.confirmingDelete = true;
    };

    $scope.confirmDeleteCreatedZone = function () {
        $scope.confirmingDelete = false;
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
                $scope.confirmingDelete = false;
                $("#zone_creation_view_modal").modal("hide");
                handleError(error, 'zonesService::sendZone-failure');
            });
    };

    $scope.cancelDeleteCreatedZone = function () {
        $scope.confirmingDelete = false;
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

    function updateGeneratedZoneDisplay (zones) {
        $scope.generatedZones = zones;
        $scope.myGeneratedZoneIds = zones.map(function (zone) { return zone['id']; });
        $scope.generatedZonesLoaded = true;
        $log.debug("Displaying generated zones: ", $scope.generatedZones);
        if ($scope.generatedZones.length > 0) {
            $("td.dataTables_empty").hide();
        } else {
            $("td.dataTables_empty").show();
        }
    }

    // These override the parent's paging functions for elements within this controller's scope
    $scope.getZonesPageNumber = function (tab) {
        return pagingService.getPanelTitle(generatedZonesPaging);
    };

    $scope.prevPageEnabled = function (tab) {
        return pagingService.prevPageEnabled(generatedZonesPaging);
    };

    $scope.nextPageEnabled = function (tab) {
        return pagingService.nextPageEnabled(generatedZonesPaging);
    };

    $scope.prevPageGeneratedZones = function () {
        var startFrom = pagingService.getPrevStartFrom(generatedZonesPaging);
        return zonesService
            .getGeneratedZones(generatedZonesPaging.maxItems, startFrom, $scope.query, $scope.searchByAdminGroup, false)
            .then(function (response) {
                generatedZonesPaging = pagingService.prevPageUpdate(response.data.nextId, generatedZonesPaging);
                updateGeneratedZoneDisplay(response.data.zones);
            })
            .catch(function (error) {
                handleError(error, 'zonesService::prevPage-failure');
            });
    };

    $scope.nextPageGeneratedZones = function () {
        return zonesService
            .getGeneratedZones(generatedZonesPaging.maxItems, generatedZonesPaging.next, $scope.query, $scope.searchByAdminGroup, false)
            .then(function (response) {
                var generatedZones = response.data.zones;
                generatedZonesPaging = pagingService.nextPageUpdate(generatedZones, response.data.nextId, generatedZonesPaging);
                if (generatedZones.length > 0) {
                    updateGeneratedZoneDisplay(response.data.zones);
                }
            })
            .catch(function (error) {
                handleError(error, 'zonesService::nextPage-failure');
            });
    };

    // Autocomplete for the generated zone name search input. Defined as a jQuery-namespaced
    // function so the parent controller's isGroupSearch handler can restore it on uncheck.
    $.generateZoneAutocompleteSearch = function () {
        $(".generateZone-search-text").autocomplete({
            source: function (request, response) {
                $.ajax({
                    url: "/api/zones/generate/info?maxItems=100",
                    dataType: "json",
                    data: {
                        nameFilter: request.term,
                        ignoreAccess: $scope.ignoreAccess
                    },
                    success: function (data) {
                        if (data && Array.isArray(data.zones)) {
                            response(data.zones.map(zone => ({ label: zone.zoneName, value: zone.zoneName })));
                        } else {
                            response([]);
                        }
                    },
                    error: function () {
                        response([]);
                    }
                });
            },
            minLength: 1,
            select: function (event, ui) {
                $scope.$apply(function () { $scope.query = ui.item.value; });
                $(this).val(ui.item.value);
                return false;
            },
            open: function () { $(this).removeClass("ui-corner-all").addClass("ui-corner-top"); },
            close: function () { $(this).removeClass("ui-corner-top").addClass("ui-corner-all"); }
        });
    };

    $.generateZoneAutocompleteSearch();

    // When "Search by admin group" is toggled, rewire only this tab's search input.
    // The parent controller handles the equivalent rewiring for .zone-search-text.
    $('.isGroupSearch').change(function () {
        if (this.checked) {
            $(".generateZone-search-text").autocomplete({
                source: function (request, response) {
                    $.ajax({
                        url: "/api/groups?maxItems=100&abridged=true",
                        dataType: "json",
                        data: { groupNameFilter: request.term, ignoreAccess: $scope.ignoreAccess },
                        success: function (data) {
                            const search = JSON.parse(JSON.stringify(data));
                            response($.map(search.groups, function (group) {
                                return { value: group.name, label: group.name };
                            }));
                        }
                    });
                },
                minLength: 1,
                select: function (event, ui) {
                    $scope.$apply(function () { $scope.query = ui.item.value; });
                    $(this).val(ui.item.value);
                    return false;
                },
                open: function () { $(this).removeClass("ui-corner-all").addClass("ui-corner-top"); },
                close: function () { $(this).removeClass("ui-corner-top").addClass("ui-corner-all"); }
            });
        } else {
            $.generateZoneAutocompleteSearch();
        }
    });

    function handleError(error, type) {
        $scope.zoneError = true;
        var alert = utilityService.failure(error, type);
        $scope.alerts.push(alert);
        if (error.data !== undefined && error.data.errors !== undefined) {
            var errors = error.data.errors;
            for (i in errors) {
                $scope.alerts.push({ type: "danger", content: errors[i] });
            }
        }
    }

    $timeout($scope.refreshGeneratedZones, 0);
});
