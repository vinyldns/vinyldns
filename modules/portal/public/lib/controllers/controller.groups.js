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

angular.module('controller.groups', []).controller('GroupsController', function ($scope, $log, $location, groupsService, profileService, utilityService, pagingService, $timeout) {
    //registering bootstrap modal close event to refresh data after create group action
    angular.element('#modal_new_group').one('hide.bs.modal', function () {
        $scope.closeModal();
    });

    $scope.groups = {items: []};
    $scope.allGroup = {items: []};
    $scope.groupsLoaded = false;
    $scope.allGroupsLoaded = false;
    $scope.isSearchByUser = false;
    $scope.alerts = [];
    $scope.ignoreAccess = false;
    $scope.hasGroups = false;
    $scope.query = "";
    $scope.validEmailDomains= [];
    $scope.maxGroupItemsDisplay = 3000;

    // Paging status for group sets
    var groupsPaging = pagingService.getNewPagingParams(100);
    var allGroupsPaging = pagingService.getNewPagingParams(100);

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
        $scope.validDomains();
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
          url: "/api/groups?maxItems=100&abridged=true",
          dataType: "json",
          data: {groupNameFilter: request.term, ignoreAccess: $scope.ignoreAccess},
          success: function(data) {
              const search =  JSON.parse(JSON.stringify(data));
              response($.map(search.groups, function(group) {
              return {value: group.name, label: group.name}
              }))
          }
        });
      },
      minLength: 1,
      select: function (event, ui) {
          $scope.query = ui.item.value;
          $("#group-search-text").val(ui.item.value);
          return false;
        },
      open: function() {
        $(this).removeClass("ui-corner-all").addClass("ui-corner-top");
      },
      close: function() {
        $(this).removeClass("ui-corner-top").addClass("ui-corner-all");
      }
    });

    // Autocomplete text-highlight
    $.ui.autocomplete.prototype._renderItem = function(ul, item) {
            let txt = String(item.label).replace(new RegExp(this.term, "gi"),"<b>$&</b>");
            return $("<li></li>")
                  .data("ui-autocomplete-item", item.value)
                  .append("<div>" + txt + "</div>")
                  .appendTo(ul);
    };

    $scope.createGroup = function (name, email, description) {
        //prevent user executing service call multiple times
        //if true prevent, if false allow for execution of rest of code
        //ng-href='/groups'
        $log.debug('createGroup::called', $scope.data);

        if ($scope.processing) {
            $log.debug('createGroup::processing is true; exiting');
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

    $scope.refresh = function () {
        groupsPaging = pagingService.resetPaging(groupsPaging);
        allGroupsPaging = pagingService.resetPaging(allGroupsPaging);
        const groupsSearchByUser = [];
        if($scope.isSearchByUser){
            if($scope.query.endsWith("%")){
                $scope.query = str.substring(0, str.length - 1);
            }
            profileService.getUserDataById($scope.query)
            .then(function (result) {
                  var groupIds = result.data.groupIds
                  groupIds.forEach((groupId) => {
                    groupsService.getGroup(groupId)
                        .then(function (result) {
                            groupsSearchByUser.push(result.data);
                         })
                  });
            $log.debug('getGroupsByUser:refresh-success', groupsSearchByUser);
            updateAllGroupDisplay(groupsSearchByUser)
            })
            .catch(function (error) {
                handleError(error, 'getGroupsByUser::refresh-failure');
            });
        }
        else {
            groupsService
                .getGroupsAbridged(groupsPaging.maxItems, undefined, false, $scope.query)
                .then(function (result) {
                      $log.debug('getGroups:refresh-success', result);
                      //update groups
                      groupsPaging.next = result.data.nextId;
                      updateGroupDisplay(result.data.groups);
                      if (!$scope.query.length) {
                          $scope.hasGroups = $scope.groups.items.length > 0;
                      }
                })
                .catch(function (error) {
                    handleError(error, 'getGroups::refresh-failure');
                });

            groupsService
                .getGroupsAbridged(allGroupsPaging.maxItems, undefined, true, $scope.query)
                .then(function (result) {
                    $log.debug('getGroups:refresh-success', result);
                    //update groups
                    allGroupsPaging.next = result.data.nextId;
                    updateAllGroupDisplay(result.data.groups);
                })
                .catch(function (error) {
                    handleError(error, 'getGroups::refresh-failure');
                });
        }
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
            $log.debug('groupsService::getGroups-success');
            return response.data;
        }

        return groupsService
            .getGroups($scope.ignoreAccess, $scope.query, $scope.maxGroupItemsDisplay)
            .then(success)
            .catch(function (error) {
                handleError(error, 'groupsService::getGroups-failure');
            });
  }

    //Function for fetching list of valid domains
     $scope.validDomains=function getValidEmailDomains() {
            function success(response) {
                 $log.debug('groupsService::listEmailDomains-success', response);
                 $scope.validEmailDomains = response.data;
                 return $scope.validEmailDomains
            }
            return groupsService
                .listEmailDomains($scope.ignoreAccess, $scope.query)
                .then(success)
                .catch(function (error) {
                    handleError(error, 'groupsService::listEmailDomains-failure');
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
        if (!groupLength && $scope.query.length != "") {
            return true
        } else {
            return false
        }
    }

    $scope.editGroup = function (groupInfo) {
        $scope.currentGroup = groupInfo;
        $scope.initialGroup = {
            name: $scope.currentGroup.name,
            email: $scope.currentGroup.email,
            description: $scope.currentGroup.description
        };
        $scope.hasChanges = false;
        $scope.validDomains();
        $("#modal_edit_group").modal("show");
    };

    // Function to check for changes
    $scope.checkForChanges = function() {
        $scope.hasChanges =
            $scope.currentGroup.name !== $scope.initialGroup.name ||
            $scope.currentGroup.email !== $scope.initialGroup.email ||
            ($scope.currentGroup.description !== $scope.initialGroup.description &&
            !($scope.currentGroup.description === "" && $scope.initialGroup.description === undefined));
    };

    $scope.getGroupAndUpdate = function(groupId, name, email, description) {
        function success(response) {
            $log.debug('groupsService::getGroup-success');
            $scope.currentGroup = response.data;

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
            return groupsService.updateGroup(groupId, payload)
                .then(success)
                .catch(function (error) {
                    $scope.closeEditModal();
                    handleError(error, 'groupsService::updateGroup-failure');
                });
        }

        return groupsService
            .getGroup(groupId)
            .then(success)
            .catch(function (error) {
                handleError(error, 'groupsService::getGroup-failure');
            });
    };

    $scope.submitEditGroup = function (name, email, description) {
        //prevent user executing service call multiple times
        //if true prevent, if false allow for execution of rest of code
        //ng-href='/groups'
        if ($scope.processing) {
            $log.debug('updateGroup::processing is true; exiting');
            return;
        }

        //flag to prevent multiple clicks until previous promise has resolved.
        $scope.processing = true;
        $scope.getGroupAndUpdate($scope.currentGroup.id, name, email, description);
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
            $log.debug($scope.profile);
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

    function updateGroupDisplay (groups) {
        $scope.groups.items = groups;
        $scope.groupsLoaded = true;
        $log.debug("Displaying my groups: ", $scope.groups.items);
        if($scope.groups.items.length > 0) {
            $("td.dataTables_empty").hide();
        } else {
            $("td.dataTables_empty").show();
        }
    }

    function updateAllGroupDisplay (groups) {
        $scope.allGroup.items = groups;
        $scope.allGroupsLoaded = true;
        $log.debug("Displaying all groups: ", $scope.allGroup.items);
        if($scope.allGroup.items.length > 0) {
            $("td.dataTables_empty").hide();
        } else {
            $("td.dataTables_empty").show();
        }
    }

    /*
     * Group set paging
     */
     $scope.getGroupsPageNumber = function(tab) {
         switch(tab) {
             case 'myGroups':
                 return pagingService.getPanelTitle(groupsPaging);
             case 'allGroups':
                 return pagingService.getPanelTitle(allGroupsPaging);
         }
     };

     $scope.prevPageEnabled = function(tab) {
        switch(tab) {
            case 'myGroups':
                return pagingService.prevPageEnabled(groupsPaging);
            case 'allGroups':
                return pagingService.prevPageEnabled(allGroupsPaging);
        }
    };

    $scope.nextPageEnabled = function(tab) {
        switch(tab) {
            case 'myGroups':
                return pagingService.nextPageEnabled(groupsPaging);
            case 'allGroups':
                return pagingService.nextPageEnabled(allGroupsPaging);
        }
    };

    $scope.prevPageMyGroups = function() {
        var startFrom = pagingService.getPrevStartFrom(groupsPaging);
        return groupsService
                    .getGroupsAbridged(groupsPaging.maxItems, startFrom, false, $scope.query)
                    .then(function(response) {
                        groupsPaging = pagingService.prevPageUpdate(response.data.nextId, groupsPaging);
                        updateGroupDisplay(response.data.groups);
                    })
                    .catch(function (error) {
                        handleError(error,'groupsService::prevPageMyGroups-failure');
                    });
    }

    $scope.prevPageAllGroups = function() {
        var startFrom = pagingService.getPrevStartFrom(allGroupsPaging);
        return groupsService
                    .getGroupsAbridged(allGroupsPaging.maxItems, startFrom, true, $scope.query)
                    .then(function(response) {
                        allGroupsPaging = pagingService.prevPageUpdate(response.data.nextId, allGroupsPaging);
                        updateAllGroupDisplay(response.data.groups);
                    })
                    .catch(function (error) {
                        handleError(error,'groupsService::prevPageAllGroups-failure');
                    });
    }

    $scope.nextPageMyGroups = function () {
        return groupsService
                    .getGroupsAbridged(groupsPaging.maxItems, groupsPaging.next, false, $scope.query)
                    .then(function(response) {
                        var groupSets = response.data.groups;
                        groupsPaging = pagingService.nextPageUpdate(groupSets, response.data.nextId, groupsPaging);

                        if (groupSets.length > 0) {
                            updateGroupDisplay(response.data.groups);
                        }
                    })
                    .catch(function (error) {
                       handleError(error,'groupsService::nextPageMyGroups-failure')
                    });
    };

    $scope.nextPageAllGroups = function () {
        return groupsService
                    .getGroupsAbridged(allGroupsPaging.maxItems, allGroupsPaging.next, true, $scope.query)
                    .then(function(response) {
                        var groupSets = response.data.groups;
                        allGroupsPaging = pagingService.nextPageUpdate(groupSets, response.data.nextId, allGroupsPaging);

                        if (groupSets.length > 0) {
                            updateAllGroupDisplay(response.data.groups);
                        }
                    })
                    .catch(function (error) {
                       handleError(error,'groupsService::nextPageAllGroups-failure')
                    });
    };

    $timeout($scope.refresh, 0);
});
