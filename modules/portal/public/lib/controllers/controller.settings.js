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

angular.module('controller.settings', [])
    .controller('SettingsController', function ($scope, $http, $location, $log, profileService,
                                             utilityService, $timeout) {


        const themeToggle = document.getElementById('toggle_checkbox')
        const themeToggleImg = document.getElementById('theme-img')
        var themeLightImg = "/assets/images/vinyldns-portal-day.png"
        var themeDarkImg = "/assets/images/vinyldns-portal-night.png"
        const themeToggleText = document.getElementById('theme-toggle-text')


        var userData = {};



        // Function to apply the dark theme
        function applyDarkTheme() {
          document.body.classList.add('dark-theme');
          themeToggleImg.src = themeDarkImg
          themeToggleText.innerHTML = "dark";
          sessionStorage.setItem('darkTheme', 'true');
        }

        // Function to remove the dark theme
        function removeDarkTheme() {
          document.body.classList.remove('dark-theme');
          themeToggleImg.src = themeLightImg
          themeToggleText.innerHTML = "light";
          sessionStorage.setItem('darkTheme', 'false');
        }

        const savedTheme = sessionStorage.getItem('darkTheme');
        if (savedTheme === 'true') {
          applyDarkTheme();
          themeToggle.checked = JSON.parse(savedTheme);
        }

        $scope.themeToggleClick = function(){
        $log.log("fsgfsdgfsdsdf")
        const isDarkTheme = document.body.classList.contains('dark-theme');
          if (isDarkTheme) {
            removeDarkTheme();
          } else {
            applyDarkTheme();
          }
        }

        /*themeToggle.addEventListener('click', () => {
          const isDarkTheme = document.body.classList.contains('dark-theme');
          if (isDarkTheme) {
            removeDarkTheme();
          } else {
            applyDarkTheme();
          }
        }, false)*/

        function profileSuccess(results) {
            //if data is provided
            if (results.data) {
                //update user profile data
                //make user profile available to page
                $scope.userData = results.data;
                $log.debug($scope.profile);
                //load data in grid
                $scope.refresh();
            }
        }

        function profileFailure(results) {
            $scope.userData = $scope.userData || {};
        }

        profileService.getAuthenticatedUserData()
                .then(profileSuccess, profileFailure)
                .catch(profileFailure);
});