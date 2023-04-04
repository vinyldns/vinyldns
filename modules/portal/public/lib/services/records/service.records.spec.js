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

describe('Service: recordsService', function () {
    beforeEach(module('ngMock'));
    beforeEach(module('service.utility'));
    beforeEach(module('service.records'));
    beforeEach(inject(function ($httpBackend, recordsService, utilityService) {
        this.recordsService = recordsService;
        this.$httpBackend = $httpBackend;
    }));

    it('http backend gets called properly when getting record sets', function () {
        this.$httpBackend.expectGET('/api/zones/id/recordsets?maxItems=100&startFrom=start&recordNameFilter=someQuery&recordTypeFilter=A&nameSort=asc').respond('success');
        this.recordsService.listRecordSetsByZone('id', '100', 'start', 'someQuery', 'A', 'asc')
            .then(function(response) {
                expect(response.data).toBe('success');
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when creating record sets', function () {
        this.$httpBackend.expectPOST('/api/zones/id/recordsets').respond('success');
        this.recordsService.createRecordSet('id', 'record')
            .then(function(response) {
                expect(response.data).toBe('success');
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when updating record sets', function () {
        this.$httpBackend.expectPUT('/api/zones/id/recordsets/recordid').respond('success');
        this.recordsService.updateRecordSet('id', 'recordid', 'record')
            .then(function(response) {
                expect(response.data).toBe('success');
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when deleting record sets', function () {
        this.$httpBackend.expectDELETE('/api/zones/zoneid/recordsets/recordid').respond('success');
        this.recordsService.delRecordSet('zoneid', 'recordid')
            .then(function(response) {
                expect(response.data).toBe('success');
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when deleting record sets', function () {
        this.$httpBackend.expectDELETE('/api/zones/zoneid/recordsets/recordid').respond('success');
        this.recordsService.delRecordSet('zoneid', 'recordid')
            .then(function(response) {
                expect(response.data).toBe('success');
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when getting zone', function () {
        this.$httpBackend.expectGET('/api/zones/zoneid').respond('success');
        this.recordsService.getZone('zoneid')
            .then(function(response) {
                expect(response.data).toBe('success');
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when syncing zone', function () {
        this.$httpBackend.expectPOST('/api/zones/zoneid/sync').respond('success');
        this.recordsService.syncZone('zoneid')
            .then(function(response) {
                expect(response.data).toBe('success');
            });
        this.$httpBackend.flush();
    });

    it('http backend gets called properly when listing record set changes', function () {
        this.$httpBackend.expectGET('/api/zones/zoneid/recordsetchanges?maxItems=100').respond('success');
        this.recordsService.listRecordSetChanges('zoneid', '100')
            .then(function(response) {
                expect(response.data).toBe('success');
            });
        this.$httpBackend.flush();
    });

    it('have toVinylRecord return a valid sshfp record', function() {
        sentRecord = {
            "id": 'recordId',
            "name": 'recordName',
            "type": 'SSHFP',
            "ttl": '300',
            "sshfpItems": [{algorithm: '1', type: '1', fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: '2', type: '1', fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None'
        };
        expectedRecord = {
            "id": 'recordId',
            "name": 'recordName',
            "type": 'SSHFP',
            "ttl": 300,
            "records": [{algorithm: 1, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: 2, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None'
        };

        var actualRecord = this.recordsService.toVinylRecord(sentRecord);
        expect(actualRecord).toEqual(expectedRecord)
    });

    it('have toDisplayRecord return a valid display record', function() {
        vinyldnsRecord = {
            "id": 'recordId',
            "name": 'recordName',
            "type": 'SSHFP',
            "ttl": 300,
            "records": [{algorithm: 1, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: 2, type: 1, fingerprint: 'F23456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None'
        };

        displayRecord = {
            "id": 'recordId',
            "name": 'recordName',
            "type": 'SSHFP',
            "ttl": 300,
            "records": undefined,
            "sshfpItems": [{algorithm: 1, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: 2, type: 1, fingerprint: 'F23456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None',
            "onlyFour": true,
            "isDotted": false,
            "canBeEdited": true
        };

        var actualRecord = this.recordsService.toDisplayRecord(vinyldnsRecord);
        expect(actualRecord).toEqual(displayRecord)
    });

    it('have toDisplayRecord return a valid display record for dotted host', function() {
        vinyldnsRecord = {
            "id": 'recordId',
            "name": 'recordName.with.dot',
            "type": 'SSHFP',
            "ttl": 300,
            "records": [{algorithm: 1, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: 2, type: 1, fingerprint: 'F23456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None'
        };

        displayRecord = {
            "id": 'recordId',
            "name": 'recordName.with.dot',
            "type": 'SSHFP',
            "ttl": 300,
            "records": undefined,
            "sshfpItems": [{algorithm: 1, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: 2, type: 1, fingerprint: 'F23456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None',
            "onlyFour": true,
            "isDotted": true,
            "canBeEdited": true
        };

        var actualRecord = this.recordsService.toDisplayRecord(vinyldnsRecord);
        expect(actualRecord).toEqual(displayRecord)
    });

    it('have toDisplayRecord return a valid display record for apex', function() {
        vinyldnsRecord = {
            "id": 'recordId',
            "name": 'apex.with.dot',
            "type": 'SSHFP',
            "ttl": 300,
            "records": [{algorithm: 1, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: 2, type: 1, fingerprint: 'F23456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None'

        };

        displayRecord = {
            "id": 'recordId',
            "name": 'apex.with.dot',
            "type": 'SSHFP',
            "ttl": 300,
            "records": undefined,
            "sshfpItems": [{algorithm: 1, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: 2, type: 1, fingerprint: 'F23456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None',
            "onlyFour": true,
            "isDotted": false,
            "canBeEdited": true
        };

        var actualRecord = this.recordsService.toDisplayRecord(vinyldnsRecord, "apex.with.dot.");
        expect(actualRecord).toEqual(displayRecord)
    });

    it('have toDisplayRecord return a valid display record for apex NS (both with trailing dot)', function() {
        vinyldnsRecord = {
            "id": 'recordId',
            "name": 'apex.with.dot.',
            "type": 'NS',
            "ttl": 300,
            "records": [{nsdname: "ns1.com."}, {nsdname: "ns2.com."}],
            "recordSetGroupChange": 'None'
        };

        displayRecord = {
            "id": 'recordId',
            "name": 'apex.with.dot.',
            "type": 'NS',
            "ttl": 300,
            "records": undefined,
            "nsRecordData": ["ns1.com.", "ns2.com."],
            "recordSetGroupChange": 'None',
            "onlyFour": true,
            "isDotted": false,
            "canBeEdited": false
    };

        var actualRecord = this.recordsService.toDisplayRecord(vinyldnsRecord, "apex.with.dot.");
        expect(actualRecord).toEqual(displayRecord)
    });

    it('have toDisplayRecord return a valid display record for apex (neither with trailing dot)', function() {
        vinyldnsRecord = {
            "id": 'recordId',
            "name": 'apex.with.dot',
            "type": 'SSHFP',
            "ttl": 300,
            "records": [{algorithm: 1, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: 2, type: 1, fingerprint: 'F23456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None'
        };

        displayRecord = {
            "id": 'recordId',
            "name": 'apex.with.dot',
            "type": 'SSHFP',
            "ttl": 300,
            "records": undefined,
            "sshfpItems": [{algorithm: 1, type: 1, fingerprint: '123456789ABCDEF67890123456789ABCDEF67890'},
                {algorithm: 2, type: 1, fingerprint: 'F23456789ABCDEF67890123456789ABCDEF67890'}],
            "recordSetGroupChange": 'None',
            "onlyFour": true,
            "isDotted": false,
            "canBeEdited": true
        };

        var actualRecord = this.recordsService.toDisplayRecord(vinyldnsRecord, "apex.with.dot");
        expect(actualRecord).toEqual(displayRecord)
    });
});
