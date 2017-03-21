var zoomCnt = -1;
var mapReady = false;
var map = null;
var camMarker = null;
var camAccuracy = null;
var locationTimer = null;
var lastLocationUpdate = 0;
var autoCenter = false;

function setCookie(cookieName, cookieValue, expdays) {
	"use strict";
    var d = new Date();
    d.setTime(d.getTime() + (expdays * 24 * 60 * 60 * 1000));
    var expires = "expires=" + d.toUTCString();
    document.cookie = cookieName + "=" + cookieValue + "; " + expires;
}

function getCookie(cookieName) {
	"use strict";
    var name = cookieName + "=";
    var ca = document.cookie.split(';');
    for(var i = 0; i < ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0) === ' ') { c = c.substring(1); }
        if (c.indexOf(name) === 0) { return c.substring(name.length, c.length); }
    }
    return "";
}

function setImageSize() {
	"use strict";
	var mjpeg = document.getElementById("mjpeg");
	if (mjpeg.width / mjpeg.height > 540 / 360) {
		mjpeg.style.height = "100%";
		mjpeg.style.width = "auto";
	} else {
		mjpeg.style.width = "100%";
		mjpeg.style.height = "auto";
	}
}

function getStreams() {
	"use strict";
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
		if (xhttp.readyState === 4) {
			var wifimjpeg = document.getElementById("wifimjpeg");
			var wifih264 = document.getElementById("wifih264");
			if (xhttp.status === 200) {
				var json = JSON.parse(xhttp.responseText);
				var mjpeg_streams = 0;
				var mjpeg_clients = "Clients:";
				var h264_streams = 0;
				var h264_clients = "Clients:";
				var connections = json.connections;
				for (var i = 0; i < connections.length; i++) {
					if (connections[i].MJPEG_stream) {
						mjpeg_streams++;
						mjpeg_clients += "\n" + connections[i].client_address;
					}
					if (connections[i].H264_stream) {
						h264_streams++;
						h264_clients += "\n" + connections[i].client_address;
					}
				}
				wifimjpeg.src = mjpeg_streams > 0 ? "images/wifi_mjpeg.png" : "images/wifi_none.png";
				wifimjpeg.title = mjpeg_streams > 0 ? mjpeg_clients : "";
				wifimjpeg.style.visibility = "visible";
				wifih264.src = h264_streams > 0 ? "images/wifi_h264.png" : "images/wifi_none.png";
				wifih264.title = h264_streams > 0 ? h264_clients : "";
				wifih264.style.visibility = "visible";
				setTimeout(getStreams, 2500);
			} else {
				wifimjpeg.style.visibility = "hidden";
				wifih264.style.visibility = "hidden";
				setTimeout(getStreams, 5000);
			}
		}
	};
	xhttp.open("GET", "status", true);
	xhttp.send();
}

function getSensors() {
	"use strict";
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
		if (xhttp.readyState === 4) {
			var battery = document.getElementById("battery");
			var torch = document.getElementById("torch");
			if (xhttp.status === 200) {
				var json = JSON.parse(xhttp.responseText);
				var connection = json.battery.connection;
				var level = json.battery.level;
				var perc = "000" + Math.round(level / 20) * 20;
				perc = perc.slice(-3);
				var image = "images/battery_";
				image += (connection === "unplugged" ? "discharging" : "charging");
				image += "_" + perc + ".png";
				battery.src = image;
				battery.title = "Battery level " + Math.round(level) + "%";
				battery.style.visibility = "visible";
				var torchState = json.torch;
				torch.src = torchState ? "images/torch_on.png" : "images/torch_off.png";
				torch.title = "Torch " + (torchState ? "on" : "off");
				torch.style.visibility = "visible";
				setTimeout(getSensors, 2500);
			} else {
				battery.style.visibility = "hidden";
				torch.style.visibility = "hidden";
				setTimeout(getSensors, 5000);
			}
		}
	};
	xhttp.open("GET", "sensors", true);
	xhttp.send();
}

function getLocation() {
	"use strict";
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
		if (xhttp.readyState === 4) {
			if (camMarker !== null && camAccuracy !== null) {
				if (xhttp.status === 200) {
					var json = JSON.parse(xhttp.responseText);
					if (json.location.accuracy !== -1) {
						var camLocation = {lat: json.location.latitude, lng: json.location.longitude};
						var locationUpdate = json.location.time;
						if (locationUpdate > lastLocationUpdate) {
							if (autoCenter) {
								map.setCenter(camLocation);
							}
							lastLocationUpdate = locationUpdate;
						}
						camMarker.setPosition(camLocation);
						camAccuracy.setCenter(camLocation);
						camAccuracy.setRadius(json.location.accuracy);
						camAccuracy.setOptions({
							strokeColor: '#00FF00',
							fillColor: '#00FF00',
						});
						locationTimer = setTimeout(getLocation, 5000);
					} else {
						camAccuracy.setOptions({
							strokeColor: '#FF0000',
							fillColor: '#FF0000',
						});
						locationTimer = setTimeout(getLocation, 20000);
					}
				} else {
					camAccuracy.setOptions({
						strokeColor: '#FF0000',
						fillColor: '#FF0000',
					});
					locationTimer = setTimeout(getLocation, 30000);
				}
			}
		}
	};
	xhttp.open("GET", "status", true);
	xhttp.setRequestHeader("GPS-mode", "fine");
	xhttp.send();
}

function h264URL() {
	"use strict";
	document.write("rtsp://" + window.location.host + "/video/h264");
}

function toggleTorch() {
	"use strict";
	var xhttp = new XMLHttpRequest();
	xhttp.open("POST", "control", true);
	xhttp.send("torch=toggle");
}

function startAutoFocus() {
	"use strict";
	var xhttp = new XMLHttpRequest();
	xhttp.open("POST", "control", true);
	xhttp.send("autofocus=start");
}

function zoomIn() {
	"use strict";
	if (zoomCnt !== -1) {
		var xhttp = new XMLHttpRequest();
		xhttp.open("POST", "control", true);
		xhttp.send("zoom=+20");
		setTimeout(zoomIn, zoomCnt++ === 0 ? 500 : 150);
	}
}

function zoomOut() {
	"use strict";
	if (zoomCnt !== -1) {
		var xhttp = new XMLHttpRequest();
		xhttp.open("POST", "control", true);
		xhttp.send("zoom=-20");
		setTimeout(zoomOut, zoomCnt++ === 0 ? 500 : 150);
	}
}

function startZoomIn() {
	"use strict";
	zoomCnt = 0;
	zoomIn();
}

function startZoomOut() {
	"use strict";
	zoomCnt = 0;
	zoomOut();
}

function stopZoom() {
	"use strict";
	zoomCnt = -1;
}

function enterFullScreen() {
	"use strict";
	var mjpeg = document.getElementById("mjpeg");
	var requestMethod = mjpeg.requestFullScreen || mjpeg.webkitRequestFullScreen || mjpeg.mozRequestFullScreen || mjpeg.msRequestFullscreen;
	if (requestMethod) {
		requestMethod.call(mjpeg);
	}
}

function initMap() {
	"use strict";
	mapReady = true;
}

function navigate(dst) {
	"use strict";
	if (locationTimer !== null) {
		clearTimeout(locationTimer);
		locationTimer = null;
	}
	map = null;
	camMarker = null;
	camAccuracy = null;
	switch (dst) {
		case "live":
			document.getElementById("live").className = "active_menu_item";
			document.getElementById("map").className = "other_menu_item";
			document.getElementById("sensors").className = "other_menu_item";
			document.getElementById("image").innerHTML = 
				"<img id=\"mjpeg\" alt=\"MJPEG\" src=\"video/mjpeg\" onload=\"setImageSize()\"/>" +
          		"<div class=\"topright\">" +
					"<img class=\"opacity\" alt=\"FULLSCREEN\" src=\"images/fullscreen.png\"" +
					"onclick=\"enterFullScreen()\"/>" +
				"</div>" +
				"<div class=\"bottomright\">" +
					"<img class=\"opacity\" alt=\"TORCH\" src=\"images/torch.png\"" +
					"onclick=\"toggleTorch()\"/>" +
					"<img class=\"opacity\" alt=\"FOCUS\" src=\"images/focus.png\"" +
					"onclick=\"startAutoFocus()\"/>" +
					"<img class=\"opacity\" alt=\"ZOOM-\" src=\"images/zoom_out.png\"" +
					"onmousedown=\"startZoomOut()\" onmouseup=\"stopZoom()\" onmouseleave=\"stopZoom()\"/>" +
					"<img class=\"opacity\" alt=\"ZOOM+\" src=\"images/zoom_in.png\"" +
					"onmousedown=\"startZoomIn()\" onmouseup=\"stopZoom()\" onmouseleave=\"stopZoom()\"/>" +
				"</div>";
			break;
		case "map":
			if (mapReady) {
				document.getElementById("live").className = "other_menu_item";
				document.getElementById("map").className = "active_menu_item";
				document.getElementById("sensors").className = "other_menu_item";
				document.getElementById("image").innerHTML = "<div id=\"gmap\"></div>";
				var xhttp = new XMLHttpRequest();
				xhttp.onreadystatechange = function() {
					if (xhttp.readyState === 4) {
						var mapDiv = document.getElementById('gmap');
						if (xhttp.status === 200) {
							var json = JSON.parse(xhttp.responseText);
							if (json.location.accuracy !== -1) {
								var camLocation = {lat: json.location.latitude, lng: json.location.longitude};
								lastLocationUpdate = json.location.time;
								var mapType = getCookie('mapTypeId'); 
								if (mapType === "") { mapType = google.maps.MapTypeId.ROADMAP; }
								var mapZoom = getCookie('mapZoom'); 
								if (mapZoom === "") { mapZoom = '15'; }
								autoCenter = true;
								map = new google.maps.Map(mapDiv, {
									mapTypeId : mapType, 
									center: camLocation,
									zoom: parseInt(mapZoom),
									fullscreenControl: true
								});
								map.addListener('maptypeid_changed', function() {
									setCookie('mapTypeId', map.getMapTypeId(), 365 * 10);
								});
								map.addListener('zoom_changed', function() {
									setCookie('mapZoom', map.getZoom(), 365 * 10);
								});
								map.addListener('dragstart', function() {
									autoCenter = false;
  								});
								camMarker = new google.maps.Marker({
									map: map,
									position: camLocation,
									icon: {url: 'images/favicon.png', anchor: new google.maps.Point(16, 16)},
									title: 'spyNet Camera'
								});
								camMarker.addListener('click', function() {
									map.setCenter(camMarker.getPosition());
									autoCenter = true;
  								});
								camMarker.addListener('dblclick', function() {
									map.setZoom(18);
									map.setCenter(camMarker.getPosition());
									autoCenter = true;
  								});
								camAccuracy = new google.maps.Circle({
									map: map,
									center: camLocation,
									radius: json.location.accuracy,
									strokeColor: '#00FF00',
									strokeOpacity: 0.3,
									strokeWeight: 1,
									fillColor: '#00FF00',
									fillOpacity: 0.2
								});
								locationTimer = setTimeout(getLocation, 5000);
							} else {
								mapDiv.innerHTML = 
									"<div class=\"warning\">" +
									"Data not available.<br>" +
									"The location service is not enabled or not yet initialized." +
									"</div>";
							}
						}
					}
				};
				xhttp.open("GET", "status", true);
				xhttp.setRequestHeader("GPS-mode", "fine");
				xhttp.send();
			}
			break;
		case "sensors":
			document.getElementById("live").className = "other_menu_item";
			document.getElementById("map").className = "other_menu_item";
			document.getElementById("sensors").className = "active_menu_item";
			document.getElementById("image").innerHTML = "SENSORS";
			break;
	}
}
