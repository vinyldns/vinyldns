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

            // paging status for recordsets
            var recordsPaging = pagingService.getNewPagingParams(100);

            $scope.refreshRecords = function() {
                recordsPaging = pagingService.resetPaging(recordsPaging);
                function success(response) {
                    recordsPaging.next = response.data.nextId;
                    updateRecordDisplay(response.data['recordSets']);
                    console.log($scope.records);
                }

                return recordsService
                    .listRecordSets(recordsPaging.maxItems, undefined, $scope.query, $scope.selectedRecordTypes.toString(), $scope.nameSort, $scope.ownerGroupFilter)
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
                console.log(error);
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
                return $scope.refreshRecords();
            };

            function updateRecordDisplay(records) {
                var newRecords = [];
                angular.forEach(records, function(record) {
                    newRecords.push(recordsService.toDisplayRecord(record, ''));
                });
                $scope.records = newRecords;
                if($scope.records.length > 0) {
                  $("td.dataTables_empty").hide();
                } else {
                  $("td.dataTables_empty").show();
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
                    .listRecordSets(recordsPaging.maxItems, startFrom, $scope.query, $scope.selectedRecordTypes.toString(), $scope.nameSort, $scope.recordOwnerGroupFilter)
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
                        .listRecordSets(recordsPaging.maxItems, recordsPaging.next, $scope.query, $scope.selectedRecordTypes.toString(), $scope.nameSort, $scope.recordOwnerGroupFilter)
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
    });
})();
