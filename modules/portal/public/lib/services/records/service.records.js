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

angular.module('service.records', [])
    .service('recordsService', function ($http, utilityService) {

        this.getRecordSets = function (id, limit, startFrom, query) {
            if (query == "") {
                query = null;
            }
            var params = {
                "maxItems": limit,
                "startFrom": startFrom,
                "recordNameFilter": query
            };
            var url = utilityService.urlBuilder("/api/zones/"+id+"/recordsets", params);
            return $http.get(url);
        };

        this.createRecordSet = function(id, payload) {
            return $http.post("/api/zones/"+id+"/recordsets", payload, {headers: utilityService.getCsrfHeader()});
        };

        this.updateRecordSet = function(id, recordId, payload) {
            return $http.put("/api/zones/"+id+"/recordsets/"+recordId, payload, {headers: utilityService.getCsrfHeader()});
        };

        this.delRecordSet = function (zid, rid) {
            return $http.delete("/api/zones/"+zid+"/recordsets/"+rid, {headers: utilityService.getCsrfHeader()});
        };

        this.getZone = function (zid) {
            return $http.get("/api/zones/"+zid);
        };

        this.syncZone = function (zid) {
            return $http.post("/api/zones/"+zid+"/sync", {}, {headers: utilityService.getCsrfHeader()});
        };

        this.listRecordSetChanges = function (zid, maxItems, startFrom) {
            var url = '/api/zones/' + zid + '/recordsetchanges';
            var params = {
                "maxItems": maxItems,
                "startFrom": startFrom
            };
            url = utilityService.urlBuilder(url, params);
            return $http.get(url);
        };

        /* remove . from the end of a given string (for zone name). returns empty string if no argument given */
        function removeTrailingDot(str) {
            if(typeof str === "undefined") {
                return "";
            }

            if (str.charAt(str.length - 1) == '.'){
                return str.slice(0, str.length - 1);
            }

            return str;
        }

        function isDotted(record, zoneName) {
            var canHaveDots = ['PTR', 'NS', 'SOA', 'SRV'];

            return canHaveDots.indexOf(record.type) == -1 &&
                record.name.indexOf(".") != -1 &&
                removeTrailingDot(record.name) != removeTrailingDot(zoneName)
        }

        function isApex(recordName, zoneName) {
            return removeTrailingDot(recordName) == removeTrailingDot(zoneName) || recordName == "@";
        }

        this.toDisplayRecord = function (record, zoneName) {
            var newRecord = angular.copy(record);
            newRecord.records = undefined;
            newRecord.isDotted = isDotted(record, zoneName);
            newRecord.canBeEdited = true;
            switch (record.type) {
                case 'A':
                    newRecord.aRecordData = [];
                    angular.forEach(record.records, function(aRecord) {
                        newRecord.aRecordData.push(aRecord.address);
                    });
                    newRecord.onlyFour = true;
                    break;
                case 'CNAME':
                    newRecord.cnameRecordData = record.records[0].cname;
                    break;
                case 'MX':
                    newRecord.mxItems = [];
                    angular.forEach(record.records, function(item) {
                        newRecord.mxItems.push(item);
                    });
                    newRecord.onlyFour = true;
                    break;
                case 'AAAA':
                    newRecord.aaaaRecordData = [];
                    angular.forEach(record.records, function(aaaaRecord) {
                        newRecord.aaaaRecordData.push(aaaaRecord.address);
                    });
                    newRecord.onlyFour = true;
                    break;
                case 'TXT':
                    newRecord.textRecordData = record.records[0].text;
                    break;
                case 'NS':
                    newRecord.nsRecordData = [];
                    angular.forEach(record.records, function(nsRecord) {
                        newRecord.nsRecordData.push(nsRecord.nsdname);
                    });
                    newRecord.onlyFour = true;
                    newRecord.canBeEdited = !isApex(record.name, zoneName)
                    break;
                case 'PTR':
                    newRecord.ptrRecordData = record.records[0].ptrdname;
                    break;
                case 'SOA':
                    newRecord.soaMName = record.records[0].mname;
                    newRecord.soaRName = record.records[0].rname;
                    newRecord.soaSerial = record.records[0].serial;
                    newRecord.soaRefresh = record.records[0].refresh;
                    newRecord.soaRetry = record.records[0].retry;
                    newRecord.soaExpire = record.records[0].expire;
                    newRecord.soaMinimum = record.records[0].minimum;
                    newRecord.canBeEdited = false;
                    break;
                case 'SRV':
                    newRecord.srvItems = [];
                    angular.forEach(record.records, function(item) {
                        newRecord.srvItems.push(item);
                    });
                    newRecord.onlyFour = true;
                    break;
                case 'SPF':
                    newRecord.spfRecordData = [];
                    angular.forEach(record.records, function(spfRecord) {
                        newRecord.spfRecordData.push(spfRecord.text);
                    });
                    newRecord.onlyFour = true;
                    break;
                case 'SSHFP':
                    newRecord.sshfpItems = [];
                    angular.forEach(record.records, function(item) {
                        newRecord.sshfpItems.push(item);
                    });
                    newRecord.onlyFour = true;
                    break;
                default:
            }
            return newRecord;
        };

        this.toVinylRecord = function (record) {
            var newRecord = {
                "id": record.id,
                "name": record.name,
                "type": record.type,
                "ttl": Number(record.ttl)
            };
            switch (record.type) {
                case 'A':
                    newRecord.records = [];
                    angular.forEach(record.aRecordData, function(address) {
                        newRecord.records.push({"address": address});
                    });
                    break;
                case 'CNAME':
                    newRecord.records = [{"cname": record.cnameRecordData}];
                    break;
                case 'MX':
                    newRecord.records = [];
                    angular.forEach(record.mxItems, function(record) {
                        newRecord.records.push({"preference": Number(record.preference), "exchange": record.exchange})
                    });
                    break;
                case 'AAAA':
                    newRecord.records = [];
                    angular.forEach(record.aaaaRecordData, function(address) {
                        newRecord.records.push({"address": address});
                    });
                    break;
                case 'TXT':
                    newRecord.records = [{"text": record.textRecordData}];
                    break;
                case 'PTR':
                    newRecord.records = [{"ptrdname": record.ptrRecordData}];
                    break;
                case 'SRV':
                    newRecord.records = [];
                    angular.forEach(record.srvItems, function(record) {
                        newRecord.records.push({"priority": Number(record.priority),
                            "weight": Number(record.weight),
                            "port": Number(record.port),
                            "target": record.target});
                    });
                    break;
                case 'SPF':
                    newRecord.records = [];
                    angular.forEach(record.spfRecordData, function(text) {
                        newRecord.records.push({"text": text});
                    });
                    break;
                case 'SSHFP':
                    newRecord.records = [];
                    angular.forEach(record.sshfpItems, function(record) {
                        newRecord.records.push({"algorithm": Number(record.algorithm), "type": Number(record.type),
                            "fingerprint": record.fingerprint})
                    });
                    break;
                case 'NS':
                    newRecord.records = [];
                    angular.forEach(record.nsRecordData, function(address) {
                      newRecord.records.push({"nsdname": address});
                    });
                    break;
                default:
            }
            return newRecord;
        };


    });
