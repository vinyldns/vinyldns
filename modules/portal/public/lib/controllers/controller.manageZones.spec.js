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

describe('Controller: ManageZonesController', function () {
    beforeEach(function () {
        module('ngMock'),
        module('service.groups'),
        module('service.records'),
        module('service.utility'),
        module('service.zones'),
        module('service.profile'),
        module('service.paging'),
        module('controller.manageZones')
    });
    beforeEach(inject(function ($rootScope, $controller, $q, groupsService, recordsService, zonesService,
        profileService, pagingService) {
        this.rootScope = $rootScope;
        this.scope = $rootScope.$new();
        this.groupsService = groupsService;
        this.zonesService = zonesService;
        this.recordsService = recordsService;
        this.profileService = profileService;
        this.pagingService = pagingService;
        this.q = $q;
        this.groupsService.getGroups = function () {
            return $q.when({
                data: {
                    groups: "all my groups"
                }
            });
        };
        zonesService.getBackendIds = function() {
                    return $q.when({
                        data: ['backend-1', 'backend-2']
                    });
                };
        this.scope.addAclRuleForm = {
            $setPristine: function(){}
        };
        this.controller = $controller('ManageZonesController', {'$scope': this.scope});
    }));

    it('updateZone changes currentManageZoneState to CONFIRM_UPDATE', function() {
       this.scope.currentManageZoneState = this.scope.manageZoneState.UPDATE;
       this.scope.clickUpdateZone();
       expect(this.scope.currentManageZoneState).toBe(this.scope.manageZoneState.CONFIRM_UPDATE);
    });

    it('cancelZoneUpdate changes currentManageZoneState to UPDATE', function() {
        this.scope.currentManageZoneState = this.scope.manageZoneState.CONFIRM_UPDATE;
        this.scope.cancelUpdateZone();
        expect(this.scope.currentManageZoneState).toBe(this.scope.manageZoneState.UPDATE);
    });

    it('submitUpdateZone calls updateZone', function() {
        this.scope.currentManageZoneState = this.scope.manageZoneState.CONFIRM_UPDATE;
        var normalizeZoneDates = spyOn(this.zonesService, 'normalizeZoneDates')
            .and.stub();
        var setConnectionKeys = spyOn(this.zonesService, 'setConnectionKeys')
            .and.stub();
        var checkBackendId = spyOn(this.zonesService, 'checkBackendId')
                    .and.stub();
        var checkSharedStatus = spyOn(this.zonesService, 'checkSharedStatus').and.stub();
        var updateZone = spyOn(this.scope, 'updateZone')
            .and.stub();

        this.scope.submitUpdateZone();

        expect(normalizeZoneDates.calls.count()).toBe(1);
        expect(setConnectionKeys.calls.count()).toBe(1);
        expect(updateZone.calls.count()).toBe(1);
        expect(this.scope.currentManageZoneState).toBe(this.scope.manageZoneState.UPDATE);
    });

    it('objectsDiffer passes if zone objects are same', function() {
        mockZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234'
        };
        mockUpdateZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234'
        };
        var normalizeZone = spyOn(this.scope, 'normalizeZone');
        var objectsDiffer = this.scope.objectsDiffer(mockZone, mockUpdateZone);
        expect(normalizeZone.calls.count()).toBe(2);
        expect(objectsDiffer).toBeFalsy();
    });

    it('objectsDiffer fails if zone objects are different', function() {
        mockZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234'
        };
        mockUpdateZone = {
            name: 'vinyldns.',
            email: 'update@example.com',
            adminGroupId: '1234'
        };
        var normalizeZone = spyOn(this.scope, 'normalizeZone')
            .and.callThrough();
        var objectsDiffer = this.scope.objectsDiffer(mockZone, mockUpdateZone);
        expect(normalizeZone.calls.count()).toBe(2);
        expect(objectsDiffer).toBeTruthy();
    });

    it('normalizeZone removes display attributes', function() {
        mockZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234',
            adminGroupName: 'name',
            hiddenKey: 'key',
            hiddenTransferKey: 'key'
        };
        expectedZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234'
        };
        var zone = this.scope.normalizeZone(mockZone);
        expect(zone).toEqual(expectedZone);
    });

    it('clearUpdateConnection clears updateZoneInfo connection', function() {
       mockUpdateZone = {
           name: 'vinyldns.',
           email: 'test@example.com',
           adminGroupId: '1234',
           connection: {
               name: "connection-name",
               keyName: "connection-key-name",
               key: "connection-key",
               primaryServer: "connection-server"
           },
           hiddenKey: 'new key',
           hiddenTransferKey: 'new key'
       };
       mockUpdateZoneCleared = {
           name: 'vinyldns.',
           email: 'test@example.com',
           adminGroupId: '1234',
           hiddenKey: '',
           hiddenTransferKey: 'new key'
       };
       this.scope.updateZoneInfo = mockUpdateZone;
       this.scope.clearUpdateConnection();
       expect(this.scope.updateZoneInfo).toEqual(mockUpdateZoneCleared);
    });

    it('clearUpdateTransferConnection clears updateZoneInfo transferConnection', function() {
        mockUpdateZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234',
            transferConnection: {
                name: "connection-name",
                keyName: "connection-key-name",
                key: "connection-key",
                primaryServer: "connection-server"
            },
            hiddenKey: 'new key',
            hiddenTransferKey: 'new key'
        };
        mockUpdateZoneCleared = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234',
            hiddenKey: 'new key',
            hiddenTransferKey: ''
        };
        this.scope.updateZoneInfo = mockUpdateZone;
        this.scope.clearUpdateTransferConnection();
        expect(this.scope.updateZoneInfo).toEqual(mockUpdateZoneCleared);
    });

    it('refresh zone properly refreshes zone', function() {
        this.scope.zoneInfo = {
            'adminGroupId': 'id101112'
        };

        mockResponse = {
            data: {
                zone: {
                    name: 'vinyldns.',
                    email: 'test@example.com',
                    adminGroupId: 'id101112',
                    adminGroupName: 'name',
                    hiddenKey: 'key',
                    hiddenTransferKey: 'key'
                }
            }
        };

        var getZone = spyOn(this.recordsService, 'getZone')
            .and.stub()
            .and.returnValue(this.q.when(mockResponse));
        var refreshAclRuleDisplay = spyOn(this.scope, 'refreshAclRuleDisplay')
            .and.stub();
        var refreshZoneChange = spyOn(this.scope, 'refreshZoneChange')
                    .and.stub();
        var validDomains = spyOn(this.scope, 'validDomains')
                    .and.stub();
        this.scope.currentManageZoneState = this.scope.manageZoneState.CONFIRM_UPDATE;
        this.scope.updateZoneInfo.hiddenKey = 'some key';
        this.scope.updateZoneInfo.hiddenTransferKey = 'some key';
        this.scope.refreshZone();
        this.scope.$digest();
        expect(getZone.calls.count()).toBe(1);
        expect(refreshAclRuleDisplay.calls.count()).toBe(1);
        expect(refreshZoneChange.calls.count()).toBe(1);
        expect(this.scope.zoneInfo).toEqual(mockResponse.data.zone);
        expect(this.scope.updateZoneInfo. adminGroupId).toEqual('id101112');
        expect(this.scope.updateZoneInfo.hiddenKey).toEqual('');
        expect(this.scope.updateZoneInfo.hiddenTransferKey).toEqual('');
        expect(this.scope.currentManageZoneState).toBe(this.scope.manageZoneState.UPDATE);
    });

    it('refresh zone properly adds error to alerts when failing', function() {
        mockError = {
            status: '404',
            statusText: 'Not Found',
            data: 'Zone not found'
        };
        var getZone = spyOn(this.recordsService, 'getZone')
            .and.stub()
            .and.returnValue(this.q.reject(mockError));

        this.scope.myGroups = [{'id': 'id123'}, {'id': 'id456'}, {'id': 'id789'}];
        this.scope.zoneInfo = {
            'adminGroupId': 'id101112'
        };

        var getGroupResponse = {
            'data': {
                'name': 'groupName'
            }
        };

        var getGroup = spyOn(this.groupsService, 'getGroup')
            .and.stub()
            .and.returnValue(getGroupResponse);

        this.scope.refreshZone();
        this.scope.$digest();
        expect(getZone.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([{
           type: 'danger',
           content: "HTTP 404 (Not Found): Zone not found"
        }]);
    });

    it('updateZone successfully calls updateZone', function() {
        var updateZone = spyOn(this.zonesService, 'updateZone')
            .and.stub()
            .and.returnValue(this.q.when('response'));
        this.scope.updateZone();
        expect(updateZone.calls.count()).toBe(1);
    });

    it('updateZone properly adds error to alerts when failing', function() {
        mockError = {
            status: '404',
            statusText: 'Not Found',
            data: 'Zone not found'
        };
        var updateZone = spyOn(this.zonesService, 'updateZone')
            .and.stub()
            .and.returnValue(this.q.reject(mockError));
        var refreshZone = spyOn(this.scope, 'refreshZone')
            .and.stub();
        this.scope.updateZone();
        this.scope.$digest();
        expect(updateZone.calls.count()).toBe(1);
        expect(refreshZone.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([{
            type: 'danger',
            content: "HTTP 404 (Not Found): Zone not found"
        }]);
    });

    it('clickCreateAclRule properly sets up currentAclRule and aclModal', function() {
       this.scope.currentAclRule = {};
       this.scope.aclModal = {};
        expectedCurrentAclRule = {
            priority: 'User',
            accessLevel: 'Read'
        };
        expectedAclModal = {
            action: this.scope.aclModalState.CREATE,
            title: 'Create ACL Rule',
            details: this.scope.aclModalParams.editable
        };
       this.scope.clickCreateAclRule();
       expect(this.scope.currentAclRule).toEqual(expectedCurrentAclRule);
       expect(this.scope.aclModal).toEqual(expectedAclModal);
    });

    it('clickDeleteAclRule properly sets up currentAclRule and aclModal', function() {
        this.scope.currentAclRuleIndex = {};
        this.scope.aclModal = {};

        var expectedCurrentAclRuleIndex = 0;
        var expectedAclModal = {
            action: this.scope.aclModalState.CONFIRM_DELETE,
            title: 'Delete ACL Rule',
            details: this.scope.aclModalParams.readOnly
        };
        this.scope.clickDeleteAclRule(0);
        expect(this.scope.currentAclRuleIndex).toEqual(expectedCurrentAclRuleIndex);
        expect(this.scope.aclModal).toEqual(expectedAclModal);
    });

    it('clickUpdateAclRule properly sets up currentAclRule and aclModal', function() {
        this.scope.currentAclRuleIndex = {};
        this.scope.currentAclRule = {};
        this.scope.aclModal = {};
        var mockRule = {
            accessLevel: 'Read',
            recordType: ['A','AAAA']
        };
        this.scope.aclRules = [mockRule];

        var expectedCurrentAclRuleIndex = 0;
        var expectedCurrentAclRule = mockRule;
        var expectedAclModal = {
            action: this.scope.aclModalState.UPDATE,
            title: 'Update ACL Rule',
            details: this.scope.aclModalParams.editable
        };
        this.scope.clickUpdateAclRule(0);
        expect(this.scope.currentAclRuleIndex).toEqual(expectedCurrentAclRuleIndex);
        expect(this.scope.currentAclRule).toEqual(expectedCurrentAclRule);
        expect(this.scope.aclModal).toEqual(expectedAclModal);
    });

    it('confirmUpdateAclRule changes aclModal action to correct state', function() {
        this.scope.aclModal = {};
        this.scope.confirmUpdateAclRule(true);
        expect(this.scope.aclModal.action).toBe(this.scope.aclModalState.CONFIRM_UPDATE);

        this.scope.aclModal = {};
        this.scope.confirmUpdateAclRule(false);
        expect(this.scope.aclModal.action).toBe(this.scope.aclModalState.UPDATE);
    });

    it('clearForm correctly sets currentAclRule', function() {
        this.scope.currentAclRule = {};
        var expectedCurrentAclRule = {
            priority: 'User',
            accessLevel: 'Read'
        };
        this.scope.clearForm();
        expect(this.scope.currentAclRule).toEqual(expectedCurrentAclRule);
    });

    it('submitAclRule works as expected when priority is not user', function() {
        this.scope.addAclRuleForm.$valid = true;
        this.scope.currentAclRule = {
            accessLevel: 'Read',
            recordType: ['A','AAAA']
        };
        var postUserLookup = spyOn(this.scope, 'postUserLookup')
            .and.stub();

        this.scope.submitAclRule('Create');
        expect(postUserLookup.calls.count()).toBe(1);
    });

    it('submitAclRule works as expected when priority is user', function() {
        this.scope.addAclRuleForm.$valid = true;
        var mockRule = {
            priority: 'User',
            userName: 'ntid',
            accessLevel: 'Read',
            recordType: ['A','AAAA']
        };
        var mockZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234',
            acl: {
                rules: []
            }
        };
        this.scope.currentAclRule = mockRule;
        this.scope.zoneInfo = mockZone;
        var getUserDataByUsername = spyOn(this.profileService, 'getUserDataByUsername')
            .and.stub()
            .and.returnValue(this.q.when({data: {id: 'found id'}}));
        var postUserLookup = spyOn(this.scope, 'postUserLookup')
            .and.stub();
        var expectedRule = {
            priority: 'User',
            userName: 'ntid',
            accessLevel: 'Read',
            recordType: ['A','AAAA'],
            userId: 'found id'
        };

        this.scope.submitAclRule('Create');
        this.scope.$digest();
        expect(getUserDataByUsername.calls.count()).toBe(1);
        expect(postUserLookup.calls.count()).toBe(1);
        expect(this.scope.currentAclRule).toEqual(expectedRule);
    });

    it('submitAclRule works as expected when priority is group', function() {
        this.scope.addAclRuleForm.$valid = true;
        var mockRule = {
            priority: 'Group',
            groupId: '1234',
            accessLevel: 'Read',
            recordType: ['A','AAAA']
        };
        var mockZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234',
            acl: {
                rules: []
            }
        };
        this.scope.currentAclRule = mockRule;
        this.scope.zoneInfo = mockZone;
        var getUserDataByUsername = spyOn(this.profileService, 'getUserDataByUsername')
            .and.stub();
        var postUserLookup = spyOn(this.scope, 'postUserLookup')
            .and.stub();
        var expectedRule = {
            priority: 'Group',
            groupId: '1234',
            accessLevel: 'Read',
            recordType: ['A','AAAA'],
        };

        this.scope.submitAclRule('Create');
        this.scope.$digest();
        expect(getUserDataByUsername.calls.count()).toBe(0);
        expect(postUserLookup.calls.count()).toBe(1);
        expect(this.scope.currentAclRule).toEqual(expectedRule);
    });

    it('postUserLookup works as expected when given Create', function() {
        var mockRule = {
            accessLevel: 'Read',
            recordType: ['A','AAAA'],
            priority: 'Group',
            groupId: '1234'
        };
        var mockZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234',
            acl: {
                rules: []
            }
        };
        this.scope.currentAclRule = mockRule;
        this.scope.zoneInfo = mockZone;
        var toVinylAclRule = spyOn(this.zonesService, 'toVinylAclRule')
            .and.stub()
            .and.returnValue(mockRule);
        var normalizeZoneDates = spyOn(this.zonesService, 'normalizeZoneDates')
            .and.stub()
            .and.returnValue(mockZone);
        var updateZone = spyOn(this.scope, 'updateZone')
            .and.stub();
        var expectedSentParamOne = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234',
            acl: {
                rules: [mockRule]
            }
        };
        var expectedSentParamTwo = 'ACL Rule Create';

        this.scope.postUserLookup('Create');
        expect(toVinylAclRule.calls.count()).toBe(1);
        expect(normalizeZoneDates.calls.count()).toBe(1);
        expect(updateZone.calls.count()).toBe(1);
        expect(updateZone.calls.mostRecent().args).toEqual([expectedSentParamOne, expectedSentParamTwo]);
    });

    it('postUserLookup works as expected when given Update', function() {
        var oldRule = {
            accessLevel: 'Read',
            recordType: ['A','AAAA'],
            priority: 'Group',
            groupId: 'old id'
        };
        var newRule = {
            accessLevel: 'Read',
            recordType: ['A','AAAA'],
            priority: 'Group',
            groupId: 'new id'
        };
        var mockZone = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234',
            acl: {
                rules: [oldRule, oldRule]
            }
        };
        this.scope.currentAclRule = newRule;
        this.scope.currentAclRuleIndex = 1;
        this.scope.zoneInfo = mockZone;
        var toVinylAclRule = spyOn(this.zonesService, 'toVinylAclRule')
            .and.stub()
            .and.returnValue(newRule);
        var normalizeZoneDates = spyOn(this.zonesService, 'normalizeZoneDates')
            .and.stub()
            .and.returnValue(mockZone);
        var updateZone = spyOn(this.scope, 'updateZone')
            .and.stub();
        var expectedSentParamOne = {
            name: 'vinyldns.',
            email: 'test@example.com',
            adminGroupId: '1234',
            acl: {
                rules: [oldRule, newRule]
            }
        };
        var expectedSentParamTwo = 'ACL Rule Update';

        this.scope.postUserLookup('Update');
        expect(toVinylAclRule.calls.count()).toBe(1);
        expect(normalizeZoneDates.calls.count()).toBe(1);
        expect(updateZone.calls.count()).toBe(1);
        expect(updateZone.calls.mostRecent().args).toEqual([expectedSentParamOne, expectedSentParamTwo]);
    });

    it('refreshAclRuleDisplay properly sets aclRules', function() {
        this.scope.zoneInfo = {
            acl: {
                rules: ['rule', 'rule', 'rule']
            }
        };
        var toDisplayAclRule = spyOn(this.zonesService, 'toDisplayAclRule')
            .and.stub()
            .and.returnValue('rule');

        this.scope.refreshAclRuleDisplay();
        expect(toDisplayAclRule.calls.count()).toBe(3);
        expect(this.scope.aclRules).toEqual(this.scope.zoneInfo.acl.rules);
    });

    it('next page should call listZoneChangesByZoneId with the correct parameters', function () {
        var mockZoneChange = {data: {
                                zoneId: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
                                zoneChanges: [{ zone: {
                                    name: "dummy.",
                                    email: "test@test.com",
                                    status: "Active",
                                    created: "2017-02-15T14:58:39Z",
                                    account: "c8234503-bfda-4b80-897f-d74129051eaa",
                                    acl: {rules: []},
                                    adminGroupId: "c8234503-bfda-4b80-897f-d74129051eaa",
                                    id: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
                                    shared: false,
                                    status: "Active",
                                    latestSync: "2017-02-15T14:58:39Z",
                                    isTest: true
                                }}],maxItems: 100}};

        var getZoneChanges = spyOn(this.zonesService, 'getZoneChanges')
            .and.stub()
            .and.returnValue(this.q.when(mockZoneChange));

        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedZoneId = this.scope.zoneId;

        this.scope.nextPageZoneHistory();

        expect(getZoneChanges.calls.count()).toBe(1);
        expect(getZoneChanges.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedZoneId]);
    });

    it('prev page should call getZoneChanges with the correct parameters', function () {
        var mockZoneChange = {data: {
                                zoneId: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
                                zoneChanges: [{ zone: {
                                    name: "dummy.",
                                    email: "test@test.com",
                                    status: "Active",
                                    created: "2017-02-15T14:58:39Z",
                                    account: "c8234503-bfda-4b80-897f-d74129051eaa",
                                    acl: {rules: []},
                                    adminGroupId: "c8234503-bfda-4b80-897f-d74129051eaa",
                                    id: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
                                    shared: false,
                                    status: "Active",
                                    latestSync: "2017-02-15T14:58:39Z",
                                    isTest: true
                                }}],maxItems: 100}};

        var getZoneChanges = spyOn(this.zonesService, 'getZoneChanges')
            .and.stub()
            .and.returnValue(this.q.when(mockZoneChange));

        var expectedMaxItems = 100;
        var expectedStartFrom =  undefined;
        var expectedZoneId = this.scope.zoneId;

        this.scope.prevPageZoneHistory();

        expect(getZoneChanges.calls.count()).toBe(1);
        expect(getZoneChanges.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedZoneId]);
    });

    it('test that we properly get Zone History data', function(){
        this.scope.zoneChanges = {};
        var mockZoneChange = {data: {
                                zoneId: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
                                zoneChanges: [{ zone: {
                                    name: "dummy.",
                                    email: "test@test.com",
                                    status: "Active",
                                    created: "2017-02-15T14:58:39Z",
                                    account: "c8234503-bfda-4b80-897f-d74129051eaa",
                                    acl: {rules: []},
                                    adminGroupId: "c8234503-bfda-4b80-897f-d74129051eaa",
                                    id: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
                                    shared: false,
                                    status: "Active",
                                    latestSync: "2017-02-15T14:58:39Z",
                                    isTest: true
                                }}],maxItems: 100}};
        var updateZoneChangeDisplay = spyOn(this.scope, 'updateZoneChangeDisplay')
            .and.stub();
        var getZoneChanges = spyOn(this.zonesService, 'getZoneChanges')
                    .and.stub()
                    .and.returnValue(this.q.when(mockZoneChange));
        this.scope.refreshZoneChange();
        this.scope.$digest();
        expect(getZoneChanges.calls.count()).toBe(1);
        expect(this.scope.zoneChanges).toEqual(mockZoneChange.data.zoneChanges);
    });
});
