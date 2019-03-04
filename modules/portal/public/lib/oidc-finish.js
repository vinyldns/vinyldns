window.setTimeout(function() {
    var element = document.getElementById("oidc");
    if (element != null) {
        window.location = element.getAttribute("content");
    }
}, 0);
