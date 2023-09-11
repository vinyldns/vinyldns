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

describe('Controller: ZonesController', function () {
    beforeEach(function () {
        module('ngMock'),
        module('service.groups'),
        module('service.profile'),
        module('service.records'),
        module('service.zones'),
        module('service.utility'),
        module('service.paging'),
        module('controller.zones')
    });
    beforeEach(inject(function ($rootScope, $controller, $q, groupsService, profileService, recordsService, zonesService, utilityService, pagingService) {
        this.scope = $rootScope.$new();
        this.groupsService = groupsService;
        this.zonesService = zonesService;
        this.zonesService.q = $q;
        this.pagingService = pagingService;

        this.scope.myGroups = {};
        this.scope.allGroups = {};
        this.scope.zones = {};

        profileService.getAuthenticatedUserData = function() {
            return $q.when({data: {id: "userId"}});
        };
        groupsService.getGroups = function () {
            return $q.when({
                data: {
                    groups: [{id: "all my groups", members: [{id: "userId"}]}]
                }
            });
        };

        zonesService.getZones = function() {
            return $q.when({
                data: {
                    zones: ["all my zones"]
                }
            });
        };
        zonesService.getBackendIds = function() {
            return $q.when({
                data: ['backend-1', 'backend-2']
            });
        };

        this.controller = $controller('ZonesController', {'$scope': this.scope});
    }));

    it('test that we properly get users groups when loading ZonesController', function(){
        var validDomains = spyOn(this.scope, 'validDomains')
                            .and.stub();
        this.scope.$digest();
        expect(this.scope.myGroups).toEqual([{id: "all my groups", members: [{id: "userId"}]}]);
    });

    it('nextPageMyZones should call getZones with the correct parameters', function () {
        var getZoneSets = spyOn(this.zonesService, 'getZones')
            .and.stub()
            .and.returnValue(this.zonesService.q.when(mockZone));

        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedSearchByAdminGroup = this.scope.searchByAdminGroup;
        var expectedignoreAccess = false;
        var expectedincludeReverse = true;

        this.scope.nextPageMyZones();

        expect(getZoneSets.calls.count()).toBe(1);
        expect(getZoneSets.calls.mostRecent().args).toEqual(
          [expectedMaxItems, expectedStartFrom, expectedQuery, expectedSearchByAdminGroup, expectedignoreAccess, expectedincludeReverse]);
    });

    it('prevPageMyZones should call getZones with the correct parameters', function () {
        var getZoneSets = spyOn(this.zonesService, 'getZones')
            .and.stub()
            .and.returnValue(this.zonesService.q.when(mockZone));

        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedSearchByAdminGroup = this.scope.searchByAdminGroup;
        var expectedignoreAccess = false;
        var expectedincludeReverse = true;

        this.scope.prevPageMyZones();

        expect(getZoneSets.calls.count()).toBe(1);
        expect(getZoneSets.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedQuery, expectedSearchByAdminGroup, expectedignoreAccess, expectedincludeReverse]);

        this.scope.nextPageMyZones();
        this.scope.prevPageMyZones();

        expect(getZoneSets.calls.count()).toBe(3);
        expect(getZoneSets.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedQuery, expectedSearchByAdminGroup, expectedignoreAccess, expectedincludeReverse]);
    });
});
