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

angular.module('service.utility', [])
    .service('utilityService', function ($log) {

    function stripQuotes(str) {
        if (str[0] == '"' && str[str.length - 1] == '"') {
            return str.substring(1, str.length - 1);
        } else {
            return str;
        }
    }

    this.formatDateTime = function(timeStamp) {
        return new Date(timeStamp).toLocaleString('en-us',{month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName:'short'});
    }

    this.failure = function (error, type) {
        var msg = "HTTP " + error.status + " (" + error.statusText + "): ";
        if (typeof error.data == "object") {
            msg += error.data.errors.join("\n");
        } else {
            msg += stripQuotes(error.data);
        }

        $log.debug(type, error);
        return {
            type: "danger", content: msg
        };
    };

    this.success = function(message, response, type) {
        if (response && type) {
            var msg = "HTTP " + response.status + " (" + response.statusText + "): " + message;
            $log.debug(type, response);
            return {
                type: "success",
                content: msg
            };
        } else {
            return {
                type: "success",
                content: message
            };
        }
    };

    // Function to copy the ID to clipboard
    this.copyToClipboard = function(id) {
        if (navigator.clipboard) {
            navigator.clipboard.writeText(id)
                .then(() => {
                    console.log("Copied to clipboard successfully!");
                })
                .catch(err => {
                    console.error("Failed to copy: ", err);
                });
        } else {
            // Create a temporary input element to hold the ID
            var tempInput = document.createElement("input");
            tempInput.style.position = "absolute";
            tempInput.style.left = "-9999px";
            tempInput.value = id;
            document.body.appendChild(tempInput);
            // Select the input value and copy it
            tempInput.select();
            document.execCommand("copy");
            // Remove the temporary input
            document.body.removeChild(tempInput);
        }
    };



    this.urlBuilder = function (url, obj) {
        var result = [];
        for (var property in obj) {
            if (obj[property] !== undefined && obj[property] !== null) {
                result.push(encodeURIComponent(property) + '=' + encodeURIComponent(obj[property]));
            }
        }
        var params = result.join('&');
        url = (params) ? url + '?' + params : url;
        return url;
    };

    this.getCsrfHeader = function () {
        var csrfElement = document.getElementById("csrf");
        if (csrfElement) {
            return {"Csrf-Token": csrfElement.getAttribute("content")} || null;
        } else return {};
    }
});
