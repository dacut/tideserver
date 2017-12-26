/*jslint browser: true*/
/*global $,google,jQuery,URLSearchParams*/

var noaaStations = null, map = null, tideserverBase = "https://tideserver.kanga.org/";
var currentStation = null;

function resizeMap() {
    "use strict";
    // Resize the map window so it fills the entire client frame.
    var h = window.innerHeight - $("#navbar").height();
    $("#map").height(h);
}

$(window).resize(resizeMap);

function onStationLoadData () {
    "use strict";
}

function onLoadPreliminaryData(data) {
    console.log("onLoadPreliminaryData: data=" + data);
}

function onLoadPredictedData(data) {
    console.log("onLoadPredictedData: data=" + data);
}

function toNOAADate(date) {
    return sprintf("%04d%02d%02d", date.getFullYear(), date.getMonth() + 1,
                   date.getDate());
}

function onStationClick() {
    "use strict";
    var lat, long, now, dates, date, i;
    currentStation = this;

    $(".map").removeClass("col-xs-12 col-sm-12").addClass("col-xs-0 col-sm-8");
    $(".station").addClass("col-xs-12 col-sm-4");

    lat = this.position.lat();
    long = this.position.lng();

    $("#station-name").text(this.title);
    $("#position").text(lat.toFixed(4) + " " + long.toFixed(4));

    // Get the NOAA dates for yesterday, today, and tomorrow.
    now = Date.now();
    dates = [toNOAADate(new Date(now - 86400000)), toNOAADate(new Date(now)), toNOAADate(new Date(now + 86400000))];

    for (i = 0; i < dates.length; ++i) {
        date = dates[i];
        jQuery.getJSON(tideserverBase + "station/" + this.stationId +
                       "/water-level/" + date + "/preliminary", onLoadPreliminaryData);
        jQuery.getJSON(tideserverBase + "station/" + this.stationId +
                       "/water-level/" + date + "/predicted", onLoadPredictedData);
    }

    return;
}

function loadNOAAStations(data) {
    "use strict";
    var i, station, location, lat, lng, marker;
    noaaStations = data.Stations;

    for (i = 0; i < noaaStations.length; ++i) {
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
