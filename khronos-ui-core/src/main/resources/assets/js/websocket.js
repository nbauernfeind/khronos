'use strict';

khronosApp.factory('WebSocket', ['$q', '$rootScope', function($q, $rootScope) {
    var Service = {
        isConnected: false
    };

    var socket;
    var callbacks = {};

    var currentCallbackId = 0;
    function getCallbackId() {
        currentCallbackId += 1;
        if(currentCallbackId > 65536) {
            currentCallbackId = 0;
        }
        return currentCallbackId;
    }

    var request = {
        url: '/ws/',
        contentType: 'application/json',
        logLevl: 'debug',
        transport: 'websocket',
        trackMessageLength: true,
        reconnectInterval: 5000,
        maxStreamingLength: 1000
    };

    var resetSocket = function () {
        socket = jQuery.atmosphere.subscribe(request);
    };

    request.onOpen = function(response) {
        console.log('WebSocket: Connected to ' + request.url + '. (' + response.transport + ')');
        Service.isConnected = true;
        request.uuid = response.request.uuid;

        for (var callback in callbacks) {
            if (callbacks.hasOwnProperty(callback)) {
                doSendRequest(callbacks[callback]);
            }
        }
    };

    request.onClientTimeout = function(response) {
        console.log('WebSocket: Client closed the connection after a timeout.');
        Service.isConnected = false;
        setTimeout(resetSocket);
    };

    request.onReopen = function(response) {
        console.log('WebSocket: Reconnected to ' + request.url + '. (' + response.transport + ')');
        Service.isConnected = true;

        for (var callback in callbacks) {
            if (callbacks.hasOwnProperty(callback)) {
                doSendRequest(callbacks[callback]);
            }
        }
    };

    request.onTransportFailure = function(errorMsg, request) {
        jQuery.atmosphere.util.info(errorMsg);
        request.fallbackTransport = 'long-polling';
        console.log('WebSocket: Falling back to long-polling.');
    };

    request.onClose = function(response) {
        console.log('WebSocket: Server closed the connection.');
        Service.isConnected = false;
    };

    request.onError = function(response) {
        console.log('WebSocket: Unknown error occurred.');
        console.log(response);
        Service.isConnected = false;
    };

    request.onReconnect = function(request, response) {
        console.log('WebSocket: Connection lost, trying to reconnect.');
        Service.isConnected = false;
    };

    request.onMessage = function(message) {
        var obj = JSON.parse(message.responseBody);
        if (callbacks.hasOwnProperty(obj.callbackId)) {
            var callback = callbacks[obj.callbackId];
            callback.scope.$apply(callback.cb(obj.data));

            var recurring = callback.request.recurring;
            if (recurring === undefined || recurring === false) {
                delete callbacks[obj.callbackId];
            }
        }
    };

    function doSendRequest(callback) {
        var request = callback.request;
        socket.push(JSON.stringify(request));

        if (request.recurring === true) {
            callback.scope.$on("$destroy", function () {
                delete callbacks[request.callbackId];
                var cancel = {
                    type: 'cancel',
                    callbackId: request.callbackId
                };
                socket.push(JSON.stringify(cancel));
            });
        }
    }

    Service.sendRecurringRequest = function($scope, request, callback) {
        request.callbackId = getCallbackId();
        request.recurring = true;

        callbacks[request.callbackId] = {
            scope: $scope,
            time: new Date(),
            request: request,
            cb: callback
        };

        if (Service.isConnected) {
            doSendRequest(callbacks[request.callbackId]);
        }
    };

    Service.sendRequest = function($scope, request) {
        var defer = $q.defer();
        request.callbackId = getCallbackId();
        callbacks[request.callbackId] = {
            scope: $scope,
            time: new Date(),
            request: request,
            cb: defer.resolve
        };

        if (Service.isConnected) {
            doSendRequest(callbacks[request.callbackId]);
        }

        return defer.promise;
    };

    // Start Service!
    resetSocket();

    return Service;
}]);