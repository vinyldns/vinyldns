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

(function() {
    'use strict';

    angular.module('recordset')
        .controller('RecordSetsController', function($scope, $log, $location, $timeout, recordsService, utilityService, pagingService, groupsService){

            $scope.recordSet = {};
            $scope.recordSetChanges = {};
            $scope.alerts = [];
            $scope.nameSort = "asc";
            $scope.nameSortSymbol = "fa-chevron-up";
            $scope.readRecordTypes = ['A', 'AAAA', 'CNAME', 'DS', 'MX', 'NS', 'PTR', "SOA", 'SRV', 'NAPTR', 'SSHFP', 'TXT'];
            $scope.selectedRecordTypes = [];
            $scope.groups = [];
            $scope.recordFqdn = undefined;
            $scope.recordType = undefined;
            $scope.recordsetChanges = {};
            $scope.currentRecord = {};
            $scope.zoneInfo = {};
            $scope.profile = {};
            $scope.recordTypes = ['A', 'AAAA', 'CNAME', 'DS', 'MX', 'NS', 'PTR', 'SRV', 'NAPTR', 'SSHFP', 'TXT', 'SOA'];
            $scope.sshfpAlgorithms = [{name: '(1) RSA', number: 1}, {name: '(2) DSA', number: 2}, {name: '(3) ECDSA', number: 3},
                {name: '(4) Ed25519', number: 4}];
            $scope.sshfpTypes = [{name: '(1) SHA-1', number: 1}, {name: '(2) SHA-256', number: 2}];
            $scope.dsAlgorithms = [{name: '(3) DSA', number: 3}, {name: '(5) RSASHA1', number: 5},
                {name: '(6) DSA_NSEC3_SHA1', number: 6}, {name: '(7) RSASHA1_NSEC3_SHA1' , number: 7},
                {name: '(8) RSASHA256', number: 8}, {name: '(10) RSASHA512' , number: 10},
                {name: '(12) ECC_GOST', number: 12}, {name: '(13) ECDSAP256SHA256' , number: 13},
                {name: '(14) ECDSAP384SHA384', number: 14}, {name: '(15) ED25519', number: 15},
                {name: '(16) ED448', number: 16},{name: '(253) PRIVATEDNS', number: 253},
                {name: '(254) PRIVATEOID', number: 254}]
            $scope.dsDigestTypes = [{name: '(1) SHA1', number: 1}, {name: '(2) SHA256', number: 2}, {name: '(3) GOSTR341194', number: 3}, {name: '(4) SHA384', number: 4}]
            $scope.isZoneAdmin = false;
            $scope.canReadZone = false;
            $scope.canCreateRecords = false;
            $scope.zoneId = undefined;
            $scope.recordModalState = {
                CREATE: 0,
                UPDATE: 1,
                DELETE: 2,
                CONFIRM_UPDATE: 3,
                CONFIRM_DELETE: 4,
                VIEW_DETAILS: 5
            };
            // read-only data for setting various classes/attributes in record modal
            $scope.recordModalParams = {
                readOnly: {
                    class: "",
                    readOnly: true
                },
                editable: {
                    class: "record-edit",
                    readOnly: false
                }
            };
            $scope.userCanAccessGroup = false;

             // paging status for record changes
             var changePaging = pagingService.getNewPagingParams(100);

            // paging status for recordsets
            var recordsPaging = pagingService.getNewPagingParams(100);
            var recordType = [];
            var recordName = [];

            $( "#record-search-text" ).autocomplete({
              source: function( request, response ) {
                $.ajax({
                  url: "/api/recordsets?maxItems=100",
                  dataType: "json",
                  data: "recordNameFilter="+request.term+"%25&nameSort=asc",
                  success: function( data ) {
                      const recordSearch =  JSON.parse(JSON.stringify(data));
                      response($.map(recordSearch.recordSets, function(item) {
                      return {value: item.fqdn +' | '+ item.type , label: 'name: ' + item.fqdn + ' | type: ' + item.type }}))}
                });
              },
              minLength: 2,
              select: function (event, ui) {
                  $scope.query = ui.item.value;
                  $("#record-search-text").val(ui.item.value);
                  return false;
                },
              open: function() {
                $( this ).removeClass( "ui-corner-all" ).addClass( "ui-corner-top" );
              },
              close: function() {
                $( this ).removeClass( "ui-corner-top" ).addClass( "ui-corner-all" );
              }
            });

            $.ui.autocomplete.prototype._renderItem = function( ul, item ) {
                    let recordSet = String(item.label).replace(new RegExp(this.term, "gi"),"<b>$&</b>");
                    return $("<li></li>")
                          .data("ui-autocomplete-item", item.value)
                          .append("<div>" + recordSet + "</div>")
                          .appendTo(ul); };

            $scope.viewRecordHistory = function(recordFqdn, recordType) {
               $scope.recordFqdn = recordFqdn;
               $scope.recordType = recordType;
               $scope.refreshRecordChangeHistory($scope.recordFqdn, $scope.recordType);
               $("#record_history_modal").modal("show");
            };

            $scope.refreshRecords = function() {
            if($scope.query.includes("|")) {
                const queryRecord = $scope.query.split('|');
                recordName = queryRecord[0].trim();
                recordType = queryRecord[1].trim(); }
            else { recordName = $scope.query;
                   recordType = $scope.selectedRecordTypes.toString(); }

              recordsPaging = pagingService.resetPaging(recordsPaging);
                function success(response) {
                    recordsPaging.next = response.data.nextId;
                    updateRecordDisplay(response.data['recordSets']);
                    getMembership();
                }
                return recordsService
                    .listRecordSetData(recordsPaging.maxItems, undefined, recordName, recordType, $scope.nameSort, $scope.ownerGroupFilter)
                    .then(success)
                    .catch(function (error) {
                        handleError(error, 'dnsChangesService::getRecordSet-failure');
                    });
            };

            groupsService.getGroups(true)
                .then(function (results) {
                    $scope.groups = results['data']['groups'];
                })
                .catch(function (error) {
                    handleError(error, 'groupsService::getGroups-failure');
                });

            function handleError(error, type) {
                var alert = utilityService.failure(error, type);
                $scope.alerts.push(alert);
                $scope.processing = false;
            }

            $scope.toggleNameSort = function() {
                if ($scope.nameSort == "asc") {
                    $scope.nameSort = "desc";
                    $scope.nameSortSymbol = "fa-chevron-down";
                } else {
                    $scope.nameSort = "asc";
                    $scope.nameSortSymbol = "fa-chevron-up";
                }
                return $scope.refreshRecords();
            };

            $scope.toggleCheckedRecordType = function(recordType) {
                if($scope.selectedRecordTypes.includes(recordType)) {
                    $scope.selectedRecordTypes.splice($scope.selectedRecordTypes.indexOf(recordType),1)
                } else {
                    $scope.selectedRecordTypes.push(recordType);
                }
            };


            function updateRecordDisplay(records) {
                var newRecords = [];
                angular.forEach(records, function(record) {
                    newRecords.push(recordsService.toDisplayRecord(record, ''));
                });
                $scope.records = newRecords;
                for(var i = 0; i < $scope.records.length; i++) {
                  if (!$scope.records[i].zoneShared){
                    getZone($scope.records[i].zoneId, i);
                  }
                }
                if ($scope.records.length > 0) {
                    $("#ShowNoRec").modal("hide");
                    $("td.dataTables_empty").hide();
                } else {
                    $("td.dataTables_empty").show();
                    $("#ShowNoRec").modal("show");
                    setTimeout(function () {
                        $("#ShowNoRec").modal("hide");
                    }, 5000);
                }
            };

            /**
             * Recordset paging
             */
            $scope.getRecordPageTitle = function() {
                return pagingService.getPanelTitle(recordsPaging);
            };

            $scope.prevPageEnabled = function() {
                return pagingService.prevPageEnabled(recordsPaging);
            };

            $scope.nextPageEnabled = function() {
                return pagingService.nextPageEnabled(recordsPaging);
            };

            $scope.prevPage = function() {
                var startFrom = pagingService.getPrevStartFrom(recordsPaging);
                return recordsService
                    .listRecordSetData(recordsPaging.maxItems, startFrom, $scope.query, $scope.selectedRecordTypes.toString(), $scope.nameSort, $scope.recordOwnerGroupFilter)
                    .then(function(response) {
                        recordsPaging = pagingService.prevPageUpdate(response.data.nextId, recordsPaging);
                        updateRecordDisplay(response.data.recordSets);
                    })
                    .catch(function (error){
                        handleError(error, 'recordsService::prevPage-failure');
                    });
            };

            $scope.nextPage = function() {
                return recordsService
                        .listRecordSetData(recordsPaging.maxItems, recordsPaging.next, $scope.query, $scope.selectedRecordTypes.toString(), $scope.nameSort, $scope.recordOwnerGroupFilter)
                        .then(function(response) {
                        var recordSets = response.data.recordSets;
                        recordsPaging = pagingService.nextPageUpdate(recordSets, response.data.nextId, recordsPaging);

                        if(recordSets.length > 0 ){
                            updateRecordDisplay(recordSets);
                        }
                    })
                    .catch(function (error){
                        handleError(error, 'recordsService::nextPage-failure');
                    });
            };

            $scope.refreshRecordChangeHistory = function(recordFqdn, recordType) {
                changePaging = pagingService.resetPaging(changePaging);
                function success(response) {
                    $scope.zoneId = response.data.zoneId;
                    $scope.refreshZone();
                    changePaging.next = response.data.nextId;
                    updateChangeDisplay(response.data.recordSetChanges)
                }
                return recordsService
                    .listRecordSetChangeHistory(changePaging.maxItems, undefined, recordFqdn, recordType)
                    .then(success)
                    .catch(function (error){
                        handleError(error, 'recordsService::getRecordSetChangeHistory-failure');
                    });
            };

            /**
             * Record change history paging
             */

            $scope.changeHistoryPrevPageEnabled = function() {
                return pagingService.prevPageEnabled(changePaging);
            };

            $scope.changeHistoryNextPageEnabled = function() {
                return pagingService.nextPageEnabled(changePaging);
            };

            $scope.changeHistoryPrevPage = function() {
                var startFrom = pagingService.getPrevStartFrom(changePaging);
                return recordsService
                    .listRecordSetChangeHistory(changePaging.maxItems, startFrom, $scope.recordFqdn, $scope.recordType)
                    .then(function(response) {
                        changePaging = pagingService.prevPageUpdate(response.data.nextId, changePaging);
                        updateChangeDisplay(response.data.recordSetChanges);
                    })
                    .catch(function (error) {
                        handleError(error, 'recordsService::changePrevPage-failure');
                    });
            };

            $scope.changeHistoryNextPage = function() {
                return recordsService
                    .listRecordSetChangeHistory(changePaging.maxItems, changePaging.next, $scope.recordFqdn, $scope.recordType)
                    .then(function(response) {
                        var changes = response.data.recordSetChanges;
                        changePaging = pagingService.nextPageUpdate(changes, response.data.nextId, changePaging);
                        if(changes.length > 0){
                            updateChangeDisplay(changes);
                        }
                    })
                    .catch(function (error) {
                        handleError(error, 'recordsService::changeNextPage-failure');
                    });
            };

            function updateChangeDisplay(changes) {
                var newChanges = [];
                angular.forEach(changes, function(change) {
                    newChanges.push(change);
                });
                $scope.recordsetChanges = newChanges;
            }

            $scope.getRecordChangeStatusLabel = function(status) {
                switch(status) {
                    case 'Complete':
                        return 'success';
                    case 'Failed':
                        return 'danger';
                    default:
                        return 'info';
                }
            };

            $scope.viewRecordInfo = function(record) {
                $scope.currentRecord = recordsService.toDisplayRecord(record);
                $scope.recordModal = {
                    action: $scope.recordModalState.VIEW_DETAILS,
                    title: "Record Info",
                    basics: $scope.recordModalParams.readOnly,
                    details: $scope.recordModalParams.readOnly,
                    sharedZone: $scope.zoneInfo.shared,
                    sharedDisplayEnabled: $scope.sharedDisplayEnabled
                };
                $("#record_modal").modal("show");
            };

            $scope.refreshZone = function() {
                function success(response) {
                    $log.debug('recordsService::getZone-success');
                    $scope.zoneInfo = response.data.zone;
                    // Get current user's groups and determine if they're an admin of this zone
                    getMembership()
                }
                return recordsService
                    .getZone($scope.zoneId)
                    .then(success)
                    .catch(function (error){
                        handleError(error, 'recordsService::getZone-catch');
                    });
            };

            function getZone(zoneId, index){
                recordsService
                .getCommonZoneDetails(zoneId)
                .then(
                    function (results) {
                        $scope.zoneDetails = results;
                        $scope.records[index].ownerGroupId = results.data.zone.adminGroupId;
                        $scope.records[index].ownerGroupName = results.data.zone.adminGroupName;
                        if($scope.records[index].ownerGroupName && $scope.canAccessGroup($scope.records[index].ownerGroupId)){
                            $scope.userCanAccessGroup = true;
                        }
                    })
                .catch(function (error){
                   handleError(error, 'recordsService::getCommonZoneDetails-catch');
                });
            }

            function getMembership(){
                groupsService
                .getGroupsStored()
                .then(
                    function (results) {
                        $scope.myGroups = results.groups;
                        $scope.myGroupIds = results.groups.map(function(grp) {return grp['id']});
                    })
                .catch(function (error){
                    handleError(error, 'groupsService::getGroupsStored-failure');
                });
            }

            $scope.canAccessGroup = function(groupId) {
                 return $scope.myGroupIds !== undefined &&  $scope.myGroupIds.indexOf(groupId) > -1;
            };
    });
})();
