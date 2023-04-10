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

describe('Controller: MembershipController', function () {
    beforeEach(function () {
        module('ngMock'),
        module('service.groups'),
        module('service.profile'),
        module('service.utility'),
        module('service.paging'),
        module('controller.membership')
    });
    beforeEach(inject(function ($rootScope, $controller, $q, groupsService, profileService, utilityService, pagingService) {
        this.rootScope = $rootScope;
        this.scope = $rootScope.$new();
        this.groupsService = groupsService;
        this.profileService = profileService;
        this.utilityService = utilityService;
        this.pagingService = pagingService;
        this.q = $q;
        var mockGroup = {
            data: {
                id: 'id',
                admins: [{id: "adminId"}],
                members: [{id: "adminId"}, {id: "nonAdmin"}]
            }
        };
          var mockDomains = {
                    data: "test.com"
          };

        var mockGroupList = {
            data: {
                maxItems: 100,
                members: [
                    {
                        id: "adminId",
                        userName: "user1",
                        firstName: "user",
                        isAdmin: true,
                        lastName: "name",
                        userName: "someUser1",
                        lockStatus: "Unlocked"
                    },
                    {
                        id: "nonAdmin",
                        userName: "user2",
                        firstName: "user",
                        isAdmin: true,
                        lastName: "name",
                        userName: "someUser2",
                        lockStatus: "Locked"
                    }]
            }
        };

        this.groupsService.getGroup = function() {
          return $q.when(mockGroup);
        };
          this.groupsService.listEmailDomains = function() {
                  return $q.when(mockDomains);
          };
        this.groupsService.getGroupMemberList = function() {
            return $q.when(mockGroupList);
        };

        this.groupsService.getGroupChanges = function () {
            return $q.when({
                data: {
                    changes: [
                        {
                            newGroup: {
                                id: "f9329f39-595d-45c9-8cdf-ac36e96e085d",
                                name: "test-group",
                                email: "test@test.com",
                                created: "2022-07-20T10:14:49Z",
                                status: "Active",
                                members: [
                                    {
                                        id: "ea7ec24e-3cc2-4740-b1b8-acde0158271f"
                                    },
                                    {
                                        id: "5bda099e-be26-4aac-a310-ecf221ee2451"
                                    }
                                ],
                                admins: [
                                    {
                                        id: "ea7ec24e-3cc2-4740-b1b8-acde0158271f"
                                    },
                                    {
                                        id: "5bda099e-be26-4aac-a310-ecf221ee2451"
                                    }
                                ]
                            },
                            changeType: "Delete",
                            userId: "ea7ec24e-3cc2-4740-b1b8-acde0158271f",
                            id: "13516a79-1c61-4b9d-b442-0a773fc9c99f",
                            created: "2022-07-20T10:24:28Z",
                            userName: "professor"
                        }
                    ],
                    maxItems: 100
                }
            });
        };

        this.controller = $controller('MembershipController', {'$scope': this.scope});

        this.mockSuccessAlert = "success";
        this.mockFailureAlert = "failure";
        this.utilitySuccess = spyOn(this.utilityService, 'success')
            .and.stub()
            .and.returnValue(this.mockSuccessAlert);
        this.utilityFailure = spyOn(this.utilityService, 'failure')
            .and.stub()
            .and.returnValue(this.mockFailureAlert);
        this.profileService.getAuthenticatedUserData = function() {
            return $q.when({
                data: {
                    id: 'someId'
                }
            })
        };
    }));

    it('addMember correctly calls utilityService when passing getUserDataByUsername', function(){
        var mockUserAccount = {
            data: {
                id: 'id'
            }
        };
        this.scope.membership.group.members = [];
        this.scope.newMemberData = {
            login: 'login'
        };
        var profileService = spyOn(this.profileService, 'getUserDataByUsername')
            .and.stub()
            .and.returnValue(this.q.when(mockUserAccount));
        var updateGroup = spyOn(this.groupsService, 'updateGroup')
            .and.stub()
            .and.returnValue(this.q.when("success"));

        this.scope.addMember();
        this.scope.$digest();

        expect(profileService.calls.count()).toBe(1);
        expect(updateGroup.calls.count()).toBe(1);
        expect(this.utilitySuccess.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([this.mockSuccessAlert]);
    });

    it('addMember correctly calls utilityService when failing getUserDataByUsername', function(){
        var mockUserAccount = 'user';
        this.scope.newMemberData = {
            login: 'login'
        };
        var profileService = spyOn(this.profileService, 'getUserDataByUsername')
            .and.stub()
            .and.returnValue(this.q.reject(mockUserAccount));

        this.scope.addMember();
        this.scope.$digest();

        expect(profileService.calls.count()).toBe(1);
        expect(this.utilityFailure.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([this.mockFailureAlert]);
    });

    it('addMember correctly calls utilityService when failing updateGroup', function(){
        this.scope.newMemberData = {
            login: 'login'
        };
        this.scope.membership.group.members = [];
        var mockUserAccount = {
            data: {
                id: 'id'
            }
        };
        var profileService = spyOn(this.profileService, 'getUserDataByUsername')
            .and.stub()
            .and.returnValue(this.q.when(mockUserAccount));
        var updateGroup = spyOn(this.groupsService, 'updateGroup')
            .and.stub()
            .and.returnValue(this.q.reject(mockUserAccount));

        this.scope.addMember();
        this.scope.$digest();

        expect(profileService.calls.count()).toBe(1);
        expect(this.utilityFailure.calls.count()).toBe(1);
        expect(updateGroup.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([this.mockFailureAlert]);
    });

    it('removeMember correctly calls utilityService when passing updateGroup', function(){
        this.scope.membership.group = {
            id: 'id',
            admins: [],
            members: []
        };
        this.scope.membership.group.members = [];
        var mockUserAccount = {
            data: {
                id: 'id'
            }
        };
        var updateGroup = spyOn(this.groupsService, 'updateGroup')
            .and.stub()
            .and.returnValue(this.q.when(mockUserAccount));

        this.scope.removeMember('memberId');
        this.scope.$digest();

        expect(this.utilitySuccess.calls.count()).toBe(1);
        expect(updateGroup.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([this.mockSuccessAlert]);
    });

    it('removeMember correctly calls utilityService when failing updateGroup', function(){
        this.scope.membership.group = {
            id: 'id',
            admins: [],
            members: []
        };
        this.scope.membership.group.members = [];
        var mockUserAccount = {
            data: {
                id: 'id'
            }
        };
        var updateGroup = spyOn(this.groupsService, 'updateGroup')
            .and.stub()
            .and.returnValue(this.q.reject(mockUserAccount));

        this.scope.removeMember('memberId');
        this.scope.$digest();

        expect(this.utilityFailure.calls.count()).toBe(1);
        expect(updateGroup.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([this.mockFailureAlert]);
    });

    it('removeMember correctly calls utilityService when passing updateGroup', function(){
        var mockMember = {
            id: 'id',
            isAdmin: false,
            userName: 'username'
        };
        this.scope.membership.group = {
            id: 'id',
            admins: [],
            members: []
        };
        var mockUserAccount = {
            data: {
                id: 'id'
            }
        };
        var updateGroup = spyOn(this.groupsService, 'updateGroup')
            .and.stub()
            .and.returnValue(this.q.when(mockUserAccount));

        this.scope.toggleAdmin(mockMember);
        this.scope.$digest();

        expect(this.utilitySuccess.calls.count()).toBe(1);
        expect(updateGroup.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([this.mockSuccessAlert]);
    });

    it('removeMember correctly calls utilityService when failing updateGroup', function(){
        var mockMember = {
            id: 'id',
            isAdmin: false,
            userName: 'username'
        };
        this.scope.membership.group = {
            id: 'id',
            admins: [],
            members: []
        };
        var mockUserAccount = {
            data: {
                id: 'id'
            }
        };
        var updateGroup = spyOn(this.groupsService, 'updateGroup')
            .and.stub()
            .and.returnValue(this.q.reject(mockUserAccount));

        this.scope.toggleAdmin(mockMember);
        this.scope.$digest();

        expect(this.utilityFailure.calls.count()).toBe(1);
        expect(updateGroup.calls.count()).toBe(1);
        expect(this.scope.alerts).toEqual([this.mockFailureAlert]);
    });

    it('refresh correctly handles error when failing getGroup', function() {
        var getGroup = spyOn(this.scope, 'getGroup')
            .and.stub()
            .and.returnValue(this.q.reject('success'));

        this.scope.refresh();
        this.scope.$digest();

        expect(getGroup.calls.count()).toBe(1);
        expect(this.utilityFailure.calls.count()).toBe(1);
    });

    it('refresh correctly handles error when failing getGroupMemberList', function() {
        var getGroupMemberList = spyOn(this.scope, 'getGroupMemberList')
            .and.stub()
            .and.returnValue(this.q.reject('failure'));

        this.scope.refresh();
        this.scope.$digest();

        expect(getGroupMemberList.calls.count()).toBe(1);
        expect(this.utilityFailure.calls.count()).toBe(1);
    });

    it('getGroupInfo sets the group info, admin status when the user is an admin', function() {

         spyOn(this.profileService, 'getAuthenticatedUserData')
            .and.stub()
            .and.returnValue(this.q.when({
                data: {
                    id: 'adminId'
                }
            }));

        this.scope.getGroupInfo("adminId");
        this.scope.$digest();

        var expectedGroup = { id: 'id',
            admins: [{id: "adminId"}],
            members: [{id: "adminId"}, {id: "nonAdmin"}] };
        var expectedMembership = [
            { id: "adminId",
                userName: "user1",
                firstName: "user",
                isAdmin: true,
                lastName: "name",
                userName: "someUser1",
                lockStatus: "Unlocked"
            },
            { id: "nonAdmin",
                userName: "user2",
                firstName: "user",
                isAdmin: true,
                lastName: "name",
                userName: "someUser2",
                lockStatus: "Locked"
            }];

        expect(this.scope.membership.group).toEqual(expectedGroup);
        expect(this.scope.membership.members).toEqual(expectedMembership);
        expect(this.scope.isGroupAdmin).toBe(true);
    });

    it('getGroupInfo sets the group info, admin status when the user is not an admin', function() {

        spyOn(this.profileService, 'getAuthenticatedUserData')
            .and.stub()
            .and.returnValue(this.q.when({
            data: {
                id: 'nonAdmin'
            }
        }));

        this.scope.getGroupInfo("adminId");
        this.scope.$digest();

        var expectedGroup = { id: 'id',
            admins: [{id: "adminId"}],
            members: [{id: "adminId"}, {id: "nonAdmin"}] };
        var expectedMembership = [
            { id: "adminId",
                userName: "user1",
                firstName: "user",
                isAdmin: true,
                lastName: "name",
                userName: "someUser1",
                lockStatus: "Unlocked"
            },
            { id: "nonAdmin",
                userName: "user2",
                firstName: "user",
                isAdmin: true,
                lastName: "name",
                userName: "someUser2",
                lockStatus: "Locked"
            }];

        expect(this.scope.membership.group).toEqual(expectedGroup);
        expect(this.scope.membership.members).toEqual(expectedMembership);
        expect(this.scope.isGroupAdmin).toBe(false);
    });

    it('getGroupInfo sets the group info, admin status when the user is a super user', function() {

        spyOn(this.profileService, 'getAuthenticatedUserData')
            .and.stub()
            .and.returnValue(this.q.when({
            data: {
                id: 'someOtherUser',
                isSuper: true
            }
        }));

        this.scope.getGroupInfo("adminId");
        this.scope.$digest();

        var expectedGroup = { id: 'id',
            admins: [{id: "adminId"}],
            members: [{id: "adminId"}, {id: "nonAdmin"}] };
        var expectedMembership = [
            { id: "adminId",
                userName: "user1",
                firstName: "user",
                isAdmin: true,
                lastName: "name",
                userName: "someUser1",
                lockStatus: "Unlocked"
            },
            { id: "nonAdmin",
                userName: "user2",
                firstName: "user",
                isAdmin: true,
                lastName: "name",
                userName: "someUser2",
                lockStatus: "Locked"
            }];

        expect(this.scope.membership.group).toEqual(expectedGroup);
        expect(this.scope.membership.members).toEqual(expectedMembership);
        expect(this.scope.isGroupAdmin).toBe(true);
    });

    it('test that we properly get group change data', function(){
        this.scope.groupChanges = {};
        var response = {
            data: {
                changes: [
                    {
                        newGroup: {
                            id: "f9329f39-595d-45c9-8cdf-ac36e96e085d",
                            name: "test-group",
                            email: "test@test.com",
                            created: "2022-07-20T10:14:49Z",
                            status: "Active",
                            members: [
                                {
                                    id: "ea7ec24e-3cc2-4740-b1b8-acde0158271f"
                                },
                                {
                                    id: "5bda099e-be26-4aac-a310-ecf221ee2451"
                                }
                            ],
                            admins: [
                                {
                                    id: "ea7ec24e-3cc2-4740-b1b8-acde0158271f"
                                },
                                {
                                    id: "5bda099e-be26-4aac-a310-ecf221ee2451"
                                }
                            ]
                        },
                        changeType: "Delete",
                        userId: "ea7ec24e-3cc2-4740-b1b8-acde0158271f",
                        id: "13516a79-1c61-4b9d-b442-0a773fc9c99f",
                        created: "2022-07-20T10:24:28Z",
                        userName: "professor"
                    }
                ],
                maxItems: 100
            }
        };
        var getGroupChangesSets = spyOn(this.groupsService, 'getGroupChanges')
            .and.stub()
            .and.returnValue(this.q.when(response));

        this.scope.refresh();
        this.scope.$digest();

        expect(getGroupChangesSets.calls.count()).toBe(1);
        expect(this.scope.groupChanges).toEqual(response.data.changes);
    });

    it('nextPage should call getGroupChanges with the correct parameters', function () {

        var response = {
            data: {
                changes: [
                    {
                        newGroup: {
                            id: "f9329f39-595d-45c9-8cdf-ac36e96e085d",
                            name: "test-group",
                            email: "test@test.com",
                            created: "2022-07-20T10:14:49Z",
                            status: "Active",
                            members: [
                                {
                                    id: "ea7ec24e-3cc2-4740-b1b8-acde0158271f"
                                },
                                {
                                    id: "5bda099e-be26-4aac-a310-ecf221ee2451"
                                }
                            ],
                            admins: [
                                {
                                    id: "ea7ec24e-3cc2-4740-b1b8-acde0158271f"
                                },
                                {
                                    id: "5bda099e-be26-4aac-a310-ecf221ee2451"
                                }
                            ]
                        },
                        changeType: "Delete",
                        userId: "ea7ec24e-3cc2-4740-b1b8-acde0158271f",
                        id: "13516a79-1c61-4b9d-b442-0a773fc9c99f",
                        created: "2022-07-20T10:24:28Z",
                        userName: "professor"
                    }
                ],
                maxItems: 100
            }
        };
        var getGroupChangesSets = spyOn(this.groupsService, 'getGroupChanges')
            .and.stub()
            .and.returnValue(this.q.when(response));

        var expectedId = "";
        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;

        this.scope.changeNextPage();

        expect(getGroupChangesSets.calls.count()).toBe(1);
        expect(getGroupChangesSets.calls.mostRecent().args).toEqual(
          [expectedId, expectedMaxItems, expectedStartFrom]);
    });

    it('prevPage should call getGroupChanges with the correct parameters', function () {

        var response = {
            data: {
                changes: [
                    {
                        newGroup: {
                            id: "f9329f39-595d-45c9-8cdf-ac36e96e085d",
                            name: "test-group",
                            email: "test@test.com",
                            created: "2022-07-20T10:14:49Z",
                            status: "Active",
                            members: [
                                {
                                    id: "ea7ec24e-3cc2-4740-b1b8-acde0158271f"
                                },
                                {
                                    id: "5bda099e-be26-4aac-a310-ecf221ee2451"
                                }
                            ],
                            admins: [
                                {
                                    id: "ea7ec24e-3cc2-4740-b1b8-acde0158271f"
                                },
                                {
                                    id: "5bda099e-be26-4aac-a310-ecf221ee2451"
                                }
                            ]
                        },
                        changeType: "Delete",
                        userId: "ea7ec24e-3cc2-4740-b1b8-acde0158271f",
                        id: "13516a79-1c61-4b9d-b442-0a773fc9c99f",
                        created: "2022-07-20T10:24:28Z",
                        userName: "professor"
                    }
                ],
                maxItems: 100
            }
        };
        var getGroupChangesSets = spyOn(this.groupsService, 'getGroupChanges')
            .and.stub()
            .and.returnValue(this.q.when(response));

        var expectedId = "";
        var expectedMaxItems = 100;
        var expectedStartFrom = undefined;

        this.scope.changePrevPage();

        expect(getGroupChangesSets.calls.count()).toBe(1);
        expect(getGroupChangesSets.calls.mostRecent().args).toEqual(
            [expectedId, expectedMaxItems, expectedStartFrom]);

        this.scope.changeNextPage();
        this.scope.changePrevPage();

        expect(getGroupChangesSets.calls.count()).toBe(3);
        expect(getGroupChangesSets.calls.mostRecent().args).toEqual(
            [expectedId, expectedMaxItems, expectedStartFrom]);
    });
});
