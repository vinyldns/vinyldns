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

'use strict';

angular.module('service.groups', [])
    .service('groupsService', function ($http, $q, utilityService) {

        var _myGroupsPromise = undefined;
        var _refreshMyGroups = true;

        this.urlBuilder = function (url, obj) {
            var result = [];
            for (var property in obj) {
                if (obj[property] !== undefined && obj[property] !== null) {
                    result.push(encodeURIComponent(property) + '=' + encodeURIComponent(obj[property]));
                }
            }
            var params = result.join('&');
            url = (params) ? url + '?' + params : url;
            return url;
        };

        // Shows the loading modal for the duration of httpPromise, hiding it on success or failure
        this.withLoader = function (httpPromise) {
            var loader = $("#loader");
            loader.modal({
                          backdrop: "static", //remove ability to close modal with click
                          keyboard: false, //remove option to close with keyboard
                          show: true //Display loader!
                          });
            httpPromise.then(() => loader.modal("hide"), () => loader.modal("hide"));
            return httpPromise;
        };

        this.createGroup = function (data) {
            var url = '/api/groups';
            return $http.post(url, data, {headers: utilityService.getCsrfHeader()});
        };

        this.getGroup = function (id, showLoader) {
            var url = '/api/groups/' + id;
            var httpPromise = $http.get(url);
            if(showLoader == false){
                return httpPromise;
            }
            return this.withLoader(httpPromise);
        };
        this.listEmailDomains = function () {
                    var url = '/api/groups/valid/domains'
                    return $http.get(url);
                };

        this.deleteGroups = function (id) {
            var url = '/api/groups/' + id;
            return $http.delete(url, {headers: utilityService.getCsrfHeader()});
        };

        this.updateGroup = function (id, data) {
            var url = '/api/groups/' + id;
            return $http.put(url, data, {headers: utilityService.getCsrfHeader()});
        };

        this.getGroupMemberList = function (uuid) {
            var url = '/api/groups/' + uuid + '/members';
            url = this.urlBuilder(url, { maxItems: 1000 });
            return this.withLoader($http.get(url));
        };

        this.addGroupMember = function (groupId, id, data) {
            var url = '/api/groups/' + groupId + '/members/' + id;
            return $http.put(url, data, {headers: utilityService.getCsrfHeader()});
        };

        this.deleteGroupMember = function (groupId, id) {
            var url = '/api/groups/' + groupId + '/members/' + id;
            return $http.delete(url, {headers: utilityService.getCsrfHeader()});
        };

        this.getGroups = function (ignoreAccess, query, maxItems) {
            if (query == "") {
                query = null;
            }
            var params = {
                "maxItems": maxItems,
                "groupNameFilter": query,
                "ignoreAccess": ignoreAccess
            };
            var url = '/api/groups';
            url = this.urlBuilder(url, params);
            return this.withLoader($http.get(url));
        };

        this.getGroupsAbridged = function (limit, startFrom, ignoreAccess, query) {
            if (query == "") {
                query = null;
            }
            var params = {
                "maxItems": limit,
                "startFrom": startFrom,
                "groupNameFilter": query,
                "ignoreAccess": ignoreAccess,
                "abridged": true
            };
            var url = '/api/groups';
            url = this.urlBuilder(url, params);
            return this.withLoader($http.get(url));
        };

        this.getGroupListChanges = function (id, count, groupId) {
            var url = '/api/groups/' + groupId + '/changes';
            url = this.urlBuilder(url, { 'startFrom': id, 'maxItems': count });
            return this.withLoader($http.get(url));
        };

        this.getGroupChanges = function (groupId, count, startFrom) {
            var url = '/api/groups/' + groupId + '/groupchanges';
            url = this.urlBuilder(url, { 'startFrom': startFrom, 'maxItems': count });
            return this.withLoader($http.get(url));
        };

        this.getGroupsStored = function () {
            if (_refreshMyGroups || _myGroupsPromise == undefined) {
                _myGroupsPromise = this.getGroups().then(
                    function(response) {
                        _refreshMyGroups = false;
                        return response.data;
                    },
                    function(error) {
                        _refreshMyGroups = true;
                        return $q.reject(error);
                    }
                )
            }
            return _myGroupsPromise;
        };
    });
