'use strict';

khronosApp.controller('StatusWidgetCtrl', ['$scope', 'WebSocket', function ($scope, WebSocket) {
    //function subscribe (arg, query) {
    //    DataStream.subscribeMetric($scope, query, function (v) {
    //        console.log("recv: ", v);
    //        $scope[arg] = v.value;
    //    });
    //}
    //
    //function kvp (key, value) {
    //    return {k: key, v: value};
    //}
    //
    //function subscribeQuery (type) {
    //    subscribe(type, [kvp('app', 'khronos'), kvp('system', 'multiplexus'), kvp('type', type)]);
    //}
    //
    //subscribeQuery('numInPoints');
    //subscribeQuery('numOutPoints');
    //subscribeQuery('numQueriesActive');
    //subscribeQuery('numStreamsActive');
}]);