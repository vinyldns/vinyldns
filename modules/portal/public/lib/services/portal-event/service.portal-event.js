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

angular.module('service.portalEvent')
    .service('portalEventService', function($http, $log, $q, utilityService) {
        function sendEvent(url, event) {
            try {
            return $http.post(url, event, {headers: utilityService.getCsrfHeader()})
                    .catch(function() {
                        // Event delivery must never change the result of the user operation.
                        $log.debug('portalEventService: event delivery failed');
                        return null;
                    });
            } catch (error) {
                // Also guard against synchronous request setup failures.
                $log.debug('portalEventService: event delivery could not be started');
                return $q.when(null);
            }
        }

        function track(event) {
            event = event || {};
            return sendEvent('/api/portal-events', {
                feature: event.feature,
                action: event.action,
                status: event.status,
                entityType: event.entityType || null,
                durationMs: event.durationMs == null ? null : event.durationMs,
                errorCode: event.errorCode || null,
                errorMessage: event.errorMessage || null
            });
        }

        function trackExport(event) {
            event = event || {};
            return sendEvent('/api/export-events', {
                type: event.type,
                zoneId: event.zoneId || null,
                recordTypes: event.recordTypes || null,
                query: event.query || null,
                rows: event.rows
            });
        }

        function getErrorMessage(error) {
            var data = error && error.data;
            var message;
            if (typeof data === 'string') {
                message = data;
            } else if (data && typeof data.message === 'string') {
                message = data.message;
            } else if (data && typeof data.errors === 'string') {
                message = data.errors;
            }
            return typeof message === 'string'
                ? message.replace(/[\x00-\x1F\x7F]/g, ' ').trim().slice(0, 512) || null
                : null;
        }

        function trackOperation(event, operation) {
            var startedAt = Date.now();
            return $q.when().then(operation).then(function(result) {
                track(angular.extend({}, event, {
                    status: 'success',
                    durationMs: Date.now() - startedAt
                }));
                return result;
            }, function(error) {
                var suppliedCode = error && error.code;
                var errorCode = typeof suppliedCode === 'string' && /^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(suppliedCode)
                    ? suppliedCode
                    : 'OPERATION_FAILED';
                track(angular.extend({}, event, {
                    status: 'failure',
                    durationMs: Date.now() - startedAt,
                    errorCode: errorCode,
                    errorMessage: getErrorMessage(error)
                }));
                return $q.reject(error);
            });
        }

        this.track = track;
        this.trackOperation = trackOperation;
        this.trackExport = trackExport;
    });
