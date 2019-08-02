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

describe('Service: utilityService', function () {
    beforeEach(module('ngMock'));
    beforeEach(module('service.utility'));
    beforeEach(inject(function (utilityService) {
        this.utilityService = utilityService;
    }));

    it('return danger alert when given error', function() {
        var error = {
            status: 'status code',
            statusText: 'status text',
            data: "error message"
        };

        var alert = this.utilityService.failure(error, "error");
        var expectedAlert = {
            type: 'danger',
            content: 'HTTP status code (status text): error message'
        };
        expect(alert).toEqual(expectedAlert);
    });

    it('return danger alert when given error with multi-error data', function() {
        var error = {
            status: 404,
            statusText: 'fail',
            data: {
                errors: [
                    'error message one',
                    'error message two'
                ]
            }
        };

        var alert = this.utilityService.failure(error, "error");
        var expectedAlert = {
            type: 'danger',
            content: 'HTTP 404 (fail): error message one\nerror message two'
        };
        expect(alert).toEqual(expectedAlert);
    });

    it('returns alert object for a promise success', function() {
        var success = {
            status: 'status code',
            statusText: 'status text'
        };
        var successMessage = 'success message';
        var type = "mockCall::mock-call-success";
        var alert = this.utilityService.success(successMessage, success, type);
        var expectedAlert = {
            type: 'success',
            content: 'HTTP status code (status text): success message'
        };
        expect(alert).toEqual(expectedAlert);
    });

    it('returns csrf header if meta tag is present in html', function() {
        var meta = document.createElement("meta");
        meta.id = "csrf";
        meta.content = "test-csrf";
        document.getElementsByTagName('head')[0].appendChild(meta);

        var header = this.utilityService.getCsrfHeader();
        var expectedHeader = {"Csrf-Token": "test-csrf"};
        expect(header).toEqual(expectedHeader);

        document.getElementById("csrf").remove();
    });

    it('returns empty object if meta tag is not present in html', function() {
        var meta = document.getElementById("csrf");
        expect(meta).toEqual(null);

        var header = this.utilityService.getCsrfHeader();
        var expectedHeader = {};
        expect(header).toEqual(expectedHeader);
    });

    it('returns formatted timeStamp', function() {
        /* Only verifying month, year and time zone format since running this test in different time zones
        will yield different days and times. */
        var timeZone = new Date().toLocaleString('en-us', {timeZoneName:'short'}).split(' ')[3];
        var timeStamp = new Date("2019-07-26T01:36:01Z");
        var value = this.utilityService.formatDateTime(timeStamp);
        var splitValue = value.split(' ');

        expect(splitValue[0]).toEqual('Jul');
        expect(splitValue[2]).toEqual('2019,');
        expect(splitValue[5]).toEqual(timeZone);
    })
});
