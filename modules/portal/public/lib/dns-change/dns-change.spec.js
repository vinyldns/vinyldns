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
        module('dns-change'),
        module('service.utility'),
        module('service.paging'),
        module('service.groups')
    });

    var deferred;

    describe('Controller: DnsChangeDetailController', function(){
        beforeEach(function () {
            module('ngMock')
        });

        beforeEach(inject(function ($rootScope, $controller, $q, dnsChangeService, pagingService, utilityService) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            this.controller = $controller('DnsChangeDetailController', {'$scope': this.scope});

            deferred = $q.defer();

            spyOn(dnsChangeService, 'getBatchChange').and.returnValue(deferred.promise);
            spyOn(dnsChangeService, 'approveBatchChange').and.returnValue(deferred.promise);
            spyOn(dnsChangeService, 'rejectBatchChange').and.returnValue(deferred.promise);
        }));

        describe('$scope.getBatchChange', function() {
            it('should resolve the promise', inject(function(dnsChangeService) {

                this.scope.getBatchChange("17350028-b2b8-428d-9f10-dbb518a0364d");

                expect(dnsChangeService.getBatchChange).toHaveBeenCalled();
                expect(dnsChangeService.getBatchChange).toHaveBeenCalledWith("17350028-b2b8-428d-9f10-dbb518a0364d");

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

            it('should reject the promise', inject(function(dnsChangeService) {
                this.scope.getBatchChange("nonExistentBatchChange");

                expect(dnsChangeService.getBatchChange).toHaveBeenCalled();
                expect(dnsChangeService.getBatchChange).toHaveBeenCalledWith("nonExistentBatchChange");

                deferred.reject({data: "Batch change with id wow cannot be found", status: 404});
                this.rootScope.$apply();

                expect(this.scope.batch).toEqual({});
                expect(this.scope.alerts).toEqual([{ type: 'danger', content: 'HTTP 404 (undefined): Batch change with id wow cannot be found' }]);
            }));
        });

        describe('$scope.confirmApprove', function() {
            it('should resolve the promise', inject(function(dnsChangeService) {

                this.scope.confirmApprove("17350028-b2b8-428d-9f10-dbb518a0364d", "great");

                expect(dnsChangeService.approveBatchChange).toHaveBeenCalled();

                deferred.resolve({data: {reviewComment: "great"}});
                this.rootScope.$apply();

                expect(this.scope.reviewConfirmationMsg).toEqual(null);
                expect(this.scope.reviewType).toEqual(null);
            }));

            it('should reject the promise', inject(function(dnsChangeService) {
                this.scope.confirmApprove("notPendingBatchChange", "great");

                expect(dnsChangeService.approveBatchChange).toHaveBeenCalled();

                var errorData = {data: [{changeType: "Add", inputName: "onofe.ok.", type: "A", ttl: 7200, record: {address: "1.1.1.1"}},
                {changeType: "Add", inputName: "wfonef.wfoefn.", type: "A", ttl: 7200, record: {address: "1.1.1.1"}, errors: ['Zone Discovery Failed: zone for "wfonef.wfoefn." doesn\'t exists']}]}

                deferred.reject({data: errorData, status: 400});
                this.rootScope.$apply();

                expect(this.scope.alerts).toEqual([{ type: 'danger', content: "HTTP 400 (undefined): Issues still remain, cannot approve DNS Change. Resolve all outstanding issues or reject the DNS Change." }]);
            }));
        });

        describe('$scope.confirmReject', function() {
            it('should resolve the promise', inject(function(dnsChangeService) {

                this.scope.confirmReject("17350028-b2b8-428d-9f10-dbb518a0364d", "great");

                expect(dnsChangeService.rejectBatchChange).toHaveBeenCalled();

                deferred.resolve({data: {reviewComment: "no bueno"}});
                this.rootScope.$apply();

                expect(this.scope.reviewConfirmationMsg).toEqual(null);
                expect(this.scope.reviewType).toEqual(null);
            }));

            it('should reject the promise', inject(function(dnsChangeService) {
                this.scope.confirmReject("notPendingBatchChange", "no bueno");

                expect(dnsChangeService.rejectBatchChange).toHaveBeenCalled();

                deferred.reject({data: "Batch change with id notPendingBatchChange is not pending review.", status: 400});
                this.rootScope.$apply();

                expect(this.scope.alerts).toEqual([{ type: 'danger', content: 'HTTP 400 (undefined): Batch change with id notPendingBatchChange is not pending review.' }]);
            }));
        });
    });

    describe('Controller: DnsChangeNewController', function(){
        var tomorrow = moment().startOf('hour').add(1, 'day').format('LL hh:mm A');

        beforeEach(function () {
            module('ngMock')
        });

        beforeEach(inject(function ($rootScope, $controller, $q, dnsChangeService, utilityService, groupsService) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            this.groupsService = groupsService;
            this.scope.newBatch = {comments: "", changes: [{changeType: "Add", type: "A", ttl: 200}]};
            this.scope.myGroups = {};

            deferred = $q.defer();

            spyOn(dnsChangeService, 'createBatchChange').and.returnValue(deferred.promise);
            groupsService.getGroups = function() {
                return $q.when({
                    data: {
                        groups: "all my groups"
                    }
                });
            };

            this.controller = $controller('DnsChangeNewController', {'$scope': this.scope});
        }));

        it("test that we properly get user's groups when loading DnsChangeNewController", function(){
            this.scope.$digest();
            expect(this.scope.myGroups).toBe("all my groups");
        });

        it("test that we set a default scheduledTime when loading DnsChangeNewController", function(){
            this.scope.$digest();
            expect(this.scope.newBatch.scheduledTime).toBe(tomorrow);
        });


        describe('$scope.addSingleChange', function() {
            it('adds a change to the changes array', function() {
               this.scope.addSingleChange();

               expect(this.scope.newBatch).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR"}, {changeType: "Add", type: "A+PTR"}], scheduledTime: tomorrow})
            });
        });

        describe('$scope.deleteSingleChange', function() {
            it('removes a change from the changes array', function() {
               this.scope.deleteSingleChange(0);

               expect(this.scope.newBatch).toEqual({comments: "", changes: [], scheduledTime: tomorrow})
            });
        });

        describe('$scope.uploadCSV', function() {
            beforeEach(inject(function($compile) {
                var formInput = $('<ng-form name="createBatchChangeForm">'+
                  '<input type="file" id="batchChangeCsv" ng-model="csvInput" name="batchChangeCsv" class="batchChangeCsv" batch-change-file>' +
                  '</ng-form>');
                $(document.body).append(formInput);
                $compile(formInput)(this.scope);
                this.scope.$digest();
            }));

            it('parses a CSV file', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\nAdd,A+PTR,test.example.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1);
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}], scheduledTime: tomorrow});
                    done();
                }, 2000);
            })

            it('is case insensitive', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\nadd,a+pTR,test.example.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}], scheduledTime: tomorrow});
                    done();
                }, 1000);
            })

            it('handles "delete" change type', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\nDelete,A+PTR,test.example.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "DeleteRecordSet", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}], scheduledTime: tomorrow});
                    done();
                }, 1000);
            })

            it('handles whitespace', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\nDelete, A+PTR ,test.example.  ,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "DeleteRecordSet", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}], scheduledTime: tomorrow});
                    done();
                }, 1000);
            })

            it('fails if the first line is not the header row', function(done) {
                var fileBlob = new Blob(["Delete,A+PTR,test.example.,,1.1.1.1\nAdd,A+PTR,test.add.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);
                var alerts = this.scope.alerts;

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR"}], scheduledTime: tomorrow});
                    done();
                }, 1000);
            })

            it('does not include empty lines', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\n,,,,,\nDelete,A+PTR,test.example.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "DeleteRecordSet", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}], scheduledTime: tomorrow});
                    done();
                }, 1000);
            })

            it('does not import non-CSV format files', function() {
               var newFile = new Blob([], {type: 'any'});
               this.scope.uploadCSV(newFile);
               expect(this.scope.newBatch).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR"}], scheduledTime: tomorrow});
            });
        });

        describe('$scope.createBatchChange', function() {
            it('should resolve the promise', inject(function(dnsChangeService) {

                this.scope.newBatch = {
                    comments: "this is a comment.",
                    changes: [{changeType: "Add", type: "A", ttl: 200, record: {address: "1.1.1.2"}}]
                };

                this.scope.createBatchChange();

                expect(dnsChangeService.createBatchChange).toHaveBeenCalled();

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

                expect(dnsChangeService.createBatchChange).toHaveBeenCalled();

                deferred.reject({config: {data: this.scope.newBatch}, data: [{changeType: "Add", inputName: 'blah.dummy.', type: "A", ttl: 200, record: {address: "1.1.1.2"}, errors: ['Zone for "blah.dummy." does not exist in Vinyl.']}], status: 400});
                this.rootScope.$apply();


                expect(this.scope.batch).toEqual({});
                expect(this.scope.newBatch).toEqual({
                    comments: "zone not found.",
                    changes: [{changeType: "Add", inputName: 'blah.dummy.', type: "A", ttl: 200, record: {address: "1.1.1.2"}, errors: ['Zone for "blah.dummy." does not exist in Vinyl.']}]
                });
                expect(this.scope.alerts).toEqual([{ type: 'danger', content: 'Errors found. Please correct and submit again.'}]);
            }));

            it('should format the batch change data', inject(function(dnsChangeService) {

                this.scope.newBatch = {
                    comments: "this is a comment.",
                    changes: [{changeType: "Add", inputName: 'blah.dummy.', type: "A+PTR", ttl: 200, record: {address: "1.1.1.2"}}],
                    scheduledTime: tomorrow,
                };

                this.scope.createBatchChange();

                expect(dnsChangeService.createBatchChange).toHaveBeenCalled();

                deferred.resolve({data: {}});
                this.rootScope.$apply();

                expect(this.scope.newBatch.ownerGroupId).toBeUndefined();
                expect(this.scope.newBatch.scheduledTime).toBeUndefined();
                expect(this.scope.newBatch.changes).toEqual([
                    {changeType: "Add", inputName: 'blah.dummy.', type: "A", ttl: 200, record: {address: "1.1.1.2"}},
                    {changeType: "Add", inputName: '1.1.1.2', type: "PTR", ttl: 200, record: {ptrdname: "blah.dummy."}}
                ]);
            }));
        });
    });

    describe('Controller: DnsChangesController', function(){
        beforeEach(function () {
            module('ngMock')
        });

        beforeEach(inject(function ($rootScope, $controller, $q, dnsChangeService, utilityService) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            this.controller = $controller('DnsChangesController', {'$scope': this.scope});

            deferred = $q.defer();

            spyOn(dnsChangeService, 'getBatchChanges').and.returnValue(deferred.promise);
        }));

        describe('$scope.getBatchChanges', function() {
            it('should resolve the promise', inject(function(dnsChangeService) {
                this.scope.getBatchChanges()
                    .then(function(response) {
                        expect(response.data.batchChanges).toBe(mockBatchChanges)
                    });

                expect(dnsChangeService.getBatchChanges).toHaveBeenCalled();

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
            this.dnsChangeService = batchChangeService;
            this.$httpBackend = $httpBackend;
        }));

        it('http backend gets called properly when getting a batch changes', function () {
            this.$httpBackend.expectGET('/api/dnschanges/123').respond('batch change returned');
            this.dnsChangeService.getBatchChange('123')
                .then(function(response) {
                    expect(response.data).toBe('batch change returned');
                });
            this.$httpBackend.flush();
        });

        it('http backend gets called properly when creating a batch change', function () {
            this.$httpBackend.expectPOST('/api/dnschanges').respond('batch change created');
            this.dnsChangeService.createBatchChange({comments: "", changes: [{changeType: "Add", type: "A", ttl: 200}]})
                .then(function(response) {
                    expect(response.data).toBe('batch change created');
                });
            this.$httpBackend.flush();
        });

        it('http backend gets called properly when approving a batch change', function () {
            this.$httpBackend.expectPOST('/api/dnschanges/123/approve').respond('batch change created');
            this.dnsChangeService.approveBatchChange("123", "good")
                .then(function(response) {
                    expect(response.data).toBe('batch change created');
                });
            this.$httpBackend.flush();
        });

        it('http backend gets called properly when rejecting a batch change', function () {
            this.$httpBackend.expectPOST('/api/dnschanges/123/reject').respond('batch change created');
            this.dnsChangeService.rejectBatchChange("123", "bad")
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

    describe('Directive: Invalid IPv4 validation for CNAME record data', function(){
        var form;
        beforeEach(inject(function($compile, $rootScope) {
            this.rootScope = $rootScope;
            this.scope = $rootScope.$new();
            var element = angular.element(
                '<form name="form">' +
                    '<input ng-model="change.cname" name="cname" invalidip />' +
                '</form>'
            );
            this.scope.change = { fqdn: null };
            $compile(element)(this.scope);
            form = this.scope.form;
        }));

        it('fails with an IPV4 address', function(){
            form.cname.$setViewValue('1.1.1.1');
            this.scope.$digest();
            expect(this.scope.change.cname).toBeUndefined();
            expect(form.cname.$valid).toBe(false);
        });

        it('fails with an IPV4 address and a trailing dot', function(){
            form.cname.$setViewValue('test.');
            this.scope.$digest();
            expect(this.scope.change.cname).toBeUndefined();
            expect(form.cname.$valid).toBe(false);
        });

        it('passes if not given an IPV4 address', function(){
            form.cname.$setViewValue('notanIP');
            this.scope.$digest();
            expect(this.scope.change.cname).toEqual('notanIP');
            expect(form.cname.$valid).toBe(true);
        });
    });

        describe('Directive: combined CNAME record data validations', function(){
            var form;
            beforeEach(inject(function($compile, $rootScope) {
                this.rootScope = $rootScope;
                this.scope = $rootScope.$new();
                var element = angular.element(
                    '<form name="form">' +
                        '<input ng-model="change.cname" name="cname" fqdn invalidip />' +
                    '</form>'
                );
                this.scope.change = { fqdn: null };
                $compile(element)(this.scope);
                form = this.scope.form;
            }));

            it('passes with at least one dot and not an IP Address', function(){
                form.cname.$setViewValue('test.com');
                this.scope.$digest();
                expect(this.scope.change.cname).toEqual('test.com');
                expect(form.cname.$valid).toBe(false);
            });

            it('fails with an IP Address', function(){
                form.cname.$setViewValue('1.1.1.1');
                this.scope.$digest();
                expect(this.scope.change.cname).toBeUndefined();
                expect(form.cname.$valid).toBe(false);
            });

            it('fails without at least one dot', function(){
                form.cname.$setViewValue('testcom');
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
