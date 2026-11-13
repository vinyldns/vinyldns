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
    beforeEach(inject(function ($rootScope, $controller, $q, $timeout, groupsService, profileService, zonesService, utilityService, pagingService) {
        this.scope = $rootScope.$new();
        this.controllerFactory = $controller;
        this.timeout = $timeout;
        this.zonesService = zonesService;
        this.zonesService.q = $q;
        this.pagingService = pagingService;

        this.scope.myGroups = {};
        this.scope.alerts = [];

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

    it('refreshes Generated Zones after a successful delete', function () {
        this.timeout.flush();
        var deleteGeneratedZone = spyOn(this.zonesService, 'deleteGeneratedZone')
            .and.returnValue(this.zonesService.q.when({ status: 202, statusText: 'Accepted' }));
        var refreshGeneratedZones = spyOn(this.scope, 'refreshGeneratedZones');
        this.scope.createZone = { id: 'zone-id', zoneName: 'example.com.' };

        this.scope.confirmDeleteCreatedZone();
        this.scope.$digest();

        expect(deleteGeneratedZone).toHaveBeenCalledWith('zone-id');
        expect(refreshGeneratedZones).not.toHaveBeenCalled();
        this.timeout.flush(1000);
        expect(refreshGeneratedZones).toHaveBeenCalledTimes(1);
    });

    it('renders Generated Zones autocomplete labels as text with regex-special search terms', function () {
        document.body.innerHTML = '<input class="generateZone-search-text" /><input class="generateZone-isGroupSearch" type="checkbox" />';
        this.controllerFactory('GenerateZonesController', { '$scope': this.scope });

        var input = $('.generateZone-search-text');
        var instance = input.autocomplete('instance');
        instance.term = 'paren(';

        var rendered = instance._renderItem($('<ul></ul>'), {
            label: '<img src=x onerror=alert(1)> paren(',
            value: '<img src=x onerror=alert(1)> paren('
        });
        expect(rendered.find('img').length).toBe(0);
        expect(rendered.find('div').text()).toBe('<img src=x onerror=alert(1)> paren(');
        expect(rendered.find('b').text()).toBe('paren(');

        $('.generateZone-isGroupSearch').prop('checked', true).trigger('change');
        instance = input.autocomplete('instance');
        instance.term = 'paren(';
        rendered = instance._renderItem($('<ul></ul>'), {
            label: '<img src=x onerror=alert(1)> paren(',
            value: '<img src=x onerror=alert(1)> paren('
        });
        expect(rendered.find('img').length).toBe(0);
        expect(rendered.find('div').text()).toBe('<img src=x onerror=alert(1)> paren(');
        expect(rendered.find('b').text()).toBe('paren(');

        document.body.innerHTML = '';
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
            custom_setting: 'custom value',
            nameservers: []
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

    it('does not allow BIND zone creation without nameservers', function () {
        this.scope.requiredFields = ['nameservers', 'admin_email'];
        this.scope.createZone = { providerParams: { nameservers: [], admin_email: 'admin@example.com' } };

        expect(this.scope.hasRequiredProviderParams()).toBe(false);

        this.scope.createZone.providerParams.nameservers = ['ns1.example.com'];
        expect(this.scope.hasRequiredProviderParams()).toBe(true);
    });

    it('uses the selected Kind to show update fields and serialize their payload locations', function () {
        this.scope.createZone = {
            provider: 'powerdns',
            providerParams: {
                kind: 'Native',
                nameservers: ['ns1.example.com'],
                masters: ['172.17.42.1', '172.17.42.2'],
                servers: ['172.17.42.3', '172.17.42.4'],
                recursion_desired: false
            }
        };
        this.scope.isEditMode = true;
        this.scope.UpdateRequiredFields = ['kind'];
        this.scope.updateZoneTemplate = {
            Kind: { type: 'Select', value: 'Native, Master, Slave, Forwarded' },
            'Name Servers': {
                type: 'Display',
                field: 'nameservers',
                value: 'Name servers used by this zone',
                'visible-when': { field: 'kind', values: ['Native', 'Master'] },
                'required-when': { field: 'kind', values: ['Native', 'Master'] }
            },
            'Native Masters': {
                type: 'Multi-Select',
                field: 'masters',
                value: '172.17.42.1, 172.17.42.2',
                'visible-when': { field: 'kind', value: 'Native' }
            },
            'Slave Masters': {
                type: 'Multi-Select',
                field: 'masters',
                value: '172.17.42.1, 172.17.42.2',
                'visible-when': { field: 'kind', value: 'Slave' },
                'payload-location-when': { field: 'kind', value: 'Slave' }
            },
            Servers: {
                type: 'IP-Array',
                field: 'servers',
                'visible-when': { field: 'kind', value: 'Forwarded' },
                'payload-location': 'zone'
            },
            'Recursion Desired': {
                type: 'Boolean',
                field: 'recursion_desired',
                'visible-when': { field: 'kind', value: 'Forwarded' }
            }
        };
        this.scope.zoneFields = Object.keys(this.scope.updateZoneTemplate).map(function (label) {
            var config = this.scope.updateZoneTemplate[label];
            return {
                label: label,
                field: config.field || label.replace(/ /g, '').toLowerCase(),
                type: config.type,
                visibleWhen: config['visible-when'],
                requiredWhen: config['required-when']
            };
        }, this);

        var nameserversField = this.scope.zoneFields.filter(function (field) { return field.field === 'nameservers'; })[0];
        var nativeMastersField = this.scope.zoneFields.filter(function (field) { return field.label === 'Native Masters'; })[0];
        var slaveMastersField = this.scope.zoneFields.filter(function (field) { return field.label === 'Slave Masters'; })[0];
        var serversField = this.scope.zoneFields.filter(function (field) { return field.field === 'servers'; })[0];

        expect(this.scope.isProviderFieldVisible(nameserversField)).toBe(true);
        expect(this.scope.isProviderFieldVisible(nativeMastersField)).toBe(true);
        expect(this.scope.isProviderFieldVisible(slaveMastersField)).toBe(false);
        expect(this.scope.getUpdatePayload(this.scope.createZone).providerParams).toEqual({
            kind: 'Native',
            nameservers: ['ns1.example.com'],
            masters: ['172.17.42.1', '172.17.42.2']
        });

        this.scope.createZone.providerParams.kind = 'Master';
        expect(this.scope.isProviderFieldVisible(nameserversField)).toBe(true);
        expect(this.scope.isProviderFieldVisible(nativeMastersField)).toBe(false);
        expect(this.scope.isProviderFieldVisible(slaveMastersField)).toBe(false);
        expect(this.scope.getUpdatePayload(this.scope.createZone).providerParams).toEqual({
            kind: 'Master',
            nameservers: ['ns1.example.com']
        });

        this.scope.createZone.providerParams.kind = 'Slave';
        var slavePayload = this.scope.getUpdatePayload(this.scope.createZone);
        expect(this.scope.isProviderFieldVisible(nameserversField)).toBe(false);
        expect(this.scope.isProviderFieldVisible(slaveMastersField)).toBe(true);
        expect(slavePayload.providerParams).toEqual({ kind: 'Slave' });
        expect(slavePayload.masters).toEqual(['172.17.42.1', '172.17.42.2']);

        this.scope.createZone.providerParams.kind = 'Forwarded';
        var forwardedPayload = this.scope.getUpdatePayload(this.scope.createZone);
        expect(this.scope.isProviderFieldVisible(nameserversField)).toBe(false);
        expect(this.scope.isProviderFieldVisible(serversField)).toBe(true);
        expect(forwardedPayload.providerParams).toEqual({ kind: 'Forwarded', recursion_desired: false });
        expect(forwardedPayload.servers).toEqual(['172.17.42.3', '172.17.42.4']);
    });

    it('hydrates root-level Masters and Servers when loading the update template', function () {
        this.zonesService.getCreateZoneTemplate = function () {
            return this.zonesService.q.when({
                data: {
                    'request-templates': {
                        'update-zone': JSON.stringify({
                            Kind: { type: 'Select', value: 'Native, Slave, Forwarded' },
                            'Slave Masters': {
                                type: 'IP-Array',
                                field: 'masters',
                                value: '172.17.42.1, 172.17.42.2',
                                'visible-when': { field: 'kind', value: 'Slave' },
                                'payload-location-when': { field: 'kind', value: 'Slave' }
                            },
                            Servers: {
                                type: 'IP-Array',
                                field: 'servers',
                                'visible-when': { field: 'kind', value: 'Forwarded' },
                                'payload-location': 'zone'
                            }
                        })
                    },
                    'required-fields': { 'update-zone': ['kind'] }
                }
            });
        }.bind(this);
        this.scope.createZone = {
            provider: 'powerdns',
            masters: ['172.17.42.1', '172.17.42.2'],
            servers: ['172.17.42.3', '172.17.42.4'],
            providerParams: { kind: 'Slave' }
        };

        this.scope.getUpdateZoneTemplate('powerdns');
        this.scope.$digest();

        expect(this.scope.createZone.providerParams.masters).toEqual(['172.17.42.1', '172.17.42.2']);
        expect(this.scope.createZone.providerParams.servers).toEqual(['172.17.42.3', '172.17.42.4']);
        expect(this.scope.zoneFields[1].type).toBe('Multi-Select');
        expect(this.scope.zoneFields[2].type).toBe('IP-Array');
    });

    it('uses the loaded nameserver list for a config-sourced provider field', function () {
        this.scope.nameservers = ['ns1.example.com', 'ns2.example.com'];
        this.zonesService.getCreateZoneTemplate = function () {
            return this.zonesService.q.when({
                data: {
                    'request-templates': {
                        'create-zone': JSON.stringify({
                            Kind: { type: 'Select', value: 'Native, Master, Slave, Forwarded' },
                            'Name Servers': {
                                type: 'Multi-Select',
                                field: 'nameservers',
                                source: 'nameservers',
                                description: 'Select zone name servers.'
                            },
                            Masters: {
                                type: 'Multi-Select',
                                field: 'masters',
                                value: '172.17.42.1, 172.17.42.2',
                                'visible-when': { field: 'kind', values: ['Master', 'Slave'] },
                                'preserve-when-hidden': true
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
            return zoneField.label === 'Name Servers';
        })[0];

        expect(field.field).toBe('nameservers');
        expect(this.scope.isProviderFieldVisible(field)).toBe(true);
        expect(this.scope.hasVisibleNameserversSelector()).toBe(true);
        expect(this.scope.getProviderFieldValues(field)).toEqual(this.scope.nameservers);
        expect(this.scope.requiredFields).toEqual(['kind', 'nameservers']);
    });

    it('uses configured IP choices as dropdowns and keeps unconfigured IP fields editable', function () {
        this.zonesService.getCreateZoneTemplate = function () {
            return this.zonesService.q.when({
                data: {
                    'request-templates': {
                        'create-zone': JSON.stringify({
                            Kind: { type: 'Select', value: 'Native, Slave, Forwarded' },
                            'Native Masters': {
                                type: 'IP-Array',
                                field: 'masters',
                                value: '10.0.0.1, 10.0.0.2',
                                'visible-when': { field: 'kind', value: 'Native' },
                                'required-when': { field: 'kind', value: 'Native' }
                            },
                            'Slave Masters': {
                                type: 'IP-Array',
                                field: 'masters',
                                value: '10.0.1.1, 10.0.1.2',
                                'visible-when': { field: 'kind', value: 'Slave' },
                                'required-when': { field: 'kind', value: 'Slave' },
                                'payload-location-when': { field: 'kind', value: 'Slave' }
                            },
                            Servers: {
                                type: 'IP-Array',
                                field: 'servers',
                                value: '10.0.2.1, 10.0.2.2',
                                'visible-when': { field: 'kind', value: 'Forwarded' },
                                'payload-location': 'zone'
                            },
                            'Manual IPs': { type: 'IP-Array', field: 'manual_ips' }
                        })
                    },
                    'required-fields': { 'create-zone': ['kind'] }
                }
            });
        }.bind(this);

        this.scope.createZone = { provider: 'powerdns', providerParams: {} };
        this.scope.getCreateZoneTemplate('powerdns', true);
        this.scope.$digest();

        var nativeMasters = this.scope.zoneFields.filter(function (field) { return field.label === 'Native Masters'; })[0];
        var slaveMasters = this.scope.zoneFields.filter(function (field) { return field.label === 'Slave Masters'; })[0];
        var forwardedServers = this.scope.zoneFields.filter(function (field) { return field.label === 'Servers'; })[0];
        var manualIps = this.scope.zoneFields.filter(function (field) { return field.label === 'Manual IPs'; })[0];

        expect(nativeMasters.type).toBe('Multi-Select');
        expect(nativeMasters.value).toEqual(['10.0.0.1', '10.0.0.2']);
        expect(slaveMasters.type).toBe('Multi-Select');
        expect(slaveMasters.value).toEqual(['10.0.1.1', '10.0.1.2']);
        expect(forwardedServers.type).toBe('Multi-Select');
        expect(forwardedServers.value).toEqual(['10.0.2.1', '10.0.2.2']);
        expect(manualIps.type).toBe('IP-Array');
        expect(this.scope.createZone.providerParams.manual_ips).toEqual(['']);

        this.scope.createZone.providerParams.kind = 'Slave';
        expect(this.scope.isDynamicZoneFieldRequired('masters')).toBe(true);
        expect(this.scope.getProviderFieldValues(slaveMasters)).toEqual(slaveMasters.value);
    });

    it('shows Forwarded recursion control and preserves its false value', function () {
        this.zonesService.getCreateZoneTemplate = function () {
            return this.zonesService.q.when({
                data: {
                    'request-templates': {
                        'create-zone': JSON.stringify({
                            Kind: { type: 'Select', value: 'Native, Master, Slave, Forwarded' },
                            'Recursion Desired': {
                                type: 'Boolean',
                                field: 'recursion_desired',
                                value: false,
                                'visible-when': { field: 'kind', value: 'Forwarded' }
                            }
                        })
                    },
                    'required-fields': { 'create-zone': ['kind'] }
                }
            });
        }.bind(this);

        this.scope.createZone = { provider: 'powerdns', providerParams: {} };
        this.scope.getCreateZoneTemplate('powerdns', true);
        this.scope.$digest();

        var recursionField = this.scope.zoneFields.filter(function (field) {
            return field.field === 'recursion_desired';
        })[0];
        expect(this.scope.createZone.providerParams.recursion_desired).toBe(false);
        expect(this.scope.isProviderFieldVisible(recursionField)).toBe(false);

        this.scope.createZone.providerParams.kind = 'Forwarded';
        expect(this.scope.isProviderFieldVisible(recursionField)).toBe(true);
        this.scope.createZone.providerParams.recursion_desired = true;
        this.scope.createZone.providerParams.kind = 'Native';
        this.scope.updateProviderFieldVisibility();
        expect(this.scope.createZone.providerParams.recursion_desired).toBe(false);
        this.scope.createZone.providerParams.kind = 'Forwarded';
        this.scope.updateProviderFieldVisibility();
        this.scope.updateZoneTemplate = {
            Kind: { type: 'Select', value: 'Native, Master, Slave, Forwarded' },
            'Recursion Desired': {
                type: 'Boolean',
                field: 'recursion_desired',
                value: false,
                'visible-when': { field: 'kind', value: 'Forwarded' }
            }
        };
        this.scope.UpdateRequiredFields = ['kind'];
        expect(this.scope.getUpdatePayload(this.scope.createZone).providerParams).toEqual({
            kind: 'Forwarded',
            recursion_desired: false
        });
    });

    it('builds PowerDNS create payload fields according to Kind', function () {
        this.scope.createZoneTemplate = {
            Kind: { type: 'Select', field: 'kind' },
            'Name Servers': {
                type: 'Multi-Select',
                field: 'nameservers',
                'visible-when': { field: 'kind', values: ['Native', 'Master'] }
            },
            Masters: {
                type: 'IP-Array',
                field: 'masters',
                'visible-when': { field: 'kind', values: ['Native', 'Slave'] },
                'payload-location-when': { field: 'kind', value: 'Slave' }
            },
            Servers: {
                type: 'IP-Array',
                field: 'servers',
                'visible-when': { field: 'kind', value: 'Forwarded' },
                'payload-location': 'zone'
            },
            'Recursion Desired': {
                type: 'Boolean',
                field: 'recursion_desired',
                'visible-when': { field: 'kind', value: 'Forwarded' }
            }
        };

        var zone = {
            groupId: 'group-id',
            provider: 'powerdns',
            zoneName: 'example.com.',
            email: 'test@example.com',
            providerParams: {
                kind: 'Native',
                nameservers: ['172.17.42.1.'],
                masters: ['172.17.42.1', '172.17.42.2', '']
            }
        };
        expect(this.scope.getCreatePayload(zone)).toEqual({
            groupId: 'group-id',
            provider: 'powerdns',
            zoneName: 'example.com.',
            email: 'test@example.com',
            providerParams: {
                kind: 'Native',
                nameservers: ['172.17.42.1.'],
                masters: ['172.17.42.1', '172.17.42.2']
            }
        });

        zone.providerParams.kind = 'Master';
        expect(this.scope.getCreatePayload(zone).providerParams).toEqual({
            kind: 'Master',
            nameservers: ['172.17.42.1.']
        });

        zone.providerParams.kind = 'Slave';
        expect(this.scope.getCreatePayload(zone)).toEqual({
            groupId: 'group-id',
            provider: 'powerdns',
            zoneName: 'example.com.',
            email: 'test@example.com',
            providerParams: { kind: 'Slave' },
            masters: ['172.17.42.1', '172.17.42.2']
        });

        zone.providerParams.kind = 'Forwarded';
        zone.providerParams.servers = ['172.17.42.3', '172.17.42.4', ''];
        zone.providerParams.recursion_desired = false;
        expect(this.scope.getCreatePayload(zone)).toEqual({
            groupId: 'group-id',
            provider: 'powerdns',
            zoneName: 'example.com.',
            email: 'test@example.com',
            providerParams: { kind: 'Forwarded', recursion_desired: false },
            servers: ['172.17.42.3', '172.17.42.4']
        });
    });

    it('omits untouched optional BIND fields from the create payload', function () {
        this.scope.createZoneTemplate = {
            Admin_Email: { type: 'String', field: 'admin_email' },
            TTL: { type: 'Integer', field: 'ttl' },
            Refresh: { type: 'Integer', field: 'refresh' },
            Retry: { type: 'Integer', field: 'retry' },
            Expire: { type: 'Integer', field: 'expire' },
            Negative_Cache_TTL: { type: 'Integer', field: 'negative_cache_ttl' }
        };

        var zone = {
            groupId: 'group-id',
            provider: 'bind',
            zoneName: 'example.com.',
            email: 'test@example.com',
            providerParams: {
                admin_email: 'admin@example.com',
                ttl: '',
                refresh: '',
                retry: '',
                expire: '',
                negative_cache_ttl: ''
            }
        };

        expect(this.scope.getCreatePayload(zone)).toEqual({
            groupId: 'group-id',
            provider: 'bind',
            zoneName: 'example.com.',
            email: 'test@example.com',
            providerParams: { admin_email: 'admin@example.com' }
        });
    });

    it('adds and removes entries in an editable provider IP array', function () {
        this.scope.createZone = { providerParams: { masters: ['172.17.42.1'] } };

        this.scope.addProviderArrayItem('masters');
        expect(this.scope.createZone.providerParams.masters).toEqual(['172.17.42.1', '']);
        this.scope.removeProviderArrayItem('masters', 0);
        expect(this.scope.createZone.providerParams.masters).toEqual(['']);
        expect(this.scope.hasProviderParamValue(this.scope.createZone.providerParams.masters)).toBe(false);
    });
});
