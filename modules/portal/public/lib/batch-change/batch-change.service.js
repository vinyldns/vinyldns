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
        .service('batchChangeService', function ($http, utilityService) {

            this.getBatchChange = function (id) {
                var url = '/api/dnschanges/' + id;
                return $http.get(url);
            };

            this.createBatchChange = function (data) {
                var url = '/api/dnschanges';
                return $http.post(url, data, {headers: utilityService.getCsrfHeader()});
            };

            this.getBatchChanges = function (maxItems, startFrom, ignoreAccess, approvalStatus) {
                var params = {
                    "maxItems": maxItems,
                    "startFrom": startFrom,
                    "ignoreAccess": ignoreAccess,
                    "approvalStatus": approvalStatus
                };
                var url = utilityService.urlBuilder('/api/dnschanges', params);
                return $http.get(url);
            };

        });
})();
