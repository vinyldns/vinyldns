/*
* Copyright 2018 Comcast Cable Communications Management, LLC
*
* Licensed under the Apache License, Version 2.0 (the \"License\");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an \"AS IS\" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
const $MENU_INDENT = $('#menu_indent'),
    $MENU_DEDENT = $('#menu_dedent'),
    $MENU_MOBILE = $('#menu_mobile'),
    $BODY = $('body'),
    $SIDEBAR_MENU = $('#sidebar-menu');

$MENU_INDENT.on('click', function() {
  $SIDEBAR_MENU.find('li.active-sm ul').show();
  $SIDEBAR_MENU.find('li.active-sm').addClass('active').removeClass('active-sm');

  $BODY.addClass('nav-md').removeClass('nav-sm');
});

$MENU_DEDENT.on('click', function() {
  $SIDEBAR_MENU.find('li.active ul').hide();
  $SIDEBAR_MENU.find('li.active').addClass('active-sm').removeClass('active');

  $BODY.addClass('nav-sm').removeClass('nav-md');
});

$MENU_MOBILE.on('click', function() {
  if ($BODY.hasClass('nav-md')) {
    $SIDEBAR_MENU.find('li.active ul').hide();
    $SIDEBAR_MENU.find('li.active').addClass('active-sm').removeClass('active');
  } else {
    $SIDEBAR_MENU.find('li.active-sm ul').show();
    $SIDEBAR_MENU.find('li.active-sm').addClass('active').removeClass('active-sm');
  }

  $BODY.toggleClass('nav-md nav-sm');
});
