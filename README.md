#### Note: This project is in super pre-alpha stage. Don't judge it just yet!

# Khronos

This is yet another timeseries project that aims to be simple, extensible, and scalable.

We use [Travis CI](http://about.travis-ci.org) for build verification.  [![Build Status](https://secure.travis-ci.org/khronos-metrics/khronos.svg?branch=master)](http://travis-ci.org/khronos-metrics/khronos)

## Getting Started

Download or build `khronos-app`, our default shaded application jar.

```xml
<dependency>
  <groupId>com.nefariouszhen.khronos</groupId>
  <artifactId>khronos-app</artifactId>
  <version>0.0.1</version>
</dependency>
```

Create a simple yaml configuration file to get started.

```yml
server:
  applicationConnectors:
    - type: http
      port: 2000
  adminConnectors:
    - type: http
      port: 2001
```

Run the application.

```bash
$ java -jar khronos-app-${version}.jar server config.yml
```

Visit http://localhost:2000/ to be sent to the dashboard.

## Collecting Data

Not implemented yet!

## Extracting Data

Not implemented yet!

## Exploring Data

Not implemented yet!

## Dashboarding Data

Not implemented yet!

## Plugin Architecture

From the ground up, Khronos was designed with a plugin architecture; it was
designed with you in mind. Extending Khronos is easy.

Start by extending KhornosExtensionConfiguration.

```scala
package my.khronos.extension

@JsonTypeName("my-extension")
class MyExtensionConfiguration extends KhronosExtensionConfiguration {
  override val additionalCSS = Seq("extensions/my-extension/css/my.css")
  override val additionalJS = Seq("extensions/my-extension/js/my.js")
  override def buildModule(): DropwizardModule[_] = new MyExtensionModule(this)
  // Plus any other configuration your extension might need.
}
```

Make your configuration discoverable by telling Jackson that it exists.

File: my-extension/src/main/resources/META-INF/services/com.nefariouszhen.khronos.KhronosExtensionConfiguration
```
my.khronos.extension.MyExtensionConfiguration
```

Write your Guice module referred to in the extension config.

```scala
class MyExtensionModule(cfg: MyExtensionConfiguration) extends DropwizardPrivateModule {
  override def doConfigure(): Unit = {
    bind[MyExtensionResource].asEagerSingleton()
    // Bind anything your extension needs!
  }

  override def install(env: Environment): Unit = {
    env.jersey().register(instance[MyExtensionResource])
    // The complete dropwizard environment is at your fingertips!
  }
}
```

Modify your config.yml file to load your extension.

```yml
extensions:
  - type: my-extension
  # Add any other configuration elements that you added in step 1.
```

Note: Don't forget to package your extension and make it available to the java
process by adding it to the class path or rebundling the fat jar.

### Adding your own Widget.

You can extend what widgets are available by writing your own. Widgets are
broken into four sections. There is the regular UI, the config UI, the
angular controller (and other javascript), and the css. Let's make a simple
widget.

File: `my-widget.cfg.html`

```html
<div class="form-group">
    <label ng-if="widget.editable" class="control-label">Sample Input: </label>
    <input type="text" class="form-control" placeholder="Write something here..."
           ng-model="widget.config.text">
</div>
```

File: `my-widget.html`
```html
<div class="mywidget" ng-controller="MyWidgetCtrl">
    <div ng-if="!widget.editable">
        <div ng-if="widget.config.text.length == 0" class="form-text"><span>No Text Specified</span></div>
        <div ng-if="widget.config.text.length" ng-show="storage.currSize != 'S'">
            <span class="text">{{widget.config.text}}</span>
        </div>
    </div>
</div>
```

File: `my-widget.js`
```javascript
khronosApp.controller('MyWidgetCtrl', ['$scope', function ($scope) {
    // Do nothing in this example.
});
```

File: `my-widget.css`
```css
.mywidget {
    overflow-x: hidden;
}
```

The backend binding.

```scala
class MyWidgetConfig extends Widget[Unit] {
  @JsonProperty
  val name: String = "My Widget"

  @JsonProperty
  val configPartial: String = "extensions/mywidget/partials/my-widget.cfg.html"

  @JsonProperty
  val partial: String = "extensions/mywidget/partials/my-widget.html"
}
```

```scala
class MyExtensionService {
  @Inject
  private[this] def installMyWidget(widgets: WidgetRegistry): Unit = {
    widgets.addWidget(new MyWidgetConfig)
  }
}
```

Note: Don't forget to bind[MyExtensionService] in MyExtensionModule.

### Adding an asynchronous remote procedure call (RPC).

You need a request token that identifies your RPC.

```scala
package my.khronos.extension

@JsonTypeName("my-extension-rpc1")
case class MyExtensionRPC1( ... ) extends WebSocketRequest
```

Make your request discoverable by telling Jackson that it exists.

File: my-extension/src/main/resrouces/META-INF/services/com.nefariouszhen.khronos.websocket.WebSocketRequest
```
my.khronos.extension.MyExtensionRPC1
```

Create your backend WebHook.

```scala
class MyExtensionService {
  @Inject
  private[this] def registerWebHooks(ws: WebSocketManager): Unit = {
    ws.registerCallback(onMyExtensionRPC1)
  }

  private[this] def onMyExtensionRPC1(writer: WebSocketWriter, request: MyExtensionRPC1): Unit = {
    // Respond to RPC.
  }
}
```

Note: Don't forget to bind[MyExtensionService] in MyExtensionModule.

Invoke the RPC from JS.

```javascript
khronosApp.controller('MyWidgetCtrl', ['$scope', 'WebSocket', function ($scope, WebSocket) {
    var params = {
        type: "my-extension-rpc1",
        // ... (other parameters in MyExtensionRPC1)
    };
    var request = WebSocket.sendRequest($scope, params, function(response) {
        console.log(response);
    });
    // Note that WebSocket.sendRecurringRequest allows you to keep sending data
    // to the same request handler until request.cancel() is called.
});
```

### Adding your own tab.

It makes sense to be able to completely add new views/pages/tabs. If you're
interested in having this functionality before I get around to it, please let
me know; I'd be happy to work on it.

### Future Example Work

I'm going to put these into their own repository. If you want/need this please
let me know and I can prioritize the work.
