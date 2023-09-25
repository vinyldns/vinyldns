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

angular.module('controller.records', [])
    .controller('RecordsController', function ($scope, $timeout, $log, recordsService, groupsService, pagingService, profileService, utilityService, $q) {

    /**
      * Scope data initial setup
      */

    $scope.query = "";
    $scope.nameSort = "asc";
    $scope.recordTypeSort = "none";
    $scope.nameSortSymbolUp = "toggle-on";
    $scope.nameSortSymbolDown = "toggle-off";
    $scope.recordTypeSortSymbolUp = "toggle-off";
    $scope.recordTypeSortSymbolDown = "toggle-off";
    $scope.alerts = [];

    $scope.recordTypes = ['A', 'AAAA', 'CNAME', 'DS', 'MX', 'NS', 'PTR', 'SRV', 'NAPTR', 'SSHFP', 'TXT'];
    $scope.readRecordTypes = ['A', 'AAAA', 'CNAME', 'DS', 'MX', 'NS', 'PTR', "SOA", 'SRV', 'NAPTR', 'SSHFP', 'TXT'];
    $scope.selectedRecordTypes = [];
    $scope.naptrFlags = ["U", "S", "A", "P"];
    $scope.sshfpAlgorithms = [{name: '(1) RSA', number: 1}, {name: '(2) DSA', number: 2}, {name: '(3) ECDSA', number: 3},
        {name: '(4) Ed25519', number: 4}];
    $scope.sshfpTypes = [{name: '(1) SHA-1', number: 1}, {name: '(2) SHA-256', number: 2}];
    $scope.dsAlgorithms = [{name: '(3) DSA', number: 3}, {name: '(5) RSASHA1', number: 5},
        {name: '(6) DSA_NSEC3_SHA1', number: 6}, {name: '(7) RSASHA1_NSEC3_SHA1' , number: 7},
        {name: '(8) RSASHA256', number: 8}, {name: '(10) RSASHA512' , number: 10},
        {name: '(12) ECC_GOST', number: 12}, {name: '(13) ECDSAP256SHA256' , number: 13},
        {name: '(14) ECDSAP384SHA384', number: 14}, {name: '(15) ED25519', number: 15},
        {name: '(16) ED448', number: 16},{name: '(253) PRIVATEDNS', number: 253},
        {name: '(254) PRIVATEOID', number: 254}]
    $scope.dsDigestTypes = [{name: '(1) SHA1', number: 1}, {name: '(2) SHA256', number: 2}, {name: '(3) GOSTR341194', number: 3}, {name: '(4) SHA384', number: 4}]
    $scope.records = {};
    $scope.recordsetChangesPreview = {};
    $scope.recordsetChanges = {};
    $scope.currentRecord = {};
    $scope.zoneInfo = {};
    $scope.profile = {};
    $scope.recordSetCount = 0;

    var loadZonesPromise;
    var loadRecordsPromise;

    $scope.recordModalState = {
        CREATE: 0,
        UPDATE: 1,
        DELETE: 2,
        CONFIRM_UPDATE: 3,
        CONFIRM_DELETE: 4,
        VIEW_DETAILS: 5
    };

    $scope.disabledStates = [$scope.recordModalState.CONFIRM_UPDATE, $scope.recordModalState.CONFIRM_DELETE, $scope.recordModalState.VIEW_DETAILS];

    // read-only data for setting various classes/attributes in record modal
    $scope.recordModalParams = {
        readOnly: {
            class: "",
            readOnly: true
        },
        editable: {
            class: "record-edit",
            readOnly: false
        }
    };

    $scope.isZoneAdmin = false;
    $scope.canReadZone = false;
    $scope.canCreateRecords = false;

    // paging status for recordsets
    var recordsPaging = pagingService.getNewPagingParams(100);

    // paging status for record changes
    var changePaging = pagingService.getNewPagingParams(100);

    /**
      * Modal control functions
      */

    $scope.deleteRecord = function(record) {
        $scope.currentRecord = angular.copy(record);
        $scope.recordModal = {
            action: $scope.recordModalState.CONFIRM_DELETE,
            title: "Delete record",
            basics: $scope.recordModalParams.readOnly,
            details: $scope.recordModalParams.readOnly,
            sharedZone: $scope.zoneInfo.shared,
            sharedDisplayEnabled: $scope.sharedDisplayEnabled
        };
        $("#record_modal").modal("show");
    };

    $scope.createRecord = function(defaultTtl) {
        record = {
            type: "A",
            ttl: defaultTtl,
            dsItems: [{keytag:'', algorithm: '', digesttype: '', digest: ''}],
            mxItems: [{preference:'', exchange:''}],
            srvItems: [{priority:'', weight:'', port:'', target:''}],
            naptrItems: [{order:'', preference:'', flags:'', service:'', regexp:'', replacement:''}],
            sshfpItems: [{algorithm:'', type:'', fingerprint:''}]
        };
        $scope.currentRecord = angular.copy(record);
        $scope.recordModal = {
            action: $scope.recordModalState.CREATE,
            title: "Create record",
            basics: $scope.recordModalParams.editable,
            details: $scope.recordModalParams.editable,
            sharedZone: $scope.zoneInfo.shared,
            sharedDisplayEnabled: $scope.sharedDisplayEnabled
        };
        $scope.addRecordForm.$setPristine();
        $("#record_modal").modal("show");
    };

    $scope.editRecord = function(record) {
        $scope.currentRecord = angular.copy(record);
        $scope.recordModal = {
            previous: angular.copy(record),
            action: $scope.recordModalState.UPDATE,
            title: "Update record",
            basics: $scope.recordModalParams.readOnly,
            details: $scope.recordModalParams.editable,
            sharedZone: $scope.zoneInfo.shared,
            sharedDisplayEnabled: $scope.sharedDisplayEnabled
        };
        $scope.addRecordForm.$setPristine();
        $("#record_modal").modal("show");
    };

    $scope.confirmUpdate = function() {
        $scope.recordModal.action = $scope.recordModalState.CONFIRM_UPDATE;
        $scope.recordModal.details = $scope.recordModalParams.readOnly;
    };

    $scope.closeRecordModal = function() {
        $scope.addRecordForm.$setPristine();
    };

    $scope.viewRecordInfo = function(record) {
        $scope.currentRecord = recordsService.toDisplayRecord(record);
        $scope.recordModal = {
            action: $scope.recordModalState.VIEW_DETAILS,
            title: "Record Info",
            basics: $scope.recordModalParams.readOnly,
            details: $scope.recordModalParams.readOnly,
            sharedZone: $scope.zoneInfo.shared,
            sharedDisplayEnabled: $scope.sharedDisplayEnabled
        };
        $("#record_modal").modal("show");
    };

    /**
      * Form submission functions
      */

    $scope.submitDeleteRecord = function(record) {
        deleteRecordSet(record);
        $("#record_modal").modal("hide");
    };

    $scope.submitCreateRecord = function() {
        var record = angular.copy($scope.currentRecord);
        record['onlyFour'] = true;

        if ($scope.addRecordForm.$valid) {
            createRecordSet(record);

            $scope.addRecordForm.$setPristine();
            $("#record_modal").modal('hide');
        }
    };

    $scope.submitUpdateRecord = function () {
        var record = angular.copy($scope.currentRecord);
        record['onlyFour'] = true;

        if ($scope.addRecordForm.$valid) {
            updateRecordSet(record);

            $scope.addRecordForm.$setPristine();
            $("#record_modal").modal('hide');
        }
    };

    /**
      * Form helpers
      */

    $scope.recordsDiffer = function(left, right) {
        return !angular.equals(left, right);
    };

    $scope.clearRecord = function(record) {
        record.ttl = undefined;
        record.data = undefined;
        if ($scope.sharedDisplayEnabled && $scope.zoneInfo.shared) {
            record.ownerGroupId = undefined;
        }
    };

    $scope.getZoneStatusLabel = function() {
        switch($scope.zoneInfo["status"]) {
            case 'Active':
                return 'success';
            case 'Deleted':
                return 'danger';
            default:
                return 'info';
        }
    };

    $scope.getRecordChangeStatusLabel = function(status) {
        switch(status) {
            case 'Complete':
                return 'success';
            case 'Failed':
                return 'danger';
            default:
                return 'info';
        }
    };

    $scope.addNewDs = function() {
        var dataObj = {preference:'', exchange:''};
        $scope.currentRecord.dsItems.push(dataObj);
    };

    $scope.deleteDs = function(index) {
        $scope.currentRecord.dsItems.splice(index, 1);
        if($scope.currentRecord.dsItems.length == 0) {
            $scope.addNewDs();
        }
    };

    $scope.addNewMx = function() {
        var dataObj = {preference:'', exchange:''};
        $scope.currentRecord.mxItems.push(dataObj);
    };

    $scope.deleteMx = function(index) {
        $scope.currentRecord.mxItems.splice(index, 1);
        if($scope.currentRecord.mxItems.length == 0) {
            $scope.addNewMx();
        }
    };

    $scope.addNewSrv = function() {
        var dataObj = {priority:'', weight:'', port:'', target:''};
        $scope.currentRecord.srvItems.push(dataObj);
    };

    $scope.deleteSrv = function(index) {
        $scope.currentRecord.srvItems.splice(index, 1);
        if($scope.currentRecord.srvItems.length == 0) {
            $scope.addNewSrv();
        }
    };

    $scope.addNewNaptr = function() {
        var dataObj = {order:'', preference:'', flags:'', service:'', regexp:'', replacement:''};
        $scope.currentRecord.naptrItems.push(dataObj);
    };

    $scope.deleteNaptr = function(index) {
        $scope.currentRecord.naptrItems.splice(index, 1);
        if($scope.currentRecord.naptrItems.length == 0) {
            $scope.addNewNaptr();
        }
    };

    $scope.addNewSshfp = function() {
        var dataObj = {algorithm:'', type:'', fingerprint: ''};
        $scope.currentRecord.sshfpItems.push(dataObj);
    };

    $scope.deleteSshfp = function(index) {
        $scope.currentRecord.sshfpItems.splice(index, 1);
        if($scope.currentRecord.sshfpItems.length == 0) {
            $scope.addNewSshfp();
        }
    };

    /**
      * Service interaction functions
      */

    function deleteRecordSet(record) {
        return recordsService
            .delRecordSet($scope.zoneId, record.id)
            .then(recordSetSuccess("Delete Record"))
            .catch(function (error){
                handleError(error, 'recordsService::delRecordSet-failure');
            });
    }

    function createRecordSet(record) {
        var payload = recordsService.toVinylRecord(record);
        payload.zoneId = $scope.zoneId;
        return recordsService
            .createRecordSet($scope.zoneId, payload)
            .then(recordSetSuccess("Create Record"))
            .catch(function (error){
                handleError(error, 'recordsService::createRecordSet-failure');
            });
    }

    function updateRecordSet(record) {
        var payload = recordsService.toVinylRecord(record);
        payload.zoneId = $scope.zoneId;
        if (record.ownerGroupId) {
            payload.ownerGroupId = record.ownerGroupId;
        }
        return recordsService
            .updateRecordSet($scope.zoneId, record.id, payload)
            .then(recordSetSuccess("Update Record"))
            .catch(function (error){
                handleError(error, 'recordsService::updateRecordSet-failure');
            });
    }

    function recordSetSuccess(action) {
        return function(response) {
            showSuccess(action, response);
            $scope.refreshRecords();
            $scope.refreshRecordChangesPreview();
            $scope.refreshRecordChanges();
        }
    }

    function handleError(error, type) {
        var alert = utilityService.failure(error, type);
        $scope.alerts.push(alert);
        $scope.processing = false;
    }

    function showSuccess(requestType, response) {
        var msg = requestType + " " + response.statusText + " (HTTP "+response.status+"): ";
        var recordSet = response.data.recordSet;
        msg += recordSet.name+"/"+recordSet.type+" updated, status: '"+recordSet.status+"'";
        $scope.alerts.push({type: "success", content: msg});
        return response;
    }

    function getMembership(){
        groupsService
        .getGroupsStored()
        .then(
            function (results) {
                $scope.myGroups = results.groups;
                $scope.myGroupIds = results.groups.map(function(grp) {return grp['id']});
                determineAdmin()
            })
        .catch(function (error){
            handleError(error, 'groupsService::getGroupsStored-failure');
        });
    }

    function determineAdmin(){
        $scope.isZoneAdmin = $scope.profile.isSuper || isInAdminGroup();
        $scope.canReadZone = canReadZone();
        $scope.canCreateRecords = $scope.zoneInfo.accessLevel == 'Delete' || canCreateRecordsViaAcl() || $scope.zoneInfo.shared;

        function canCreateRecordsViaAcl() {
            return $scope.zoneInfo.acl.rules.some(b => b.accessLevel == "Write" || b.accessLevel == "Delete")
        };
    }

    function isInAdminGroup() {
        var groupMember = false;
        var theGroupIndex = $scope.myGroupIds.indexOf($scope.zoneInfo.adminGroupId);
        if (theGroupIndex > -1) {
            var groupMemberIds = $scope.myGroups[theGroupIndex].members.map(function(member) {return member['id']});
            groupMember = groupMemberIds.indexOf($scope.profile.id) > -1;
        }
        return groupMember;
    }

    function canReadZone() {
        return $scope.myGroupIds.indexOf($scope.zoneInfo.adminGroupId) > -1;
    }

    function canAccessGroup(groupId) {
        return $scope.myGroupIds.indexOf(groupId) > -1;
    };

    /**
      * Global data-updating functions
      */

    $scope.refreshZone = function() {
        function success(response) {
            $log.debug('recordsService::getZone-success');
            $scope.zoneInfo = response.data.zone;
            // Get current user's groups and determine if they're an admin of this zone
            getMembership()
        }
        return recordsService
            .getZone($scope.zoneId)
            .then(success)
            .catch(function (error){
                handleError(error, 'recordsService::getZone-catch');
            });
    };

    $scope.syncZone = function() {
        function success(response) {
            $log.debug('recordsService::syncZone-success');
            location.reload();
        }
        return recordsService
            .syncZone($scope.zoneId)
            .then(success)
            .catch(function (error){
                handleError(error, 'recordsService::syncZone-failure');
            });
    };

    $scope.refreshRecordChangesPreview = function() {
        function success(response) {
            $log.debug('recordsService::getRecordSetChanges-success');
            var newChanges = [];
            angular.forEach(response.data.recordSetChanges, function(change) {
                newChanges.push(change);
            });
            $scope.recordsetChangesPreview = newChanges;
        }
        return recordsService
            .listRecordSetChanges($scope.zoneId, 5)
            .then(success)
            .catch(function (error){
                handleError(error, 'recordsService::getRecordSetChanges-failure');
            });
    };

    $scope.refreshRecordChanges = function() {
        changePaging = pagingService.resetPaging(changePaging);
        function success(response) {
            $log.debug('recordsService::getRecordSetChanges-success');
            changePaging.next = response.data.nextId;
            updateChangeDisplay(response.data.recordSetChanges)
        }
        return recordsService
            .listRecordSetChanges($scope.zoneId, changePaging.maxItems, undefined)
            .then(success)
            .catch(function (error){
                handleError(error, 'recordsService::getRecordSetChanges-failure');
            });
    };

    function updateChangeDisplay(changes) {
        var newChanges = [];
        angular.forEach(changes, function(change) {
            newChanges.push(change);
        });
        $scope.recordsetChanges = newChanges;
    }

    $scope.refreshRecords = function() {
        recordsPaging = pagingService.resetPaging(recordsPaging);
        function success(response) {
            $log.debug('recordsService::listRecordSetsByZone-success ('+ response.data.recordSets.length +' records)');
            recordsPaging.next = response.data.nextId;
            updateRecordDisplay(response.data.recordSets);
        }
        return recordsService
            .listRecordSetsByZone($scope.zoneId, recordsPaging.maxItems, undefined, $scope.query, $scope.selectedRecordTypes.toString(), $scope.nameSort, $scope.recordTypeSort)
            .then(success)
            .catch(function (error){
                handleError(error, 'recordsService::listRecordSetsByZone-failure');
            });
    };

    function updateRecordDisplay(records) {
        $q.all([loadZonesPromise, loadRecordsPromise])
            .then(function(){
                var newRecords = [];
                angular.forEach(records, function(record) {
                    newRecords.push(recordsService.toDisplayRecord(record, $scope.zoneInfo.name));
                });
                $scope.records = newRecords;
                $scope.getRecordSetCount();
                if($scope.records.length > 0) {
                  $("td.dataTables_empty").hide();
                } else {
                  $("td.dataTables_empty").show();
                }
            });
    };

    $scope.getRecordSetCount = function getRecordSetsCount() {
        function success(response) {
                 $log.debug('RecordService::getRecordSetsCount-success',  response.data);
                 return $scope.recordSetCount = response.data.count
             }
             return recordsService
                 .recordSetCount($scope.zoneId)
                 .then(success)
                 .catch(function (error) {
                     handleError(error, 'groupsService::getRecordSetsCount-failure');
                 });
    }

    /**
     * Recordset paging
     */
    $scope.getRecordPageTitle = function() {
        return pagingService.getPanelTitle(recordsPaging);
    };

    $scope.prevPageEnabled = function() {
        return pagingService.prevPageEnabled(recordsPaging);
    };

    $scope.nextPageEnabled = function() {
        return pagingService.nextPageEnabled(recordsPaging);
    };

    $scope.prevPage = function() {
        var startFrom = pagingService.getPrevStartFrom(recordsPaging);
        return recordsService
            .listRecordSetsByZone($scope.zoneId, recordsPaging.maxItems, startFrom, $scope.query, $scope.selectedRecordTypes.toString(), $scope.nameSort, $scope.recordTypeSort)
            .then(function(response) {
                recordsPaging = pagingService.prevPageUpdate(response.data.nextId, recordsPaging);
                updateRecordDisplay(response.data.recordSets);
            })
            .catch(function (error){
                handleError(error, 'recordsService::prevPage-failure');
            });
    };

    $scope.nextPage = function() {
        return recordsService
                .listRecordSetsByZone($scope.zoneId, recordsPaging.maxItems, recordsPaging.next, $scope.query, $scope.selectedRecordTypes.toString(), $scope.nameSort, $scope.recordTypeSort)
                .then(function(response) {
                var recordSets = response.data.recordSets;
                recordsPaging = pagingService.nextPageUpdate(recordSets, response.data.nextId, recordsPaging);

                if (recordSets.length > 0){
                    updateRecordDisplay(recordSets);
                }
            })
            .catch(function (error){
                handleError(error, 'recordsService::nextPage-failure');
            });
    };

    $scope.toggleNameSort = function() {
    $scope.recordTypeSort = "none"
    $scope.recordTypeSortSymbolDown = "toggle-off";
    $scope.recordTypeSortSymbolUp = "toggle-off";
        if ($scope.nameSort == "asc") {
            $scope.nameSort = "desc";
            $scope.nameSortSymbolDown = "toggle-on";
            $scope.nameSortSymbolUp = "toggle-off";
        } else {
            $scope.nameSort = "asc";
            $scope.nameSortSymbolDown = "toggle-off";
            $scope.nameSortSymbolUp = "toggle-on";
        }
        return $scope.refreshRecords();
    };

    $scope.toggleRecordTypeSort = function() {
        $scope.nameSort = ""
        $scope.nameSortSymbolDown = "toggle-off";
        $scope.nameSortSymbolUp = "toggle-off";
        if ($scope.recordTypeSort == "asc") {
            $scope.recordTypeSort = "desc";
            $scope.recordTypeSortSymbolDown = "toggle-on";
            $scope.recordTypeSortSymbolUp = "toggle-off";
        } else {
            $scope.recordTypeSort = "asc";
            $scope.recordTypeSortSymbolDown = "toggle-off";
            $scope.recordTypeSortSymbolUp = "toggle-on";
        }
        return $scope.refreshRecords();
    };

    $scope.toggleCheckedRecordType = function(recordType) {
        if($scope.selectedRecordTypes.includes(recordType)) {
            $scope.selectedRecordTypes.splice($scope.selectedRecordTypes.indexOf(recordType), 1);
        } else {
            $scope.selectedRecordTypes.push(recordType);
        }
    };

    /**
     * Record change paging
     */
    $scope.getChangePageTitle = function() {
        return pagingService.getPanelTitle(changePaging);
    };

    $scope.changePrevPageEnabled = function() {
        return pagingService.prevPageEnabled(changePaging);
    };

    $scope.changeNextPageEnabled = function() {
        return pagingService.nextPageEnabled(changePaging);
    };

    $scope.changePrevPage = function() {
        var startFrom = pagingService.getPrevStartFrom(changePaging);
        return recordsService
            .listRecordSetChanges($scope.zoneId, changePaging.maxItems, startFrom)
            .then(function(response) {
                changePaging = pagingService.prevPageUpdate(response.data.nextId, changePaging);
                updateChangeDisplay(response.data.recordSetChanges);
            })
            .catch(function (error) {
                handleError(error, 'recordsService::changePrevPage-failure');
            });
    };

    $scope.changeNextPage = function() {
        return recordsService
            .listRecordSetChanges($scope.zoneId, changePaging.maxItems, changePaging.next)
            .then(function(response) {
                var changes = response.data.recordSetChanges;
                changePaging = pagingService.nextPageUpdate(changes, response.data.nextId, changePaging);

                if(changes.length > 0 ){
                    updateChangeDisplay(changes);
                }
            })
            .catch(function (error) {
                handleError(error, 'recordsService::changeNextPage-failure');
            });
    };

    function profileSuccess(results) {
        if (results.data) {
            $scope.profile = results.data;
            $log.debug('profileService::getAuthenticatedUserData-success');
        }
    }

    function profileFailure(results) {
        handleError(results, 'profileService::getAuthenticatedUserData-catch');
    }

    loadZonesPromise = $timeout($scope.refreshZone, 0);
    loadRecordsPromise = $timeout($scope.refreshRecords, 0);
    $timeout($scope.refreshRecordChangesPreview, 0);
    $timeout($scope.refreshRecordChanges, 0);

    profileService.getAuthenticatedUserData()
        .then(profileSuccess, profileFailure)
        .catch(profileFailure);
});
