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

angular.module('controller.manageZones', [])
    .controller('ManageZonesController', function ($scope, $timeout, $log, recordsService, zonesService, groupsService,
                                                   profileService, utilityService, pagingService) {

    groupsService.getGroupsStored()
        .then(function (results) {
            $scope.myGroups = results.groups;
        })
        .catch(function (error){
            handleError(error, 'getMyGroup:get-groups-failure');
        });

     zonesService.getBackendIds().then(function (results) {
         if (results.data) {
             $scope.backendIds = results.data;
         }
     });

    /**
     * Zone scope data initial setup
     */

    $scope.alerts = [];
    $scope.zoneInfo = {};
    $scope.zoneChanges = {};
    $scope.updateZoneInfo = {};
    $scope.manageZoneState = {
        UPDATE: 0,
        CONFIRM_UPDATE: 1
    };
    $scope.allGroups = [];

    $scope.keyAlgorithms = ['HMAC-MD5', 'HMAC-SHA1', 'HMAC-SHA224', 'HMAC-SHA256', 'HMAC-SHA384', 'HMAC-SHA512'];

    /**
     * Acl scope data initial setup
     */

    $scope.aclTypes = ['User', 'Group'];
    $scope.aclAccessLevels = ['Read', 'Write', 'Delete', 'No Access'];
    $scope.currentAclRule = {};
    $scope.currentAclRuleIndex = {};
    $scope.aclRules = [];
    $scope.aclModalState = {
        CREATE: 0,
        UPDATE: 1,
        CONFIRM_UPDATE: 2,
        CONFIRM_DELETE: 3,
        VIEW_DETAILS: 4
    };
    $scope.aclModalParams = {
        readOnly: {
            class: '',
            readOnly: true
        },
        editable: {
            class: 'acl-edit',
            readOnly: false
        }
    };
    $scope.aclRecordTypes = ['A', 'AAAA', 'CNAME', 'DS', 'MX', 'NS', 'PTR', 'SRV', 'NAPTR', 'SSHFP', 'TXT'];

    var zoneHistoryPaging = pagingService.getNewPagingParams(100);

    /**
     * Zone modal control functions
     */

    $scope.clickUpdateZone = function() {
        $scope.currentManageZoneState = $scope.manageZoneState.CONFIRM_UPDATE;
    };

    $scope.cancelUpdateZone = function() {
        $scope.currentManageZoneState = $scope.manageZoneState.UPDATE;
    };

    $scope.confirmDeleteZone = function() {
        $("#delete_zone_connection_modal").modal("show");
    };

    $scope.submitDeleteZone = function() {
        zonesService.delZone($scope.zoneInfo.id)
            .then(function (response) {
                $("#delete_zone_connection_modal").modal("hide");
                var msg = response.statusText + " (HTTP "+response.status+"): " + response.data.changeType + " zone '" + response.data.zone.name + "'";
                $scope.alerts.push({type: "success", content: msg});
                $timeout(function(){
                    location.href = "/zones";
                 }, 2000);
            })
            .catch(function (error) {
                $("#delete_zone_connection_modal").modal("hide");
                $scope.zoneError = true;
                handleError(error, 'zonesService::sendZone-failure');
            });
    };

    /**
     * Acl modal control functions
     */

    $scope.clickCreateAclRule = function() {
        $scope.currentAclRule = {
            priority: 'User',
            accessLevel: 'Read'
        };
        $scope.aclModal = {
            action: $scope.aclModalState.CREATE,
            title: 'Create ACL Rule',
            details: $scope.aclModalParams.editable
        };
        $scope.addAclRuleForm.$setPristine();
        $('#acl_modal').modal('show');
    };

    $scope.clickDeleteAclRule = function(index) {
        $scope.currentAclRuleIndex = index;
        $scope.currentAclRule = $scope.aclRules[index];
        $scope.aclModal = {
            action: $scope.aclModalState.CONFIRM_DELETE,
            title: 'Delete ACL Rule',
            details: $scope.aclModalParams.readOnly
        };
        $('#acl_modal').modal('show');
    };

    $scope.clickUpdateAclRule = function(index) {
        $scope.currentAclRuleIndex = index;
        $scope.currentAclRule = angular.copy($scope.aclRules[index]);
        $scope.aclModal = {
            action: $scope.aclModalState.UPDATE,
            title: 'Update ACL Rule',
            details: $scope.aclModalParams.editable
        };
        $('#acl_modal').modal('show');
    };

    $scope.confirmUpdateAclRule = function (bool) {
        if (bool) {
            $scope.aclModal.action = $scope.aclModalState.CONFIRM_UPDATE;
        } else {
            $scope.aclModal.action = $scope.aclModalState.UPDATE;
        }
    };

    $scope.closeAclModal = function() {
        $scope.addAclRuleForm.$setPristine();
    };

    $scope.clearForm = function() {
        $scope.currentAclRule = {
            priority: 'User',
            accessLevel: 'Read'
        };
        $scope.addAclRuleForm.$setPristine();
    };

    /**
     * Zone form submission functions
     */

    $scope.submitUpdateZone = function () {
        var zone = angular.copy($scope.updateZoneInfo);
        zone = zonesService.normalizeZoneDates(zone);
        zone = zonesService.setConnectionKeys(zone);
        zone = zonesService.checkBackendId(zone);
        zone = zonesService.checkSharedStatus(zone);
        $scope.currentManageZoneState = $scope.manageZoneState.UPDATE;
        $scope.updateZone(zone, 'Zone Update');
    };

    $scope.submitDeleteAclRule = function() {
        var newZone = angular.copy($scope.zoneInfo);
        newZone = zonesService.normalizeZoneDates(newZone);
        newZone.acl.rules.splice($scope.currentAclRuleIndex, 1);
        $scope.updateZone(newZone, 'ACL Rule Delete');
        $("#acl_modal").modal('hide');
    };

    $scope.submitAclRule = function(type) {
        if ($scope.addAclRuleForm.$valid) {

            $("#acl_modal").modal('hide');
            if ($scope.currentAclRule.priority == 'User') {
                profileService.getUserDataByUsername($scope.currentAclRule.userName)
                    .then(function (profile) {
                        $log.log('profileService::getUserDataByUsername-success');
                        $scope.currentAclRule.userId = profile.data.id;
                        $scope.postUserLookup(type);
                    })
                    .catch(function (error){
                        handleError(error, 'profileService::getUserDataByUsername-failure');
                    });
            } else {
                $scope.postUserLookup(type);
            }
        }
    };

    $scope.postUserLookup = function(type) {
        var newRule = zonesService.toVinylAclRule($scope.currentAclRule);
        var newZone = angular.copy($scope.zoneInfo);
        newZone = zonesService.normalizeZoneDates(newZone);
        if (type == 'Update') {
            newZone.acl.rules[$scope.currentAclRuleIndex] = newRule;
            $scope.updateZone(newZone, 'ACL Rule Update');
        } else if (type == 'Create') {
            newZone.acl.rules.push(newRule);
            $scope.updateZone(newZone, 'ACL Rule Create');
        }
        $scope.addAclRuleForm.$setPristine();
    };

    /**
     * Form helpers
     */

    $scope.objectsDiffer = function(left, right) {
        var l = $scope.normalizeZone(left);
        var r = $scope.normalizeZone(right);
        return !angular.equals(l, r);
    };

    $scope.normalizeZone = function(zone) {
        var vinyldnsZone = angular.copy(zone);
        delete vinyldnsZone.adminGroupName;
        delete vinyldnsZone.hiddenKey;
        delete vinyldnsZone.hiddenTransferKey;
        return vinyldnsZone;
    };

    $scope.clearUpdateConnection = function() {
        delete $scope.updateZoneInfo.connection;
        $scope.updateZoneInfo.hiddenKey = '';
    };

    $scope.clearUpdateTransferConnection = function() {
        delete $scope.updateZoneInfo.transferConnection;
        $scope.updateZoneInfo.hiddenTransferKey = '';
    };

    function handleError(error, type) {
        var alert = utilityService.failure(error, type);
        $scope.alerts.push(alert);
        $scope.processing = false;
    }

    function showSuccess(requestType, response) {
        var msg = requestType + " " + response.statusText + " (HTTP "+response.status+"): ";
        msg += $scope.zoneInfo.name + ' updated';
        $scope.alerts.push({type: "success", content: msg});
        $timeout($scope.refreshZone(), 2000);
    }

    /**
     * Global data-updating functions
     */

    $scope.refreshZone = function() {
        function success(response) {
            $log.log('recordsService::getZone-success');
            $scope.zoneInfo = response.data.zone;
            $scope.updateZoneInfo = angular.copy($scope.zoneInfo);
            $log.log('wqewweq::getZone-success', $scope.updateZoneInfo);
            $log.log('sfsafd::getZone-success', $scope.zoneInfo);
            $scope.updateZoneInfo.hiddenKey = '';
            $scope.updateZoneInfo.hiddenTransferKey = '';
            $scope.currentManageZoneState = $scope.manageZoneState.UPDATE;
            $scope.refreshAclRuleDisplay();
            $scope.refreshZoneChange();
        }
        return recordsService
            .getZone($scope.zoneId)
            .then(success)
            .catch(function (error){
                handleError(error, 'recordsService::getZone-failure');
            });
    };

    $scope.refreshZoneChange = function() {
        zoneHistoryPaging = pagingService.resetPaging(zoneHistoryPaging);
         function success(response) {
            $log.log('zonesService::getZoneChanges-success');
            zoneHistoryPaging.next = response.data.nextId;
            $scope.zoneChanges = response.data.zoneChanges;
            $scope.updateZoneChangeDisplay(response.data.zoneChanges);
         }
         return zonesService
               .getZoneChanges(zoneHistoryPaging.maxItems, undefined, $scope.zoneId)
               .then(success)
               .catch(function (error) {
                    handleError(error, 'zonesService::getZoneChanges-failure');
               });
    };

    $scope.refreshAclRule = function (index) {
        $scope.allAclRules = [];
        $scope.aclRulesModal = {
            action: $scope.aclModalState.VIEW_DETAILS,
            title: "ACL Rules Info",
            basics: $scope.aclModalParams.readOnly,
            details: $scope.aclModalParams.readOnly,
        };
        if ($scope.zoneChanges[index].zone.acl.rules.length!=0){
            for (var length = 0; length < $scope.zoneChanges[index].zone.acl.rules.length; length++) {
                $scope.allAclRules.push($scope.zoneChanges[index].zone.acl.rules[length]);
                if ($scope.allAclRules[length].hasOwnProperty('userId')){
                getAclUser($scope.allAclRules[length].userId, length); }
                else{ getAclGroup($scope.allAclRules[length].groupId, length);}
            }
        $scope.aclModalViewForm.$setPristine();
        $("#aclModalView").modal("show");}
        else{$("#aclModalView").modal("hide");}
    };

    $scope.closeAclModalView = function() {
        $scope.aclModalViewForm.$setPristine();
    };

    $scope.updateZoneChangeDisplay = function (zoneChange) {
            for (var length = 0; length < zoneChange.length; length++) {
                getZoneGroup(zoneChange[length].zone.adminGroupId, length);
                 getZoneUser(zoneChange[length].userId, length);
            }
        };

    $scope.refreshAclRuleDisplay = function() {
        $scope.aclRules = [];
        angular.forEach($scope.zoneInfo.acl.rules, function (rule) {
            $scope.aclRules.push(zonesService.toDisplayAclRule(rule));
        });
    };

    /**
     * Get User name and Group Name with Ids for Zone history
     */

    function getZoneGroup(groupId, length) {
        function success(response) {
            $log.log('groupsService::getZoneGroup-success');
            $scope.zoneChanges[length].zone.adminGroupName = response.data.name;
        }
            return groupsService
                    .getGroup(groupId)
                    .then(success)
                    .catch(function (error) {
                        handleError(error, 'groupsService::getZoneGroup-failure');
                    });
    }

    function getZoneUser(userId, length) {
        function success(response) {
            $log.log('profileService::getZoneUserDataById-success');
            $scope.zoneChanges[length].userName = response.data.userName;
        }
        return profileService
            .getUserDataById(userId)
            .then(success)
            .catch(function (error) {
                handleError(error, 'profileService::getZoneUserDataById-failure');
            });
    };

    function getAclGroup(groupId, length) {
        function success(response) {
            $log.log('groupsService::getAclGroup-success');
            $scope.allAclRules[length].groupName = response.data.name;
        }
        return groupsService
                .getGroup(groupId)
                .then(success)
                .catch(function (error) {
                    handleError(error, 'groupsService::getAclGroup-failure');
                });
    }

    function getAclUser(userId, length) {
        function success(response) {
            $log.log('profileService::getAclUserDataById-success');
            $scope.allAclRules[length].userName = response.data.userName;
        }
        return profileService
            .getUserDataById(userId)
            .then(success)
            .catch(function (error) {
                handleError(error, 'profileService::getAclUserDataById-failure');
            });
    };

    /**
     * Zone history Pagination
     */

    $scope.getZoneHistoryPageNumber = function() {
       return pagingService.getPanelTitle(zoneHistoryPaging);
    };

    $scope.prevPageEnabled = function() {
        return pagingService.prevPageEnabled(zoneHistoryPaging);
    };

    $scope.nextPageEnabled = function(tab) {
        return pagingService.nextPageEnabled(zoneHistoryPaging);
    };

    $scope.nextPageZoneHistory = function () {
        return zonesService
            .getZoneChanges(zoneHistoryPaging.maxItems, zoneHistoryPaging.next, $scope.zoneId )
            .then(function(response) {
                var zoneChanges = response.data.zoneChanges;
                zoneHistoryPaging = pagingService.nextPageUpdate(zoneChanges, response.data.nextId, zoneHistoryPaging);

                if (zoneChanges.length > 0) {
                    $scope.zoneChanges = response.data.zoneChanges;
                    $scope.updateZoneChangeDisplay(response.data.zoneChanges)
                }
            })
            .catch(function (error) {
               handleError(error,'zonesService::nextPage-failure')
            });
    };

    $scope.prevPageZoneHistory = function() {
        var startFrom = pagingService.getPrevStartFrom(zoneHistoryPaging);
        return zonesService
            .getZoneChanges(zoneHistoryPaging.maxItems, startFrom, $scope.zoneId )
            .then(function(response) {
                zoneHistoryPaging = pagingService.prevPageUpdate(response.data.nextId, zoneHistoryPaging);
                $scope.zoneChanges = response.data.zoneChanges;
                $scope.updateZoneChangeDisplay(response.data.zoneChanges);
            })
            .catch(function (error) {
                handleError(error,'zonesService::prevPage-failure');
            });
    };

    /**
     *  Service interaction functions
     */

    $scope.updateZone = function(zone, message) {
        return zonesService
            .updateZone($scope.zoneId, zone)
            .then(function(response){showSuccess(message, response)})
            .catch(function (error){
                $timeout($scope.refreshZone(), 1000);
                handleError(error, 'zonesService::updateZone-failure');
            });
    };

    $timeout($scope.refreshZone, 0);
});
