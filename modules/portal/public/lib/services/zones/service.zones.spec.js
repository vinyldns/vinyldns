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

describe('Service: zoneService', function () {
    beforeEach(module('ngMock'));
    beforeEach(module('service.zones'));
    beforeEach(module('service.groups'));
    beforeEach(module('service.utility'));
    beforeEach(inject(function ($httpBackend, $q, zonesService, groupsService, utilityService) {
        this.zonesService = zonesService;
        this.groupsService = groupsService;
        this.q = $q;
        this.$httpBackend = $httpBackend;
    }));

    it('http backend gets called properly when getting zones', function () {
        this.$httpBackend.expectGET('/api/zones?maxItems=100&startFrom=start&nameFilter=someQuery&searchByAdminGroup=false&ignoreAccess=false&includeReverse=true').respond('zone returned');
        this.zonesService.getZones('100', 'start', 'someQuery', false, false, true)
            .then(function(response) {
                expect(response.data).toBe('zone returned');
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when getting zoneChanges', function () {
        this.$httpBackend.expectGET('/api/zones/zoneid/changes?maxItems=100&startFrom=start').respond('zoneChanges returned');
        this.zonesService.getZoneChanges('100', 'start', 'zoneid', false)
            .then(function(response) {
                expect(response.data).toBe('zoneChanges returned');
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when sending zone', function (done) {
        this.$httpBackend.expectPOST('/api/zones').respond('zone sent');
        this.zonesService.sendZone('zone payload')
            .then(function(response) {
                expect(response.data).toBe('zone sent');
                done();
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when deleting zone', function (done) {
        this.$httpBackend.expectDELETE('/api/zones/id').respond('zone deleted');
        this.zonesService.delZone('id')
            .then(function(response) {
                expect(response.data).toBe('zone deleted');
                done();
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when getting backend ids', function () {
            this.$httpBackend.expectGET('/api/zones/backendids').respond('ids returned');
            this.zonesService.getBackendIds()
                .then(function(response) {
                    expect(response.data).toBe('ids returned');
                });
            this.$httpBackend.flush();
        });

    it('sendZone should completely remove connections if they have empty objects', function(done) {

        var zonePayload = {
            connection: {},
            transferConnection: {}
        };

        this.$httpBackend.expectPOST('/api/zones', {}).respond('zone sent');

        this.zonesService.sendZone(zonePayload)
            .then(function(response) {
                expect(response.data).toBe('zone sent');
                done();
            });
        this.$httpBackend.flush();
    });

    it('sendZone should completely remove connections if they have empty data', function(done) {

        var zonePayload = {
            connection: { name: "   "},
            transferConnection: { name: "   " }
        };

        this.$httpBackend.expectPOST('/api/zones', {}).respond('zone sent');

        this.zonesService.sendZone(zonePayload)
            .then(function(response) {
                expect(response.data).toBe('zone sent');
                done();
            });
        this.$httpBackend.flush();
    });

    it('sendZone should add the zone name to the connection name', function(done) {

        var zonePayload = {
            name: "frodo",
            connection: { server: "middleEarth"},
            transferConnection: { server: "narnia" }
        };

        var sanitizedPayload = {
            name: "frodo",
            connection: {
                name: "frodo",
                server: "middleEarth"
            },
            transferConnection: {
                name: "frodo",
                server: "narnia"
            }
        };

        this.$httpBackend.expectPOST('/api/zones', sanitizedPayload).respond('zone sent');

        this.zonesService.sendZone(zonePayload)
            .then(function(response) {
                expect(response.data).toBe('zone sent');
                done();
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when updating a zone', function (done) {
        this.$httpBackend.expectPUT('/api/zones/id').respond('update sent');
        var sanitizeConnections = spyOn(this.zonesService, 'sanitizeConnections');
        this.zonesService.updateZone('id', 'zone')
            .then(function(response) {
                expect(response.data).toBe('update sent');
                done();
            });
        expect(sanitizeConnections.calls.count()).toBe(1);
        this.$httpBackend.flush();
    });

    it('sanitizeConnections does not clear connection attribute when not empty', function() {
        var payload = {
            name: 'mockZone.',
            connection: {
                name: 'mockZone.',
                keyName: 'key-name',
                key: 'key-value',
                server: 'server'
            }
        };
        var sanitizedPayload = this.zonesService.sanitizeConnections(payload);
        expect(sanitizedPayload).toEqual(payload)
    });

    it('sanitizeConnections does adds name when not there', function() {
        var payload = {
            name: 'mockZone.',
            connection: {
                name: '',
                keyName: 'key-name',
                key: 'key-value',
                server: 'server'
            }
        };
        var expectedPayload = {
            name: 'mockZone.',
            connection: {
                name: 'mockZone.',
                keyName: 'key-name',
                key: 'key-value',
                server: 'server'
            }
        };
        var sanitizedPayload = this.zonesService.sanitizeConnections(payload);
        expect(sanitizedPayload).toEqual(expectedPayload)
    });

    it('sanitizeConnections does clear connection attribute when empty', function() {
        var payload = {
            name: 'mockZone.',
            connection: {
                name: '',
                keyName: '',
                key: '',
                server: ''
            }
        };
        var expectedPayload = {
            name: 'mockZone.'
        };
        var sanitizedPayload = this.zonesService.sanitizeConnections(payload);
        expect(sanitizedPayload).toEqual(expectedPayload)
    });

    it('sanitizeConnections does clear connection attribute when only name is left', function() {
        var payload = {
            name: 'mockZone.',
            connection: {
                name: 'mockZone.',
                keyName: '',
                key: '',
                server: ''
            }
        };
        var expectedPayload = {
            name: 'mockZone.'
        };
        var sanitizedPayload = this.zonesService.sanitizeConnections(payload);
        expect(sanitizedPayload).toEqual(expectedPayload)
    });

    it('toApiIso converts to correct format', function() {
        var dateString = new Date().toDateString();
        var isoDate = this.zonesService.toApiIso(dateString);

        /* when we parse the DateTimes from the api it gets turned into javascript readable format
         * instead of just staying the way it is, so for now converting it back to ISO format on the fly,
         * ISO 8601 standards are YYYY-MM-DDTHH:MM:SS:SSSZ, but the DateTime ISO the api uses is
         * YYYY-MM-DDTHH:MM:SSZ, so the SSS has to be dropped */
        expect(isoDate).toMatch("\\d{4}-\\d{2}-\\d{2}\\D\\d{2}:\\d{2}:\\d{2}Z");
    });

    it('normalizeZoneDate changes DateTimes to api ISO format', function() {
       var mockZone = {
           created: new Date().toDateString()
       };
       expect(mockZone.created).not.toMatch("\\d{4}-\\d{2}-\\d{2}\\D\\d{2}:\\d{2}:\\d{2}Z");
       var normalizedMockZone = this.zonesService.normalizeZoneDates(mockZone);
       expect(normalizedMockZone.created).toMatch("\\d{4}-\\d{2}-\\d{2}\\D\\d{2}:\\d{2}:\\d{2}Z");
    });

    it('setConnectionKeys properly sets connection key to be hidden Key', function() {
        var mockZone = {
            connection: {
                name: 'vinyldns',
                keyName: 'name',
                key: 'key',
                primaryServer: 'server'
            },
            hiddenKey: 'new key'
        };
        var expectedZone = {
            connection: {
                name: 'vinyldns',
                keyName: 'name',
                key: 'new key',
                primaryServer: 'server'
            },
            hiddenKey: 'new key'
        };
        var newZone = this.zonesService.setConnectionKeys(mockZone);
        expect(newZone).toEqual(expectedZone);
    });

    it('setConnectionKeys properly clears connection', function() {
        var mockZone = {
            connection: {
                name: 'vinyldns',
                keyName: '',
                key: 'key',
                primaryServer: ''
            },
            hiddenKey: ''
        };
        var expectedZone = {
            connection: {
                name: 'vinyldns',
                keyName: '',
                key: '',
                primaryServer: ''
            },
            hiddenKey: ''
        };
        var newZone = this.zonesService.setConnectionKeys(mockZone);
        expect(newZone).toEqual(expectedZone);
    });

    it('toVinylAclRule returns rule in correct format with accessLevel No Access', function() {
       var mockAclRule = {
           accessLevel: 'No Access',
           description: 'description',
           recordMask: 'mask',
           recordTypes: ['A', 'AAAA'],
           displayName: 'All Users'
       };
       var expectedAclRule = {
           accessLevel: 'NoAccess',
           description: 'description',
           recordMask: 'mask',
           recordTypes: ['A', 'AAAA'],
           displayName: 'All Users'
       };
       var rule = this.zonesService.toVinylAclRule(mockAclRule);
       expect(rule).toEqual(expectedAclRule);
    });

    it('toVinylAclRule returns rule in correct format with priority User', function() {
        var mockAclRule = {
            accessLevel: 'Read',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'ntid',
            priority: 'User',
            userName: 'ntid',
            userId: 'user id'
        };
        var expectedAclRule = {
            accessLevel: 'Read',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'ntid',
            userId: 'user id'
        };
        var rule = this.zonesService.toVinylAclRule(mockAclRule);
        expect(rule).toEqual(expectedAclRule);
    });

    it('toVinylAclRule returns rule in correct format with priority Group', function() {
        var mockAclRule = {
            accessLevel: 'Read',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'group name',
            priority: 'Group',
            groupName: 'group name',
            groupId: 'group id'
        };
        var expectedAclRule = {
            accessLevel: 'Read',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'group name',
            groupId: 'group id'
        };
        var rule = this.zonesService.toVinylAclRule(mockAclRule);
        expect(rule).toEqual(expectedAclRule);
    });

    it('toDisplayAclRule return rule in correct format when it has groupId', function() {
        var mockAclRule = {
            accessLevel: 'Read',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'group name',
            groupId: 'group id'
        };
        var expectedAclRule = {
            accessLevel: 'Read',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'group name',
            priority: 'Group',
            groupId: 'group id'
        };
        var rule = this.zonesService.toDisplayAclRule(mockAclRule);
        expect(rule).toEqual(expectedAclRule);
    });

    it('toDisplayAclRule return rule in correct format when it has userId', function() {
        var mockAclRule = {
            accessLevel: 'Read',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'ntid',
            userId: 'user id'
        };
        var expectedAclRule = {
            accessLevel: 'Read',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'ntid',
            priority: 'User',
            userId: 'user id',
            userName: 'ntid'
        };
        var rule = this.zonesService.toDisplayAclRule(mockAclRule);
        expect(rule).toEqual(expectedAclRule);
    });

    it('toDisplayAclRule return rule in correct format when it has NoAccess', function() {
        var mockAclRule = {
            accessLevel: 'NoAccess',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'All Users'
        };
        var expectedAclRule = {
            accessLevel: 'No Access',
            description: 'description',
            recordMask: 'mask',
            recordTypes: ['A', 'AAAA'],
            displayName: 'All Users',
            priority: 'All Users'
        };
        var rule = this.zonesService.toDisplayAclRule(mockAclRule);
        expect(rule).toEqual(expectedAclRule);
    });
});
