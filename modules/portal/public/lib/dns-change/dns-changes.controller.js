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

    angular.module('dns-change')
        .controller('DnsChangesController', function($scope, $timeout, $q, $log, dnsChangeService, pagingService, utilityService){
            $scope.batchChanges = [];
            $scope.currentBatchChange;

            // Set default params: empty start from and 100 max items
            var batchChangePaging = pagingService.getNewPagingParams(100);
            var yesterday = moment().subtract(1, 'days').startOf('day').format('YYYY-MM-DD HH:mm:ss');
            var now = moment().format('YYYY-MM-DD HH:mm:ss');
            $scope.filter = {dateTimeRangeStart: "", dateTimeRangeEnd: ""};

            $scope.getBatchChanges = function(maxItems, startFrom) {
                function success(response) {
                    return response;
                }

                return dnsChangeService
                    .getBatchChanges(maxItems, startFrom, $scope.ignoreAccess, $scope.approvalStatus, $scope.submitterName, $scope.groupName, $scope.filter.dateTimeRangeStart, $scope.filter.dateTimeRangeEnd)
                    .then(success)
                    .catch(function(error) {
                        handleError(error, 'dnsChangesService::getBatchChanges-failure');
                    });
            };

            $scope.getLocalTimeZone = function() {
                return new Date().toLocaleString('en-us', {timeZoneName:'short'}).split(' ')[3];
            }

            function handleError(error, type) {
                var alert = utilityService.failure(error, type);
                $scope.alerts.push(alert);
            }

            // Initialize tooltips after the view has rendered
            $timeout(function() {
                $('[data-toggle="tooltip"]').tooltip();
            }, 0);

            // Function to copy the ID to clipboard
            $scope.copyToClipboard = function(copyText) {
                utilityService.copyToClipboard(copyText);
                // Trigger success alert using utilityService
                var alert = utilityService.success('Successfully copied Batch ID to clipboard');
                $scope.alerts.push(alert);
            };

            $scope.refreshBatchChanges = function() {
                batchChangePaging = pagingService.resetPaging(batchChangePaging);
                $scope.groupName = null;
                $scope.submitterName = null;
                 if ($scope.searchByGroup) {
                    $scope.groupName = $scope.searchName;
                 } else {
                    $scope.submitterName = $scope.searchName;
                 }
                function success(response) {
                    batchChangePaging.next = response.data.nextId;
                    $scope.batchChanges = response.data.batchChanges;
                    for(var i = 0; i < $scope.batchChanges.length; i++) {
                        $scope.batchChanges[i].createdTimestamp = utilityService.formatDateTime($scope.batchChanges[i].createdTimestamp);
                    }
                }

                return dnsChangeService
                    .getBatchChanges(batchChangePaging.maxItems, undefined, $scope.ignoreAccess, $scope.approvalStatus, $scope.submitterName, $scope.groupName, $scope.filter.dateTimeRangeStart, $scope.filter.dateTimeRangeEnd)
                    .then(success)
                    .catch(function (error){
                        handleError(error, 'dnsChangesService::getBatchChanges-failure');
                    });
            };

            $scope.resetDateTimeFilter = function() {
                $scope.filter.dateTimeRangeStart = "";
                $scope.filter.dateTimeRangeEnd = "";
                $('input[name="dateTimeRange"]').data('daterangepicker').setStartDate(yesterday);
                $('input[name="dateTimeRange"]').data('daterangepicker').setEndDate(now);
                $scope.refreshBatchChanges();
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
                    .getBatchChanges(batchChangePaging.maxItems, startFrom, $scope.ignoreAccess, $scope.approvalStatus, $scope.submitterName, $scope.groupName, $scope.filter.dateTimeRangeStart, $scope.filter.dateTimeRangeEnd)
                    .then(function(response) {
                        batchChangePaging = pagingService.prevPageUpdate(response.data.nextId, batchChangePaging);
                        $scope.batchChanges = response.data.batchChanges;
                    })
                    .catch(function (error){
                        handleError(error, 'dnsChangesService::getBatchChanges-failure');
                    });
            };

            $scope.nextPage = function() {
                return $scope
                    .getBatchChanges(batchChangePaging.maxItems, batchChangePaging.next, $scope.ignoreAccess, $scope.approvalStatus, $scope.submitterName, $scope.groupName, $scope.filter.dateTimeRangeStart, $scope.filter.dateTimeRangeEnd)
                    .then(function(response) {
                        var batchChanges = response.data.batchChanges;
                        batchChangePaging = pagingService.nextPageUpdate(batchChanges, response.data.nextId, batchChangePaging);

                        if(batchChanges.length > 0 ){
                            $scope.batchChanges = batchChanges;
                        }
                    })
                    .catch(function (error){
                        handleError(error, 'dnsChangesService::getBatchChanges-failure');
                    });
            };

            $scope.getAllRequests = function(ignoreAccess){
                $scope.ignoreAccess = ignoreAccess;
                $scope.refreshBatchChanges();
            }

            $scope.cancelChange = function(batchChange) {
                $scope.currentBatchChange = batchChange;
                $("#cancel_batch_change").modal("show");

            }

            $scope.confirmCancel = function() {
                batchChangePaging = pagingService.resetPaging(batchChangePaging);
                $("#cancel_batch_change").modal("hide");

                function success(response) {
                    var alert = utilityService.success('Successfully cancelled DNS Change', response, 'cancelBatchChange: cancelBatchChange successful');
                    $scope.alerts.push(alert);
                    $scope.refreshBatchChanges();
                }

                return dnsChangeService
                    .cancelBatchChange($scope.currentBatchChange.id)
                    .then(success)
                    .catch(function (error){
                        handleError(error, 'dnsChangesService::cancelBatchChange-failure');
                    });
            };

            $scope.cancelCancel = function() {
                $("#cancel_batch_change").modal("hide");
                $scope.currentBatchChange = null;
            }

            $scope.canCancelBatchChange = function(batchChange, accountName) {
                return batchChange.approvalStatus == 'PendingReview' && accountName == batchChange.userName;
            }

            $("#dt-range-txt-box").on("click", function() {
                  $(".daterangepicker").addClass("dt-select-box");
            });

            $('input[name="dateTimeRange"]').daterangepicker({
                timePicker: true,
                timePickerSeconds: true,
                startDate: yesterday,
                endDate: now,
                locale: {
                    format: 'YYYY-MM-DD HH:mm:ss'
                }
            }, function(start, end) {
                 $scope.filter.dateTimeRangeStart = start.format('YYYY-MM-DD HH:mm:ss');
                 $scope.filter.dateTimeRangeEnd = end.format('YYYY-MM-DD HH:mm:ss');
                 $scope.refreshBatchChanges();
            });

           $("div.daterangepicker").click( function(e) {
                e.stopPropagation();
            });

            $timeout($scope.refreshBatchChanges, 0);
        });
})();
