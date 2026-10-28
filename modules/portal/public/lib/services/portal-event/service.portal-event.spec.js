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

describe('Service: portalEventService', function() {
    beforeEach(function() {
        module('ngMock');
        module('service.utility');
        module('service.portalEvent');
    });

    beforeEach(inject(function($rootScope, $q, $httpBackend, portalEventService) {
        this.rootScope = $rootScope;
        this.q = $q;
        this.httpBackend = $httpBackend;
        this.portalEventService = portalEventService;
    }));

    afterEach(function() {
        this.httpBackend.verifyNoOutstandingExpectation();
        this.httpBackend.verifyNoOutstandingRequest();
    });

    it('omits unused event fields and sends the event asynchronously', function() {
        this.httpBackend.expectPOST('/api/portal-events', function(body) {
            var event = JSON.parse(body);
            return event.feature === 'zone_records' &&
                event.status === 'success' &&
                event.entityId === undefined &&
                event.metadata === undefined &&
                event.correlationId === undefined;
        }).respond(204);

        this.portalEventService.track({
            feature: 'zone_records',
            action: 'csv_export',
            status: 'success'
        });
        this.httpBackend.flush();
    });

    it('sends a normalized export event to the export endpoint', function() {
        this.httpBackend.expectPOST('/api/export-events', {
            type: 'search',
            zoneId: null,
            recordTypes: 'A,AAAA',
            query: 'host.example',
            rows: 23
        }).respond(204);

        this.portalEventService.trackExport({
            type: 'search',
            recordTypes: 'A,AAAA',
            query: 'host.example',
            rows: 23
        });
        this.httpBackend.flush();
    });

    it('does not reject when export event delivery fails', function() {
        var result;
        this.httpBackend.expectPOST('/api/export-events').respond(500);

        this.portalEventService.trackExport({type: 'zone', zoneId: 'zone-1', rows: 1})
            .then(function(value) { result = value; });
        this.httpBackend.flush();
        this.rootScope.$digest();

        expect(result).toBe(null);
    });

    it('preserves the original operation result', function() {
        var result;
        this.httpBackend.expectPOST('/api/portal-events').respond(204);

        this.portalEventService.trackOperation(
            {feature: 'record_update', action: 'update'},
            function() { return this.q.when('saved'); }.bind(this)
        ).then(function(value) { result = value; });
        this.rootScope.$digest();
        this.httpBackend.flush();
        this.rootScope.$digest();

        expect(result).toBe('saved');
    });

    it('preserves the original operation error when telemetry delivery fails', function() {
        var originalError = {
            code: 'UPDATE_FAILED',
            data: 'RecordSet with name already-exists and type A already exists in zone ok.'
        };
        var receivedError;
        this.httpBackend.expectPOST('/api/portal-events', function(body) {
            var event = JSON.parse(body);
            return event.errorCode === 'UPDATE_FAILED' &&
                event.errorMessage === 'RecordSet with name already-exists and type A already exists in zone ok.';
        }).respond(500);

        this.portalEventService.trackOperation(
            {feature: 'record_update', action: 'update'},
            function() { return this.q.reject(originalError); }.bind(this)
        ).catch(function(error) { receivedError = error; });
        this.rootScope.$digest();
        this.httpBackend.flush();
        this.rootScope.$digest();

        expect(receivedError).toBe(originalError);
    });

    it('uses the generic code and a bounded message for an API string error', function() {
        var apiError = {status: 400, data: new Array(601).join('A')};
        this.httpBackend.expectPOST('/api/portal-events', function(body) {
            var event = JSON.parse(body);
            return event.errorCode === 'OPERATION_FAILED' && event.errorMessage.length === 512;
        }).respond(204);

        this.portalEventService.trackOperation(
            {feature: 'record_create', action: 'create'},
            function() { return this.q.reject(apiError); }.bind(this)
        ).catch(function() {});
        this.rootScope.$digest();
        this.httpBackend.flush();
        this.rootScope.$digest();
    });
});
