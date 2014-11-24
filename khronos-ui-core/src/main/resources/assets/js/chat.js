$(function () {
    "use strict";

    var header = $('#header');
    var content = $('#content');
    var input = $('#input');
    var status = $('#status');
    var myName = false;
    var author = null;
    var logged = false;
    var socket = jQuery.atmosphere;
    var subSocket;
    var transport = 'websocket';

    // We are now ready to cut the request
    var request = { url: '/ws/',
        contentType : "application/json",
        logLevel : 'debug',
        transport : transport ,
        trackMessageLength : true,
        reconnectInterval : 5000,
        maxStreamingLength : 1000 };


    request.onOpen = function(response) {
        content.html($('<p>', { text: 'Atmosphere connected using ' + response.transport }));
        input.removeAttr('disabled').focus();
        status.text('KVP to watch:');
        transport = response.transport;

        // Carry the UUID. This is required if you want to call subscribe(request) again.
        request.uuid = response.request.uuid;
    };

    request.onClientTimeout = function(r) {
        content.html($('<p>', { text: 'Client closed the connection after a timeout. Reconnecting in ' + request.reconnectInterval }));
        subSocket.push(JSON.stringify({ author: author, message: 'is inactive and closed the connection. Will reconnect in ' + request.reconnectInterval }));
        input.attr('disabled', 'disabled');
        setTimeout(function (){
            subSocket = socket.subscribe(request);
        }, request.reconnectInterval);
    };

    request.onReopen = function(response) {
        input.removeAttr('disabled').focus();
        content.html($('<p>', { text: 'Atmosphere re-connected using ' + response.transport }));
    };

    // For demonstration of how you can customize the fallbackTransport using the onTransportFailure function
    request.onTransportFailure = function(errorMsg, request) {
        jQuery.atmosphere.util.info(errorMsg);
        request.fallbackTransport = "long-polling";
        header.html($('<h3>', { text: 'Atmosphere Chat. Default transport is WebSocket, fallback is ' + request.fallbackTransport }));
    };

    request.onMessage = function (response) {

        var message = response.responseBody;
        try {
            var json = JSON.parse(message);
            console.log("Received: ", json);
        } catch (e) {
            console.log('This doesn\'t look like a valid JSON: ', message);
            console.log(e);
            return;
        }

        input.removeAttr('disabled').focus();
        if (!logged && myName) {
            logged = true;
            status.text(myName + ': ').css('color', 'blue');
        } else {
            var date = typeof(json.time) == 'string' ? parseInt(json.time) : json.time;
            addMessage(json.tsp.tm, json.tsp.value, 'black', new Date(date));
        }
    };

    request.onClose = function(response) {
        content.html($('<p>', { text: 'Server closed the connection after a timeout' }));
        input.attr('disabled', 'disabled');
    };

    request.onError = function(response) {
        content.html($('<p>', { text: 'Sorry, but there\'s some problem with your '
        + 'socket or the server is down' }));
        logged = false;
    };

    request.onReconnect = function(request, response) {
        content.html($('<p>', { text: 'Connection lost, trying to reconnect. Trying to reconnect ' + request.reconnectInterval}));
        input.attr('disabled', 'disabled');
    };

    subSocket = socket.subscribe(request);

    var cnt = 0;

    input.keydown(function(e) {
        if (e.keyCode === 13) {
            var msg = $(this).val();

            subSocket.push(JSON.stringify({ sid: cnt, keys: JSON.parse(msg) }));

            cnt += 1;

            $(this).val('');

            input.attr('disabled', 'disabled');
        }
    });

    function addMessage(author, message, color, datetime) {
        content.append('<p><span style="color:' + color + '">' + author + '</span> @ ' +
        + (datetime.getHours() < 10 ? '0' + datetime.getHours() : datetime.getHours()) + ':'
        + (datetime.getMinutes() < 10 ? '0' + datetime.getMinutes() : datetime.getMinutes())
        + ': ' + message + '</p>');
    }
});