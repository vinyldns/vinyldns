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

describe('Service: pagingService', function () {
    beforeEach(module('ngMock'));
    beforeEach(module('service.paging'));
    beforeEach(inject(function (pagingService) {
        this.pagingService = pagingService;
    }));


    it('should be defined', function () {
        expect(this.pagingService).toBeDefined();
    });

    it('should give a base paging item based on input maxItems', function () {
        var paging1 = this.pagingService.getNewPagingParams(10);
        var paging2 = this.pagingService.getNewPagingParams(20);

        expect(paging1.maxItems).toEqual(10);
        expect(paging2.maxItems).toEqual(20);

        expect(paging1.pageNum).toEqual(0);
        expect(paging1.startKeys).toEqual([]);
        expect(paging1.next).toBeUndefined();
    });

    it('should reset values on an existing paging item', function () {
        var oldPaging = {
            maxItems: 25,
            pageNum: 2,
            startKeys: ["page1", "page2"],
            next: "page3"
        };

        var paging = this.pagingService.resetPaging(oldPaging);

        expect(paging.maxItems).toEqual(25);
        expect(paging.pageNum).toEqual(0);
        expect(paging.startKeys).toEqual([]);
        expect(paging.next).toBeUndefined();
    });

    it('should update paging values for nextPage with empty data', function () {
        var paging = {
            maxItems: 25,
            pageNum: 2,
            startKeys: ["page1", "page2"],
            next: "page3"
        };

        paging = this.pagingService.nextPageUpdate([], undefined, paging);

        expect(paging.maxItems).toEqual(25);
        expect(paging.pageNum).toEqual(2);
        expect(paging.startKeys).toEqual(["page1", "page2"]);
        expect(paging.next).toBeUndefined();
    });

    it('should update paging values for nextPage with data', function () {
        var paging = {
            maxItems: 2,
            pageNum: 2,
            startKeys: ["page1", "page2"],
            next: "page3"
        };

        paging = this.pagingService.nextPageUpdate(["somedata", "somedata"], "page4", paging);

        expect(paging.maxItems).toEqual(2);
        expect(paging.pageNum).toEqual(3);
        expect(paging.startKeys).toEqual(["page1", "page2", "page3"]);
        expect(paging.next).toEqual("page4");
    });

    it('should update paging values for previous page', function () {
        var paging = {
            maxItems: 2,
            pageNum: 2,
            startKeys: ["page1", "page2"],
            next: "page3"
        };

        paging = this.pagingService.prevPageUpdate("page2newkey", paging);

        expect(paging.maxItems).toEqual(2);
        expect(paging.pageNum).toEqual(1);
        expect(paging.startKeys).toEqual(["page1"]);
        expect(paging.next).toEqual("page2newkey");
    });

});
