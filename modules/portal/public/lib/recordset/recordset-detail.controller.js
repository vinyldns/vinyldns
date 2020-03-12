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
        .controller('RecordSetDetailController', function($scope, $log, $location, $timeout, recordsService, utilityService){

            $scope.recordSet = {};
            $scope.recordSetChanges = {};
            $scope.alerts = [];

            $scope.getRecordSet = function(recordSetId) {
                function success(response) {
                    $scope.recordSet = response.data['recordSet'];
                }

                return recordsService
                    .getRecordSet(recordSetId)
                    .then(success)
                    .catch(function (error) {
                        handleError(error, 'dnsChangesService::getRecordSet-failure');
                    });
            };

            $scope.refresh = function() {
                var id = $location.absUrl().toString();
                id = id.substring(id.lastIndexOf('/') + 1);

                $scope.getRecordSet(id);
            };

            $timeout($scope.refresh, 0);
    });
})();
