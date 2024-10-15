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
        .controller('DnsChangeDetailController', function($scope, $log, $location, $timeout, dnsChangeService, utilityService){

            $scope.batch = {};
            $scope.alerts = [];
            $scope.reviewComment;
            $scope.reviewConfirmationMsg;
            $scope.reviewType;

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
                    $scope.notice = $scope.notices.find(notice => notice['status'] == $scope.batch.status)
                }

                return dnsChangeService
                    .getBatchChange(batchChangeId)
                    .then(success)
                    .catch(function (error) {
                        handleError(error, 'dnsChangesService::getBatchChange-failure');
                    });
            };

            $scope.refresh = function() {
                var id = $location.absUrl().toString();
                id = id.substring(id.lastIndexOf('/') + 1);

                $scope.getBatchChange(id);
                $scope.cancelReview();
            };

            $scope.approve = function() {
                $scope.reviewConfirmationMsg = "Are you sure you want to approve this DNS Change?"
                $scope.reviewType = "approve";
            }

            $scope.reject = function() {
                $scope.reviewConfirmationMsg = "Are you sure you want to reject this DNS Change?"
                $scope.reviewType = "reject";
            }

            $scope.cancelReview = function() {
                $scope.reviewConfirmationMsg = null;
                $scope.reviewType = null;
            }

            $scope.confirmApprove = function() {
                function success(response) {
                    $scope.refresh();
                }

                return dnsChangeService
                    .approveBatchChange($scope.batch.id, $scope.reviewComment)
                    .then(success)
                    .catch(function (error) {
                        if (typeof error.data == "object") {
                            for (var i = 0; i < error.data.length; i++) {
                                if (error.data[i].errors) {
                                    $scope.batch.changes[i].validationErrors = error.data[i].errors
                                    $scope.batch.changes[i].outstandingErrors = true
                                } else {
                                    $scope.batch.changes[i].validationErrors = []
                                }
                            }
                            var errorAlert = {data: "Issues still remain, cannot approve DNS Change. Resolve all outstanding issues or reject the DNS Change.", status: error.status}
                            handleError(errorAlert, 'dnsChangesService::approveBatchChange-failure');
                        } else {
                            handleError(error, 'dnsChangesService::approveBatchChange-failure');
                        }
                    });
            };

            $scope.confirmReject = function() {
                function success(response) {
                    $scope.refresh();
                }

                return dnsChangeService
                    .rejectBatchChange($scope.batch.id, $scope.reviewComment)
                    .then(success)
                    .catch(function (error) {
                        handleError(error, 'dnsChangesService::rejectBatchChange-failure');
                    });
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

                return dnsChangeService
                    .cancelBatchChange($scope.batch.id)
                    .then(success)
                    .catch(function (error){
                        handleError(error, 'dnsChangesService::cancelBatchChange-failure');
                    });
            };

            $scope.cancelCancel = function() {
                $("#cancel_batch_change").modal("hide");
            }

            $scope.exportToCSV = function (batchId) {
              var filename = batchId + '.csv';
              var csv = [];
              var changes = document.querySelectorAll("table tr");
              $log.debug(batchId)
              var colOrder = [0, 4, 1, 6, 5, 2, 3, 7, 8];

                for (var i = 0; i < changes.length; i++) {
                    var row = [], cols = changes[i].querySelectorAll("td, th");

                    for (var j = 0; j < colOrder.length; j++) {
                        var colIndex = colOrder[j];
                        if (cols[colIndex]) {
                            row.push('"' + cols[colIndex].innerText + '"');
                        }
                    }
                csv.push(row.join(","));
              }
              var csvFile = new Blob([csv.join("\n")], { type: "text/csv" });
              // link to export csv
              var downloadBatchChanges = document.createElement("a");
              downloadBatchChanges.download = filename;
              downloadBatchChanges.href = window.URL.createObjectURL(csvFile);
              document.body.appendChild(downloadBatchChanges);
              downloadBatchChanges.click();
              document.body.removeChild(downloadBatchChanges);
            }


            $timeout($scope.refresh, 0);
    });
})();
