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
        .controller('BatchChangeNewController', function($scope, $log, $location, $timeout, $q, batchChangeService, utilityService, groupsService){
            groupsService.getGroups()
                .then(function (results) {
                    $scope.myGroups = results['data']['groups'];
                    if ($scope.myGroups.length == 1) {
                        $scope.newBatch.ownerGroupId = $scope.myGroups[0]['id']
                    }
                })
                .catch(function (error) {
                    handleError(error, 'groupsService::getGroups-failure');
                });

            var tomorrow = moment().startOf('hour').add(1, 'day');
            $scope.newBatch = {comments: "", changes: [{changeType: "Add", type: "A+PTR"}], scheduledTime: tomorrow.format('LL hh:mm A')};
            $scope.scheduledOption = false;
            $scope.formStatus = "pendingSubmit";
            $scope.confirmationPrompt = "Are you sure you want to submit this batch change request?";

            $scope.addSingleChange = function() {
                $scope.newBatch.changes.push({changeType: "Add", type: "A+PTR"});
                var changesLength = $scope.newBatch.changes.length;
                $timeout(function() {document.getElementsByClassName("changeType")[changesLength - 1].focus()});
            };

            $scope.createBatchChange = function() {
                //flag to prevent multiple clicks until previous promise has resolved.
                $scope.processing = true;

                var payload = $scope.newBatch;

                function formatData(payload) {
                    if (!$scope.newBatch.ownerGroupId) {
                         delete payload.ownerGroupId
                    }

                    if ($scope.scheduledOption && $scope.newBatch.scheduledTime) {
                        payload.scheduledTime = moment($scope.newBatch.scheduledTime, 'LL hh:mm A').utc().format();
                    } else {
                        delete payload.scheduledTime;
                    }

                    for (var i = 0; i < payload.changes.length; i++) {
                        var entry = payload.changes[i]
                        if(entry.type == 'A+PTR' || entry.type == 'AAAA+PTR') {
                            entry.type = entry.type.slice(0, -4);
                            var newEntry = {changeType: entry.changeType, type: "PTR", ttl: entry.ttl, inputName: entry.record.address, record: {ptrdname: entry.inputName}}
                            payload.changes.splice(i+1, 0, newEntry)
                        }
                    }
                }

                function parseSingleChangeResponse(singleChanges) {
                    $scope.allowManualReview = true;
                    $scope.ownerGroupError = null;
                    $scope.newBatch.changes = singleChanges;
                    for(var i = 0; i < $scope.newBatch.changes.length; i++) {
                        if ($scope.newBatch.changes[i].errors) {
                            $scope.singleChangeErrors = true;
                            if (
                              $scope.manualReviewEnabled
                              && ($scope.newBatch.changes[i].errors.every(e => e.includes('Zone Discovery Failed') || e.includes('requires manual review')))
                            ) {
                                $scope.newBatch.changes[i].softErrors = true;
                                $scope.newBatch.changes[i].hardErrors = false;
                            } else {
                                $scope.anyHardErrors = true;
                                $scope.newBatch.changes[i].softErrors = false;
                                $scope.newBatch.changes[i].hardErrors = true;
                                $scope.ownerGroupError = $scope.newBatch.changes[i].errors.filter(e => e.includes('owner group ID must be specified for record'))[0]
                            }
                        } else {
                            $scope.newBatch.changes[i].softErrors = false;
                            $scope.newBatch.changes[i].hardErrors = false;
                        }
                    }
                }

                function success(response) {
                    var alert = utilityService.success('Successfully created DNS Change', response, 'createBatchChange: createBatchChange successful');
                    $scope.alerts.push(alert);
                    $timeout(function(){
                        location.href = "/dnschanges/" + response.data.id;
                     }, 2000);
                }

                formatData(payload);

                return batchChangeService.createBatchChange(payload, $scope.allowManualReview)
                    .then(success)
                    .catch(function(error){
                        if (payload.scheduledTime) {
                            $scope.newBatch.scheduledTime = moment(payload.scheduledTime).local().format('LL hh:mm A')
                        }
                        if (error.data.errors || typeof error.data == "string") {
                            if (typeof error.data == "string" && error.data.includes('requires owner group for manual review')) {
                                $scope.ownerGroupError = JSON.parse(error.data); //JSON.parse to remove extra set of quotes.
                            } else {
                                $scope.ownerGroupError = null;
                            }
                            handleError(error, 'batchChangesService::createBatchChange-failure');
                        } else {
                            parseSingleChangeResponse(error.data);
                            if (!$scope.singleChangeErrors) {
                                $scope.confirmationPrompt = "Would you like to submit this change to be processed at your requested time?"
                                $scope.alerts.push({type: 'success', content: 'No errors found! DNS Change can be submitted.'});
                            } else {
                                if ($scope.manualReviewEnabled && !$scope.anyHardErrors) {
                                    $scope.confirmationPrompt = "Would you like to submit this change for review?"
                                    $scope.alerts.push({type: 'warning', content: 'Issues found that require manual review. Please correct or confirm submission for review.'});
                                } else {
                                    $scope.cancelSubmit();
                                    $scope.alerts.push({type: 'danger', content: 'Errors found. Please correct and submit again.'});
                                }
                            }
                        }
                    });
            };

            $scope.deleteSingleChange = function(changeNumber) {
                $('.batch-change-delete').blur();
                $scope.newBatch.changes.splice(changeNumber, 1);
            };

            $scope.cancelSubmit = function() {
                setReviewParam();
                $scope.formStatus = "pendingSubmit";
                $scope.confirmationPrompt = "Are you sure you want to submit this batch change request?";
            };

            $scope.confirmSubmit = function(form) {
                if(form.$invalid){
                    form.$setSubmitted();
                    $scope.formStatus = "pendingSubmit";
                }
            };

            $scope.submitChange = function() {
                $scope.formStatus = "pendingConfirm";
                $scope.singleChangeErrors = false;
                $scope.anyHardErrors = false;
                $scope.ownerGroupError = null;
                setReviewParam();
            }

            $scope.getLocalTimeZone = function() {
                return new Date().toLocaleString('en-us', {timeZoneName:'short'}).split(' ')[3];
            }

            function handleError(error, type) {
                var alert = utilityService.failure(error, type);
                $scope.alerts.push(alert);
            }

            function setReviewParam() {
                $scope.allowManualReview = $scope.manualReviewEnabled ? false : null;
            }

            $scope.uploadCSV = function(file) {
                parseFile(file).then(function(dataLength){
                    $scope.alerts.push({type: 'success', content: 'Successfully imported ' + dataLength + ' changes.' });
                }, function(error) {
                    $scope.alerts.push({type: 'danger', content: error});
                });

                function parseFile(file) {
                  return $q(function(resolve, reject) {
                      var reader = new FileReader();
                      reader.onload = function(e) {
                          var rows = e.target.result.split("\n");
                          if (rows[0].trim() == "Change Type,Record Type,Input Name,TTL,Record Data") {
                            $scope.newBatch.changes = [];
                            for(var i = 1; i < rows.length; i++) {
                              var lengthCheck = rows[i].replace(/,+/g, '').trim().length
                              if (lengthCheck == 0) { continue; }
                              parseRow(rows[i])
                            }
                            $scope.$apply()
                            resolve($scope.newBatch.changes.length);
                          } else {
                            reject("Import failed. Not a valid file.");
                          }
                      }
                      reader.readAsText(file);
                  });
                }

                function parseRow(row) {
                    var change = {};
                    var headers = ["changeType", "type", "inputName", "ttl", "record"];
                    var rowContent = row.split(",");
                    for (var j = 0; j < rowContent.length; j++) {
                        if (headers[j] == "changeType") {
                            if (rowContent[j].match(/add/i)) {
                               change[headers[j]] = "Add"
                            } else if (rowContent[j].match(/delete/i)) {
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
            }

            $('input[name="scheduledTime"]').daterangepicker({
                singleDatePicker: true,
                timePicker: true,
                startDate: tomorrow,
                locale: {
                  format: 'LL hh:mm A'
                }
              });

        });
})();
