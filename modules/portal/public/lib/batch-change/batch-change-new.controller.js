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
        .controller('BatchChangeNewController', function($scope, $log, $location, $timeout, batchChangeService, utilityService, groupsService){
            groupsService.getMyGroups()
                .then(function (results) {
                    $scope.myGroups = results['data']['groups'];
                })
                .catch(function (error) {
                    handleError(error, 'groupsService::getMyGroups-failure');
                });

            $scope.batch = {};
            $scope.newBatch = {comments: "", changes: [{changeType: "Add", type: "A+PTR", ttl: 200}]};
            $scope.alerts = [];
            $scope.batchChangeErrors = false;
            $scope.formStatus = "pendingSubmit";

            $scope.addSingleChange = function() {
                $scope.newBatch.changes.push({changeType: "Add", type: "A+PTR", ttl: 200});
                var changesLength = $scope.newBatch.changes.length;
                $timeout(function() {document.getElementsByClassName("changeType")[changesLength - 1].focus()});
            };

            $scope.cancelSubmit = function() {
                $scope.formStatus = "pendingSubmit";
            };

            $scope.confirmSubmit = function(form) {
                console.log(form.$error)
                if(form.$invalid){
                    form.$setSubmitted();
                    $scope.formStatus = "pendingSubmit";
                }
            };

            $scope.createBatchChange = function() {
                //flag to prevent multiple clicks until previous promise has resolved.
                $scope.processing = true;

                var payload = $scope.newBatch;
                if (!$scope.newBatch.ownerGroupId) {
                     delete payload.ownerGroupId
                }

                function formatData(payload) {
                    for (var i = 0; i < payload.changes.length; i++) {
                        var entry = payload.changes[i]
                        if(entry.type == 'A+PTR' || entry.type == 'AAAA+PTR') {
                            entry.type = entry.type.slice(0, -4);
                            var newEntry = {changeType: entry.changeType, type: "PTR", ttl: entry.ttl, inputName: entry.record.address, record: {ptrdname: entry.inputName}}
                            payload.changes.splice(i+1, 0, newEntry)
                        }
                    }
                }

                function success(response) {
                    var alert = utilityService.success('Successfully Created Batch Change', response, 'createBatchChange: createBatchChange successful');
                    $scope.alerts.push(alert);
                    $timeout(function(){
                        location.href = "/batchchanges/" + response.data.id;
                     }, 2000);
                    $scope.batch = response.data;
                }

                formatData(payload);

                return batchChangeService.createBatchChange(payload)
                    .then(success)
                    .catch(function (error){
                        if(error.data.errors || error.status !== 400){
                            handleError(error, 'batchChangesService::createBatchChange-failure');
                        } else {
                            $scope.newBatch.changes = error.data;
                            $scope.batchChangeErrors = true;
                            $scope.formStatus = "pendingSubmit";
                            $scope.alerts.push({type: 'danger', content: 'Errors found. Please correct and submit again.'});
                        }
                    });
            };

            $scope.deleteSingleChange = function(changeNumber) {
                $('.batch-change-delete').blur();
                $scope.newBatch.changes.splice(changeNumber, 1);
            };

            $scope.submitChange = function() {
                $scope.formStatus = "pendingConfirm";
            }

            function handleError(error, type) {
                var alert = utilityService.failure(error, type);
                $scope.alerts.push(alert);
            }

            $scope.uploadCSV = function(file) {
                if (file.type == "text/csv"){
                    $scope.newBatch.changes = [];
                    var reader = new FileReader();
                    reader.onload = function(e) {
                        var rows = e.target.result.split("\n");
                        for(var i = 1; i < rows.length; i++) {
                        if (rows[i].length < 10) { continue; }
                            parseRow(rows[i])
                        }
                        $timeout();
                    }
                    reader.readAsText(file);
                    resetForm();
                } else {
                    $scope.alerts.push({type: 'danger', content: 'Import failed. Not a valid CSV file.'});
                };

                function parseRow(row) {
                    var change = {};
                    var headers = ["changeType", "type", "inputName", "ttl", "record"];
                    var rowContent = row.split(",");
                    for(var j = 0; j < rowContent.length; j++) {
                        if (headers[j] == "changeType") {
                            if (rowContent[j].trim().match(/add/i)) {
                               change[headers[j]] = "Add"
                            } else if (rowContent[j].trim().match(/delete/i)) {
                                change[headers[j]] = "DeleteRecordSet"
                            }
                        } else if (headers[j] == "type") {
                            change[headers[j]] = rowContent[j].trim().toUpperCase()
                        } else if (headers[j] == "ttl") {
                            change[headers[j]] = parseInt(rowContent[j].trim())
                        } else if (headers[j] == "record"){
                            if (change["type"] == "A" || change["type"] == "AAAA" || change["type"] == "A+PTR" || change["type"] == "AAAA+PTR"){
                                change[headers[j]] = {"address": rowContent[j].trim()}
                            } else if (change["type"] == "CNAME") {
                                change[headers[j]] = {"cname": rowContent[j].trim()}
                            } else if (change["type"] == "PTR") {
                                change[headers[j]] = {"ptrdname": rowContent[j].trim()}
                            }
                        } else {
                            change[headers[j]] = rowContent[j].trim()
                        }
                    }
                    $scope.newBatch.changes.push(change);
                }

                function resetForm() {
                    document.getElementById("batchChangeCsv").value = null;
                    $scope.csvInput = null;
                    $scope.csvForm.$setPristine();
                    $scope.csvForm.$setUntouched();
                }
            }
        });
})();
