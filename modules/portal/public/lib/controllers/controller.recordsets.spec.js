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

describe('Controller: RecordSetsController', function () {
    beforeEach(function () {
        module('ngMock'),
        module('service.records'),
        module('service.groups'),
        module('service.utility'),
        module('service.paging'),
        module('recordset')
    });

    beforeEach(inject(function ($rootScope, $controller, $q, recordsService, groupsService) {
        this.rootScope = $rootScope;
        this.controllerFactory = $controller;

        recordsService.listRecordSetData = function() {
            return $q.when({data: {recordSets: []}});
        };

        groupsService.getGroupsAbridged = function() {
            return $q.when({data: {groups: []}});
        };
    }));

    it('renders record autocomplete labels as text while preserving highlights', function () {
        document.body.innerHTML = '<input id="record-search-text" />';

        var scope = this.rootScope.$new();
        this.controllerFactory('RecordSetsController', {'$scope': scope});

        var instance = $('#record-search-text').autocomplete('instance');
        instance.term = 'Team';

        var rendered = instance._renderItem($('<ul></ul>'), {
            label: '<img src=x onerror=alert(1)>Team',
            value: '<img src=x onerror=alert(1)>Team'
        });

        expect(rendered.find('img').length).toBe(0);
        expect(rendered.find('div').text()).toBe('<img src=x onerror=alert(1)>Team');
        expect(rendered.find('b').text()).toBe('Team');

        document.body.innerHTML = '';
    });
});
