angular.module('vinyldns', [
    'services.module',
    'controllers.module',
    'directives.module',
    'dns-change',
    'recordset'
])
    .config(function ($httpProvider, $animateProvider, $logProvider) {
        $httpProvider
            .defaults.transformResponse.push(function (responseData) {
                convertDateStringsToDates(responseData);
                return responseData;
            });
        $animateProvider
            .classNameFilter(/toshow/);
        // turning off $log. Change to true for local development and testing
        $logProvider.debugEnabled(false);
    })
    .controller('AppController', function ($scope, $timeout, profileService, utilityService) {
        document.body.style.cursor = 'default';
        $scope.alerts = [];

        $scope.regenerateCredentials = function() {
            document.body.style.cursor = 'wait';
            profileService.regenerateCredentials()
                .then(function(success) {
                    var alert = utilityService.success(success.data, success, 'profileService::regenerateCredentials-success');
                    document.body.style.cursor = 'default';
                    $("#mb-creds").modal('hide');
                    $scope.alerts.push(alert);
                    $timeout(function(){
                        location.reload();
                     }, 2000);
                })
                .catch(function(error){
                    var alert = utilityService.failure(error, 'profileService::regenerateCredentials-failure');
                    document.body.style.cursor = 'default';
                    $("#mb-creds").modal('hide');
                    location.reload();
                    $scope.alerts.push(alert);
                    $timeout(function(){
                        location.reload();
                     }, 2000);
                });
        };
    });

// Workaround for jQuery SECVULN (https://github.com/advisories/GHSA-gxr4-xjj5-5px2)
jQuery.htmlPrefilter = function( html ) {
    return html;
};

var regexIso8601 = /(\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d\.\d+([+-][0-2]\d:[0-5]\d|Z))|(\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d([+-][0-2]\d:[0-5]\d|Z))|(\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d([+-][0-2]\d:[0-5]\d|Z))/;
function convertDateStringsToDates(input) {
    if (typeof input !== "object") return input;

    for (var key in input) {
        if (!input.hasOwnProperty(key)) continue;

        var value = input[key];
        var match;
        if (typeof value === "string" && (match = value.match(regexIso8601))) {
            var milliseconds = Date.parse(match[0]);
            if (!isNaN(milliseconds)) {
                value = new Date(milliseconds);
                input[key] = value.toString();
                input[key] = input[key].substring(0, 24);
            }
        } else if (typeof value === "object") {
            convertDateStringsToDates(value);
        }
    }
}
