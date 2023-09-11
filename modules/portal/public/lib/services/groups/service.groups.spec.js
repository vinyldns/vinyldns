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

describe('Service: groupsService', function () {
    beforeEach(module('ngMock'));
    beforeEach(module('service.groups'));
    beforeEach(module('service.utility'));
    beforeEach(inject(function (groupsService, $httpBackend, utilityService) {
        this.groupsService = groupsService;
        this.$httpBackend = $httpBackend;
        this.serializeParams = function (obj) {
            var result = [];
            for (var property in obj)
                result.push(encodeURIComponent(property) + '=' + encodeURIComponent(obj[property]));
            return result.join('&');
        };
        jasmine.getJSONFixtures().fixturesPath = 'base/mocks';
    }));

    it('should be defined', function () {
        expect(this.groupsService).toBeDefined();
    });

    it('should have getGroups method', function () {
        expect(this.groupsService.getGroup).toBeDefined();
    });

    it('should have listEmailDomains method', function () {
        expect(this.groupsService.listEmailDomains).toBeDefined();
    });

    it('should have createGroups method', function () {
        expect(this.groupsService.createGroup).toBeDefined();
    });

    it('should have deleteGroups method', function () {
        expect(this.groupsService.deleteGroups).toBeDefined();
    });

    it('createGroup method should return 202 with valid group', function (done) {
        var url = '/api/groups';
        this.$httpBackend.when('POST', url).respond(202, getJSONFixture('mockGroupSubmit.json'));
        this.groupsService.createGroup(getJSONFixture('mockGroupSubmit.json'))
            .then(function (response) {
                expect(response.status).toBe(202);
                done();
            }, function (error) {
                fail('createGroup expected 202, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('createGroup method should return 400 invalid group', function (done) {
        var url = '/api/groups';
        this.$httpBackend.when('POST', url, getJSONFixture('mockGroupBadSubmit.json'))
            .respond(function () {
                return [400, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.createGroup(getJSONFixture('mockGroupBadSubmit.json'))
            .then(function (response) {
                fail('createGroup expected 400, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(400);
                done();
            });
        this.$httpBackend.flush();
    });

    it('createGroup method should return 409 existing group', function (done) {
        var url = '/api/groups';
        this.$httpBackend.when('POST', url, getJSONFixture('mockGroupExistingSubmit.json'))
            .respond(function () {
                return [409, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.createGroup(getJSONFixture('mockGroupExistingSubmit.json'))
            .then(function (response) {
                fail('createGroup expected 409, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(409);
                done();
            });
        this.$httpBackend.flush();
    });

    it('deleteGroups method should return 202 with valid group', function (done) {
        var url = '/api/groups/:id';
        this.$httpBackend.whenRoute('DELETE', url).respond(202, getJSONFixture('mockGroupSubmit.json'));
        this.groupsService.deleteGroups('abc123')
            .then(function (response) {
                expect(response.status).toBe(202);
                done();
            }, function (error) {
                fail('deleteGroup expected 202, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('deleteGroups method should return 400 invalid group', function (done) {
        var url = '/api/groups/:id';
        this.$httpBackend.whenRoute('DELETE', url)
            .respond(function () {
                return [400, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.deleteGroups('abc123')
            .then(function (response) {
                fail('deleteGroup expected 400, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(400);
                done();
            });
        this.$httpBackend.flush();
    });

    it('deleteGroups method should return 404 invalid group', function (done) {
        var url = '/api/groups/:id';
        this.$httpBackend.whenRoute('DELETE', url)
            .respond(function () {
                return [404, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.deleteGroups('abc123')
            .then(function (response) {
                fail('deleteGroup expected 404, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(404);
                done();
            });
        this.$httpBackend.flush();
    });

    it('createGroups method should return 409 existing group', function (done) {
        var url = '/api/groups';
        this.$httpBackend.when('POST', url, getJSONFixture('mockGroupExistingSubmit.json'))
            .respond(function () {
                return [409, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.createGroup(getJSONFixture('mockGroupExistingSubmit.json'))
            .then(function (response) {
                fail('createGroup expected 409, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(409);
                done();
            });
        this.$httpBackend.flush();
    });

    it('getGroup method should return json', function (done) {
        var url = '/api/groups/:id';
        this.$httpBackend.whenRoute('GET', url).respond(getJSONFixture('mockGroup.json'));
        this.groupsService.getGroup('abc123').then(function (response) {
            expect(response.data).toEqual(getJSONFixture('mockGroup.json'));
            done();
        }, function (error) {
            fail('getGroup expected 202 with json, but got' + error.status.toString());
            done();
        });
        this.$httpBackend.flush();
    });

    it('listEmailDomains method should return json', function (done) {
            var url = '/api/groups/valid/domains';
            this.$httpBackend.whenRoute('GET', url).respond(getJSONFixture('mockDomains.json'));
            this.groupsService.listEmailDomains().then(function (response) {
                expect(response.data).toEqual(getJSONFixture('mockDomains.json'));
                done();
            }, function (error) {
                fail('listEmailDomains expected 202 with json, but got' + error.status.toString());
                done();
            });
            this.$httpBackend.flush();
        });

    it('getGroupMemberList method should return 200 with valid group', function (done) {
        var uuid = 123;
        var url = '/api/groups/:groupId/members';
        this.$httpBackend.whenRoute('GET', url).respond(200, getJSONFixture('mockGroupGetMemberList.json'));
        this.groupsService.getGroupMemberList(uuid)
            .then(function (response) {
                expect(response.status).toBe(200);
                done();
            }, function (error) {
                fail('getGroupMemberList expected 200, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('getGroupMemberList method should return 400 with invalid group', function (done) {
        var uuid = 123;
        var url = '/api/groups/:groupId/members';
        this.$httpBackend.whenRoute('GET', url).respond(400, getJSONFixture('mockGroupGetMemberList.json'));
        this.groupsService.getGroupMemberList(uuid)
            .then(function (response) {
                fail('getGroupMemberList expected 400, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(400);
                done();
            });
        this.$httpBackend.flush();
    });

    it('getGroupMemberList method should return 403 with invalid group', function (done) {
        var uuid = 123;
        var url = '/api/groups/:groupId/members';
        this.$httpBackend.whenRoute('GET', url).respond(403, getJSONFixture('mockGroupGetMemberList.json'));
        this.groupsService.getGroupMemberList(uuid)
            .then(function (response) {
                fail('getGroupMemberList expected 403, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(403);
                done();
            });
        this.$httpBackend.flush();
    });

    it('getGroupMemberList method should return 503 with invalid group', function (done) {
        var uuid = 123;
        var url = '/api/groups/:groupId/members';
        this.$httpBackend.whenRoute('GET', url).respond(503, getJSONFixture('mockGroupGetMemberList.json'));
        this.groupsService.getGroupMemberList(uuid)
            .then(function (response) {
                fail('getGroupMemberList expected 503, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(503);
                done();
            });
        this.$httpBackend.flush();
    });

    it('addGroupMember method should return 404 user id does not exist', function (done) {
        var groupId = 6;
        var id = 12;
        var url = '/api/groups/:groupId/members/:id';
        this.$httpBackend.whenRoute('PUT', url)
            .respond(function () {
                return [404, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.addGroupMember(groupId, id)
            .then(function (response) {
                fail('addGroupMember expected 404, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(404);
                done();
            });
        this.$httpBackend.flush();
    });

    it('addGroupMember method should return 404 group does not exist', function (done) {
        var groupId = 6;
        var id = 12;
        var url = '/api/groups/:groupId/members/:id';
        this.$httpBackend.whenRoute('PUT', url)
            .respond(function () {
                return [404, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.addGroupMember(groupId, id)
            .then(function (response) {
                fail('addGroupMember expected 404, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(404);
                done();
            });
        this.$httpBackend.flush();
    });

    it('addGroupMember method should return 200 group does exist', function (done) {
        var uuid = 123;
        var groupId = 6;
        var id = 12;
        var count = 2;
        var url = '/api/groups/:groupId/members/:uuid';
        this.$httpBackend.whenRoute('PUT', url).respond(200, getJSONFixture('mockGroupGetMemberList.json'));
        this.groupsService.addGroupMember(groupId, uuid, id, count)
            .then(function (response) {
                expect(response.status).toBe(200);
                done();
            }, function (error) {
                fail('addGroupMember expected 200, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('deleteGroupMember method should return 404 group does not exist', function (done) {
        var groupId = 6;
        var id = 12;
        var url = '/api/groups/:groupId/members/:id';
        this.$httpBackend.whenRoute('DELETE', url)
            .respond(function () {
                return [404, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.deleteGroupMember(groupId, id)
            .then(function (response) {
                fail('deleteGroupMember expected 404, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(404);
                done();
            });
        this.$httpBackend.flush();
    });

    it('deleteGroupMember method should return 400 bad request', function (done) {
        var groupId = 6;
        var id = 12;
        var url = '/api/groups/:groupId/members/:id';
        this.$httpBackend.whenRoute('DELETE', url)
            .respond(function () {
                return [400, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.deleteGroupMember(groupId, id)
            .then(function (response) {
                fail('deleteGroupMember expected 404, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(400);
                done();
            });
        this.$httpBackend.flush();
    });

    it('deleteGroupMember method should return 200 when member is deleted from group', function (done) {
        var groupId = 6;
        var id = 12;
        var url = '/api/groups/:groupId/members/:uuid';
        this.$httpBackend.whenRoute('DELETE', url).respond(200, getJSONFixture('mockGroupGetMemberList.json'));
        this.groupsService.deleteGroupMember(groupId, id)
            .then(function (response) {
                expect(response.status).toBe(200);
                done();
            }, function (error) {
                fail('deleteGroupMember expected 200, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('updateGroup method should return 200 with valid group', function (done) {
        var url = '/api/groups/:id';
        this.$httpBackend.whenRoute('PUT', url).respond(200, getJSONFixture('mockGroupSubmit.json'));
        this.groupsService.updateGroup('abc123')
            .then(function (response) {
                expect(response.status).toBe(200);
                done();
            }, function (error) {
                fail('updateGroup expected 200, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('updateGroup method should return 400 invalid group', function (done) {
        var url = '/api/groups/:id';
        this.$httpBackend.whenRoute('PUT', url)
            .respond(function () {
                return [400, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.updateGroup('abc123')
            .then(function (response) {
                fail('updateGroup expected 400, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(400);
                done();
            });
        this.$httpBackend.flush();
    });

    it('updateGroup method should return 404 not found', function (done) {
        var url = '/api/groups/:id';
        this.$httpBackend.whenRoute('PUT', url)
            .respond(function () {
                return [404, 'response body', {}, 'TestPhrase'];
            });
        this.groupsService.updateGroup('abc123')
            .then(function (response) {
                fail('updateGroup expected 404, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(404);
                done();
            });
        this.$httpBackend.flush();
    });

    it('getGroupListChanges method should return 200 with valid response', function (done) {
        var uuid = 123;
        var id = 12;
        var count = 2;
        var url = '/api/groups/:groupId/changes';
        this.$httpBackend.whenRoute('GET', url).respond(200, getJSONFixture('mockGroupListChanges.json'));
        this.groupsService.getGroupListChanges(uuid, id, count)
            .then(function (response) {
                expect(response.status).toBe(200);
                done();
            }, function (error) {
                fail('getGroupListChanges expected 200, but got ' + error.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('getGroupListChanges method should return 404 group does not exist', function (done) {
        var uuid = 123;
        var id = 12;
        var count = 2;
        var url = '/api/groups/:groupId/changes';
        this.$httpBackend.whenRoute('GET', url).respond(404);
        this.groupsService.getGroupListChanges(uuid, id, count)
            .then(function (response) {
                fail('getGroupListChanges expected 404, but got ' + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(404);
                done();
            });
        this.$httpBackend.flush();
    });

    it('getGroups method should return 200 with valid groups', function (done) {
        var url = '/api/groups';
        this.$httpBackend.whenRoute('GET', url).respond(200, getJSONFixture('mockGroupList.json'));
        this.groupsService.getGroups()
            .then(function (response) {
                expect(response.status).toBe(200);
                done();
            }, function (error) {
                fail('getGroups expected 200, but got ' + response.status.toString());
                done();
            });
        this.$httpBackend.flush();
    });

    it('getGroups method should return 401 with invalid credentials', function (done) {
        var url = '/api/groups';
        this.$httpBackend.whenRoute('GET', url).respond(401, "The resource requires authentication, which was not supplied with the request");
        this.groupsService.getGroups()
            .then(function (response) {
                fail("getGroups expected 401, but got " + response.status.toString());
                done();
            }, function (error) {
                expect(error.status).toBe(401);
                done();
            });
        this.$httpBackend.flush();
    });

    it('getGroupsStored should only call the api on its 1st call', function (done) {
        var url = '/api/groups';
        var groupsResult = getJSONFixture('mockGroupList.json');
        this.$httpBackend.whenRoute('GET', url).respond(200, groupsResult);
        var getGroupsCalls = spyOn(this.groupsService, 'getGroups').and.callThrough();
        var getGroupsStoredCalls = spyOn(this.groupsService, 'getGroupsStored').and.callThrough();

        this.groupsService.getGroupsStored().then(function (response) {
            expect(response).toEqual(groupsResult);
        }, function (error) {
            fail('getGroup expected 202 with json, but got' + error.status.toString());
        });

        this.$httpBackend.flush();

        this.groupsService.getGroupsStored().then(function (response) {
            expect(response).toEqual(groupsResult);
        }, function (error) {
            fail('getGroup expected 202 with json, but got' + error.status.toString());
        });

        expect(getGroupsStoredCalls.calls.count()).toBe(2);
        expect(getGroupsCalls.calls.count()).toBe(1);
        done();

    });

    it('getGroupsStored should call the api twice when there is an error', function (done) {
        var url = '/api/groups';
        this.$httpBackend.whenRoute('GET', url).respond(401, "The resource requires authentication, which was not supplied with the request");
        var getGroupsCalls = spyOn(this.groupsService, 'getGroups').and.callThrough();
        var getGroupsStoredCalls = spyOn(this.groupsService, 'getGroupsStored').and.callThrough();

        this.groupsService.getGroupsStored().then(function (response) {
            fail("getGroups expected 401, but got " + response.status.toString());
        }, function (error) {
            expect(error.status).toBe(401);
        });

        this.$httpBackend.flush();

        this.groupsService.getGroupsStored().then(function (response) {
            fail("getGroups expected 401, but got " + response.status.toString());
        }, function (error) {
            expect(error.status).toBe(401);
        });

        this.$httpBackend.flush();

        expect(getGroupsStoredCalls.calls.count()).toBe(2);
        expect(getGroupsCalls.calls.count()).toBe(2);
        done();

    });
});
