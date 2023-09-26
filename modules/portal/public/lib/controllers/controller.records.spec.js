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

describe('Controller: RecordsController', function () {
    beforeEach(function() {
        module('ngMock'),
        module('service.groups'),
        module('service.records'),
        module('service.paging'),
        module('service.profile'),
        module('service.utility'),
        module('directives.modals.record.module'),
        module('controller.records')
    });
    beforeEach(inject(function ($rootScope, $controller, $httpBackend, $q, groupsService, recordsService, pagingService, profileService) {
        this.rootScope = $rootScope;
        this.scope = this.rootScope.$new();
        this.$httpBackend = $httpBackend;
        this.controller = $controller('RecordsController',{'$scope':this.scope});
        this.groupsService = groupsService;
        this.recordsService = recordsService;
        this.pagingService = pagingService;
        this.profileService = profileService;
        this.q = $q;
    }));

    it('clearRecord should clear ttl and data', function () {
        var record = {'ttl' : 300, 'data': 'some data'};
        this.scope.clearRecord(record);
        expect(record.ttl).toBe(undefined);
        expect(record.data).toBe(undefined);
    });

    it('getRecordChangeStatusLabel returns success if Completed', function () {
        var goodStatus = "Complete";
        expect(this.scope.getRecordChangeStatusLabel(goodStatus)).toBe("success");
    });

    it('getRecordChangeStatusLabel returns danger if Failed', function () {
        var badStatus = "Failed";
        expect(this.scope.getRecordChangeStatusLabel(badStatus)).toBe("danger");
    });

    it('getRecordChangeStatusLabel returns info if anything but Active or Deleted', function () {
        var notActiveOrDelete = "notActiveOrDelete";
        expect(this.scope.getRecordChangeStatusLabel(notActiveOrDelete)).toBe("info");
    });

    it('getZoneStatusLabel returns success if Active', function() {
        this.scope.zoneInfo["status"] = "Active";
        expect(this.scope.getZoneStatusLabel()).toBe("success");
    });

    it('getZoneStatusLabel returns danger if Deleted', function() {
        this.scope.zoneInfo["status"] = "Deleted";
        expect(this.scope.getZoneStatusLabel()).toBe("danger");
    });

    it('getZoneStatusLabel returns info if anything but Active or Deleted', function() {
        this.scope.zoneInfo["status"] = "notActiveOrDelete";
        expect(this.scope.getZoneStatusLabel()).toBe("info");
    });

    it('addNewSshfp should add an empty sshfp object to currentRecord.sshfpItems', function() {
       this.scope.currentRecord.sshfpItems = [];
       this.scope.addNewSshfp();
       expect(this.scope.currentRecord.sshfpItems).toEqual([{algorithm: '', type: '', fingerprint: ''}]);
    });

    it('deleteSshfp should delete the correct sshfp object from currentRecord.sshfpItems', function() {
        this.scope.currentRecord.sshfpItems = [{algorithm: '1', type: '', fingerprint: ''},
                                               {algorithm: '2', type: '', fingerprint: ''},
                                               {algorithm: '3', type: '', fingerprint: ''}];
        this.scope.deleteSshfp(1);
        expect(this.scope.currentRecord.sshfpItems).toEqual([{algorithm: '1', type: '', fingerprint: ''},
            {algorithm: '3', type: '', fingerprint: ''}]);
    });

    it('deleteSshfp should keep at least one sshfp object', function() {
        this.scope.currentRecord.sshfpItems = [{algorithm: '1', type: '', fingerprint: ''},
            {algorithm: '2', type: '', fingerprint: ''}];
        this.scope.deleteSshfp(0);
        this.scope.deleteSshfp(0);
        expect(this.scope.currentRecord.sshfpItems).toEqual([{algorithm: '', type: '', fingerprint: ''}]);
    });

    it('refreshZone updates zoneInfo and isZoneAdmin when user is in admin group', function() {
        mockZone = {
            name: "dummy.",
            email: "test@test.com",
            status: "Active",
            created: "2017-02-15T14:58:39Z",
            account: "c8234503-bfda-4b80-897f-d74129051eaa",
            acl: {rules: []},
            adminGroupId: "c8234503-bfda-4b80-897f-d74129051eaa",
            id: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
            shared: false,
            status: "Active"
        };
        mockGroups = {data: { groups: [
            {id: "c8234503-bfda-4b80-897f-d74129051eaa",
                name: "test",
                email: "test@test.com",
                admins: [{id: "7096b806-c12a-4171-ba13-7fabb523acee"}],
                created: "2017-02-15T14:58:31Z",
                members: [{id: "7096b806-c12a-4171-ba13-7fabb523acee"}],
                status: "Active"}
                ],
            maxItems: 100}};

        this.scope.zoneInfo = {};
        this.scope.profile = {id: "7096b806-c12a-4171-ba13-7fabb523acee", isSuper: false};
        spyOn(this.recordsService, 'getZone')
            .and.stub()
            .and.returnValue(this.q.when({ data: {zone: mockZone}}));
        spyOn(this.groupsService, 'getGroups')
            .and.stub()
            .and.returnValue(this.q.when(mockGroups));
        this.$httpBackend.when('GET', '/api/users/currentuser').respond({});
        this.scope.refreshZone();
        this.scope.$digest();

        expect(this.scope.zoneInfo).toEqual(mockZone);
        expect(this.scope.isZoneAdmin).toBe(true);
    });

    it('refreshZone updates zoneInfo and isZoneAdmin when user is not in admin group', function() {
        mockZone = {
            name: "dummy.",
            email: "test@test.com",
            status: "Active",
            created: "2017-02-15T14:58:39Z",
            account: "c8234503-bfda-4b80-897f-d74129051eaa",
            acl: {rules: []},
            adminGroupId: "c8234503-bfda-4b80-897f-d74129051eaa",
            id: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
            shared: false,
            status: "Active"
        };
        mockGroups = {data: { groups: [
            {id: "some-other-id",
                name: "test",
                email: "test@test.com",
                admins: [{id: "7096b806-c12a-4171-ba13-7fabb523acee"}],
                created: "2017-02-15T14:58:31Z",
                members: [{id: "7096b806-c12a-4171-ba13-7fabb523acee"}],
                status: "Active"}
        ],
            maxItems: 100}};

        this.scope.zoneInfo = {};
        this.scope.profile = {id: "notAdmin", isSuper: false};
        spyOn(this.recordsService, 'getZone')
            .and.stub()
            .and.returnValue(this.q.when({ data: {zone: mockZone}}));
        spyOn(this.groupsService, 'getGroups')
            .and.stub()
            .and.returnValue(this.q.when(mockGroups));
        this.$httpBackend.when('GET', '/api/users/currentuser').respond({});
        this.scope.refreshZone();
        this.scope.$digest();

        expect(this.scope.zoneInfo).toEqual(mockZone);
        expect(this.scope.isZoneAdmin).toBe(false);
    });

    it('refreshZone updates zoneInfo and isZoneAdmin when user is a super user', function() {
        mockZone = {
            name: "dummy.",
            email: "test@test.com",
            status: "Active",
            created: "2017-02-15T14:58:39Z",
            account: "c8234503-bfda-4b80-897f-d74129051eaa",
            acl: {rules: []},
            adminGroupId: "c8234503-bfda-4b80-897f-d74129051eaa",
            id: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
            shared: false,
            status: "Active"
        };
        mockGroups = {data: { groups: [
            {id: "some-other-id",
                name: "test",
                email: "test@test.com",
                admins: [{id: "7096b806-c12a-4171-ba13-7fabb523acee"}],
                created: "2017-02-15T14:58:31Z",
                members: [{id: "7096b806-c12a-4171-ba13-7fabb523acee"}],
                status: "Active"}
        ],
            maxItems: 100}};

        this.scope.zoneInfo = {};
        this.scope.profile = {id: "notAdmin", isSuper: true};
        spyOn(this.recordsService, 'getZone')
            .and.stub()
            .and.returnValue(this.q.when({ data: {zone: mockZone}}));
        spyOn(this.groupsService, 'getGroups')
            .and.stub()
            .and.returnValue(this.q.when(mockGroups));
        this.$httpBackend.when('GET', '/api/users/currentuser').respond({});
        this.scope.refreshZone();
        this.scope.$digest();

        expect(this.scope.zoneInfo).toEqual(mockZone);
        expect(this.scope.isZoneAdmin).toBe(true);
    });

    it('refreshZone updates zoneInfo and isZoneAdmin when user is a support user only', function() {
        mockZone = {
            name: "dummy.",
            email: "test@test.com",
            status: "Active",
            created: "2017-02-15T14:58:39Z",
            account: "c8234503-bfda-4b80-897f-d74129051eaa",
            acl: {rules: []},
            adminGroupId: "c8234503-bfda-4b80-897f-d74129051eaa",
            id: "c5c87405-2ec8-4e03-b2dc-c6758a5d9666",
            shared: false,
            status: "Active"
        };
        mockGroups = {data: { groups: [
            {id: "some-other-id",
                name: "test",
                email: "test@test.com",
                admins: [{id: "7096b806-c12a-4171-ba13-7fabb523acee"}],
                created: "2017-02-15T14:58:31Z",
                members: [{id: "7096b806-c12a-4171-ba13-7fabb523acee"}],
                status: "Active"}
        ],
            maxItems: 100}};

        this.scope.zoneInfo = {};
        this.scope.profile = {id: "notAdmin", isSuper: false};
        spyOn(this.recordsService, 'getZone')
            .and.stub()
            .and.returnValue(this.q.when({ data: {zone: mockZone}}));
        spyOn(this.groupsService, 'getGroups')
            .and.stub()
            .and.returnValue(this.q.when(mockGroups));
        this.$httpBackend.when('GET', '/api/users/currentuser').respond({});
        this.scope.refreshZone();
        this.scope.$digest();

        expect(this.scope.zoneInfo).toEqual(mockZone);
        expect(this.scope.isZoneAdmin).toBe(false);
    });

    it('refresh should call listRecordSetsByZone with the correct parameters', function () {
        var mockRecords = {data: { recordSets: [
            {   name: "dummy",
                records: [{address: "1.1.1.1"}],
                status: "Active",
                ttl: 38400,
                type: "A"}
            ],
            maxItems: 100}};

        var listRecordSetsByZone = spyOn(this.recordsService, 'listRecordSetsByZone')
            .and.stub()
            .and.returnValue(this.q.when(mockRecords));

        var expectedZoneId = this.scope.zoneId;
        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedNameSort = "asc";
        var expectedRecordTypeSort = "none";


        this.scope.refreshRecords();

        expect(listRecordSetsByZone.calls.count()).toBe(1);
        expect(listRecordSetsByZone.calls.mostRecent().args).toEqual(
            [expectedZoneId, expectedMaxItems, expectedStartFrom, expectedQuery, "", expectedNameSort, expectedRecordTypeSort]);
    });

    it('next page should call listRecordSetsByZone with the correct parameters', function () {
        var mockRecords = {data: { recordSets: [
            {   name: "dummy",
                records: [{address: "1.1.1.1"}],
                status: "Active",
                ttl: 38400,
                type: "A"}
            ],
            maxItems: 100}};

        var listRecordSetsByZone = spyOn(this.recordsService, 'listRecordSetsByZone')
            .and.stub()
            .and.returnValue(this.q.when(mockRecords));

        var expectedZoneId = this.scope.zoneId;
        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedNameSort = "asc";
        var expectedRecordTypeSort = "none";

        this.scope.nextPage();

        expect(listRecordSetsByZone.calls.count()).toBe(1);
        expect(listRecordSetsByZone.calls.mostRecent().args).toEqual(
            [expectedZoneId, expectedMaxItems, expectedStartFrom, expectedQuery, "", expectedNameSort, expectedRecordTypeSort]);
    });

    it('prev page should call listRecordSetsByZone with the correct parameters', function () {
        var mockRecords = {data: { recordSets: [
            {   name: "dummy",
                records: [{address: "1.1.1.1"}],
                status: "Active",
                ttl: 38400,
                type: "A"}
            ],
            maxItems: 100}};

        var listRecordSetsByZone = spyOn(this.recordsService, 'listRecordSetsByZone')
            .and.stub()
            .and.returnValue(this.q.when(mockRecords));

        var expectedZoneId = this.scope.zoneId;
        var expectedMaxItems = 100;
        var expectedStartFrom =  undefined;
        var expectedQuery = this.scope.query;
        var expectedNameSort = "asc";
        var expectedRecordTypeSort = "none";

        this.scope.prevPage();

        expect(listRecordSetsByZone.calls.count()).toBe(1);
        expect(listRecordSetsByZone.calls.mostRecent().args).toEqual(
            [expectedZoneId, expectedMaxItems, expectedStartFrom, expectedQuery, '', expectedNameSort, expectedRecordTypeSort]);
    });

    it('toggle name sort should call listRecordSetsByZone with the correct parameters', function () {
        var mockRecords = {data: { recordSets: [
            {   name: "dummy",
                records: [{address: "1.1.1.1"}],
                status: "Active",
                ttl: 38400,
                type: "A"}
            ],
            maxItems: 100,
            nameSort: "desc"}};

        var listRecordSetsByZone = spyOn(this.recordsService, 'listRecordSetsByZone')
            .and.stub()
            .and.returnValue(this.q.when(mockRecords));

        var expectedZoneId = this.scope.zoneId;
        var expectedMaxItems = 100;
        var expectedStartFrom =  undefined;
        var expectedQuery = this.scope.query;
        var expectedNameSort = "desc";
        var expectedRecordTypeSort = "none";

        this.scope.toggleNameSort();

        expect(listRecordSetsByZone.calls.count()).toBe(1);
        expect(listRecordSetsByZone.calls.mostRecent().args).toEqual(
            [expectedZoneId, expectedMaxItems, expectedStartFrom, expectedQuery, '', expectedNameSort, expectedRecordTypeSort]);
    });

    it('toggle record type sort should call listRecordSetsByZone with the correct parameters', function () {
        var mockRecords = {data: { recordSets: [
            {   name: "dummy",
                records: [{address: "1.1.1.1"}],
                status: "Active",
                ttl: 38400,
                type: "A"}
            ],
            maxItems: 100,
            nameSort: "",
            recordTypeSort: "asc"}};

        var listRecordSetsByZone = spyOn(this.recordsService, 'listRecordSetsByZone')
            .and.stub()
            .and.returnValue(this.q.when(mockRecords));

        var expectedZoneId = this.scope.zoneId;
        var expectedMaxItems = 100;
        var expectedStartFrom =  undefined;
        var expectedQuery = this.scope.query;
        var expectedNameSort = "";
        var expectedRecordTypeSort = "asc";

        this.scope.toggleRecordTypeSort();

        expect(listRecordSetsByZone.calls.count()).toBe(1);
        expect(listRecordSetsByZone.calls.mostRecent().args).toEqual(
            [expectedZoneId, expectedMaxItems, expectedStartFrom, expectedQuery, '', expectedNameSort, expectedRecordTypeSort]);

    });

    it('filter by record type should call listRecordSetsByZone with the correct parameters', function () {
        var mockRecords = {data: { recordSets: [
            {   name: "dummy",
                records: [{address: "1.1.1.1"}],
                status: "Active",
                ttl: 38400,
                type: "A"}
            ],
            maxItems: 100,
            recordTypeFilter: "A"}};

        var listRecordSetsByZone = spyOn(this.recordsService, 'listRecordSetsByZone')
            .and.stub()
            .and.returnValue(this.q.when(mockRecords));

        var expectedZoneId = this.scope.zoneId;
        var expectedMaxItems = 100;
        var expectedStartFrom =  undefined;
        var expectedQuery = this.scope.query;
        var expectedRecordTypeFilter = "A";
        var expectedNameSort = "asc";
        var expectedRecordTypeSort = "none";


        this.scope.toggleCheckedRecordType("A");
        this.scope.refreshRecords();

        expect(listRecordSetsByZone.calls.count()).toBe(1);
        expect(listRecordSetsByZone.calls.mostRecent().args).toEqual(
            [expectedZoneId, expectedMaxItems, expectedStartFrom, expectedQuery, expectedRecordTypeFilter, expectedNameSort, expectedRecordTypeSort]);
    });
});
