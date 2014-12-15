angular.module('angular-displaymode', [])
    .directive('ngDisplayMode', function ($window) {
        return {
            restrict: 'A',
            scope: {
                ngDisplayMode: '='
            },
            template: '<span class="mobile"></span><span class="tablet"></span><span class="tablet-landscape"></span><span class="desktop"></span>',
            link: function (scope, elem, attrs) {
                var markers = elem.find('span');

                function isVisible(element) {
                    return element
                        && element.style.display != 'none'
                        && element.offsetWidth
                        && element.offsetHeight;
                }

                function update() {
                    angular.forEach(markers, function (element) {
                        if (isVisible(element)) {
                            scope.ngDisplayMode = element.className;
                            return false;
                        }
                    });
                }

                var t;
                angular.element($window).bind('resize', function () {
                    clearTimeout(t);
                    t = setTimeout(function () {
                        scope.$apply(update);
                    }, 25);
                });

                update();
            }
        };
    });