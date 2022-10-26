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

        this.createGroup = function (data) {
            var url = '/api/groups';
            return $http.post(url, data, {headers: utilityService.getCsrfHeader()});
        };

        this.getGroup = function (id) {
            var url = '/api/groups/' + id;
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
            return $http.get(url);
        };

        this.addGroupMember = function (groupId, id, data) {
            var url = '/api/groups/' + groupId + '/members/' + id;
            return $http.put(url, data, {headers: utilityService.getCsrfHeader()});
        };

        this.deleteGroupMember = function (groupId, id) {
            var url = '/api/groups/' + groupId + '/members/' + id;
            return $http.delete(url, {headers: utilityService.getCsrfHeader()});
        };

        this.getGroups = function (ignoreAccess, query) {
            if (query == "") {
                query = null;
            }
            var params = {
                "maxItems": 3000,
                "groupNameFilter": query,
                "ignoreAccess": ignoreAccess
            };
            var url = '/api/groups';
            url = this.urlBuilder(url, params);
            return $http.get(url);
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
            return $http.get(url);
        };

        this.getGroupListChanges = function (id, count, groupId) {
            var url = '/api/groups/' + groupId + '/changes';
            url = this.urlBuilder(url, { 'startFrom': id, 'maxItems': count });
            return $http.get(url);
        };

        this.getGroupChanges = function (groupId, count, startFrom) {
            var url = '/api/groups/' + groupId + '/groupchanges';
            url = this.urlBuilder(url, { 'startFrom': startFrom, 'maxItems': count });
            return $http.get(url);
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
