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
    .controller('RecordsController', function ($scope, $timeout, $log, recordsService, groupsService, pagingService, utilityService, $q) {

    /**
      * Scope data initial setup
      */

    $scope.query = "";
    $scope.alerts = [];

    $scope.recordTypes = ['A', 'AAAA', 'CNAME', 'MX', 'NS', 'PTR', 'SPF', 'SRV', 'SSHFP', 'TXT'];
    $scope.sshfpAlgorithms = [{name: '(1) RSA', number: 1}, {name: '(2) DSA', number: 2}, {name: '(3) ECDSA', number: 3},
        {name: '(4) Ed25519', number: 4}];
    $scope.sshfpTypes = [{name: '(1) SHA-1', number: 1}, {name: '(2) SHA-256', number: 2}];

    $scope.records = {};
    $scope.recordsetChangesPreview = {};
    $scope.recordsetChanges = {};
    $scope.currentRecord = {};
    $scope.zoneInfo = {};

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

    $scope.createRecord = function() {
        record = {
            type: "A",
            ttl: 300,
            mxItems: [{preference:'', exchange:''}],
            srvItems: [{priority:'', weight:'', port:'', target:''}],
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
        .getMyGroupsStored()
        .then(
            function (results) {
                $scope.myGroups = results.groups;
                determineAdmin()
            })
        .catch(function (error){
            handleError(error, 'groupsService::getMyGroupsStored-failure');
        });
    }

    function determineAdmin(){
        var groupIds = $scope.myGroups.map(function(grp) {return grp['id']});
        $scope.isZoneAdmin = groupIds.indexOf($scope.zoneInfo.adminGroupId) > -1;
    }

    $scope.isGroupMember = function(groupId) {
        var groupMember = $scope.myGroups.find(function(group) {
            return groupId === group.id;
        });
        return groupMember !== undefined
    };

    /**
      * Global data-updating functions
      */

    $scope.refreshZone = function() {
        function success(response) {
            $log.log('recordsService::getZone-success');
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
            $log.log('recordsService::syncZone-success');
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
            $log.log('recordsService::getRecordSetChanges-success');
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
            $log.log('recordsService::getRecordSetChanges-success');
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
            $log.log('recordsService::getRecordSets-success ('+ response.data.recordSets.length +' records)');
            recordsPaging.next = response.data.nextId;
            updateRecordDisplay(response.data.recordSets);
        }
        return recordsService
            .getRecordSets($scope.zoneId, recordsPaging.maxItems, undefined, $scope.query)
            .then(success)
            .catch(function (error){
                handleError(error, 'recordsService::getRecordSets-failure');
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
                if($scope.records.length > 0) {
                  $("td.dataTables_empty").hide();
                } else {
                  $("td.dataTables_empty").show();
                }
            });
    };

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
            .getRecordSets($scope.zoneId, recordsPaging.maxItems, startFrom, $scope.query)
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
                .getRecordSets($scope.zoneId, recordsPaging.maxItems, recordsPaging.next, $scope.query)
                .then(function(response) {
                var recordSets = response.data.recordSets;
                recordsPaging = pagingService.nextPageUpdate(recordSets, response.data.nextId, recordsPaging);

                if(recordSets.length > 0 ){
                    updateRecordDisplay(recordSets);
                }
            })
            .catch(function (error){
                handleError(error, 'recordsService::nextPage-failure');
            });
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

    loadZonesPromise = $timeout($scope.refreshZone, 0);
    loadRecordsPromise = $timeout($scope.refreshRecords, 0);
    $timeout($scope.refreshRecordChangesPreview, 0);
    $timeout($scope.refreshRecordChanges, 0);

});
