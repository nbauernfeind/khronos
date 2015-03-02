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

Khronos was designed with you in mind. Everyone's infrastructure differs; there are no magic bullets. I also
expect that people will come up with way better ideas than the ones I've put into this project. Khronos was designed,
from the ground up, with a plugin architecture in mind.
