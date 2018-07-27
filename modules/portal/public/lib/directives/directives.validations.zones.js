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

angular.module('directives.validations.zones.module', [])
    .directive('validateTtl', function () {
        //requires an isolated scope
        var minTTL = 30;
        return {
            //restrict to an attribute type
            restrict: 'A',
            //element must have ng-model attribute
            require: 'ngModel',
            link: function (scope, el, attrs, ctrl) {
                //add a parse that will process each time the value
                //is parsed into the model when the user updates it.

                ctrl.$parsers.unshift(function (value) {
                    //if a number then it's valid
                    //test and set the validity after update.
                    var valid = !Number.isNaN(value) && Number(value) >= 30;
                    ctrl.$setValidity('invalidTTL', valid);
                    // if it's valid, return the value to the model,
                    // otherwise return undefined

                    return valid ? value : undefined;
                });
            }
        }
    });
    
