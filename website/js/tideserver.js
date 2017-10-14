"use strict";

var noaa_stations = null;
var map = null;

function resizeMap() {
    // Resize the map window so it fills the entire client frame.
    var h = window.innerHeight - $("#navbar").height();
    $("#map").height(h);
}

$(window).resize(resizeMap);

function initMap() {
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
            function(position) {
                map.setCenter({
                    lat: position.coords.latitude,
                    lng: position.coords.longitude
                });
            });
    }

    jQuery.getJSON("/stations/noaa-stations.json", loadNOAAStations);
}

function loadNOAAStations(stations) {
    noaa_stations = stations;

    for (var i in stations) {
        var station = stations[i];
        var location = station["metadata"]["location"];
        var lat = location["lat"];
        var lng = location["long"];

        if (typeof(lat) === "string" && typeof(lng) == "string") {
            var marker = new google.maps.Marker({
                position: {
                    lat: parseFloat(lat),
                    lng: parseFloat(lng)
                },
                map: map,
                title: station["StationName"],
                stationId: station["StationId"]
            });

            marker.addListener("click", onStationClick);
        }
    }
}

function onStationClick() {
    console.log("clicked: this=" + this);
    for (var prop in this) {
        if (this.hasOwnProperty(prop)) {
            console.log("   property " + prop + " = " + this[prop]);
        }
    }
}

function initStationGraph() {
    var searchParams = new URLSearchParams(
        document.location.search.substring(1));
    var auth = searchParams.get("authority");
    var stationId = searchParams.get("stationId");
    var stationName = searchParams.get("stationName");
    var lat = searchParams.get("lat");
    var lng = searchParams.get("lng");
    var harmonicsUrl;

    console.log("auth=" + auth + " stationId=" + stationId);

    if (auth && stationId) {
        if (auth === "noaa") {
            harmonicsUrl = "/harmonics/noaa/" + stationId + ".json";
            jQuery.getJSON(harmonicsUrl, function(data) {
                loadNOAAStationHarmonics(
                    stationId, stationName, lat, lng, data);
            });
        }
    }
}

function loadNOAAStationHarmonics(stationId, stationName, lat, lng, data) {
    var svg = $("#chart");

    
}
