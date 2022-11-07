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

describe('Service: profileService', function () {
    beforeEach(function () {
        module('ngMock'),
        module('service.utility'),
        module('service.profile')
    });

    beforeEach(inject(function ($rootScope, $httpBackend, profileService, utilityService) {
        this.$rootScope = $rootScope;
        this.scope = this.$rootScope.$new();
        this.$httpBackend = $httpBackend;
        this.profileService = profileService;
        this.utilityService = utilityService;

        jasmine.getJSONFixtures().fixturesPath='base/mocks';
    }));
    
    it('should be defined', function () {
        expect(this.profileService).toBeDefined();
    });

    it('should have getAuthenticatedUserData method', function () {
        expect(this.profileService.getAuthenticatedUserData()).toBeDefined();
    });

    it('should have getUserDataByUsername method', function () {
        expect(this.profileService.getUserDataByUsername).toBeDefined();
    });

    it('should have getUserDataById method', function () {
        expect(this.profileService.getUserDataByUsername).toBeDefined();
    });

    it('should have regenerateCredentials method', function () {
        expect(this.profileService.regenerateCredentials()).toBeDefined();
    });

    it('getAuthenticatedUserData method should return 200 with valid user', function (done) {
        var url = '/api/users/currentuser';
        this.$httpBackend.whenRoute('GET', url).respond(200, getJSONFixture('mockUserData.json'));
        this.profileService.getAuthenticatedUserData()
            .then(function (response) {
                expect(response.status).toBe(200);
                done();
            }, function (error) {
                fail('getUserData expected 200, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });
    it('getAuthenticatedUserData method should return 400 with invalid user', function (done) {
        var url = '/api/users/currentuser';
        this.$httpBackend.whenRoute('GET', url).respond(400);
        this.profileService.getAuthenticatedUserData()
            .then(function (response) {
                fail('getUserData expected 400, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(400);
                done();
            });
        this.$httpBackend.flush();
    });

    it('getUserDataByUsername method should return 200 with valid user', function (done) {
        this.$httpBackend.expectGET('/api/users/lookupuser/username').respond('success');
        this.profileService.getUserDataByUsername('username')
            .then(function (response) {
                expect(response.status).toBe(200);
                expect(response.data).toBe('success');
                done();
            }, function (error) {
                fail('lookupUserAccount expected 200, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('getUserDataByUsername method should return 400 with invalid user', function (done) {
        var url = '/api/users/lookupuser/:uname';
        this.$httpBackend.whenRoute('GET', url)
            .respond(function () {
                return [400, 'response body', {}, 'TestPhrase'];
            });
        this.profileService.getUserDataByUsername('badUsername')
            .then(function (response) {
                fail('lookupUserAccount expected 400, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(400);
                done();
            });
        this.$httpBackend.flush();
    });

    it('regenerateCredentials method should return 200 with valid user', function (done) {
        var url = '/regenerate-creds';
        this.$httpBackend.whenRoute('POST', url).respond(200, getJSONFixture('mockUserData.json'));
        this.profileService.regenerateCredentials()
            .then(function (response) {
                expect(response.status).toBe(200);
                done();
            }, function (error) {
                fail('regenerateCredentials expected 200, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });


    it('getUserDataByUserId method should return 200 with valid user', function (done) {
        this.$httpBackend.expectGET('/api/users/userId').respond('success');
        this.profileService.getUserDataById('userId')
            .then(function (response) {
                expect(response.status).toBe(200);
                expect(response.data).toBe('success');
                done();
            }, function (error) {
                fail('lookupUserAccount expected 200, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('getUserDataByUserId method should return 400 with invalid user', function (done) {
        var url = '/api/users/:userId';
        this.$httpBackend.whenRoute('GET', url)
            .respond(function () {
                return [400, 'response body', {}, 'TestPhrase'];
            });
        this.profileService.getUserDataById('badUserId')
            .then(function (response) {
                fail('lookupUserAccount expected 400, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(400);
                done();
            });
        this.$httpBackend.flush();
    });


    it('regenerateCredentials method should return 400 with invalid user', function (done) {
        var url = '/regenerate-creds';
        this.$httpBackend.whenRoute('POST', url).respond(400);
        this.profileService.regenerateCredentials()
            .then(function (response) {
                fail('regenerateCredentials expected 400, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(400);
                done();
            });
        this.$httpBackend.flush();
    });
});
