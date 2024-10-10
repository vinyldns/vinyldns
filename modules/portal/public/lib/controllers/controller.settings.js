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

angular.module('controller.settings', [])
    .controller('SettingsController', function ($scope, $timeout, $log, profileService, utilityService, $q) {
        // Variables for Unlock/Lock User Form
        $scope.username = '';
        $scope.action = '';
        $scope.responseMessage = '';
        $scope.alerts = [];

        function handleError(error, type) {
            var alert = utilityService.failure(error, type);
            $scope.alerts.push(alert);
        }


        // Function to fetch user data by username
        $scope.fetchUserData = function(username) {
            return profileService.getUserDataByUsername(username)
                .then(function(response) {

                    return response.data // Return full statusResponse
                })
                .catch(function(error) {
                    handleError(error, 'profileService::getUserDataByUsername-failure');
                });
        };

        // Variables for Lock/Unlock Form
        $scope.username = '';
        $scope.action = '';
        $scope.responseMessage = '';

        // Function to handle Unlock/Lock user action
        $scope.submitForm = function() {
            // Call fetchUserData and handle the returned promise
            $scope.fetchUserData($scope.username)
                .then(function(statusResponse) {

                    // Now, we can proceed with lockUnlock action
                    return profileService.lockUnlock(statusResponse.id, $scope.action);
                })
                .then(function(response) {
                    // Trigger success alert using utilityService
                    var alert = utilityService.success('User ' + $scope.username + ' has been ' + $scope.action + 'ed successfully!');
                    $scope.alerts.push(alert);
                })
                .catch(function (error) {
                    handleError(error, 'profileService::lock/unlock-failure');
                });
        };

        // Clear Lock/Unlock Form
        $scope.clearLockUnlockForm = function() {
            $scope.username = '';
            $scope.action = '';
            $scope.responseMessage = '';
        };

        // Variables for Check User Status Form
        $scope.statusUsername = '';
        $scope.statusResponse = null;

        // Function to check user status
        $scope.checkUserStatus = function() {

            // Call fetchUserData and handle the promise
            $scope.fetchUserData($scope.statusUsername)
                .then(function(statusResponse) {
                    $scope.statusResponse = statusResponse;
                })
                .catch(function (error) {
                    handleError(error, 'profileService::getUserDataByUsername-failure');
                });
        };

        // Clear Status Form
        $scope.clearStatusForm = function() {
            $scope.statusUsername = '';
            $scope.statusResponse = null;
        };

        // Variables for Update User Permission Form
        $scope.permissionUsername = '';
        $scope.selectedPermission = '';

        $scope.transformPermission = function(permission) {
            if (permission) {
                return permission.toLowerCase().replace(/\s+/g, '');
            }
            return permission;
        };

        $scope.updatePermission = function() {
            // Call fetchUserData and handle the returned promise
            $scope.fetchUserData($scope.permissionUsername)
                .then(function(statusResponse) {
                    var permission = $scope.selectedPermission.toLowerCase().replace(/\s+/g, '').replace(/user$/, '');

                    // Now, we can proceed with updateUserPermission action
                    return profileService.updateUserPermission(statusResponse.id, permission);
                })
                .then(function(response) {
                    // Trigger success alert using utilityService
                    var alert = utilityService.success('User ' + $scope.permissionUsername + ' permission has been updated successfully!');
                    $scope.alerts.push(alert);
                })
                .catch(function (error) {
                    handleError(error, 'profileService::update-user-permission-failure');
                });
        };

        $scope.clearPermissionForm = function() {
            $scope.permissionUsername = '';
            $scope.selectedPermission = '';
        };
    });