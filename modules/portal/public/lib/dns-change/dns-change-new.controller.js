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
        .controller('DnsChangeNewController', function($scope, $log, $location, $timeout, $q, dnsChangeService, utilityService, groupsService){
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

            $scope.batch = {};
            var tomorrow = moment().startOf('hour').add(1, 'day');
            $scope.newBatch = {comments: "", changes: [{changeType: "Add", type: "A+PTR"}], scheduledTime: tomorrow.format('LL hh:mm A')};
            $scope.alerts = [];
            $scope.batchChangeErrors = false;
            $scope.ownerGroupError = false;
            $scope.softErrors = false;
            $scope.formStatus = "pendingSubmit";
            $scope.scheduledOption = false;
            $scope.allowManualReview = false;
            $scope.confirmationPrompt = "Are you sure you want to submit this batch change request?";
            $scope.manualReviewEnabled;
            $scope.naptrFlags = ["U", "S", "A", "P"];

            $scope.addSingleChange = function() {
                $scope.newBatch.changes.push({changeType: "Add", type: "A+PTR"});
                var changesLength = $scope.newBatch.changes.length;
                $timeout(function() {document.getElementsByClassName("changeType")[changesLength - 1].focus()});
            };

            $scope.cancelSubmit = function() {
                $scope.formStatus = "pendingSubmit";
                $scope.allowManualReview = false;
                $scope.confirmationPrompt = "Are you sure you want to submit this batch change request?";
            };

            $scope.confirmSubmit = function(form) {
                if(form.$invalid){
                    form.$setSubmitted();
                    $scope.formStatus = "pendingSubmit";
                }
            };

            $scope.clearRecordData = function(changeIndex) {
                delete $scope.newBatch.changes[changeIndex].record;
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
                        if(entry.type == 'NAPTR') {
                            // Since regexp can be left empty
                            if(entry.record.regexp == undefined){
                                var newEntry = {changeType: entry.changeType, type: "NAPTR", ttl: entry.ttl, inputName: entry.inputName, record: {order: entry.record.order, preference: entry.record.preference, flags: entry.record.flags, service: entry.record.service, regexp: '', replacement: entry.record.replacement}}
                                payload.changes[i] = newEntry;
                            }
                        }
                        if(entry.changeType == 'DeleteRecordSet' && entry.record) {
                            var recordDataEmpty = true;
                            for (var attr in entry.record) {
                                if (entry.record[attr] != undefined && entry.record[attr].toString().length > 0) {
                                    recordDataEmpty = false
                                }
                            }
                            if (recordDataEmpty) {
                                delete entry.record
                            }
                        }
                    }
                }

                function success(response) {
                    var alert = utilityService.success('Successfully created DNS Change', response, 'createBatchChange: createBatchChange successful');
                    $scope.alerts.push(alert);
                    // This is the message we have in akka http config to handle timeout
                    if(response.data.message === "Successfully submitted DNS changes. Please wait a while for the changes to get processed."){
                        $timeout(function(){
                            location.href = "/dnschanges";
                         }, 2000);
                    } else {
                        $timeout(function(){
                            location.href = "/dnschanges/" + response.data.id;
                         }, 2000);
                        $scope.batch = response.data;
                    }
                }

                formatData(payload);

                return dnsChangeService.createBatchChange(payload, true)
                    .then(success)
                    .catch(function (error){
                        if(payload.scheduledTime) {
                         $scope.newBatch.scheduledTime = moment(payload.scheduledTime).local().format('LL hh:mm A')
                        }
                        if(error.data.errors || error.status !== 400 || typeof error.data == "string"){
                            handleError(error, 'dnsChangesService::createBatchChange-failure');
                        } else {
                            $scope.newBatch.changes = error.data;
                            $scope.batchChangeErrors = true;
                            $scope.listOfErrors = error.data.flatMap(d => d.errors)
                            $scope.ownerGroupError = $scope.listOfErrors.some(e => e.includes('owner group ID must be specified for record'));
                            $scope.softErrors = false;
                            $scope.formStatus = "pendingSubmit";
                            $scope.alerts.push({type: 'danger', content: 'Errors found. Please correct and submit again.'});
                        }
                    });
            };

            $scope.deleteSingleChange = function(changeNumber) {
                $('.batch-change-delete').blur();
                $scope.newBatch.changes.splice(changeNumber, 1);
            };

            $scope.submitChange = function(manualReviewEnabled) {
                $scope.formStatus = "pendingConfirm";
                $scope.manualReviewEnabled = manualReviewEnabled;
            }

            $scope.getLocalTimeZone = function() {
                return new Date().toLocaleString('en-us', {timeZoneName:'short'}).split(' ')[3];
            }

            function handleError(error, type) {
                var alert = utilityService.failure(error, type);
                $scope.alerts.push(alert);
            }

            $scope.uploadCSV = function(file) {
                parseFile(file).then(function(dataLength){
                    $scope.alerts.push({type: 'success', content: 'Successfully imported ' + dataLength + ' changes.' });
                }, function(error) {
                    $scope.alerts.push({type: 'danger', content: error});
                });

                function parseFile(file) {
                  return $q(function(resolve, reject) {
                    if (!file.name.endsWith('.csv')) {
                      reject("Import failed. File should be of ‘.csv’ type.");
                    }
                    else {
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
                          reject("Import failed. CSV header must be: Change Type,Record Type,Input Name,TTL,Record Data");
                        }
                      }
                      reader.readAsText(file);
                    }
                  });
                }

                function decode(str) {
                    // regex from:
                    // https://www.bennadel.com/blog/1504-ask-ben-parsing-csv-strings-with-javascript-exec-regular-expression-command.htm
                    // matches[0] is full match text with delimiter if any
                    // matches[1] is delimiter (usually ',')
                    // matches[2] is quoted field or undefined, internal quotes are doubled by convention
                    // matches[3] is standard field or undefined
                    // one of [2] or [3] will be undefined
                    const regex = /(,|\r?\n|\r|^)(?:"([^"]*(?:""[^"]*)*)"|([^,\r\n]*))/gi;
                    const matches = [...str.matchAll(regex)];
                    return matches.map(match => match[2] !== undefined ? match[2].replace(/""/g, '"') : match[3]);
                }

                function parseRow(row) {
                    var change = {};
                    var headers = ["changeType", "type", "inputName", "ttl", "record"];
                    var rowContent = decode(row);
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
                            } else if (change["type"] == "TXT") {
                                change[headers[j]] = {"text": rowContent[j].trim()}
                            } else if (change["type"] == "NS") {
                                change[headers[j]] = {"nsdname": rowContent[j].trim()}
                            } else if (change["type"] == "MX") {
                                var mxData = rowContent[j].trim().split(' ');
                                change[headers[j]] = {"preference": parseInt(mxData[0]), "exchange": mxData[1]}
                            } else if (change["type"] == "NAPTR") {
                                var naptrData = rowContent[j].trim().split(' ');
                                if(naptrData.length == 6){
                                    change[headers[j]] = {"order": parseInt(naptrData[0]), "preference": parseInt(naptrData[1]), "flags": naptrData[2], "service": naptrData[3], "regexp": naptrData[4], "replacement": naptrData[5]}
                                } else {
                                    change[headers[j]] = {"order": parseInt(naptrData[0]), "preference": parseInt(naptrData[1]), "flags": naptrData[2], "service": naptrData[3], "regexp": '', "replacement": naptrData[4]}
                                }
                            } else if (change["type"] == "SRV") {
                                var srvData = rowContent[j].trim().split(' ');
                                change[headers[j]] = {"priority": parseInt(srvData[0]), "weight": parseInt(srvData[1]), "port": parseInt(srvData[2]), "target": srvData[3]}
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
