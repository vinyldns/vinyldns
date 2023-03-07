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

describe('Controller: GroupsController', function () {
    beforeEach(function () {
        module('ngMock'),
        module('service.groups'),
        module('service.profile'),
        module('service.utility'),
        module('service.paging'),
        module('controller.groups')
    });
    beforeEach(inject(function ($rootScope, $controller, $q, groupsService, profileService, utilityService, pagingService) {
        this.scope = $rootScope.$new();
        this.groupsService = groupsService;
        this.utilityService = utilityService;
        this.q = $q;
        this.pagingService = pagingService;

        profileService.getAuthenticatedUserData = function() {
            return $q.when('data')
        };
        this.groupsService.getGroups = function() {
            return $q.when({
                data: {
                    group: 'mock group'
                }
            })
        };

        groupsService.getGroupsAbridged = function () {
            return $q.when({
                data: {
                    groups: ["all my groups"]
                }
            });
        };

        this.controller = $controller('GroupsController', {'$scope': this.scope});

        this.mockSuccessAlert = 'success';
        this.mockFailureAlert = 'failure';
        this.utilitySuccess = spyOn(this.utilityService, 'success')
            .and.stub()
            .and.returnValue(this.mockSuccessAlert);
        this.utilityFailure = spyOn(this.utilityService, 'failure')
            .and.stub()
            .and.returnValue(this.mockFailureAlert);
    }));

    it('test that we properly set group data when running refresh', function(){
        this.scope.groups = {};
        var response = {
            data: {
                groups: "all my groups"
            }
        };
        var getGroups = spyOn(this.groupsService, 'getGroupsAbridged')
            .and.stub()
            .and.returnValue(this.q.when(response));

        this.scope.refresh();
        this.scope.$digest();

        expect(getGroups.calls.count()).toBe(2);
        expect(this.scope.groups.items).toBe("all my groups");
    });

    it('createGroup correctly calls utilityService when passing createGroup', function() {
        this.scope.profile = {
            id: 'profile_ID'
        };
        this.scope.data = {
            name: 'NewGroup'
        };
        var reset = spyOn(this.scope, 'reset')
            .and.stub();
        var refresh = spyOn(this.scope,'refresh')
            .and.stub();
        var groupsService = spyOn(this.groupsService, 'createGroup')
            .and.stub()
            .and.returnValue(this.q.when('success'));

        this.scope.createGroup();
        this.scope.$digest();

        expect(reset.calls.count()).toBe(1);
        expect(refresh.calls.count()).toBe(1);
        expect(groupsService.calls.count()).toBe(1);
        expect(this.utilitySuccess.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([this.mockSuccessAlert]);
    });

   it('createGroup correctly calls utilityService when failing createGroup', function() {
        this.scope.profile = {
            id: 'profile_ID'
        };
        this.scope.data = {
            name: 'NewGroup'
        };
        var createGroup = spyOn(this.groupsService, 'createGroup')
            .and.stub()
            .and.returnValue(this.q.reject('Group'));

        this.scope.createGroup();
        this.scope.$digest();

        expect(createGroup.calls.count()).toBe(1);
        expect(this.utilityFailure.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([this.mockFailureAlert]);
   });

    it('nextPageMyGroups should call getGroupsAbridged with the correct parameters', function () {

        var response = {
            data: {
                groups: "all my groups"
            }
        };
        var getGroupSets = spyOn(this.groupsService, 'getGroupsAbridged')
            .and.stub()
            .and.returnValue(this.q.when(response));

        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedIgnoreAccess = false;

        this.scope.nextPageMyGroups();

        expect(getGroupSets.calls.count()).toBe(1);
        expect(getGroupSets.calls.mostRecent().args).toEqual(
          [expectedMaxItems, expectedStartFrom, expectedIgnoreAccess, expectedQuery]);
    });

    it('prevPageMyGroups should call getGroupsAbridged with the correct parameters', function () {

        var response = {
            data: {
                groups: "all my groups"
            }
        };
        var getGroupSets = spyOn(this.groupsService, 'getGroupsAbridged')
            .and.stub()
            .and.returnValue(this.q.when(response));

        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedIgnoreAccess = false;

        this.scope.prevPageMyGroups();

        expect(getGroupSets.calls.count()).toBe(1);
        expect(getGroupSets.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedIgnoreAccess, expectedQuery]);

        this.scope.nextPageMyGroups();
        this.scope.prevPageMyGroups();

        expect(getGroupSets.calls.count()).toBe(3);
        expect(getGroupSets.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedIgnoreAccess, expectedQuery]);
    });

    it('nextPageAllGroups should call getGroupsAbridged with the correct parameters', function () {

        var response = {
            data: {
                groups: "all groups"
            }
        };
        var getGroupSets = spyOn(this.groupsService, 'getGroupsAbridged')
            .and.stub()
            .and.returnValue(this.q.when(response));

        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedIgnoreAccess = true;

        this.scope.nextPageAllGroups();

        expect(getGroupSets.calls.count()).toBe(1);
        expect(getGroupSets.calls.mostRecent().args).toEqual(
          [expectedMaxItems, expectedStartFrom, expectedIgnoreAccess, expectedQuery]);
    });

    it('prevPageAllGroups should call getGroupsAbridged with the correct parameters', function () {

        var response = {
            data: {
                groups: "all groups"
            }
        };
        var getGroupSets = spyOn(this.groupsService, 'getGroupsAbridged')
            .and.stub()
            .and.returnValue(this.q.when(response));

        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedIgnoreAccess = true;

        this.scope.prevPageAllGroups();

        expect(getGroupSets.calls.count()).toBe(1);
        expect(getGroupSets.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedIgnoreAccess, expectedQuery]);

        this.scope.nextPageAllGroups();
        this.scope.prevPageAllGroups();

        expect(getGroupSets.calls.count()).toBe(3);
        expect(getGroupSets.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedIgnoreAccess, expectedQuery]);
    });
});
