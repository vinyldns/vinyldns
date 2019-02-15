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

describe('BatchChange', function(){

    beforeEach(function () {
        module('batch-change'),
        module('service.utility'),
        module('service.paging'),
        module('service.groups'),
        module('constants')
    });

    var deferred;

    describe('Controller: BatchChangeDetailController', function(){
        beforeEach(function () {
            module('ngMock')
        });

        beforeEach(inject(function ($rootScope, $controller, $q, batchChangeService, pagingService, utilityService) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            this.controller = $controller('BatchChangeDetailController', {'$scope': this.scope});

            deferred = $q.defer();

            spyOn(batchChangeService, 'getBatchChange').and.returnValue(deferred.promise);
        }));

        describe('$scope.getBatchChange', function() {
            it('should resolve the promise', inject(function(batchChangeService) {

                this.scope.getBatchChange("17350028-b2b8-428d-9f10-dbb518a0364d");

                expect(batchChangeService.getBatchChange).toHaveBeenCalled();
                expect(batchChangeService.getBatchChange).toHaveBeenCalledWith("17350028-b2b8-428d-9f10-dbb518a0364d");

                var mockBatchChange = {
                    userId: "17350028-b2b8-428d-9f10-dbb518a0364d",
                    userName: "bwrigh833",
                    comments: "this is a test comment.",
                    createdTimestamp: "2018-05-09T14:20:50Z",
                    changes: [{zoneId: "937191c4-b1fd-4ab5-abb4-9553a65b44ab", zoneName: "old-vinyldns2.", recordName:"test1", inputName: "test1.old-vinyldns2.", type: "A", ttl: 200, record: {address:"1.1.1.1"}, status: "Pending", id: "c29d33e4-9bee-4417-a99b-6e815fdeb748", changeType: "Add"}],
                    status: "Pending",
                    id: "921e70fa-9bec-48eb-a520-a8e6106158e2"
                };

                deferred.resolve({data: mockBatchChange});
                this.rootScope.$apply();

                expect(this.scope.batch).toEqual(mockBatchChange);
            }));

            it('should reject the promise', inject(function(batchChangeService) {
                this.scope.getBatchChange("nonExistentBatchChange");

                expect(batchChangeService.getBatchChange).toHaveBeenCalled();
                expect(batchChangeService.getBatchChange).toHaveBeenCalledWith("nonExistentBatchChange");

                deferred.reject({data: "Batch change with id wow cannot be found", status: 404});
                this.rootScope.$apply();

                expect(this.scope.batch).toEqual({});
                expect(this.scope.alerts).toEqual([{ type: 'danger', content: 'HTTP 404 (undefined): Batch change with id wow cannot be found' }]);
            }));
        });
    });

    describe('Controller: BatchChangeNewController', function(){
        beforeEach(function () {
            module('ngMock')
        });

        beforeEach(inject(function ($rootScope, $controller, $q, batchChangeService, utilityService, groupsService) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            this.groupsService = groupsService;
            this.scope.newBatch = {comments: "", changes: [{changeType: "Add", type: "A", ttl: 200}]};
            this.scope.myGroups = {};

            deferred = $q.defer();

            spyOn(batchChangeService, 'createBatchChange').and.returnValue(deferred.promise);
            groupsService.getMyGroups = function() {
                return $q.when({
                    data: {
                        groups: "all my groups"
                    }
                });
            };

            this.controller = $controller('BatchChangeNewController', {'$scope': this.scope});
        }));

        it("test that we properly get user's groups when loading BatchChangeNewController", function(){
            this.scope.$digest();
            expect(this.scope.myGroups).toBe("all my groups");
        });

        describe('$scope.addSingleChange', function() {
            it('adds a change to the changes array', function() {
               this.scope.addSingleChange();

               expect(this.scope.newBatch).toEqual({comments: "", changes: [{changeType: "Add", type: "A", ttl: 200}, {changeType: "Add", type: "A", ttl: 200}]})
            });
        });

        describe('$scope.deleteSingleChange', function() {
            it('removes a change from the changes array', function() {
               this.scope.deleteSingleChange(0);

               expect(this.scope.newBatch).toEqual({comments: "", changes: []})
            });
        });

        describe('$scope.createBatchChange', function() {
            it('should resolve the promise', inject(function(batchChangeService) {

                this.scope.newBatch = {
                    comments: "this is a comment.",
                    changes: [{changeType: "Add", type: "A", ttl: 200, record: {address: "1.1.1.2"}}]
                };

                this.scope.createBatchChange();

                expect(batchChangeService.createBatchChange).toHaveBeenCalled();

                var mockBatchChange = {
                    userId: "17350028-b2b8-428d-9f10-dbb518a0364d",
                    userName: "bwrigh833",
                    comments: "this is a test comment.",
                    createdTimestamp: "2018-05-09T14:20:50Z",
                    changes: [{zoneId: "937191c4-b1fd-4ab5-abb4-9553a65b44ab", zoneName: "old-vinyldns2.", recordName:"test1", inputName: "test1.old-vinyldns2.", type: "A", ttl: 200, record: {address:"1.1.1.1"}, status: "Pending", id: "c29d33e4-9bee-4417-a99b-6e815fdeb748", changeType: "Add"}],
                    status: "Pending",
                    id: "921e70fa-9bec-48eb-a520-a8e6106158e2"
                };

                deferred.resolve({data: mockBatchChange});
                this.rootScope.$apply();

                expect(this.scope.batch).toEqual(mockBatchChange);
            }));

            it('should reject the promise', inject(function(batchChangeService) {

                this.scope.newBatch = {
                    comments: "zone not found.",
                    changes: [{changeType: "Add", inputName: 'blah.dummy.', type: "A", ttl: 200, record: {address: "1.1.1.2"}}]
                };

                this.scope.createBatchChange();

                expect(batchChangeService.createBatchChange).toHaveBeenCalled();

                deferred.reject({config: {data: this.scope.newBatch}, data: [{changeType: "Add", inputName: 'blah.dummy.', type: "A", ttl: 200, record: {address: "1.1.1.2"}, errors: ['Zone for "blah.dummy." does not exist in Vinyl.']}], status: 400});
                this.rootScope.$apply();


                expect(this.scope.batch).toEqual({});
                expect(this.scope.newBatch).toEqual({
                    comments: "zone not found.",
                    changes: [{changeType: "Add", inputName: 'blah.dummy.', type: "A", ttl: 200, record: {address: "1.1.1.2"}, errors: ['Zone for "blah.dummy." does not exist in Vinyl.']}]
                });
                expect(this.scope.alerts).toEqual([{ type: 'danger', content: 'Errors found. Please correct and submit again.'}]);
            }));
        });
    });

    describe('Controller: BatchChangesController', function(){
        beforeEach(function () {
            module('ngMock')
        });

        beforeEach(inject(function ($rootScope, $controller, $q, batchChangeService, utilityService) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            this.controller = $controller('BatchChangesController', {'$scope': this.scope});

            deferred = $q.defer();

            spyOn(batchChangeService, 'getBatchChanges').and.returnValue(deferred.promise);
        }));

        describe('$scope.getBatchChanges', function() {
            it('should resolve the promise', inject(function(batchChangeService) {
                this.scope.getBatchChanges()
                    .then(function(response) {
                        expect(response.data.batchChanges).toBe(mockBatchChanges)
                    });

                expect(batchChangeService.getBatchChanges).toHaveBeenCalled();

                var mockBatchChanges = {
                    comments: "this is hopefully a full failure",
                    createdTimestamp: "Fri May 18 2018 15:01:41",
                    id: "5bcfd2fe-81d8-4eae-897f-aa8a7c9cbc00",
                    singleChangeCount: 1,
                    status: "Failed",
                    userId: "17350028-b2b8-428d-9f10-dbb518a0364d",
                    userName:"bwrigh833"
                };

                deferred.resolve({data: {batchChanges: mockBatchChanges}});
                this.rootScope.$apply();
            }));
        });
    });

    describe('Service: batchChange', function() {
        beforeEach(inject(function ($httpBackend, batchChangeService) {
            this.batchChangeService = batchChangeService;
            this.$httpBackend = $httpBackend;
        }));

        it('http backend gets called properly when getting a batch changes', function () {
            this.$httpBackend.expectGET('/api/batchchanges/123').respond('batch change returned');
            this.batchChangeService.getBatchChange('123')
                .then(function(response) {
                    expect(response.data).toBe('batch change returned');
                });
            this.$httpBackend.flush();
        });

        it('http backend gets called properly when creating a batch change', function () {
            this.$httpBackend.expectPOST('/api/batchchanges').respond('batch change created');
            this.batchChangeService.createBatchChange({comments: "", changes: [{changeType: "Add", type: "A", ttl: 200}]})
                .then(function(response) {
                    expect(response.data).toBe('batch change created');
                });
            this.$httpBackend.flush();
        });
    });

    describe('Directive: FQDN validation', function(){
        var form;
        beforeEach(inject(function($compile, $rootScope) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            var element = angular.element(
                '<form name="form">' +
                    '<input ng-model="change.cname" name="cname" fqdn />' +
                '</form>'
            );
            this.scope.change = { fqdn: null };
            $compile(element)(this.scope);
            form = this.scope.form;
        }));

        it('passes with at least one dot', function(){
            form.cname.$setViewValue('test.com');
            this.scope.$digest();
            expect(this.scope.change.cname).toEqual('test.com');
            expect(form.cname.$valid).toBe(true);
        });

        it('passes with trailing dot', function(){
            form.cname.$setViewValue('test.');
            this.scope.$digest();
            expect(this.scope.change.cname).toEqual('test.');
            expect(form.cname.$valid).toBe(true);
        });

        it('fails without any dots', function(){
            form.cname.$setViewValue('invalidfqdn');
            this.scope.$digest();
            expect(this.scope.change.cname).toBeUndefined();
            expect(form.cname.$valid).toBe(false);
        });
    });

    describe('Directive: IPv4 validation', function(){
        var form;
        beforeEach(inject(function($compile, $rootScope) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            var element = angular.element(
                '<form name="form">' +
                    '<input ng-model="change.address" name="address" ipv4 />' +
                '</form>'
            );
            this.scope.change = { address: null };
            $compile(element)(this.scope);
            form = this.scope.form;
        }));

        it('passes with correct IPv4 format', function(){
            form.address.$setViewValue('1.1.1.1');
            this.scope.$digest();
            expect(this.scope.change.address).toEqual('1.1.1.1');
            expect(form.address.$valid).toBe(true);
        });

        it('fails with incorrect IPv4 format', function(){
            form.address.$setViewValue('bad.ipv4');
            this.scope.$digest();
            expect(this.scope.change.address).toBeUndefined();
            expect(form.address.$valid).toBe(false);
        });
    });

    describe('Directive: IPv6 validation', function(){
        var form;
        beforeEach(inject(function($compile, $rootScope) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            var element = angular.element(
                '<form name="form">' +
                    '<input ng-model="change.address" name="address" ipv6 />' +
                '</form>'
            );
            this.scope.change = { address: null };
            $compile(element)(this.scope);
            form = this.scope.form;
        }));

        it('passes with correct IPv6 format', function(){
            form.address.$setViewValue('fd69:27cc:fe91::60');
            this.scope.$digest();
            expect(this.scope.change.address).toEqual('fd69:27cc:fe91::60');
            expect(form.address.$valid).toBe(true);
        });

        it('fails with incorrect IPv6 format', function(){
            form.address.$setViewValue('bad.ipv6');
            this.scope.$digest();
            expect(this.scope.change.address).toBeUndefined();
            expect(form.address.$valid).toBe(false);
        });
    });
});
