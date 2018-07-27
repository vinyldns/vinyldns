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

'use strict';

angular.module('service.paging', [])
    .service('pagingService', function () {
        this.nextPageUpdate = function(data, nextId, paging){
            var newPage = angular.copy(paging);
            if (data.length == 0) {
                /*
                 * Vinyl sometimes returns a startFrom when there's not a next page. We should fix that in Vinyl
                 * itself. Once that's done, this logic can go away.
                 *
                 * For now, if someone clicks on "Next Page" but there's not anything there, we should just stay
                 * where we are and take away the "Next Page" button. Don't think there's anything more we can do
                 * without preloading (which seems overly complicated for a problem we should fix at the source).
                 */
                newPage.next = undefined;
            } else {
                if (paging.next) {
                    newPage.startKeys.push(paging.next);
                }
                newPage.next = nextId;
                newPage.pageNum++;
            }
            return newPage
        };

        this.prevPageUpdate = function(nextId, paging) {
            var newPage = angular.copy(paging);
            newPage.startKeys.pop();
            newPage.next = nextId;
            newPage.pageNum--;

            return newPage;
        };

        this.getPrevStartFrom = function(paging) {
            var startFrom = undefined;
            // page 0: there is no previous
            // page 1: previous start key is undefined
            // page 2: startKeys holds the current start key above previous key, so -2
            if (paging.pageNum > 1) {
                startFrom = paging.startKeys[paging.startKeys.length - 2];
            }
            return startFrom;
        };

        this.nextPageEnabled = function(paging) {
            return Boolean(paging.next);
        };

        this.prevPageEnabled = function(paging) {
            return paging.pageNum >= 1
        };

        this.getPanelTitle = function(paging) {
            return paging.pageNum > 0 ? "[Page "+(paging.pageNum+1)+"]" : ""
        };

        this.resetPaging = function(paging) {
            var newPage = angular.copy(paging);
            newPage.pageNum = 0;
            newPage.startKeys = [];
            newPage.next = undefined;
            return newPage;
        };

        var emptyPaging = {
            maxItems: 10,
            pageNum: 0,
            startKeys: [],
            next: undefined
        };

        this.getNewPagingParams = function(maxItems) {
            var temp = angular.copy(emptyPaging);
            temp.maxItems = maxItems;
            return temp
        }
    });
