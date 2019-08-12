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

    angular.module('batch-change')
        .controller('BatchChangeDetailController', function($scope, $log, $location, $timeout, batchChangeService, utilityService){

            $scope.batch = {};
            $scope.alerts = [];

            $scope.getBatchChange = function(batchChangeId) {
                function success(response) {
                    $scope.batch = response.data;
                    $scope.batch.createdTimestamp = utilityService.formatDateTime(response.data.createdTimestamp);
                    if (response.data.scheduledTime) {
                        $scope.batch.scheduledTime = utilityService.formatDateTime(response.data.scheduledTime);
                    }
                    if (response.data.reviewTimestamp) {
                        $scope.batch.reviewTimestamp = utilityService.formatDateTime(response.data.reviewTimestamp);
                    }
                }

                return batchChangeService
                    .getBatchChange(batchChangeId)
                    .then(success)
                    .catch(function (error) {
                        handleError(error, 'batchChangesService::getBatchChange-failure');
                    });
            };

            $scope.refresh = function() {
                var id = $location.absUrl().toString();
                id = id.substring(id.lastIndexOf('/') + 1);

                $scope.getBatchChange(id);
            };

            function handleError(error, type) {
                var alert = utilityService.failure(error, type);
                $scope.alerts.push(alert);
            }

            $scope.cancelChange = function() {
                $("#cancel_batch_change").modal("show");
            }

            $scope.confirmCancel = function() {
                $("#cancel_batch_change").modal("hide");
                function success(response) {
                    var alert = utilityService.success('Successfully cancelled DNS Change', response, 'cancelBatchChange: cancelBatchChange successful');
                    $scope.alerts.push(alert);
                    $scope.refresh();
                }

                return batchChangeService
                    .cancelBatchChange($scope.batch.id)
                    .then(success)
                    .catch(function (error){
                        handleError(error, 'batchChangesService::cancelBatchChange-failure');
                    });
            };

            $scope.cancelCancel = function() {
                $("#cancel_batch_change").modal("hide");
            }

            $timeout($scope.refresh, 0);
    });
})();
