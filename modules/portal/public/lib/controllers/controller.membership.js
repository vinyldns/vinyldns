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

angular.module('controller.membership', []).controller('MembershipController', function ($scope, $log, $location, $timeout,
                                                                                         groupsService, profileService, utilityService) {

    $scope.membership = { members: [], group: {} };
    $scope.membershipLoaded = false;
    $scope.alerts = [];
    $scope.isGroupAdmin = false;

    function handleError(error, type) {
        var alert = utilityService.failure(error, type);
        $scope.alerts.push(alert);
    }

    $scope.getGroupMemberList = function(groupId) {
        function success(response) {
            $log.debug('groupsService::getGroupMemberList-success');
            return response.data;
        }
        return groupsService
            .getGroupMemberList(groupId)
            .then(success)
            .catch(function (error) {
                handleError(error, 'groupsService::getGroupMemberList-failure');
            });
    };

    $scope.getGroup = function(groupId) {
        function success(response) {
            $log.debug('groupsService::getGroup-success');
            return response.data;
        }
        return groupsService
            .getGroup(groupId)
            .then(success)
            .catch(function (error) {
                handleError(error, 'groupsService::getGroup-failure');
            });
    };

    function determineAdmin() {
        profileService.getAuthenticatedUserData().then(
            function (results) {
                var admins = $scope.membership.group.admins.map(function (id_json) {
                     return id_json['id']});
                var superUser = angular.isUndefined(results.data.isSuper) ? false : results.data.isSuper;
                $scope.isGroupAdmin = admins.indexOf(results.data.id) > -1 || superUser;
                });
    }

    $scope.resetNewMemberData = function() {
        $scope.newMemberData = {
            isAdmin : false,
            login : ""
        };
    };

    $scope.addMember = function() {
        $log.debug('addGroupMember::newMemberData', $scope.newMemberData);
        function lookupAccountSuccess(response) {
            if (response.data) {
                $scope.membership.group.members.push({ id: response.data.id });

                if ($scope.newMemberData.isAdmin) {
                    $scope.membership.group.admins.push({ id: response.data.id });
                }

                function updateGroupSuccess(results) {
                    var alert = utilityService.success("Added " + $scope.newMemberData.login, response,
                        'addMember::getUserDataByUsername successful');
                    $scope.alerts.push(alert);
                    $scope.refresh();
                    return results;
                }
                return groupsService
                    .updateGroup($scope.membership.group.id, $scope.membership.group)
                    .then(updateGroupSuccess)
                    .catch(function (error) {
                        $scope.refresh();
                        handleError(error, 'groupsService::updateGroup-failure-catch');
                    });
            }
        }

        return profileService.getUserDataByUsername($scope.newMemberData.login)
            .then(lookupAccountSuccess)
            .catch(function (error) {
                handleError(error, 'profileService::getUserDataByUsername-failure-catch');
            });
    };

    $scope.removeMember = function(memberId) {

        var keepUser = function (user) {
            return user.id != memberId;
        };

        $log.debug('removing group member ' + memberId + ' from group ' + $scope.membership.group.id);

        $scope.membership.group.admins = $scope.membership.group.admins.filter(keepUser);
        $scope.membership.group.members = $scope.membership.group.members.filter(keepUser);

        function success(results) {
            var alert = utilityService.success("Successfully Removed Member", results,
                "groupsService::updateGroup-success");
            $scope.alerts.push(alert);
            $scope.refresh();
            return results.data;
        }
        //update the group
        return groupsService
            .updateGroup($scope.membership.group.id, $scope.membership.group)
            .then(success)
            .catch(function (error) {
                $scope.refresh();
                handleError(error, 'groupsService::updateGroup-failure');
            });
    };

    $scope.toggleAdmin = function(member) {

        var keepUser = function (user) {
            return user.id != member.id;
        };

        $log.debug('toggleAdmin::toggled for member', member);

        if(member.isAdmin) {
            $log.debug('toggleAdmin::toggled making an admin');
            $scope.membership.group.admins.push({ id: member.id });
        } else {
            $log.debug('toggleAdmin::toggled removing as admin');
            $scope.membership.group.admins = $scope.membership.group.admins.filter(keepUser);
        }

        function success(results) {
            var alert = utilityService.success("Toggled Status of " + member.userName, results,
                "toggleAdmin::status toggled");
            $scope.alerts.push(alert);
            $scope.refresh();
            return results.data;
        }

        //update the group
        return groupsService
            .updateGroup($scope.membership.group.id, $scope.membership.group)
            .then(success)
            .catch(function (error) {
                $scope.refresh();
                handleError(error, 'groupsService::updateGroup-failure');
            });
    };


    $scope.getGroupInfo = function (id) {
        //store group membership
        function getGroupSuccess(result) {
            $log.debug('refresh::getGroupSuccess-success', result);
            //update groups
            $scope.membership.group = result;

            determineAdmin();

            function getGroupMemberListSuccess(result) {
                $log.debug('refresh::getGroupMemberList-success', result);
                //update groups
                $scope.membership.members = result.members;
                $scope.membershipLoaded = true;
                return result;
            }

            return $scope.getGroupMemberList(id)
                .then(getGroupMemberListSuccess)
                .catch(function (error) {
                    handleError(error, 'refresh::getGroupMemberList-failure');
                });
        }

        return $scope.getGroup(id)
            .then(getGroupSuccess)
            .catch(function (error) {
                handleError(error, 'refresh::getGroup-failure');
            });
    };

    $scope.refresh = function () {
        var id = $location.absUrl().toString();
        id = id.substring(id.lastIndexOf('/') + 1);
        $log.debug('loading group with id ', id);

        $scope.isGroupAdmin = false;

        $scope.resetNewMemberData();
        $scope.getGroupInfo(id);
    };


    $timeout($scope.refresh, 0);
});
