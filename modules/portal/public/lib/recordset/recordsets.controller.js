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
        .controller('RecordSetsController', function($scope, $log, $location, $timeout, recordsService, utilityService, pagingService){

            $scope.recordSet = {};
            $scope.recordSetChanges = {};
            $scope.alerts = [];
            $scope.readRecordTypes = ['A', 'AAAA', 'CNAME', 'DS', 'MX', 'NS', 'PTR', "SOA", 'SRV', 'NAPTR', 'SSHFP', 'TXT'];
            $scope.selectedRecordTypes = [];

            // paging status for recordsets
            var recordsPaging = pagingService.getNewPagingParams(100);

            $scope.refreshRecords = function() {
                recordsPaging = pagingService.resetPaging(recordsPaging);
                function success(response) {
                    $scope.records = response.data['recordSets'];
                    console.log($scope.records);
                }

                return recordsService
                    .listRecordSets(recordsPaging.maxItems, undefined, $scope.query, $scope.selectedRecordTypes.toString(), $scope.nameSort)
                    .then(success)
                    .catch(function (error) {
                        handleError(error, 'dnsChangesService::getRecordSet-failure');
                    });
            };

            function handleError(error, type) {
                console.log(error);
                var alert = utilityService.failure(error, type);
                $scope.alerts.push(alert);
                $scope.processing = false;
            }
//            $scope.refresh = function() {
//                $scope.getRecordSets
//            };
//
//            $timeout($scope.refresh, 0);
    });
})();
