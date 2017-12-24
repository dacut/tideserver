/*jslint browser: true*/
/*global $,google,jQuery,URLSearchParams*/

var noaaStations = null, map = null, tideserverBase = "https://tideserver.kanga.org/";

function resizeMap() {
    "use strict";
    // Resize the map window so it fills the entire client frame.
    var h = window.innerHeight - $("#navbar").height();
    $("#map").height(h);
}

$(window).resize(resizeMap);

function onStationClick() {
    "use strict";
    var prop;

    console.log("clicked: this=" + this);
    for (prop in this) {
        if (this.hasOwnProperty(prop)) {
            console.log("   property " + prop + " = " + this[prop]);
        }
    }
}

function loadNOAAStations(data) {
    "use strict";
    var i, station, location, lat, lng, marker;
    noaaStations = data.Stations;

    for (i in noaaStations) {
        station = noaaStations[i];
        location = station.metadata.location;
        lat = location.lat;
        lng = location.long;

        if (typeof lat === "string" && typeof lng === "string") {
            marker = new google.maps.Marker({
                position: {
                    lat: parseFloat(lat),
                    lng: parseFloat(lng)
                },
                map: map,
                title: station.name,
                icon: {
                    scaledSize: { width: 22, height: 40 },
                    size: { width: 22, height: 40 },
                    url: "/images/marker-blue_hdpi.png"
                },
                stationId: station.ID
            });

            marker.addListener("click", onStationClick);
        }
    }
}

function initMap() {
    "use strict";
    var defaultCenter = {lat: 47.6249, lng: -122.5210};

    resizeMap();

    map = new google.maps.Map(document.getElementById('map'), {
        zoom: 7,
        center: defaultCenter,
        streetViewControl: false,
        mapTypeId: "hybrid"
    });

    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
            function (position) {
                map.setCenter({
                    lat: position.coords.latitude,
                    lng: position.coords.longitude
                });
            }
        );
    }

    jQuery.getJSON(tideserverBase + "stations", loadNOAAStations);
}

/*jslint unparam: true*/
function loadNOAAStationHarmonics(stationId, stationName, lat, lng, data) {
    "use strict";
    var svg = $("#chart");
}
/*jslint unparam: false*/

function initStationGraph() {
    "use strict";
    var searchParams = new URLSearchParams(
        document.location.search.substring(1)),
        auth = searchParams.get("authority"),
        stationId = searchParams.get("stationId"),
        stationName = searchParams.get("stationName"),
        lat = searchParams.get("lat"),
        lng = searchParams.get("lng"),
        harmonicsUrl;

    console.log("auth=" + auth + " stationId=" + stationId);

    if (auth && stationId) {
        if (auth === "noaa") {
            harmonicsUrl = "/harmonics/noaa/" + stationId + ".json";
            jQuery.getJSON(harmonicsUrl, function (data) {
                loadNOAAStationHarmonics(stationId, stationName, lat, lng, data);
            });
        }
    }
}
