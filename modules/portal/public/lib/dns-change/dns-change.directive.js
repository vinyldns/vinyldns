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

(function () {
    var app = angular.module('dns-change');
    var FQDN_REGEX = /\./;
    var INVALID_FQDN_REGEX = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}\.?$/;
    var IPV4_REGEX = /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
    var IPV6_REGEX = new RegExp(['^(',
                                '([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|',
                                '([0-9a-fA-F]{1,4}:){1,7}:|',
                                '([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|',
                                '([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|',
                                '([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|',
                                '([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|',
                                '([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|',
                                '([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|',
                                '([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|',
                                '([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|',
                                '[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|',
                                ':((:[0-9a-fA-F]{1,4}){1,7}|:)|',
                                'fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|',
                                '::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|',
                                '(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|',
                                '([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])',
                                ')$'
    ].join(''));

    app.directive('fqdn', function () {
        return {
            require: 'ngModel',
            link: function (scope, elm, attrs, ctrl) {
                ctrl.$validators.fqdn = function (modelValue, viewValue) {
                    if (attrs.required || (viewValue !== undefined && viewValue.length > 0)) {
                       return FQDN_REGEX.test(viewValue);
                    }
                    return true;
                };
            }
        };
    });

    app.directive('invalidip', function () {
        return {
            require: 'ngModel',
            link: function (scope, elm, attrs, ctrl) {
                ctrl.$validators.invalidip = function (modelValue, viewValue) {
                    if (attrs.required || (viewValue !== undefined && viewValue.length > 0)) {
                       return !INVALID_FQDN_REGEX.test(viewValue)
                    }
                    return true;
                };
            }
        };
    });

    app.directive('ipv4', function () {
        return {
            require: 'ngModel',
            link: function (scope, elm, attrs, ctrl) {
                ctrl.$validators.ipv4 = function (modelValue, viewValue) {
                    if (attrs.required || (viewValue !== undefined && viewValue.length > 0)) {
                        return IPV4_REGEX.test(viewValue);
                    }
                    return true;
                }
            }
        };
    });

    app.directive('ipv6', function () {
        return {
            require: 'ngModel',
            link: function(scope, elm, attrs, ctrl) {
                ctrl.$validators.ipv6 = function (modelValue, viewValue) {
                    if (attrs.required || (viewValue !== undefined && viewValue.length > 0)) {
                        return IPV6_REGEX.test(viewValue);
                    }
                    return true;
                };
            }
        };
    });

    app.directive('batchChangeFile', function () {
        return {
            require: 'ngModel',
            link: function (scope, elm, attrs, ctrl) {
                elm.on('change', function (e) {
                    if (e.target.files.length > 0) {
                        ctrl.$setViewValue(e.target.files[0]);
                    }
                    ctrl.$setViewValue();
                });
            }
        };
    });
})();
