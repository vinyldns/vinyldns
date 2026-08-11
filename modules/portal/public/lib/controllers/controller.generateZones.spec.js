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

describe('Controller: GenerateZonesController', function () {
    beforeEach(function () {
        module('ngMock'),
        module('service.groups'),
        module('service.profile'),
        module('service.records'),
        module('service.zones'),
        module('service.utility'),
        module('service.paging'),
        module('controller.generateZones')
    });
    beforeEach(inject(function ($rootScope, $controller, $q, groupsService, profileService, zonesService, utilityService, pagingService) {
        this.scope = $rootScope.$new();
        this.zonesService = zonesService;
        this.zonesService.q = $q;
        this.pagingService = pagingService;

        this.scope.myGroups = {};
        this.scope.alerts = [];

        $.fn.select2 = function () { return this; };

        zonesService.getNameservers = function () {
            return $q.when({ data: [] });
        };
        zonesService.getAllowedDNSProviders = function () {
            return $q.when({ data: { allowedDNSProviders: [] } });
        };
        zonesService.getGeneratedZones = function () {
            return $q.when({
                data: {
                    zones: ["all generated zones"]
                }
            });
        };

        this.controller = $controller('GenerateZonesController', { '$scope': this.scope });
    }));

    it('nextPageGeneratedZones should call getGeneratedZones with the correct parameters', function () {
        var getZoneSets = spyOn(this.zonesService, 'getGeneratedZones')
            .and.stub()
            .and.returnValue(this.zonesService.q.when({ data: { zones: [] } }));

        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedSearchByAdminGroup = this.scope.searchByAdminGroup;
        var expectedIgnoreAccess = false;

        this.scope.nextPageGeneratedZones();

        expect(getZoneSets.calls.count()).toBe(1);
        expect(getZoneSets.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedQuery, expectedSearchByAdminGroup, expectedIgnoreAccess]);
    });

    it('prevPageGeneratedZones should call getGeneratedZones with the correct parameters', function () {
        var getZoneSets = spyOn(this.zonesService, 'getGeneratedZones')
            .and.stub()
            .and.returnValue(this.zonesService.q.when({ data: { zones: [] } }));

        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;
        var expectedQuery = this.scope.query;
        var expectedSearchByAdminGroup = this.scope.searchByAdminGroup;
        var expectedIgnoreAccess = false;

        this.scope.prevPageGeneratedZones();

        expect(getZoneSets.calls.count()).toBe(1);
        expect(getZoneSets.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedQuery, expectedSearchByAdminGroup, expectedIgnoreAccess]);

        this.scope.nextPageGeneratedZones();
        this.scope.prevPageGeneratedZones();

        expect(getZoneSets.calls.count()).toBe(3);
        expect(getZoneSets.calls.mostRecent().args).toEqual(
            [expectedMaxItems, expectedStartFrom, expectedQuery, expectedSearchByAdminGroup, expectedIgnoreAccess]);
    });
});
