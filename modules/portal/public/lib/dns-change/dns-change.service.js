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
        .service('dnsChangeService', function ($http, utilityService) {

            this.getBatchChange = function (id) {
                var url = '/api/dnschanges/' + id;
                return $http.get(url);
            };

            this.createBatchChange = function (data, allowManualReview) {
                var params = {
                    "allowManualReview": allowManualReview
                }
                var url = utilityService.urlBuilder('/api/dnschanges', params);
                return $http.post(url, data, {headers: utilityService.getCsrfHeader()});
                let loader = $("#loader");
                             loader.modal({
                                           backdrop: "static",
                                           keyboard: false, //remove option to close with keyboard
                                           show: true //Display loader!
                                          })
                let promis =  $http.post(url, data, {headers: utilityService.getCsrfHeader()});
                    // Hide loader when api gets response
                    promis.then(()=>loader.modal("hide"))
                          .catch(()=>loader.modal("hide"))
                return promis
            };

            this.getBatchChanges = function (maxItems, startFrom, ignoreAccess, approvalStatus, userName, dateTimeRangeStart, dateTimeRangeEnd) {
                var params = {
                    "maxItems": maxItems,
                    "startFrom": startFrom,
                    "ignoreAccess": ignoreAccess,
                    "approvalStatus": approvalStatus,
                    "userName": userName,
                    "dateTimeRangeStart": dateTimeRangeStart,
                    "dateTimeRangeEnd": dateTimeRangeEnd
                };
                var url = utilityService.urlBuilder('/api/dnschanges', params);
                return $http.get(url);
            };

            this.cancelBatchChange = function (id) {
                var url = '/api/dnschanges/' + id + '/cancel';
                return $http.post(url, {}, {headers: utilityService.getCsrfHeader()});
            };

            this.approveBatchChange = function (id, reviewComment) {
                var url = '/api/dnschanges/' + id + '/approve';
                var data = reviewComment ? {'reviewComment': reviewComment} : {};
                return $http.post(url, data, {headers: utilityService.getCsrfHeader()});
            };

            this.rejectBatchChange = function (id, reviewComment) {
                var url = '/api/dnschanges/' + id + '/reject';
                var data = reviewComment ? {'reviewComment': reviewComment} : {};
                return $http.post(url, data, {headers: utilityService.getCsrfHeader()});
            };
        });
})();
