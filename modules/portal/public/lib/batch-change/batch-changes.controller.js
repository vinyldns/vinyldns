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
        .controller('BatchChangesController', function($scope, $timeout, batchChangeService, pagingService, utilityService){
            $scope.batchChanges = [];
            $scope.onlyPendingReview = false;
            $scope.approvalStatus = undefined;
            $scope.ignoreAccess = undefined;

            // Set default params: empty start from and 100 max items
            var batchChangePaging = pagingService.getNewPagingParams(100);

            $scope.getBatchChanges = function(maxItems, startFrom) {
                function success(response) {
                    return response;
                }

                return batchChangeService
                    .getBatchChanges(maxItems, startFrom, $scope.ignoreAccess, $scope.approvalStatus)
                    .then(success)
                    .catch(function(error) {
                        handleError(error, 'batchChangesService::getBatchChanges-failure');
                    });
            };

            function handleError(error, type) {
                var alert = utilityService.failure(error, type);
                $scope.alerts.push(alert);
            }

            $scope.refreshBatchChanges = function() {
                batchChangePaging = pagingService.resetPaging(batchChangePaging);

                function success(response) {
                    batchChangePaging.next = response.data.nextId;
                    $scope.batchChanges = response.data.batchChanges;
                }

                return batchChangeService
                    .getBatchChanges(batchChangePaging.maxItems, undefined, $scope.ignoreAccess, $scope.approvalStatus)
                    .then(success)
                    .catch(function (error){
                        handleError(error, 'batchChangesService::getBatchChanges-failure');
                    });
            };

            // Previous page button enabled?
            $scope.prevPageEnabled = function() {
                return pagingService.prevPageEnabled(batchChangePaging);
            };

            // Next page button enabled?
            $scope.nextPageEnabled = function() {
                return pagingService.nextPageEnabled(batchChangePaging);
            };

            // Get page number for display
            $scope.getPageTitle = function() {
                return pagingService.getPanelTitle(batchChangePaging);
            };

            $scope.prevPage = function() {
                var startFrom = pagingService.getPrevStartFrom(batchChangePaging);
                return $scope
                    .getBatchChanges(batchChangePaging.maxItems, startFrom, $scope.ignoreAccess, $scope.approvalStatus)
                    .then(function(response) {
                        batchChangePaging = pagingService.prevPageUpdate(response.data.nextId, batchChangePaging);
                        $scope.batchChanges = response.data.batchChanges;
                    })
                    .catch(function (error){
                        handleError(error, 'batchChangesService::getBatchChanges-failure');
                    });
            };

            $scope.nextPage = function() {
                return $scope
                    .getBatchChanges(batchChangePaging.maxItems, batchChangePaging.next, $scope.ignoreAccess, $scope.approvalStatus)
                    .then(function(response) {
                        var batchChanges = response.data.batchChanges;
                        batchChangePaging = pagingService.nextPageUpdate(batchChanges, response.data.nextId, batchChangePaging);

                        if(batchChanges.length > 0 ){
                            $scope.batchChanges = batchChanges;
                        }
                    })
                    .catch(function (error){
                        handleError(error, 'batchChangesService::getBatchChanges-failure');
                    });
            };

            $scope.pendingFilter = function(){
                if($scope.onlyPendingReview == true) {
                    $scope.onlyPendingReview = false;
                    $scope.approvalStatus = undefined;
                    $scope.refreshBatchChanges();
                } else {
                    $scope.onlyPendingReview = true;
                    $scope.approvalStatus = 'PendingApproval';
                    $scope.refreshBatchChanges();
                }
            }

            $scope.myRequests = function(){
                $scope.ignoreAccess = false;
                $scope.refreshBatchChanges();
            }

            $scope.allRequests = function(){
                $scope.ignoreAccess = true;
                $scope.refreshBatchChanges();
            }

            $scope.formatDateTime = function(timeStamp) {
                if (timeStamp) {
                    return utilityService.formatDateTime(timeStamp);
                }
            }

            $timeout($scope.refreshBatchChanges, 0);
        });
})();
