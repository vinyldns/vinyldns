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

    it('resetGeneratedZoneModalState should clear previous modal selections and mode', function () {
        this.scope.createZone = { provider: 'powerdns', providerParams: { kind: 'Native', masters: ['172.17.42.1'] } };
        this.scope.zoneFields = [{ field: 'masters' }];
        this.scope.createZoneTemplate = { previous: true };
        this.scope.updateZoneTemplate = { previous: true };
        this.scope.requiredFields = ['kind'];
        this.scope.UpdateRequiredFields = ['kind'];
        this.scope.isEditMode = true;
        this.scope.isDelete = true;
        this.scope.confirmingDelete = true;
        this.scope.processing = true;

        this.scope.resetGeneratedZoneModalState();

        expect(this.scope.createZone.providerParams.nameservers).toEqual([]);
        expect(this.scope.zoneFields).toEqual([]);
        expect(this.scope.createZoneTemplate).toBeUndefined();
        expect(this.scope.updateZoneTemplate).toBeUndefined();
        expect(this.scope.requiredFields).toEqual([]);
        expect(this.scope.UpdateRequiredFields).toEqual([]);
        expect(this.scope.isEditMode).toBe(false);
        expect(this.scope.isDelete).toBe(false);
        expect(this.scope.confirmingDelete).toBe(false);
        expect(this.scope.processing).toBe(false);
    });

    it('getUpdatePayload should preserve existing nameservers when editing provider fields', function () {
        this.scope.UpdateRequiredFields = ['nameservers', 'admin_email'];
        this.scope.updateZoneTemplate = {
            "Admin_Email": { type: 'String', value: 'admin@example.com' }
        };
        var zone = {
            groupId: 'group-id',
            zoneName: 'example.com',
            email: 'owner@example.com',
            provider: 'bind',
            providerParams: {
                nameservers: ['ns1.example.com', 'ns2.example.com'],
                admin_email: 'admin@example.com'
            }
        };

        var payload = this.scope.getUpdatePayload(zone);

        expect(payload.providerParams).toEqual({
            nameservers: ['ns1.example.com', 'ns2.example.com'],
            admin_email: 'admin@example.com'
        });
    });

    it('viewCreatedZone should show configured numeric and unconfigured provider params', function () {
        this.zonesService.getCreateZoneTemplate = function () {
            return this.zonesService.q.when({
                data: {
                    'request-templates': {
                        'create-zone': JSON.stringify({
                            Admin_Email: { type: 'String', value: 'admin@example.com' },
                            TTL: { type: 'Integer', value: '3600' }
                        })
                    },
                    'required-fields': { 'create-zone': [] }
                }
            });
        }.bind(this);

        this.scope.viewCreatedZone({
            provider: 'bind',
            providerParams: {
                admin_email: 'admin@example.com',
                ttl: 3600,
                custom_setting: 'custom value'
            }
        });
        this.scope.$digest();

        expect(this.scope.zoneFields.map(function (field) { return field.field; })).toEqual([
            'admin_email', 'ttl', 'custom_setting'
        ]);
        expect(this.scope.createZone.providerParams).toEqual({
            admin_email: 'admin@example.com',
            ttl: 3600,
            custom_setting: 'custom value'
        });
        expect(this.scope.hasProviderParamValue(3600)).toBe(true);
        expect(this.scope.hasProviderParamValue('')).toBe(false);
    });

    it('viewCreatedZone should normalize serialized nameservers for the details view', function () {
        this.zonesService.getCreateZoneTemplate = function () {
            return this.zonesService.q.when({
                data: {
                    'request-templates': { 'create-zone': JSON.stringify({}) },
                    'required-fields': { 'create-zone': [] }
                }
            });
        }.bind(this);

        this.scope.viewCreatedZone({
            provider: 'powerdns',
            providerParams: { nameservers: '["ns1.example.com", "ns2.example.com"]' }
        });
        this.scope.$digest();

        expect(this.scope.createZone.providerParams.nameservers).toEqual([
            'ns1.example.com', 'ns2.example.com'
        ]);
        expect(this.scope.displayNameservers).toBe('ns1.example.com, ns2.example.com');
    });

    it('isDynamicZoneFieldRequired should honor provider update required fields', function () {
        this.scope.isEditMode = true;
        this.scope.UpdateRequiredFields = ['nameservers', 'admin_email'];
        this.scope.createZone = { providerParams: { nameservers: [] } };

        expect(this.scope.isDynamicZoneFieldRequired('nameservers')).toBe(true);
        expect(this.scope.isDynamicZoneFieldRequired('admin_email')).toBe(true);
        expect(this.scope.isDynamicZoneFieldRequired('ttl')).toBe(false);
        expect(this.scope.hasRequiredProviderParams()).toBe(false);
        this.scope.createZone.providerParams = {
            nameservers: ['ns1.example.com'],
            admin_email: 'admin@example.com'
        };
        expect(this.scope.hasRequiredProviderParams()).toBe(true);
    });

    it('hides and clears Masters when Kind is Native', function () {
        this.scope.createZone = { providerParams: { kind: 'Native', nameservers: ['ns1.example.com'], masters: ['172.17.42.1'] } };
        this.scope.UpdateRequiredFields = ['kind'];
        this.scope.updateZoneTemplate = {
            Kind: { type: 'Select', value: 'Native, Master' },
            'Native Name Servers': { type: 'Display', field: 'nameservers', value: 'Native name servers', 'visible-when': { field: 'kind', value: 'Native' } },
            Masters: { type: 'Multi-Select', value: '172.17.42.1, 172.17.42.2' }
        };
        this.scope.zoneFields = [{
            field: 'nameservers',
            type: 'Display',
            visibleWhen: { field: 'kind', value: 'Native' }
        }, {
            field: 'masters',
            type: 'Multi-Select',
            visibleWhen: { field: 'kind', value: 'Master' }
        }];

        expect(this.scope.isProviderFieldVisible(this.scope.zoneFields[1])).toBe(false);
        this.scope.updateProviderFieldVisibility();
        expect(this.scope.createZone.providerParams.masters).toEqual([]);
        expect(this.scope.isProviderFieldVisible(this.scope.zoneFields[0])).toBe(true);
        expect(this.scope.displayProviderParamValue(this.scope.createZone.providerParams.nameservers)).toBe('ns1.example.com');
        expect(this.scope.getUpdatePayload(this.scope.createZone).providerParams).toEqual({ kind: 'Native' });

        this.scope.createZone.providerParams.kind = 'Master';
        expect(this.scope.isProviderFieldVisible(this.scope.zoneFields[1])).toBe(true);
        this.scope.updateProviderFieldVisibility();
        expect(this.scope.createZone.providerParams.nameservers).toEqual(['ns1.example.com']);
    });

    it('uses the loaded nameserver list for a config-sourced provider field', function () {
        this.scope.nameservers = ['ns1.example.com', 'ns2.example.com'];
        this.zonesService.getCreateZoneTemplate = function () {
            return this.zonesService.q.when({
                data: {
                    'request-templates': {
                        'create-zone': JSON.stringify({
                            Kind: { type: 'Select', value: 'Native, Master' },
                            'Native Name Servers': {
                                type: 'Multi-Select',
                                field: 'nameservers',
                                source: 'nameservers',
                                description: 'Select Native name servers.',
                                'visible-when': { field: 'kind', value: 'Native' },
                                'preserve-when-hidden': true
                            },
                            Masters: {
                                type: 'Multi-Select',
                                value: '172.17.42.1, 172.17.42.2',
                                'visible-when': { field: 'kind', value: 'Master' }
                            }
                        })
                    },
                    'required-fields': { 'create-zone': ['kind', 'nameservers'] }
                }
            });
        }.bind(this);

        this.scope.getCreateZoneTemplate('powerdns');
        this.scope.$digest();
        var field = this.scope.zoneFields.filter(function (zoneField) {
            return zoneField.label === 'Native Name Servers';
        })[0];

        expect(field.field).toBe('nameservers');
        expect(this.scope.isProviderFieldVisible(field)).toBe(false);
        this.scope.createZone.providerParams.kind = 'Native';
        expect(this.scope.isProviderFieldVisible(field)).toBe(true);
        expect(this.scope.hasVisibleNameserversSelector()).toBe(true);
        expect(this.scope.getProviderFieldValues(field)).toEqual(this.scope.nameservers);
        expect(this.scope.requiredFields).toEqual(['kind', 'nameservers']);
    });
});
