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
        module('service.groups')
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

               expect(this.scope.newBatch).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR", ttl: 200}, {changeType: "Add", type: "A+PTR", ttl: 200}]})
            });
        });

        describe('$scope.deleteSingleChange', function() {
            it('removes a change from the changes array', function() {
               this.scope.deleteSingleChange(0);

               expect(this.scope.newBatch).toEqual({comments: "", changes: []})
            });
        });

        describe('$scope.uploadCSV', function() {
            beforeEach(inject(function($compile) {
                var form = $('<ng-form name="csvForm" isolate-form>'+
                  '<input type="file" id="batchChangeCsv" ng-model="csvInput" name="batchChangeCsv" class="batchChangeCsv" filled csv>' +
                  '</ng-form>');
                $(document.body).append(form);
                $compile(form)(this.scope);
                this.scope.$digest();
            }));

            it('parses a CSV file', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\nAdd,A+PTR,test.example.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1);
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}]});
                    done();
                }, 2000);
            })

            it('is case insensitive', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\nadd,a+pTR,test.example.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}]});
                    done();
                }, 1000);
            })

            it('handles "delete" change type', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\nDelete,A+PTR,test.example.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "DeleteRecordSet", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}]});
                    done();
                }, 1000);
            })

            it('handles whitespace', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\nDelete, A+PTR ,test.example.  ,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "DeleteRecordSet", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}]});
                    done();
                }, 1000);
            })

            it('does not include the first line', function(done) {
                var fileBlob = new Blob(["Delete,A+PTR,test.example.,,1.1.1.1\nAdd,A+PTR,test.add.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR", inputName: "test.add.", ttl: 200, record: {address: "1.1.1.1"}}]});
                    done();
                }, 1000);
            })

            it('does not include empty lines', function(done) {
                var fileBlob = new Blob(["Change Type,Record Type,Input Name,TTL,Record Data\n,,,,,\nDelete,A+PTR,test.example.,200,1.1.1.1"], { type: 'text/csv' });
                var batchChange = this.scope.newBatch;
                this.scope.uploadCSV(fileBlob);

                setTimeout(function() {
                    expect(batchChange.changes.length).toEqual(1)
                    expect(batchChange).toEqual({comments: "", changes: [{changeType: "DeleteRecordSet", type: "A+PTR", inputName: "test.example.", ttl: 200, record: {address: "1.1.1.1"}}]});
                    done();
                }, 1000);
            })

            it('does not import non-CSV format files', function() {
               var newFile = {name: 'a.pdf', type: 'any'}
               this.scope.uploadCSV(newFile);

               expect(this.scope.newBatch).toEqual({comments: "", changes: [{changeType: "Add", type: "A+PTR", ttl: 200}]});
               expect(this.scope.alerts).toEqual([{ type: 'danger', content: 'Import failed. Not a valid CSV file.'}]);
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

    describe('Directive: csv validation', function(){
        var form, scope, elm;
        beforeEach(inject(function($compile, $rootScope) {
            this.rootScope = $rootScope;
            scope = $rootScope.$new();
            elm = angular.element(
                '<form name="form">' +
                    '<input type="file" ng-model="batchChangeCsv" name="csvInput" csv />' +
                '</form>'
            );
            $compile(elm)(scope);
            form = scope.form;
        }));

        it('succeeds when given file is a CSV type', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: [{name: 'CSV', type: 'text/csv'}]}});
            expect(form.csvInput.$valid).toBe(true);
        });

        it('fails when given file is not a CSV type', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: [{name: 'plain', type: 'text/plain'}]}});
            expect(form.csvInput.$valid).toBe(false);
        });

        it('fails when no file is given', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: []}});
            expect(form.csvInput.$valid).toBe(false);
        });

        it('sets the view value to the file', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: [{name: 'CSV', type: 'text/csv'}]}});
            expect(form.csvInput.$viewValue).toEqual({name: 'CSV', type: 'text/csv'});
        });

        it('sets the view value to the given file regardless of file type', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: [{name: 'a.pdf', type: 'text/pdf'}]}});
            expect(form.csvInput.$viewValue).toEqual({name: 'a.pdf', type: 'text/pdf'});
        });

        it('sets the view value to undefined when no file is given', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: []}});
            expect(form.csvInput.$viewValue).toEqual(undefined);
        });
    });

    describe('Directive: filled validation', function(){
        var form, scope, elm;
        beforeEach(inject(function($compile, $rootScope) {
            this.rootScope = $rootScope;
            scope = $rootScope.$new();
            elm = angular.element(
                '<form name="form">' +
                    '<input type="file" ng-model="batchChangeCsv" name="csvInput" filled />' +
                '</form>'
            );
            $compile(elm)(scope);
            form = scope.form;
        }));

        it('passes when a file is given', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: [{name: 'a.pdf', type: 'any'}]}});
            expect(form.csvInput.$valid).toBe(true);
        });

        it('fails when no file is given', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: []}});
            expect(form.csvInput.$valid).toBe(false);
        });
    });

    describe('Directive: form isolation', function(){
        var form, scope, elm;
        beforeEach(inject(function($compile, $rootScope) {
            this.rootScope = $rootScope;
            scope = $rootScope.$new();
            elm = angular.element(
                '<form name="form">' +
                    '<ng-form name="csvForm" isolate-form>' +
                        '<input type="file" ng-model="batchChangeCsv" name="csvInput" filled />' +
                    '</ng-form>' +
                '</form>'
            );
            $compile(elm)(scope);
            form = scope.form;
            csvForm = scope.csvForm;
        }));

        it('does validate the nested form', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: []}});
            expect(csvForm.$valid).toBe(false);
        });

        it('does not validate the parent form', function(){
            scope.$digest();
            elm.find('input').triggerHandler({type: 'change', target: {files: []}});
            expect(form.$valid).toBe(true);
        });
    });
});
