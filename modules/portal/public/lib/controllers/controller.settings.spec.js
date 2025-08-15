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

describe('Controller: SettingsController', function () {
    var $q, deferred, profileServiceMock, utilityServiceMock;

    beforeEach(function () {
        module('ngMock'),
        module('service.profile'),
        module('service.utility'),
        module('controller.settings');
    });

    beforeEach(inject(function ($rootScope, $controller, _$q_, _profileService_, _utilityService_) {
        this.scope = $rootScope.$new();
        $q = _$q_;
        profileServiceMock = _profileService_;
        utilityServiceMock = _utilityService_;

        // Mock profileService methods
        spyOn(profileServiceMock, 'getUserDataByUsername').and.callFake(function(username) {
            deferred = $q.defer();
            deferred.resolve({data: {id: "userId"}});
            return deferred.promise;
        });

        spyOn(profileServiceMock, 'lockUnlock').and.callFake(function(id, action) {
            deferred = $q.defer();
            deferred.resolve({data: "success"});
            return deferred.promise;
        });

        spyOn(profileServiceMock, 'updateUserPermission').and.callFake(function(id, permission) {
            deferred = $q.defer();
            deferred.resolve({data: "success"});
            return deferred.promise;
        });

        // Mock utilityService methods
        spyOn(utilityServiceMock, 'success').and.callFake(function(message) {
            return {type: 'success', msg: message};
        });

        spyOn(utilityServiceMock, 'failure').and.callFake(function(error, type) {
            return {type: 'danger', msg: error};
        });

        // Create the controller
        this.controller = $controller('SettingsController', {
            '$scope': this.scope,
            'profileService': profileServiceMock,
            'utilityService': utilityServiceMock
        });
    }));

    it('should fetch user data when checkUserStatus is called', function() {
        this.scope.statusUsername = 'testUser';
        this.scope.checkUserStatus();
        this.scope.$digest();

        expect(profileServiceMock.getUserDataByUsername).toHaveBeenCalledWith('testUser');
        expect(this.scope.statusResponse.id).toEqual('userId');
    });

    it('should handle error when fetchUserData fails', function() {
        profileServiceMock.getUserDataByUsername.and.callFake(function() {
            deferred = $q.defer();
            deferred.reject('error');
            return deferred.promise;
        });

        this.scope.statusUsername = 'testUser';
        this.scope.checkUserStatus();
        this.scope.$digest();

        expect(utilityServiceMock.failure).toHaveBeenCalledWith('error', 'profileService::getUserDataByUsername-failure');
    });

    it('should lock or unlock the user when submitForm is called', function() {
        this.scope.username = 'testUser';
        this.scope.action = 'lock';
        this.scope.submitForm();
        this.scope.$digest();

        expect(profileServiceMock.getUserDataByUsername).toHaveBeenCalledWith('testUser');
        expect(profileServiceMock.lockUnlock).toHaveBeenCalledWith('userId', 'lock');
        expect(utilityServiceMock.success).toHaveBeenCalledWith('User testUser has been locked successfully!');
        expect(this.scope.alerts.length).toBe(1);
    });

    it('should update user permission when updatePermission is called', function() {
        this.scope.permissionUsername = 'testUser';
        this.scope.selectedPermission = 'Admin';
        this.scope.updatePermission();
        this.scope.$digest();

        expect(profileServiceMock.getUserDataByUsername).toHaveBeenCalledWith('testUser');
        expect(profileServiceMock.updateUserPermission).toHaveBeenCalledWith('userId', 'admin');
        expect(utilityServiceMock.success).toHaveBeenCalledWith('User testUser permission has been updated successfully!');
        expect(this.scope.alerts.length).toBe(1);
    });

    it('should clear forms when clear functions are called', function() {
        this.scope.username = 'testUser';
        this.scope.action = 'lock';
        this.scope.clearLockUnlockForm();
        expect(this.scope.username).toBe('');
        expect(this.scope.action).toBe('');

        this.scope.permissionUsername = 'testUser';
        this.scope.selectedPermission = 'Admin';
        this.scope.clearPermissionForm();
        expect(this.scope.permissionUsername).toBe('');
        expect(this.scope.selectedPermission).toBe('');

        this.scope.statusUsername = 'testUser';
        this.scope.statusResponse = {id: 'someId'};
        this.scope.clearStatusForm();
        expect(this.scope.statusUsername).toBe('');
        expect(this.scope.statusResponse).toBe(null);
    });
});
