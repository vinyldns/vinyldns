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

angular.module('service.profile', [])
    .service('profileService', function ($http, utilityService) {
        this.withLoader = function (httpPromise) {
            var loader = $('#loader');
            loader.modal({
                backdrop: 'static',
                keyboard: false,
                show: true
            });
            httpPromise.then(function () {
                loader.modal('hide');
            }, function () {
                loader.modal('hide');
            });
            return httpPromise;
        };

        this.getAuthenticatedUserData = function () {
            return $http.get('/api/users/currentuser');
        };

        this.getUserDataByUsername = function(username){
            return $http.get('/api/users/lookupuser/' + username);
        }

        this.getUserDataById = function(userId){
            return $http.get('/api/users/' + encodeURIComponent(userId));
        }

        this.searchUsersByName = function(pattern, showLoader){
            var request = $http.get('/api/users/search?pattern=' + encodeURIComponent(pattern));
            if (showLoader === false) {
                return request;
            }
            return this.withLoader(request);
        }

        this.regenerateCredentials = function(){
            return $http.post('/regenerate-creds', {}, {headers: utilityService.getCsrfHeader()});
        }
    });
