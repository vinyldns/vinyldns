var CURRENT_URL = window.location.href.split('#')[0].split('?')[0],
    $MENU_INDENT = $('#menu_indent'),
    $MENU_DEDENT = $('#menu_dedent'),
    $MENU_MOBILE = $('#menu_mobile'),
    $BODY = $('body'),
    $SIDEBAR_MENU = $('#sidebar-menu');

$MENU_INDENT.on('click', function() {
	console.log('clicked - menu indent');
  console.log("HEEEEEEY!")

	$SIDEBAR_MENU.find('li.active-sm ul').show();
	$SIDEBAR_MENU.find('li.active-sm').addClass('active').removeClass('active-sm');


	$BODY.addClass('nav-md').removeClass('nav-sm');
});

$MENU_DEDENT.on('click', function() {
	console.log('clicked - menu dedent');
  console.log("HEEEEEEY!")

	$SIDEBAR_MENU.find('li.active ul').hide();
	$SIDEBAR_MENU.find('li.active').addClass('active-sm').removeClass('active');

	$BODY.addClass('nav-sm').removeClass('nav-md');
});

$MENU_MOBILE.on('click', function() {
	console.log('clicked - menu indent');
  console.log("HEEEEEEY!")

  if ($BODY.hasClass('nav-md')) {
    $SIDEBAR_MENU.find('li.active ul').hide();
    $SIDEBAR_MENU.find('li.active').addClass('active-sm').removeClass('active');
  } else {
    $SIDEBAR_MENU.find('li.active-sm ul').show();
    $SIDEBAR_MENU.find('li.active-sm').addClass('active').removeClass('active-sm');
  }

  $BODY.toggleClass('nav-md nav-sm');
});
