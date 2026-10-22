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

(function(window) {
    function escapeRegExp(value) {
        return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    }

    function renderItem(ul, item, term) {
        var label = String(item.label || "");
        var $item = $("<li></li>").data("ui-autocomplete-item", item.value);
        var $content = $("<div></div>");

        if (!term) {
            return $item.append($content.text(label)).appendTo(ul);
        }

        var matcher = new RegExp(escapeRegExp(String(term)), "gi");
        var lastIndex = 0;

        label.replace(matcher, function(match, offset) {
            if (offset > lastIndex) {
                $content.append(document.createTextNode(label.slice(lastIndex, offset)));
            }

            $("<b></b>").text(match).appendTo($content);
            lastIndex = offset + match.length;
            return match;
        });

        if (lastIndex === 0) {
            $content.text(label);
        } else if (lastIndex < label.length) {
            $content.append(document.createTextNode(label.slice(lastIndex)));
        }

        return $item.append($content).appendTo(ul);
    }

    function applyRenderer(autocomplete) {
        var instance = autocomplete.autocomplete("instance");
        if (instance) {
            instance._renderItem = function(ul, item) {
                return renderItem(ul, item, this.term);
            };
        }
    }

    window.vinyldnsAutocomplete = {
        applyRenderer: applyRenderer,
        renderItem: renderItem
    };
})(window);
