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

angular.module('controller.groups', []).controller('GroupsController', function ($scope, $log, $location, groupsService, profileService, utilityService) {
    //registering bootstrap modal close event to refresh data after create group action
    angular.element('#modal_new_group').one('hide.bs.modal', function () {
        $scope.closeModal();
    });

    $scope.groups = {items: []};
    $scope.groupsLoaded = false;
    $scope.alerts = [];
    $scope.ignoreAccess = false;
    $scope.hasGroups = false; // Re-assigned each time groups are fetched without a query
    $scope.query = "";

    function handleError(error, type) {
        var alert = utilityService.failure(error, type);
        $scope.alerts.push(alert);
        $scope.processing = false;
    }

    //views
    //shared modal
    var modalDialog;

    $scope.openModal = function (evt) {
        $scope.currentGroup = {};
        void (evt && evt.preventDefault());
        if (!modalDialog) {
            modalDialog = angular.element('#modal_new_group').modal();
        }
        modalDialog.modal('show');
    };

    $scope.closeModal = function (evt) {
        void (evt && evt.preventDefault());
        if (!modalDialog) {
            modalDialog = angular.element('#modal_new_group').modal();
        }
        modalDialog.modal('hide');
        return true;
    };

    $scope.closeEditModal = function (evt) {
        void (evt && evt.preventDefault());
        editModalDialog = angular.element('#modal_edit_group').modal();
        editModalDialog.modal('hide');
        $scope.reset();
        $scope.refresh();
        return true;
    };

    // Autocomplete for group search
    $("#group-search-text").autocomplete({
      source: function( request, response ) {
        $.ajax({
          url: "/api/groups?maxItems=1500&abridged=true",
          dataType: "json",
          data: {groupNameFilter: request.term, ignoreAccess: $scope.ignoreAccess},
          success: function(data) {
              const search =  JSON.parse(JSON.stringify(data));
              response($.map(search.groups, function(group) {
              return {value: group.name, label: group.name}}))}
        });
      },
      minLength: 1,
      select: function (event, ui) {
          $("#group-search-text").val(ui.item.value);
          return false;
        },
      open: function() {
        $(this).removeClass( "ui-corner-all").addClass("ui-corner-top");
      },
      close: function() {
        $(this).removeClass("ui-corner-top").addClass("ui-corner-all");
      }
    }).data("ui-autocomplete")._renderItem = function(ul, item) {
            let txt = String(item.label).replace(new RegExp(this.term, "gi"),"<b>$&</b>");
            return $("<li></li>")
                  .data("ui-autocomplete-item", item.value)
                  .append("<a>" + txt + "</a>")
                  .appendTo(ul); };

    $scope.createGroup = function (name, email, description) {
        //prevent user executing service call multiple times
        //if true prevent, if false allow for execution of rest of code
        //ng-href='/groups'
        $log.log('createGroup::called', $scope.data);

        if ($scope.processing) {
            $log.log('createGroup::processing is true; exiting');
            return;
        }
        //flag to prevent multiple clicks until previous promise has resolved.
        $scope.processing = true;

        //data from user form values
        var payload =
            {
                'name': name,
                'email': email,
                'description': description,
                'members': [{id: $scope.profile.id}],
                'admins': [{id: $scope.profile.id}]
            };

        //create group success callback
        function success(response) {
            var alert = utilityService.success('Successfully Created Group: ' + name, response, 'createGroup::createGroup successful');
            $scope.alerts.push(alert);
            $scope.closeModal();
            $scope.reset();
            $scope.refresh();
            return response.data;
        }

        return groupsService.createGroup(payload)
            .then(success)
            .catch(function (error) {
                handleError(error, 'groupsService::createGroup-failure');
            });
    };

    $scope.allGroups = function () {
        $scope.ignoreAccess = true;
        $scope.refresh();
    }

    $scope.myGroups = function () {
        $scope.ignoreAccess = false;
        $scope.refresh();
    }

    $scope.refresh = function () {
        function success(result) {
            $log.log('getGroups:refresh-success', result);
            //update groups
            $scope.groups.items = result.groups;
            $scope.groupsLoaded = true;
            if (!$scope.query.length) {
                $scope.hasGroups = $scope.groups.items.length > 0;
            }
            return result;
        }

        getGroupsAbridged($scope.ignoreAccess)
            .then(success)
            .catch(function (error) {
                handleError(error, 'getGroups::refresh-failure');
            });
    };

    $scope.reset = function () {
        //reset processing flag
        $scope.processing = false;
        //fields with ng-patterns need to be set to null first then cleared
        $scope.createGroupForm.$commitViewValue();
        //this resets $scope.currentGroup object to empty object;
        angular.copy({}, $scope.currentGroup);
        //reset all validations & error messages to pre-form submission state
        $scope.createGroupForm.$setUntouched();
        $scope.createGroupForm.$setPristine();

        return true;
    };

    function getGroups() {
        function success(response) {
            $log.log('groupsService::getGroups-success');
            return response.data;
        }

        return groupsService
            .getGroups($scope.ignoreAccess, $scope.query)
            .then(success)
            .catch(function (error) {
                handleError(error, 'groupsService::getGroups-failure');
            });
    }

    function getGroupsAbridged() {
        function success(response) {
            $log.log('groupsService::getGroups-success');
            return response.data;
        }

        return groupsService
            .getGroupsAbridged($scope.ignoreAccess, $scope.query)
            .then(success)
            .catch(function (error) {
                handleError(error, 'groupsService::getGroups-failure');
            });
    }

    // Return true if there are no groups created by the user
    $scope.haveNoGroups = function (groupLength) {
        if (!$scope.hasGroups && !groupLength && $scope.groupsLoaded && $scope.query.length == "") {
            return true
        } else {
            return false
        }
    }

    // Return true if no groups are found related to the search query
    $scope.searchCriteria = function (groupLength) {
        if ($scope.groupsLoaded && !groupLength && $scope.query.length != "") {
            return true
        } else {
            return false
        }
    }

    $scope.editGroup = function (groupInfo) {
        $scope.currentGroup = groupInfo;
        $("#modal_edit_group").modal("show");
    };

    $scope.submitEditGroup = function (name, email, description) {
        //prevent user executing service call multiple times
        //if true prevent, if false allow for execution of rest of code
        //ng-href='/groups'
        $log.log('updateGroup::called', $scope.data);

        if ($scope.processing) {
            $log.log('updateGroup::processing is true; exiting');
            return;
        }
        //flag to prevent multiple clicks until previous promise has resolved.
        $scope.processing = true;

        //data from user form values
        var payload =
            {
                'id': $scope.currentGroup.id,
                'name': name,
                'email': email,
                'members': $scope.currentGroup.members,
                'admins': $scope.currentGroup.admins
            };

        if (description) {
            payload['description'] = description;
        }

        //update group success callback
        function success(response) {
            var alert = utilityService.success('Successfully Updated Group: ' + name, response, 'updateGroup::updateGroup successful');
            $scope.alerts.push(alert);
            $scope.closeEditModal();
            $scope.reset();
            $scope.refresh();
            return response.data;
        }

        return groupsService.updateGroup($scope.currentGroup.id, payload)
            .then(success)
            .catch(function (error) {
                handleError(error, 'groupsService::updateGroup-failure');
            });
    };

    $scope.confirmDeleteGroup = function (groupInfo) {
        $scope.currentGroup = groupInfo;
        $("#delete_group_modal").modal("show");
    };

    $scope.submitDeleteGroup = function () {
        function success(response) {
            $("#delete_group_modal").modal("hide");
            $scope.refresh();
            var alert = utilityService.success('Removed Group: ' + $scope.currentGroup.name, response, 'groupsService::deleteGroup successful');
            $scope.alerts.push(alert);
        }

        groupsService.deleteGroups($scope.currentGroup.id)
            .then(success)
            .catch(function (error) {
                handleError(error, 'groupsService::deleteGroup-failure');
            });
    };

    function profileSuccess(results) {
        //if data is provided
        if (results.data) {
            //update user profile data
            //make user profile available to page
            $scope.profile = results.data;
            $log.log($scope.profile);
            //load data in grid
            $scope.refresh();
        }
    }

    function profileFailure(results) {
        $scope.profile = $scope.profile || {};
    }

    $scope.groupAdmin = function (group) {
        var isAdmin = group.admins.find(function (x) {
            return x.id === $scope.profile.id;
        });
        var isSuper = $scope.profile.isSuper;
        return isAdmin || isSuper;
    }

    $scope.canSeeGroup = function (group) {
        var isMember = group.members.some(x => x.id === $scope.profile.id);
        var isSupport = $scope.profile.isSupport;
        var isSuper = $scope.profile.isSuper;
        return isMember || isSupport || isSuper;
    }

    //get user data on groups view load
    profileService.getAuthenticatedUserData()
        .then(profileSuccess, profileFailure)
        .catch(profileFailure);

});
